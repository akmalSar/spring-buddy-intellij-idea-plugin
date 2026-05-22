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
 * @author Akmal Sarhan
 */
public class LombokConstructorAnnotationInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new LombokConstructorAnnotationInspection());

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
        this.myFixture.addFileToProject("lombok/NoArgsConstructor.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface NoArgsConstructor {}
                """);
        this.myFixture.addFileToProject("lombok/Builder.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface Builder {}
                """);
        this.myFixture.addFileToProject("lombok/NonNull.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.FIELD)
                @Retention(RetentionPolicy.SOURCE)
                public @interface NonNull {}
                """);
        this.myFixture.addFileToProject("jakarta/persistence/Entity.java", """
                package jakarta.persistence;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Entity {}
                """);
        this.myFixture.addFileToProject("com/fasterxml/jackson/databind/annotation/JsonDeserialize.java", """
                package com.fasterxml.jackson.databind.annotation;
                import java.lang.annotation.*;
                @Target({ElementType.TYPE, ElementType.METHOD})
                @Retention(RetentionPolicy.RUNTIME)
                public @interface JsonDeserialize {}
                """);
    }

    // =========================================================================
    // CONSTRUCTOR-001
    // =========================================================================

    public void testFlagsAllArgsConstructorWithoutNoArgsConstructorOnJpaEntity() {
        this.myFixture.configureByText("InvoiceEntity.java", """
                import lombok.AllArgsConstructor;
                import jakarta.persistence.Entity;
                <warning descr="JPA and Jackson require a no-args constructor for deserialization and proxying. Add @NoArgsConstructor alongside @AllArgsConstructor.">@AllArgsConstructor</warning>
                @Entity
                public class InvoiceEntity {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFlagsAllArgsConstructorWithoutNoArgsConstructorOnJsonDeserialize() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.AllArgsConstructor;
                import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
                <warning descr="JPA and Jackson require a no-args constructor for deserialization and proxying. Add @NoArgsConstructor alongside @AllArgsConstructor.">@AllArgsConstructor</warning>
                @JsonDeserialize
                public class InvoiceDto {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testNoWarningWhenNoArgsConstructorAlreadyPresent() {
        this.myFixture.configureByText("InvoiceEntity.java", """
                import lombok.AllArgsConstructor;
                import lombok.NoArgsConstructor;
                import jakarta.persistence.Entity;
                @AllArgsConstructor
                @NoArgsConstructor
                @Entity
                public class InvoiceEntity {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixAddsNoArgsConstructorImport() {
        this.myFixture.addFileToProject("InvoiceEntity.java", """
                package demo;
                
                import jakarta.persistence.Entity;
                import lombok.AllArgsConstructor;
                
                @AllArgsConstructor
                @Entity
                public class InvoiceEntity {
                    private int amount;
                }
                """);

        this.myFixture.configureByText("Test.java", "public class Test { void m() { demo.InvoiceEntity dto = null; } }");
        this.myFixture.doHighlighting();

        VirtualFile file = this.myFixture.findFileInTempDir("InvoiceEntity.java");
        assertNotNull(file);
        this.myFixture.openFileInEditor(file);
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@AllArgsConstructor");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@AllArgsConstructor".length() / 2));

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add @NoArgsConstructor"));

        String result = this.myFixture.getFile().getText();
        assertTrue("Should add import lombok.NoArgsConstructor; but got:\n" + result,
                result.contains("import lombok.NoArgsConstructor;"));
        assertTrue("Should add @NoArgsConstructor but got:\n" + result,
                result.contains("@NoArgsConstructor"));
    }

    // =========================================================================
    // CONSTRUCTOR-002
    // =========================================================================

    public void testFlagsRequiredArgsConstructorWithNoQualifyingFields() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.RequiredArgsConstructor;
                <warning descr="@RequiredArgsConstructor generates a no-args constructor when there are no final or @NonNull fields. This is likely unintentional. Use @NoArgsConstructor explicitly to signal intent.">@RequiredArgsConstructor</warning>
                public class InvoiceDto {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testNoWarningWhenFinalFieldsExist() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.RequiredArgsConstructor;
                @RequiredArgsConstructor
                public class InvoiceDto {
                    private final int amount = 0;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testNoWarningWhenNonNullFieldsExist() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.RequiredArgsConstructor;
                import lombok.NonNull;
                @RequiredArgsConstructor
                public class InvoiceDto {
                    @NonNull
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixReplacesRequiredArgsConstructorAndAddsNoArgsConstructorImport() {
        this.myFixture.addFileToProject("InvoiceDto.java", """
                package demo;
                
                import lombok.RequiredArgsConstructor;
                
                @RequiredArgsConstructor
                public class InvoiceDto {
                    private int amount;
                }
                """);

        this.myFixture.configureByText("Test.java", "public class Test { void m() { demo.InvoiceDto dto = null; } }");
        this.myFixture.doHighlighting();

        VirtualFile file = this.myFixture.findFileInTempDir("InvoiceDto.java");
        assertNotNull(file);
        this.myFixture.openFileInEditor(file);
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@RequiredArgsConstructor");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@RequiredArgsConstructor".length() / 2));

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with @NoArgsConstructor"));

        String result = this.myFixture.getFile().getText();
        assertTrue("Should add import lombok.NoArgsConstructor; but got:\n" + result,
                result.contains("import lombok.NoArgsConstructor;"));
        assertTrue("Should add @NoArgsConstructor but got:\n" + result,
                result.contains("@NoArgsConstructor"));
        assertFalse("Should remove @RequiredArgsConstructor but got:\n" + result,
                result.contains("@RequiredArgsConstructor"));
    }

    // =========================================================================
    // CONSTRUCTOR-003
    // =========================================================================

    public void testFlagsConflictBetweenAllArgsAndRequiredArgsConstructor() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.AllArgsConstructor;
                import lombok.RequiredArgsConstructor;
                @AllArgsConstructor
                <weak_warning descr="@AllArgsConstructor and @RequiredArgsConstructor together generate two constructors that may conflict. Lombok may produce duplicate constructor errors or silently skip one. Pick one and remove the other.">@RequiredArgsConstructor</weak_warning>
                public class InvoiceDto {
                    private final int amount = 0;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixRemovesConflictingRequiredArgsConstructor() {
        this.myFixture.addFileToProject("InvoiceDto.java", """
                import lombok.AllArgsConstructor;
                import lombok.RequiredArgsConstructor;
                @AllArgsConstructor
                @RequiredArgsConstructor
                public class InvoiceDto {
                    private final int amount = 0;
                }
                """);

        this.myFixture.configureByText("Test.java", "public class Test { void m() { InvoiceDto dto = null; } }");
        this.myFixture.doHighlighting();

        VirtualFile file = this.myFixture.findFileInTempDir("InvoiceDto.java");
        assertNotNull(file);
        this.myFixture.openFileInEditor(file);
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@RequiredArgsConstructor");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@RequiredArgsConstructor".length() / 2));

        this.myFixture.launchAction(
                this.myFixture.findSingleIntention("Remove conflicting @RequiredArgsConstructor"));

        PsiFile psiFile = com.intellij.psi.PsiManager.getInstance(this.myFixture.getProject())
                .findFile(this.myFixture.findFileInTempDir("InvoiceDto.java"));
        assertNotNull(psiFile);
        assertTrue("Should keep @AllArgsConstructor", psiFile.getText().contains("@AllArgsConstructor"));
        assertFalse("Should remove @RequiredArgsConstructor", psiFile.getText().contains("@RequiredArgsConstructor"));
    }

    public void testNoWarningWhenOnlyAllArgsConstructor() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.AllArgsConstructor;
                @AllArgsConstructor
                public class InvoiceDto {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // BUILDER-JPA
    // =========================================================================

    public void testFlagsBuilderOnJpaEntityWithoutNoArgsConstructor() {
        this.myFixture.configureByText("InvoiceEntity.java", """
                import lombok.Builder;
                import jakarta.persistence.Entity;
                <error descr="@Builder on a JPA entity removes the no-args constructor required by JPA/Hibernate. Always combine with @NoArgsConstructor(force = true) or put @Builder on a static method / constructor.">@Builder</error>
                @Entity
                public class InvoiceEntity {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testNoWarningWhenBuilderOnJpaEntityWithNoArgsConstructor() {
        this.myFixture.configureByText("InvoiceEntity.java", """
                import lombok.Builder;
                import lombok.NoArgsConstructor;
                import jakarta.persistence.Entity;
                @Builder
                @NoArgsConstructor
                @Entity
                public class InvoiceEntity {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixAddsNoArgsConstructorImportForBuilderJpaEntity() {
        this.myFixture.addFileToProject("InvoiceEntity.java", """
                package demo;
                
                import jakarta.persistence.Entity;
                import lombok.Builder;
                
                @Builder
                @Entity
                public class InvoiceEntity {
                    private int amount;
                }
                """);

        this.myFixture.configureByText("Test.java", "public class Test { void m() { demo.InvoiceEntity dto = null; } }");
        this.myFixture.doHighlighting();

        VirtualFile file = this.myFixture.findFileInTempDir("InvoiceEntity.java");
        assertNotNull(file);
        this.myFixture.openFileInEditor(file);
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@Builder");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@Builder".length() / 2));

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add @NoArgsConstructor for JPA entity"));

        String result = this.myFixture.getFile().getText();
        assertTrue("Should add import lombok.NoArgsConstructor; but got:\n" + result,
                result.contains("import lombok.NoArgsConstructor;"));
        assertTrue("Should add @NoArgsConstructor but got:\n" + result,
                result.contains("@NoArgsConstructor"));
    }

    // =========================================================================
    // PROJECTION-001
    // =========================================================================

    public void testFlagsAllArgsConstructorOnJpqlProjection() {
        this.myFixture.addFileToProject("InvoiceView.java", """
                import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
                @JsonDeserialize
                public interface InvoiceView {}
                """);
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.AllArgsConstructor;
                <warning descr="@AllArgsConstructor on a JPQL projection class is fragile. Field reordering silently breaks @Query 'select new ClassName(...)' expressions. Consider @Value with an explicit named constructor instead.">@AllArgsConstructor</warning>
                public class InvoiceDto implements InvoiceView {
                    private int id;
                    private int title;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // METADATA
    // =========================================================================

    public void testInspectionHasDisplayName() {
        LombokConstructorAnnotationInspection inspection = new LombokConstructorAnnotationInspection();
        assertNotNull(inspection.getDisplayName());
        assertFalse(inspection.getDisplayName().isBlank());
    }
}
