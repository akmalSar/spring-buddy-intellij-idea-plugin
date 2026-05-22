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
public class LombokBuilderDefaultInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new LombokBuilderDefaultInspection());

        this.myFixture.addFileToProject("lombok/AllArgsConstructor.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface AllArgsConstructor {}
                """);
        this.myFixture.addFileToProject("lombok/Builder.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface Builder {
                    @Target(ElementType.FIELD)
                    @Retention(RetentionPolicy.SOURCE)
                    @interface Default {}
                }
                """);
    }

    public void testFlagsBuilderDefaultWithAllArgsConstructor() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.Builder;
                import lombok.AllArgsConstructor;
                @AllArgsConstructor
                public class InvoiceDto {
                    <warning descr="@Builder.Default is bypassed when the all-args constructor is used directly. The default value will not be applied, and the field will be whatever was passed in — including null.">@lombok.Builder.Default</warning>
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixRemovesAllArgsConstructorWhenBuilderDefaultPresent() {
        this.myFixture.addFileToProject("InvoiceDto.java", """
                import lombok.Builder;
                import lombok.AllArgsConstructor;
                @AllArgsConstructor
                public class InvoiceDto {
                    @lombok.Builder.Default
                    private int amount;
                }
                """);

        this.myFixture.configureByText("Test.java", "public class Test { void m() { InvoiceDto dto = null; } }");
        this.myFixture.doHighlighting();

        VirtualFile file = this.myFixture.findFileInTempDir("InvoiceDto.java");
        assertNotNull(file);
        this.myFixture.openFileInEditor(file);
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@lombok.Builder.Default");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@lombok.Builder.Default".length() / 2));

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Remove @AllArgsConstructor"));

        PsiFile psiFile = com.intellij.psi.PsiManager.getInstance(this.myFixture.getProject())
                .findFile(this.myFixture.findFileInTempDir("InvoiceDto.java"));
        assertNotNull(psiFile);
        assertFalse("@AllArgsConstructor should be removed", psiFile.getText().contains("@AllArgsConstructor"));
        assertTrue("@Builder.Default should remain", psiFile.getText().contains("@lombok.Builder.Default"));
    }

    public void testNoWarningWhenNoAllArgsConstructor() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.Builder;
                public class InvoiceDto {
                    @lombok.Builder.Default
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testNoWarningWhenNoBuilderDefault() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.AllArgsConstructor;
                @AllArgsConstructor
                public class InvoiceDto {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testInspectionHasDisplayName() {
        LombokBuilderDefaultInspection inspection = new LombokBuilderDefaultInspection();
        assertNotNull(inspection.getDisplayName());
        assertFalse(inspection.getDisplayName().isBlank());
    }
}
