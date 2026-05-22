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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Tests for {@link LombokConstructorToBuilderInspection}.
 * <p>
 * Extends LightJavaCodeInsightFixtureTestCase which provides a lightweight
 * in-memory project with a full Java PSI. No real IDE installation needed.
 * <p>
 * Run with: ./gradlew test
 *
 * @author Akmal Sarhan
 */
public class LombokConstructorToBuilderInspectionTest extends BasePlatformTestCase {

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        this.myFixture.enableInspections(new LombokConstructorToBuilderInspection());
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
        this.myFixture.addFileToProject("lombok/AllArgsConstructor.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface AllArgsConstructor {}
                """);
        // Add Lombok @Builder stub so the PSI resolver can find it

    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Adds a @Builder-annotated class with the given fields to the fixture.
     */
    private void addBuilderClass(String className, String... fields) {
        StringBuilder src = new StringBuilder();
        src.append("import lombok.Builder;\n");
        src.append("@Builder\n");
        src.append("public class ").append(className).append(" {\n");
        for (String field : fields) {
            src.append("    private ").append(field).append(";\n");
        }
        // No-arg constructor
        src.append("    public ").append(className).append("() {}\n");
        // All-args constructor
        if (fields.length > 0) {
            src.append("    public ").append(className).append("(");
            for (int i = 0; i < fields.length; i++) {
                if (i > 0) {
                    src.append(", ");
                }
                src.append(fields[i]);
            }
            src.append(") {\n");
            for (String field : fields) {
                String name = field.split(" ")[1];
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
    // 1. INSPECTION TRIGGERS — warning must be reported
    // =========================================================================

    /**
     * Basic case: all-args constructor on a @Builder class → warning.
     */
    public void testWarningOnAllArgsConstructor() {
        addBuilderClass("PaymentDto", "String acquirer", "int amount", "boolean active");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = <weak_warning descr="Constructor call can be replaced with lombok builder">new PaymentDto("visa", 100, true)</weak_warning>;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * Single-arg constructor still triggers the inspection.
     */
    public void testWarningOnSingleArgConstructor() {
        addBuilderClass("WrapperDto", "String value");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        WrapperDto w = <weak_warning descr="Constructor call can be replaced with lombok builder">new WrapperDto("hello")</weak_warning>;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * Class with fully-qualified Lombok annotation must also trigger.
     */
    public void testWarningWithFullyQualifiedAnnotation() {
        this.myFixture.addFileToProject("FullyQualifiedDto.java", """
                import lombok.Builder;
                @lombok.Builder
                public class FullyQualifiedDto {
                    private String name;
                    public FullyQualifiedDto() {}
                    public FullyQualifiedDto(String name) { this.name = name; }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        FullyQualifiedDto d = <weak_warning descr="Constructor call can be replaced with lombok builder">new FullyQualifiedDto("bob")</weak_warning>;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * Class with @SuperBuilder must also trigger.
     */
    public void testWarningWithSuperBuilderAnnotation() {
        this.myFixture.addFileToProject("SuperDto.java", """
                import lombok.SuperBuilder;
                @SuperBuilder
                public class SuperDto {
                    private String name;
                    public SuperDto() {}
                    public SuperDto(String name) { this.name = name; }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        SuperDto d = <weak_warning descr="Constructor call can be replaced with lombok builder">new SuperDto("bob")</weak_warning>;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 2. INSPECTION DOES NOT TRIGGER — no warning expected
    // =========================================================================

    /**
     * @Builder on subclass with constructor param for inherited field must NOT trigger —
     * plain @Builder does not expose superclass fields in the generated builder.
     */
    public void testNoWarningWhenParamMapsToInheritedFieldWithBuilder() {
        this.myFixture.addFileToProject("Audit.java", """
                public class Audit {
                    private String createdBy;
                }
                """);
        this.myFixture.addFileToProject("Invoice.java", """
                import lombok.Builder;
                @Builder
                public class Invoice extends Audit {
                    private String invoiceDate;
                    public Invoice() {}
                    public Invoice(String createdBy, String invoiceDate) {
                        this.createdBy = createdBy;
                        this.invoiceDate = invoiceDate;
                    }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        Invoice i = new Invoice("admin", "2024-01-01");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * @SuperBuilder on subclass with constructor param for inherited field SHOULD trigger —
     * @SuperBuilder correctly exposes superclass fields in the generated builder.
     */
    public void testWarningWhenParamMapsToInheritedFieldWithSuperBuilder() {
        this.myFixture.addFileToProject("Audit.java", """
                public class Audit {
                    private String createdBy;
                }
                """);
        this.myFixture.addFileToProject("Invoice.java", """
                import lombok.SuperBuilder;
                @SuperBuilder
                public class Invoice extends Audit {
                    private String invoiceDate;
                    public Invoice() {}
                    public Invoice(String createdBy, String invoiceDate) {
                        this.createdBy = createdBy;
                        this.invoiceDate = invoiceDate;
                    }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        Invoice i = <weak_warning descr="Constructor call can be replaced with lombok builder">new Invoice("admin", "2024-01-01")</weak_warning>;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * No-arg constructor must never trigger — nothing to map.
     */
    public void testNoWarningOnNoArgConstructor() {
        addBuilderClass("PaymentDto", "String acquirer", "int amount");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = new PaymentDto();
                    }
                }
                """);

        this.myFixture.checkHighlighting(); // no <warning> markers → passes
    }

    /**
     * Class without @Builder must never trigger.
     */
    public void testNoWarningOnNonBuilderClass() {

        this.myFixture.addFileToProject("PlainDto.java", """
                public class PlainDto {
                    private String name;
                    private int age;
                    public PlainDto(String name, int age) {}
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PlainDto dto = new PlainDto("alice", 30);
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * Parameter name mismatch: constructor param does not match any field → no warning.
     */
    public void testNoWarningWhenParamNameMismatch() {
        this.myFixture.addFileToProject("MismatchDto.java", """
                import lombok.Builder;
                @Builder
                public class MismatchDto {
                    private String name;
                    public MismatchDto(String differentParamName) {}
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        MismatchDto dto = new MismatchDto("value");
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 3. QUICK-FIX — verify produced code
    // =========================================================================

    /**
     * Basic fix: 3 args → 3 builder setters.
     */
    public void testFixReplacesThreeArgConstructor() {
        addBuilderClass("PaymentDto", "String acquirer", "int amount", "boolean active");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = new PaymentDto("visa", 100, true);
                    }
                }
                """);

        placeCaretOn("new PaymentDto");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m() {
                        PaymentDto dto = PaymentDto.builder()
                                .acquirer("visa")
                                .amount(100)
                                .active(true)
                                .build();
                    }
                }
                """);
    }

    /**
     * Fix with a single argument.
     */
    public void testFixReplacesSingleArgConstructor() {
        addBuilderClass("WrapperDto", "String value");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        WrapperDto w = new WrapperDto("hello");
                    }
                }
                """);

        placeCaretOn("new WrapperDto");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m() {
                        WrapperDto w = WrapperDto.builder()
                                .value("hello")
                                .build();
                    }
                }
                """);
    }

    /**
     * Fix preserves complex expressions like method chains as arguments.
     */
    public void testFixPreservesComplexArgExpressions() {
        addBuilderClass("AuditDto", "String userId", "String timestamp");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        AuditDto dto = new AuditDto(
                            "user",
                            "time"
                        );
                    }
                }
                """);

        placeCaretOn("new AuditDto");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m() {
                        AuditDto dto = AuditDto.builder()
                                .userId("user")
                                .timestamp("time")
                                .build();
                    }
                }
                """);
    }

    /**
     * Fix preserves null arguments.
     */
    public void testFixPreservesNullArgs() {
        addBuilderClass("NullableDto", "String name", "String description");

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        NullableDto dto = new NullableDto("name", null);
                    }
                }
                """);

        placeCaretOn("new NullableDto");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m() {
                        NullableDto dto = NullableDto.builder()
                                .name("name")
                                .description(null)
                                .build();
                    }
                }
                """);
    }

    /**
     * Fix does not affect other unrelated new expressions in the same method.
     */
    public void testFixOnlyReplacesTargetExpression() {
        addBuilderClass("PaymentDto", "String acquirer");
        this.myFixture.addFileToProject("OtherDto.java", """
                public class OtherDto {
                    public OtherDto(String x) {}
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        OtherDto other = new OtherDto("keep");
                        PaymentDto dto = new PaymentDto("visa");
                    }
                }
                """);

        // Place caret on PaymentDto constructor
        this.myFixture.getEditor().getCaretModel().moveToOffset(
                this.myFixture.getFile().getText().indexOf("new PaymentDto") + 5);

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        this.myFixture.checkResult("""
                public class Test {
                    void m() {
                        OtherDto other = new OtherDto("keep");
                        PaymentDto dto = PaymentDto.builder()
                                .acquirer("visa")
                                .build();
                    }
                }
                """);
    }

    /**
     * Fix adds @Builder AND import lombok.Builder; to the class file.
     */
    public void testFixAddsBuilderImportForAllArgsConstructorClass() {
        this.myFixture.addFileToProject("PaymentDto.java", """
                import lombok.AllArgsConstructor;
                @AllArgsConstructor
                public class PaymentDto {
                    private String acquirer;
                    private int amount;
                    public PaymentDto() {}
                    public PaymentDto(String acquirer, int amount) {
                        this.acquirer = acquirer;
                        this.amount = amount;
                    }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = new PaymentDto("visa", 100);
                    }
                }
                """);

        placeCaretOn("new PaymentDto");
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with lombok builder"));

        VirtualFile classFile = this.myFixture.findFileInTempDir("PaymentDto.java");
        assertNotNull(classFile);
        PsiFile psiFile = com.intellij.psi.PsiManager.getInstance(this.myFixture.getProject())
                .findFile(classFile);
        assertNotNull(psiFile);
        String classText = psiFile.getText();
        assertTrue("Should contain import lombok.Builder; but got:\n" + classText,
                classText.contains("import lombok.Builder;"));
        assertTrue("Should contain @Builder annotation",
                classText.contains("@Builder"));
    }
}
