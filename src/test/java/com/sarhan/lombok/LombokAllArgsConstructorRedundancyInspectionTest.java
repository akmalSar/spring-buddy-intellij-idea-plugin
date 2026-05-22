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
 * Tests for {@link LombokAllArgsConstructorRedundancyInspection} and {@link AllArgsConstructorRedundancyFix}.
 *
 * @author Akmal Sarhan
 */
public class LombokAllArgsConstructorRedundancyInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new LombokAllArgsConstructorRedundancyInspection());

        this.myFixture.addFileToProject("lombok/AllArgsConstructor.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface AllArgsConstructor {}
                """);
        this.myFixture.addFileToProject("lombok/RequiredArgsConstructor.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface RequiredArgsConstructor {}
                """);
        this.myFixture.addFileToProject("org/springframework/stereotype/Component.java", """
                package org.springframework.stereotype;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Component {}
                """);
        this.myFixture.addFileToProject("org/springframework/boot/context/properties/ConfigurationProperties.java", """
                package org.springframework.boot.context.properties;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface ConfigurationProperties {}
                """);
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
        this.myFixture.addFileToProject("lombok/NoArgsConstructor.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface NoArgsConstructor {}
                """);
    }

    // =========================================================================
    // 1. REDUNDANT — inspection triggers
    // =========================================================================

    /**
     * {@code @AllArgsConstructor} + manual constructor + zero usages → redundant.
     */
    public void testFlagsRedundantAllArgsConstructor() {
        this.myFixture.configureByText("PaymentDto.java", """
                import lombok.AllArgsConstructor;
                <warning descr="Redundant @AllArgsConstructor annotation">@AllArgsConstructor</warning>
                public class PaymentDto {
                    private int acquirer;
                    private int amount;
                    public PaymentDto(int acquirer, int amount) {
                        this.acquirer = acquirer;
                        this.amount = amount;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    public void testDoesNotFlagInjectedJavaSnippetInNonJavaFile() {
        this.myFixture.configureByText("README.md", """
                import lombok.AllArgsConstructor;
                @AllArgsConstructor
                public class PaymentDto {
                    private int acquirer;
                    private int amount;
                    public PaymentDto(int acquirer, int amount) {
                        this.acquirer = acquirer;
                        this.amount = amount;
                    }
                }
                """);

        this.myFixture.checkHighlighting(false, false, true, true);
    }

    /**
     * Fix removes the redundant annotation.
     */
    public void testFixRemovesRedundantAnnotation() {
        this.myFixture.addFileToProject("PaymentDto.java", """
                import lombok.AllArgsConstructor;
                @AllArgsConstructor
                public class PaymentDto {
                    private int acquirer;
                    private int amount;
                    public PaymentDto(int acquirer, int amount) {
                        this.acquirer = acquirer;
                        this.amount = amount;
                    }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = null;
                    }
                }
                """);

        this.myFixture.doHighlighting();

        // Open PaymentDto.java and place caret on the annotation
        VirtualFile paymentDtoFile = this.myFixture.findFileInTempDir("PaymentDto.java");
        assertNotNull(paymentDtoFile);
        this.myFixture.openFileInEditor(paymentDtoFile);
        String fileText = this.myFixture.getFile().getText();
        int offset = fileText.indexOf("@AllArgsConstructor");
        assertTrue("Caret anchor not found: @AllArgsConstructor", offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@AllArgsConstructor".length() / 2));

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Remove redundant @AllArgsConstructor"));

        VirtualFile classFile = this.myFixture.findFileInTempDir("PaymentDto.java");
        assertNotNull(classFile);
        PsiFile psiFile = com.intellij.psi.PsiManager.getInstance(this.myFixture.getProject()).findFile(classFile);
        assertNotNull(psiFile);
        String text = psiFile.getText();
        assertFalse("@AllArgsConstructor should be removed", text.contains("@AllArgsConstructor"));
        assertTrue("Constructor should remain", text.contains("public PaymentDto(int acquirer, int amount)"));
    }

    // =========================================================================
    // 2. NOT REDUNDANT — manual constructor missing
    // =========================================================================

    public void testNoWarningWhenNoManualConstructor() {
        this.myFixture.addFileToProject("PaymentDto.java", """
                import lombok.AllArgsConstructor;
                @AllArgsConstructor
                public class PaymentDto {
                    private String acquirer;
                    private int amount;
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = null;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 3. NOT REDUNDANT — constructor is used
    // =========================================================================

    public void testNoWarningWhenConstructorIsUsed() {
        this.myFixture.addFileToProject("PaymentDto.java", """
                import lombok.AllArgsConstructor;
                @AllArgsConstructor
                public class PaymentDto {
                    private String acquirer;
                    private int amount;
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

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 4. NOT REDUNDANT — Builder + NoArgsConstructor trifecta
    // =========================================================================

    public void testNoWarningWhenBuilderAndNoArgsConstructorPresent() {
        this.myFixture.addFileToProject("PaymentDto.java", """
                import lombok.AllArgsConstructor;
                import lombok.Builder;
                import lombok.NoArgsConstructor;
                @AllArgsConstructor
                @Builder
                @NoArgsConstructor
                public class PaymentDto {
                    private String acquirer;
                    private int amount;
                    public PaymentDto(String acquirer, int amount) {
                        this.acquirer = acquirer;
                        this.amount = amount;
                    }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = null;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * @SuperBuilder + @NoArgsConstructor also forms a trifecta requiring @AllArgsConstructor.
     */
    public void testNoWarningWhenSuperBuilderAndNoArgsConstructorPresent() {
        this.myFixture.addFileToProject("PaymentDto.java", """
                import lombok.AllArgsConstructor;
                import lombok.SuperBuilder;
                import lombok.NoArgsConstructor;
                @AllArgsConstructor
                @SuperBuilder
                @NoArgsConstructor
                public class PaymentDto {
                    private String acquirer;
                    private int amount;
                    public PaymentDto(String acquirer, int amount) {
                        this.acquirer = acquirer;
                        this.amount = amount;
                    }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = null;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 5. NOT REDUNDANT — Spring managed
    // =========================================================================

    /**
     * Spring component constructor can be used for dependency injection, so @AllArgsConstructor
     * is not safely redundant even if fields are not final.
     */
    public void testNoWarningWhenSpringComponentWithoutFinalFields() {
        this.myFixture.addFileToProject("PaymentDto.java", """
                import lombok.AllArgsConstructor;
                import org.springframework.stereotype.Component;
                @AllArgsConstructor
                @Component
                public class PaymentDto {
                    private String acquirer;
                    private int amount;
                    public PaymentDto(String acquirer, int amount) {
                        this.acquirer = acquirer;
                        this.amount = amount;
                    }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = null;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * Spring component with uninitialized final fields needs constructor injection → NOT redundant.
     */
    public void testNoWarningWhenSpringComponentWithFinalFields() {
        this.myFixture.addFileToProject("PaymentDto.java", """
                import lombok.AllArgsConstructor;
                import org.springframework.stereotype.Component;
                @AllArgsConstructor
                @Component
                public class PaymentDto {
                    private final String acquirer;
                    private int amount;
                    public PaymentDto(String acquirer, int amount) {
                        this.acquirer = acquirer;
                        this.amount = amount;
                    }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = null;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 6. NOT REDUNDANT — @ConfigurationProperties
    // =========================================================================

    public void testNoWarningWhenConfigurationProperties() {
        this.myFixture.addFileToProject("PaymentDto.java", """
                import lombok.AllArgsConstructor;
                import org.springframework.boot.context.properties.ConfigurationProperties;
                @AllArgsConstructor
                @ConfigurationProperties
                public class PaymentDto {
                    private String acquirer;
                    private int amount;
                    public PaymentDto(String acquirer, int amount) {
                        this.acquirer = acquirer;
                        this.amount = amount;
                    }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = null;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 7. NOT REDUNDANT — uninitialized final fields
    // =========================================================================

    public void testNoWarningWhenUninitializedFinalFields() {
        this.myFixture.addFileToProject("PaymentDto.java", """
                import lombok.AllArgsConstructor;
                @AllArgsConstructor
                public class PaymentDto {
                    private final String acquirer;
                    private int amount;
                    public PaymentDto(String acquirer, int amount) {
                        this.acquirer = acquirer;
                        this.amount = amount;
                    }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = null;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 8. NOT REDUNDANT — @RequiredArgsConstructor (different annotation)
    // =========================================================================

    public void testNoWarningForRequiredArgsConstructor() {
        this.myFixture.addFileToProject("PaymentDto.java", """
                import lombok.RequiredArgsConstructor;
                @RequiredArgsConstructor
                public class PaymentDto {
                    private final String acquirer;
                    private int amount;
                    public PaymentDto(String acquirer, int amount) {
                        this.acquirer = acquirer;
                        this.amount = amount;
                    }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        PaymentDto dto = null;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 9. NOT REDUNDANT — subclass constructor params match parent fields
    // =========================================================================

    /**
     * Subclass constructor parameters that match parent fields do NOT make
     * {@code @AllArgsConstructor} redundant on the subclass. Lombok's generated
     * all-args constructor would include the subclass's own fields too.
     */
    public void testNoWarningWhenSubclassConstructorOnlyInitializesParentFields() {
        this.myFixture.addFileToProject("LookupDto.java", """
                public class LookupDto {
                    private Long id;
                }
                """);
        this.myFixture.addFileToProject("LookupPaymentStateDto.java", """
                import lombok.AllArgsConstructor;
                @AllArgsConstructor
                public class LookupPaymentStateDto extends LookupDto {
                    public LookupPaymentStateDto(Long id) {
                        super(id);
                    }
                    private String pillColor;
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        LookupPaymentStateDto dto = null;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * After converting all {@code new LookupDto(1L)} to builder calls, the
     * explicit parent constructor has zero direct usages. But subclasses still
     * call {@code super(id)}, so the constructor is NOT unused and
     * {@code @AllArgsConstructor} must stay.
     */
    public void testNoWarningWhenParentConstructorOnlyUsedViaSuper() {
        this.myFixture.addFileToProject("LookupDto.java", """
                import lombok.AllArgsConstructor;
                @AllArgsConstructor
                public class LookupDto {
                    private Long id;
                    public LookupDto(Long id) {
                        this.id = id;
                    }
                }
                """);
        this.myFixture.addFileToProject("LookupDocumentTemplateDto.java", """
                public class LookupDocumentTemplateDto extends LookupDto {
                    public LookupDocumentTemplateDto(Long id) {
                        super(id);
                    }
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        LookupDto dto = null;
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 10. METADATA
    // =========================================================================

    public void testInspectionHasDisplayName() {
        LombokAllArgsConstructorRedundancyInspection inspection = new LombokAllArgsConstructorRedundancyInspection();
        assertNotNull(inspection.getDisplayName());
        assertFalse(inspection.getDisplayName().isBlank());
    }

    public void testInspectionGroupName() {
        LombokAllArgsConstructorRedundancyInspection inspection = new LombokAllArgsConstructorRedundancyInspection();
        assertNotNull(inspection.getGroupDisplayName());
    }
}
