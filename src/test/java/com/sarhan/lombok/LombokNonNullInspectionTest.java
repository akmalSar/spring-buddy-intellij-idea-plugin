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
public class LombokNonNullInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new LombokNonNullInspection());

        this.myFixture.addFileToProject("lombok/NonNull.java", """
                package lombok;
                public @interface NonNull {}
                """);
        this.myFixture.addFileToProject("lombok/Builder.java", """
                package lombok;
                public @interface Builder {
                    @interface Default {}
                }
                """);
    }

    public void testFlagsNonNullOnPrimitive() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.NonNull;
                public class InvoiceDto {
                    <error descr="Lombok @NonNull on a primitive field is meaningless. Primitives can never be null. Remove the annotation.">@NonNull</error>
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixRemovesNonNullOnPrimitive() {
        this.myFixture.addFileToProject("InvoiceDto.java", """
                import lombok.NonNull;
                public class InvoiceDto {
                    @NonNull
                    private int amount;
                }
                """);

        this.myFixture.configureByText("Test.java", "public class Test { void m() { InvoiceDto dto = null; } }");
        this.myFixture.doHighlighting();

        VirtualFile file = this.myFixture.findFileInTempDir("InvoiceDto.java");
        assertNotNull(file);
        this.myFixture.openFileInEditor(file);
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@NonNull");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@NonNull".length() / 2));

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Remove @NonNull"));

        PsiFile psiFile = com.intellij.psi.PsiManager.getInstance(this.myFixture.getProject())
                .findFile(this.myFixture.findFileInTempDir("InvoiceDto.java"));
        assertNotNull(psiFile);
        assertFalse("@NonNull should be removed", psiFile.getText().contains("@NonNull"));
        assertTrue("Field should remain", psiFile.getText().contains("private int amount;"));
    }

    public void testFlagsNonNullOnBooleanPrimitive() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.NonNull;
                public class InvoiceDto {
                    <error descr="Lombok @NonNull on a primitive field is meaningless. Primitives can never be null. Remove the annotation.">@NonNull</error>
                    private boolean active;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFlagsNonNullAndBuilderDefaultOnSameField() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.NonNull;
                import lombok.Builder;
                public class InvoiceDto {
                    <error descr="@NonNull and @Builder.Default on the same field will cause a NullPointerException during construction because the null check fires before the default value is applied."><error descr="Lombok @NonNull on a primitive field is meaningless. Primitives can never be null. Remove the annotation.">@NonNull</error></error>
                    @lombok.Builder.Default
                    private int amount;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testInspectionHasDisplayName() {
        LombokNonNullInspection inspection = new LombokNonNullInspection();
        assertNotNull(inspection.getDisplayName());
        assertFalse(inspection.getDisplayName().isBlank());
    }
}
