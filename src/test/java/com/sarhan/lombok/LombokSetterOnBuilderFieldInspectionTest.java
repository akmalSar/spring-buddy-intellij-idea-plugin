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
public class LombokSetterOnBuilderFieldInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new LombokSetterOnBuilderFieldInspection());

        this.myFixture.addFileToProject("lombok/Builder.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface Builder {}
                """);
        this.myFixture.addFileToProject("lombok/Setter.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target({ElementType.FIELD, ElementType.TYPE})
                @Retention(RetentionPolicy.SOURCE)
                public @interface Setter {}
                """);
    }

    public void testFlagsSetterOnBuilderField() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.Builder;
                import lombok.Setter;
                @Builder
                public class InvoiceDto {
                    <weak_warning descr="@Setter on a field in a @Builder class undermines immutability. If the field must be mutable, document this explicitly. If not, remove @Setter.">@Setter</weak_warning>
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixRemovesSetterAnnotation() {
        this.myFixture.addFileToProject("InvoiceDto.java", """
                import lombok.Builder;
                import lombok.Setter;
                @Builder
                public class InvoiceDto {
                    @Setter
                    private int amount;
                }
                """);

        this.myFixture.configureByText("Test.java", "public class Test { void m() { InvoiceDto dto = null; } }");
        this.myFixture.doHighlighting();

        VirtualFile file = this.myFixture.findFileInTempDir("InvoiceDto.java");
        assertNotNull(file);
        this.myFixture.openFileInEditor(file);
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@Setter");
        assertTrue("Caret anchor not found: @Setter", offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@Setter".length() / 2));

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Remove @Setter"));

        PsiFile psiFile = com.intellij.psi.PsiManager.getInstance(this.myFixture.getProject())
                .findFile(this.myFixture.findFileInTempDir("InvoiceDto.java"));
        assertNotNull(psiFile);
        assertFalse("@Setter should be removed", psiFile.getText().contains("@Setter"));
        assertTrue("Field should remain", psiFile.getText().contains("private int amount;"));
    }

    public void testNoWarningWhenNoBuilderAnnotation() {
        this.myFixture.configureByText("PlainDto.java", """
                import lombok.Setter;
                public class PlainDto {
                    @Setter
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testNoWarningWhenNoSetterAnnotation() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.Builder;
                @Builder
                public class InvoiceDto {
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testWarningWithShortAnnotationName() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.Builder;
                @Builder
                public class InvoiceDto {
                    <weak_warning descr="@Setter on a field in a @Builder class undermines immutability. If the field must be mutable, document this explicitly. If not, remove @Setter.">@lombok.Setter</weak_warning>
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testInspectionHasDisplayName() {
        LombokSetterOnBuilderFieldInspection inspection = new LombokSetterOnBuilderFieldInspection();
        assertNotNull(inspection.getDisplayName());
        assertFalse(inspection.getDisplayName().isBlank());
    }
}
