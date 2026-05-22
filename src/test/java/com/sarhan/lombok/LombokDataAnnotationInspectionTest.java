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
public class LombokDataAnnotationInspectionTest extends BasePlatformTestCase {

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("lombok/Data.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface Data {}
                """);
        this.myFixture.addFileToProject("lombok/RequiredArgsConstructor.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface RequiredArgsConstructor {}
                """);
        this.myFixture.addFileToProject("lombok/AllArgsConstructor.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface AllArgsConstructor {}
                """);
        this.myFixture.addFileToProject("lombok/NoArgsConstructor.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface NoArgsConstructor {}
                """);
        this.myFixture.addFileToProject("lombok/Getter.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target({ElementType.TYPE, ElementType.FIELD})
                @Retention(RetentionPolicy.SOURCE)
                public @interface Getter {}
                """);
        this.myFixture.addFileToProject("lombok/Setter.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target({ElementType.TYPE, ElementType.FIELD})
                @Retention(RetentionPolicy.SOURCE)
                public @interface Setter {}
                """);
        this.myFixture.addFileToProject("jakarta/persistence/Entity.java", """
                package jakarta.persistence;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.RUNTIME)
                public @interface Entity {}
                """);
    }

    public void testFlagsRedundantRequiredArgsConstructorWithData() {
        this.myFixture.enableInspections(new LombokDataAnnotationInspection.RedundantRequiredArgsConstructor());
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.Data;
                import lombok.RequiredArgsConstructor;
                @Data
                <weak_warning descr="@RequiredArgsConstructor is redundant — @Data already implies it. Remove the explicit annotation.">@RequiredArgsConstructor</weak_warning>
                public class InvoiceDto {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixRemovesRedundantRequiredArgsConstructor() {
        this.myFixture.enableInspections(new LombokDataAnnotationInspection.RedundantRequiredArgsConstructor());
        this.myFixture.addFileToProject("InvoiceDto.java", """
                import lombok.Data;
                import lombok.RequiredArgsConstructor;
                @Data
                @RequiredArgsConstructor
                public class InvoiceDto {
                    private int amount;
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
                this.myFixture.findSingleIntention("Remove redundant @RequiredArgsConstructor"));

        PsiFile psiFile = com.intellij.psi.PsiManager.getInstance(this.myFixture.getProject())
                .findFile(this.myFixture.findFileInTempDir("InvoiceDto.java"));
        assertNotNull(psiFile);
        assertFalse("@RequiredArgsConstructor should be removed",
                psiFile.getText().contains("@RequiredArgsConstructor"));
        assertTrue("@Data should remain", psiFile.getText().contains("@Data"));
    }

    public void testDoesNotFlagRequiredArgsConstructorWhenNoArgsConstructorIsPresent() {
        this.myFixture.enableInspections(new LombokDataAnnotationInspection.RedundantRequiredArgsConstructor());
        this.myFixture.configureByText("Nexnet2AAXResponse.java", """
                import lombok.Data;
                import lombok.NoArgsConstructor;
                import lombok.RequiredArgsConstructor;
                @Data
                @RequiredArgsConstructor
                @NoArgsConstructor
                public class Nexnet2AAXResponse {
                    private int status;
                    private int code;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testNoWarningWhenNoData() {
        this.myFixture.enableInspections(new LombokDataAnnotationInspection.RedundantRequiredArgsConstructor());
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.RequiredArgsConstructor;
                @RequiredArgsConstructor
                public class InvoiceDto {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFlagsDataWithManualConstructor() {
        this.myFixture.enableInspections(new LombokDataAnnotationInspection.DataWithManualConstructor());
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.Data;
                <warning descr="@Data will not generate @AllArgsConstructor when a constructor is already defined. Only @RequiredArgsConstructor is generated, which may not match developer intent. Be explicit: use @AllArgsConstructor if a full constructor is needed.">@Data</warning>
                public class InvoiceDto {
                    private int amount;
                    public InvoiceDto(int amount) { this.amount = amount; }
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFlagsDataOnJpaEntity() {
        this.myFixture.enableInspections(new LombokDataAnnotationInspection.DataOnJpaEntity());
        this.myFixture.configureByText("InvoiceEntity.java", """
                import lombok.Data;
                import jakarta.persistence.Entity;
                <warning descr="@Data on JPA entities is dangerous. It includes @EqualsAndHashCode and @ToString, which leads to broken collections, LazyInitializationException, StackOverflowError, and identity issues. Use explicit @Getter + @Setter (or @Value for DTOs) instead.">@Data</warning>
                @Entity
                public class InvoiceEntity {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixReplacesDataWithGetterSetterOnJpaEntity() {
        this.myFixture.enableInspections(new LombokDataAnnotationInspection.DataOnJpaEntity());
        this.myFixture.configureByText("InvoiceEntity.java", """
                import jakarta.persistence.Entity;
                import lombok.Data;
                
                @Data
                @Entity
                public class InvoiceEntity {
                    private int amount;
                }
                """);

        this.myFixture.doHighlighting();
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@Data");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@Data".length() / 2));

        this.myFixture.launchAction(
                this.myFixture.findSingleIntention("Replace @Data with @Getter and @Setter"));

        String result = this.myFixture.getFile().getText();
        assertFalse("@Data should be removed but got:\n" + result,
                result.contains("@Data"));
        assertTrue("Should import Getter but got:\n" + result,
                result.contains("import lombok.Getter;"));
        assertTrue("Should import Setter but got:\n" + result,
                result.contains("import lombok.Setter;"));
        assertTrue("Should add @Getter but got:\n" + result,
                result.contains("@Getter"));
        assertTrue("Should add @Setter but got:\n" + result,
                result.contains("@Setter"));
        assertTrue("Should keep @Entity but got:\n" + result,
                result.contains("@Entity"));
    }

    public void testFixDoesNotDuplicateExistingGetterOrSetterOnJpaEntity() {
        this.myFixture.enableInspections(new LombokDataAnnotationInspection.DataOnJpaEntity());
        this.myFixture.configureByText("InvoiceEntity.java", """
                import jakarta.persistence.Entity;
                import lombok.Data;
                import lombok.Getter;
                
                @Getter
                @Data
                @Entity
                public class InvoiceEntity {
                    private int amount;
                }
                """);

        this.myFixture.doHighlighting();
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@Data");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@Data".length() / 2));

        this.myFixture.launchAction(
                this.myFixture.findSingleIntention("Replace @Data with @Getter and @Setter"));

        String result = this.myFixture.getFile().getText();
        assertFalse("@Data should be removed but got:\n" + result,
                result.contains("@Data"));
        assertEquals("Should not duplicate @Getter", 1, countOccurrences(result, "@Getter"));
        assertEquals("Should add one @Setter", 1, countOccurrences(result, "@Setter"));
    }

    public void testFlagsDataWithAllArgsConstructorDiscouraged() {
        this.myFixture.enableInspections(new LombokDataAnnotationInspection.DataWithAllArgsConstructor());
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.Data;
                import lombok.AllArgsConstructor;
                @Data
                <weak_warning descr="@AllArgsConstructor with @Data is discouraged. @Data generates setters, undermining the constructor's immutability. Consider @Value for immutable DTOs instead.">@AllArgsConstructor</weak_warning>
                public class InvoiceDto {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testInspectionHasDisplayName() {
        LombokDataAnnotationInspection inspection = new LombokDataAnnotationInspection.DataOnJpaEntity();
        assertNotNull(inspection.getDisplayName());
        assertFalse(inspection.getDisplayName().isBlank());
    }
}
