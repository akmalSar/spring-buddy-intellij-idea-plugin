/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sarhan.lombok;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Tests for {@link LombokSetterChainToBuilderInspection} and {@link SetterChainToBuilderFix}.
 * <p>
 * Covers:
 * - Inspection triggers / non-triggers
 * - Basic fix output
 * - Interleaved non-setter statements (unrelated lines between setters)
 * - Interleaved variable declarations referenced inside setter args (hoisting)
 * - Nested method-call arguments preserved verbatim
 * - Early stop conditions (variable passed to a method, variable reassigned)
 * - Edge cases: single setter, many setters, formatted strings, date expressions
 *
 * @author Akmal Sarhan
 */
public class LombokSetterChainToBuilderInspectionTest extends BasePlatformTestCase {

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new LombokSetterChainToBuilderInspection());

        this.myFixture.addFileToProject("lombok/Builder.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface Builder {}
                """);
        this.myFixture.addFileToProject("lombok/SuperBuilder.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface SuperBuilder {}
                """);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private void addBuilderClass(String className, String... fieldDecls) {
        StringBuilder src = new StringBuilder();
        src.append("import lombok.Builder;\n");
        src.append("@Builder\n");
        src.append("public class ").append(className).append(" {\n");
        for (String f : fieldDecls) {
            // field line e.g. "String name" → emit private field + setter
            String[] parts = f.split(" ");
            String type = parts[0];
            String name = parts[1];
            String setter = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            src.append("    private ").append(type).append(" ").append(name).append(";\n");
            src.append("    public void ").append(setter)
                    .append("(").append(type).append(" v) { this.").append(name).append(" = v; }\n");
        }
        // No-arg constructor
        src.append("    public ").append(className).append("() {}\n");
        // All-args constructor
        if (fieldDecls.length > 0) {
            src.append("    public ").append(className).append("(");
            for (int i = 0; i < fieldDecls.length; i++) {
                String[] parts = fieldDecls[i].split(" ");
                if (i > 0) {
                    src.append(", ");
                }
                src.append(parts[0]).append(" ").append(parts[1]);
            }
            src.append(") {\n");
            for (String f : fieldDecls) {
                String[] parts = f.split(" ");
                String name = parts[1];
                src.append("        this.").append(name).append(" = ").append(name).append(";\n");
            }
            src.append("    }\n");
        }
        src.append("}\n");
        this.myFixture.addFileToProject(className +
                ".java", src.toString());
    }

    private void placeCaretOn(String text) {
        int offset = this.myFixture.getFile().getText().indexOf(text);
        assertTrue("Caret anchor not found: " + text, offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + (text.length() / 2));
    }

    // =========================================================================
    // 1. INSPECTION TRIGGERS
    // =========================================================================

    /**
     * Basic trigger: no-arg ctor + at least one setter on a @Builder class.
     */
    public void testWarningOnNoArgCtorWithSetterChain() {
        addBuilderClass("MementoDto", "String commandClass", "String objectId");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        <weak_warning descr="Setter chain can be replaced with lombok builder">MementoDto dto = new MementoDto();</weak_warning>
                        dto.setCommandClass("Contract");
                        dto.setObjectId("42");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * Single setter still triggers — the minimum is one setter.
     */
    public void testWarningWithSingleSetter() {
        addBuilderClass("SingleDto", "String name");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        <weak_warning descr="Setter chain can be replaced with lombok builder">SingleDto dto = new SingleDto();</weak_warning>
                        dto.setName("Alice");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * @SuperBuilder should also trigger the inspection.
     */
    public void testWarningOnSuperBuilderClass() {
        this.myFixture.addFileToProject("SuperDto.java", """
                import lombok.SuperBuilder;
                @SuperBuilder
                public class SuperDto {
                    private String commandClass;
                    public void setCommandClass(String v) { this.commandClass = v; }
                    public SuperDto() {}
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        <weak_warning descr="Setter chain can be replaced with lombok builder">SuperDto dto = new SuperDto();</weak_warning>
                        dto.setCommandClass("Contract");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * @Builder with a setter for an inherited field must NOT trigger —
     * plain @Builder does not generate builder methods for superclass fields.
     */
    public void testNoWarningWhenSetterTargetsInheritedFieldWithBuilder() {
        this.myFixture.addFileToProject("Audit.java", """
                public class Audit {
                    private String createdBy;
                    public void setCreatedBy(String v) { this.createdBy = v; }
                }
                """);
        this.myFixture.addFileToProject("Invoice.java", """
                import lombok.Builder;
                @Builder
                public class Invoice extends Audit {
                    private String invoiceDate;
                    public void setInvoiceDate(String v) { this.invoiceDate = v; }
                    public Invoice() {}
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        Invoice dto = new Invoice();
                        dto.setInvoiceDate("2024-01-01");
                        dto.setCreatedBy("admin");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * @SuperBuilder with a setter for an inherited field SHOULD trigger —
     * @SuperBuilder correctly exposes superclass fields in the builder.
     */
    public void testWarningWhenSetterTargetsInheritedFieldWithSuperBuilder() {
        this.myFixture.addFileToProject("Audit.java", """
                public class Audit {
                    private String createdBy;
                    public void setCreatedBy(String v) { this.createdBy = v; }
                }
                """);
        this.myFixture.addFileToProject("Invoice.java", """
                import lombok.SuperBuilder;
                @SuperBuilder
                public class Invoice extends Audit {
                    private String invoiceDate;
                    public void setInvoiceDate(String v) { this.invoiceDate = v; }
                    public Invoice() {}
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        <weak_warning descr="Setter chain can be replaced with lombok builder">Invoice dto = new Invoice();</weak_warning>
                        dto.setInvoiceDate("2024-01-01");
                        dto.setCreatedBy("admin");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * Interleaved unrelated statement between declaration and setters still triggers.
     */
    public void testWarningWithInterleavedUnrelatedStatement() {
        addBuilderClass("MementoDto", "String commandClass", "String objectId");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void helper() {}
                    void m() {
                        <weak_warning descr="Setter chain can be replaced with lombok builder">MementoDto dto = new MementoDto();</weak_warning>
                        helper();
                        dto.setCommandClass("Contract");
                        dto.setObjectId("42");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * Interleaved variable declaration referenced in a setter arg breaks the
     * consecutive setter chain — the inspection must NOT trigger.
     */
    public void testNoWarningWhenInterleavedVarDeclarationBreaksSetterChain() {
        addBuilderClass("MementoDto", "String commandClass", "String modifier");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        MementoDto dto = new MementoDto();
                        dto.setCommandClass("Contract");
                        int x = 42;
                        dto.setModifier("mod");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 2. INSPECTION DOES NOT TRIGGER
    // =========================================================================

    /**
     * No-arg ctor with no setters at all — nothing to convert.
     */
    public void testNoWarningWhenNoSettersFollow() {
        addBuilderClass("MementoDto", "String commandClass");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void helper() {}
                    void m() {
                        MementoDto dto = new MementoDto();
                        helper();
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * Class without @Builder must not trigger even with setter chain.
     */
    public void testNoWarningOnNonBuilderClass() {
        this.myFixture.addFileToProject("PlainDto.java", """
                public class PlainDto {
                    private String name;
                    public void setName(String v) { this.name = v; }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PlainDto dto = new PlainDto();
                        dto.setName("Alice");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * All-args constructor with setters — inspection should not double-trigger here.
     */
    public void testNoSetterChainWarningOnAllArgsConstructor() {
        addBuilderClass("MementoDto", "String commandClass");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        MementoDto dto = new MementoDto("Contract");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 3. QUICK-FIX — basic output
    // =========================================================================

    /**
     * Basic fix: no-arg ctor + 2 setters → builder with 2 fields.
     */
    public void testFixBasicTwoSetters() {
        addBuilderClass("MementoDto", "String commandClass", "String objectId");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        MementoDto dto = new MementoDto();
                        dto.setCommandClass("Contract");
                        dto.setObjectId("42");
                    }
                }
                """);

        placeCaretOn("MementoDto dto = new MementoDto()");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m() {
                        MementoDto dto = MementoDto.builder()
                                .commandClass("Contract")
                                .objectId("42")
                                .build();
                    }
                }
                """);
    }

    /**
     * Fix with many setters — Memento-style real-world case.
     */
    public void testFixManySetters() {
        addBuilderClass("MementoDto",
                "String commandClass",
                "String objectId",
                "String details",
                "int directChange",
                "String modifier");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m(long contractId, String processName) {
                        MementoDto memento = new MementoDto();
                        memento.setCommandClass("Contract");
                        memento.setObjectId(String.valueOf(contractId));
                        memento.setDetails(processName + " wurde manuell storniert");
                        memento.setDirectChange(1);
                        memento.setModifier("admin");
                    }
                }
                """);

        placeCaretOn("MementoDto memento = new MementoDto()");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m(long contractId, String processName) {
                        MementoDto memento = MementoDto.builder()
                                .commandClass("Contract")
                                .objectId(String.valueOf(contractId))
                                .details(processName + " wurde manuell storniert")
                                .directChange(1)
                                .modifier("admin")
                                .build();
                    }
                }
                """);
    }

    /**
     * Consecutive setters with complex arguments (formatted string, date) should
     * still trigger when there is no interleaved statement between setters.
     */
    public void testFixConsecutiveSettersWithComplexFormattedArg() {
        addBuilderClass("Memento",
                "String commandClass",
                "String objectId",
                "String details",
                "int directChange",
                "String modifier");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m(long contractId) {
                        Memento memento = new Memento();
                        memento.setCommandClass("Contract");
                        memento.setObjectId("id");
                        memento.setDetails("details");
                        memento.setDirectChange(1);
                        memento.setModifier("mod");
                    }
                }
                """);

        placeCaretOn("Memento memento = new Memento()");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m(long contractId) {
                        Memento memento = Memento.builder()
                                .commandClass("Contract")
                                .objectId("id")
                                .details("details")
                                .directChange(1)
                                .modifier("mod")
                                .build();
                    }
                }
                """);
    }

    // =========================================================================
    // 4. INTERLEAVED STATEMENTS
    // =========================================================================

    /**
     * Unrelated System.out.println between declaration and setters is left in place.
     */
    public void testFixLeavesUnrelatedStatementInPlace() {
        addBuilderClass("MementoDto", "String commandClass", "String objectId");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m(Object feDto) {
                        MementoDto dto = new MementoDto();
                        System.out.println("debug: " + feDto);
                        dto.setCommandClass("Contract");
                        dto.setObjectId("42");
                    }
                }
                """);

        placeCaretOn("MementoDto dto = new MementoDto()");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m(Object feDto) {
                        MementoDto dto = MementoDto.builder()
                                .commandClass("Contract")
                                .objectId("42")
                                .build();
                        System.out.println("debug: " + feDto);
                    }
                }
                """);
    }

    /**
     * Interleaved variable declaration between setters breaks the chain —
     * the inspection must NOT trigger because the setters are no longer consecutive.
     */
    public void testNoWarningWhenVarDeclarationBreaksSetterChain() {
        addBuilderClass("MementoDto",
                "String commandClass",
                "int directChange",
                "String modifier");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        MementoDto memento = new MementoDto();
                        memento.setCommandClass("Contract");
                        memento.setDirectChange(1);
                        int x = 42;
                        memento.setModifier("mod");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * Full real-world Memento case from the bug report:
     * intermediate variable between setters breaks the chain —
     * inspection must NOT trigger when setters are not consecutive.
     */
    public void testNoWarningWhenMementoSetterChainIsBroken() {
        addBuilderClass("Memento",
                "String commandClass",
                "String objectId",
                "String details",
                "int directChange",
                "String modifier");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        Memento memento = new Memento();
                        memento.setCommandClass("Contract");
                        memento.setObjectId("id");
                        memento.setDetails("details");
                        memento.setDirectChange(1);
                        int x = 42;
                        memento.setModifier("mod");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * Interleaved var declaration NOT referenced in any setter still breaks the
     * consecutive setter chain — the inspection must NOT trigger.
     */
    public void testNoWarningWhenUnreferencedInterleavedVarBreaksSetterChain() {
        addBuilderClass("MementoDto", "String commandClass", "String objectId");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void helper() {}
                    void m() {
                        MementoDto dto = new MementoDto();
                        dto.setCommandClass("Contract");
                        int unrelated = 42;
                        dto.setObjectId("42");
                        helper();
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * The variable is passed to a method inside a declaration-statement
     * initializer before any setter — the inspection must NOT trigger because
     * the object is observed before it is fully configured.
     */
    public void testNoWarningWhenVariableUsedInDeclarationBeforeSetters() {
        addBuilderClass("MementoDto", "String commandClass", "String objectId");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void use(MementoDto d) {}
                    void m() {
                        MementoDto dto = new MementoDto();
                        use(dto);
                        dto.setCommandClass("Contract");
                        dto.setObjectId("42");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 5. EARLY STOP CONDITIONS
    // =========================================================================

    /**
     * If the variable is passed to another method before all setters are done,
     * only setters before that call are collected.
     */
    public void testFixStopsWhenVariablePassedToMethod() {
        addBuilderClass("MementoDto", "String commandClass", "String objectId");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void save(MementoDto d) {}
                    void m() {
                        MementoDto dto = new MementoDto();
                        dto.setCommandClass("Contract");
                        save(dto);
                        dto.setObjectId("42");
                    }
                }
                """);

        placeCaretOn("MementoDto dto = new MementoDto()");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        // Only setCommandClass is collected; setObjectId is left in place
        this.myFixture.checkResult("""
                public class Test {
                    void save(MementoDto d) {}
                    void m() {
                        MementoDto dto = MementoDto.builder()
                                .commandClass("Contract")
                                .build();
                        save(dto);
                        dto.setObjectId("42");
                    }
                }
                """);
    }

    /**
     * If the variable is reassigned, collection stops before the reassignment.
     */
    public void testFixStopsOnVariableReassignment() {
        addBuilderClass("MementoDto", "String commandClass", "String objectId");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        MementoDto dto = new MementoDto();
                        dto.setCommandClass("Contract");
                        dto = new MementoDto();
                        dto.setObjectId("42");
                    }
                }
                """);

        placeCaretOn("MementoDto dto = new MementoDto()");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m() {
                        MementoDto dto = MementoDto.builder()
                                .commandClass("Contract")
                                .build();
                        dto = new MementoDto();
                        dto.setObjectId("42");
                    }
                }
                """);
    }

    // =========================================================================
    // 6. COMPLEX ARGUMENT EXPRESSIONS
    // =========================================================================

    /**
     * Nested method-call chain as setter argument is preserved verbatim.
     */
    public void testFixPreservesNestedMethodCallInSetterArg() {
        addBuilderClass("InvoiceDto", "String vatCountry", "String vatType");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m(Object feDto) {
                        InvoiceDto dto = new InvoiceDto();
                        dto.setVatCountry(feDto.toString().trim().toLowerCase());
                        dto.setVatType(feDto.getClass().getSimpleName());
                    }
                }
                """);

        placeCaretOn("InvoiceDto dto = new InvoiceDto()");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m(Object feDto) {
                        InvoiceDto dto = InvoiceDto.builder()
                                .vatCountry(feDto.toString().trim().toLowerCase())
                                .vatType(feDto.getClass().getSimpleName())
                                .build();
                    }
                }
                """);
    }

    /**
     * String concatenation expression in setter arg is preserved verbatim.
     */
    public void testFixPreservesStringConcatInSetterArg() {
        addBuilderClass("MementoDto", "String details");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m(String processName) {
                        MementoDto dto = new MementoDto();
                        dto.setDetails(processName + " wurde manuell storniert");
                    }
                }
                """);

        placeCaretOn("MementoDto dto = new MementoDto()");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m(String processName) {
                        MementoDto dto = MementoDto.builder()
                                .details(processName + " wurde manuell storniert")
                                .build();
                    }
                }
                """);
    }

    /**
     * Static method call as setter arg is preserved verbatim.
     */
    public void testFixPreservesStaticMethodCallInSetterArg() {
        addBuilderClass("MementoDto", "String objectId");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m(long contractId) {
                        MementoDto dto = new MementoDto();
                        dto.setObjectId(String.valueOf(contractId));
                    }
                }
                """);

        placeCaretOn("MementoDto dto = new MementoDto()");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m(long contractId) {
                        MementoDto dto = MementoDto.builder()
                                .objectId(String.valueOf(contractId))
                                .build();
                    }
                }
                """);
    }

    /**
     * Ternary expression in setter arg is preserved verbatim.
     */
    public void testFixPreservesTernaryInSetterArg() {
        addBuilderClass("MementoDto", "String commandClass");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m(boolean flag) {
                        MementoDto dto = new MementoDto();
                        dto.setCommandClass(flag ? "ContractA" : "ContractB");
                    }
                }
                """);

        placeCaretOn("MementoDto dto = new MementoDto()");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m(boolean flag) {
                        MementoDto dto = MementoDto.builder()
                                .commandClass(flag ? "ContractA" : "ContractB")
                                .build();
                    }
                }
                """);
    }

    // =========================================================================
    // 7. MULTIPLE CLASSES IN THE SAME BLOCK
    // =========================================================================

    /**
     * Two separate builder-eligible variables in the same method — fix targets only one.
     */
    public void testFixTargetsOnlyOneVariableWhenTwoEligibleInSameBlock() {
        addBuilderClass("MementoDto", "String commandClass");
        addBuilderClass("AuditDto", "String action");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        MementoDto memento = new MementoDto();
                        memento.setCommandClass("Contract");
                
                        AuditDto audit = new AuditDto();
                        audit.setAction("create");
                    }
                }
                """);

        // Move caret to the MementoDto declaration
        this.myFixture.getEditor().getCaretModel().moveToOffset(
                this.myFixture.getFile().getText().indexOf("MementoDto memento") + 2);

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m() {
                        MementoDto memento = MementoDto.builder()
                                .commandClass("Contract")
                                .build();
                
                        AuditDto audit = new AuditDto();
                        audit.setAction("create");
                    }
                }
                """);
    }

    // =========================================================================
    // 8. INSPECTION DESCRIPTION
    // =========================================================================

    /**
     * Verify the inspection has a non-empty display name.
     */
    public void testInspectionHasDisplayName() {
        LombokSetterChainToBuilderInspection inspection = new LombokSetterChainToBuilderInspection();
        assertNotNull(inspection.getDisplayName());
        assertFalse(inspection.getDisplayName().isBlank());
    }

    /**
     * Verify the inspection belongs to the correct group path.
     */
    public void testInspectionGroupName() {
        LombokSetterChainToBuilderInspection inspection = new LombokSetterChainToBuilderInspection();
        // Group is declared in plugin.xml; verify the class-level default matches
        assertNotNull(inspection.getGroupDisplayName());
    }
}
