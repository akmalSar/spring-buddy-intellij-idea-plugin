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

import java.util.List;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.assertj.core.api.Assertions;

/**
 * Tests for {@link LombokBuilderRedundancyInspection} and {@link BuilderRedundancyFix}.
 *
 * @author Akmal Sarhan
 */
public class LombokBuilderRedundancyInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new LombokBuilderRedundancyInspection());

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

    // =========================================================================
    // 1. REDUNDANT — explicit builder() method exists
    // =========================================================================

    public void testFlagsRedundantBuilderWithExplicitMethod() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.Builder;
                <warning descr="Redundant @Builder — class already declares an explicit builder() method">@Builder</warning>
                public class InvoiceDto {
                    private int amount;
                    public static InvoiceBuilder builder() {
                        return new InvoiceBuilder();
                    }
                }
                class InvoiceBuilder {}
                """);

        this.myFixture.checkHighlighting();
    }

    public void testFixRemovesRedundantBuilderAnnotation() {
        this.myFixture.addFileToProject("InvoiceDto.java", """
                import lombok.Builder;
                @Builder
                public class InvoiceDto {
                    private int amount;
                    public static InvoiceBuilder builder() {
                        return new InvoiceBuilder();
                    }
                }
                class InvoiceBuilder {}
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        InvoiceDto dto = null;
                    }
                }
                """);

        this.myFixture.doHighlighting();

        VirtualFile invoiceFile = this.myFixture.findFileInTempDir("InvoiceDto.java");
        assertNotNull(invoiceFile);
        this.myFixture.openFileInEditor(invoiceFile);

        String fileText = this.myFixture.getFile().getText();
        int importOffset = fileText.indexOf("import lombok.Builder;");
        int offset = fileText.indexOf("@Builder", importOffset + 1);
        assertTrue("Caret anchor not found: @Builder", offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@Builder".length() / 2));

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Remove unused/redundant @Builder"));

        String text = this.myFixture.getFile().getText();
        assertFalse("@Builder annotation should be removed", text.contains("\n@Builder"));
        assertTrue("Explicit builder() should remain", text.contains("public static InvoiceBuilder builder()"));
    }

    public void testFlagsRedundantSuperBuilderWithExplicitMethod() {
        this.myFixture.configureByText("InvoiceDto.java", """
                import lombok.SuperBuilder;
                <warning descr="Redundant @Builder — class already declares an explicit builder() method">@SuperBuilder</warning>
                public class InvoiceDto {
                    private int amount;
                    public static InvoiceBuilder builder() {
                        return new InvoiceBuilder();
                    }
                }
                class InvoiceBuilder {}
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 2. UNUSED — zero references and no builder call sites
    // =========================================================================

    public void testFlagsUnusedBuilderWhenZeroReferences() {
        this.myFixture.configureByText("UnusedDto.java", """
                import lombok.Builder;
                <weak_warning descr="Unused @Builder — class has zero references and no builder call sites in the project">@Builder</weak_warning>
                public class UnusedDto {
                    private int value;
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 3. NOT REDUNDANT — class has resolved references (primary check finds them)
    // =========================================================================

    public void testNoWarningWhenClassHasRegularReferences() {
        this.myFixture.addFileToProject("UsedDto.java", """
                import lombok.Builder;
                @Builder
                public class UsedDto {
                    private String name;
                }
                """);

        this.myFixture.configureByText("Test.java", """
                public class Test {
                    void m() {
                        UsedDto dto = new UsedDto();
                    }
                }
                """);

        this.myFixture.checkHighlighting();
    }

    /**
     * When the only reference is an unresolved {@code ClassName.builder()} call,
     * {@link com.intellij.psi.search.searches.ReferencesSearch} misses it because
     * the synthetic method does not exist in the PSI. The inspection must fall
     * back to a textual scan so it does not falsely flag the builder as unused.
     * <p>
     * We test this by configuring the annotated class file and asserting that no
     * {@code WEAK_WARNING} from our inspection is registered. The unresolved
     * method error in the caller file is ignored because we do not run
     * {@code checkHighlighting()} on that file.
     */
    public void testNoWarningWhenOnlyReferenceIsUnresolvedBuilderCall() {
        this.myFixture.addFileToProject("UsedDto.java", """
                import lombok.Builder;
                @Builder
                public class UsedDto {
                    private String name;
                }
                """);

        this.myFixture.addFileToProject("Test.java", """
                public class Test {
                    void m() {
                        UsedDto dto = UsedDto.builder().name("x").build();
                    }
                }
                """);

        // Configure the annotated class as the current file and check highlights
        this.myFixture.configureByText("UsedDto.java", """
                import lombok.Builder;
                @Builder
                public class UsedDto {
                    private String name;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        for (HighlightInfo h : highlights) {
            if ((h.getSeverity() == com.intellij.lang.annotation.HighlightSeverity.WEAK_WARNING)
                    && (h.getDescription() != null)
                    && h.getDescription().contains("Unused @Builder")) {
                Assertions.fail("Builder should NOT be flagged as unused when a .builder() call exists in the project");
            }
        }
    }

    // =========================================================================
    // 4. NOT REDUNDANT — abstract classes are skipped
    // =========================================================================

    public void testNoWarningForAbstractClass() {
        this.myFixture.configureByText("AbstractDto.java", """
                import lombok.Builder;
                @Builder
                public abstract class AbstractDto {
                    private int value;
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 5. NOT REDUNDANT — class without @Builder
    // =========================================================================

    public void testNoWarningForNonBuilderClass() {
        this.myFixture.configureByText("PlainDto.java", """
                public class PlainDto {
                    private int value;
                }
                """);

        this.myFixture.checkHighlighting();
    }

    // =========================================================================
    // 6. METADATA
    // =========================================================================

    public void testInspectionHasDisplayName() {
        LombokBuilderRedundancyInspection inspection = new LombokBuilderRedundancyInspection();
        assertNotNull(inspection.getDisplayName());
        assertFalse(inspection.getDisplayName().isBlank());
    }

    public void testInspectionGroupName() {
        LombokBuilderRedundancyInspection inspection = new LombokBuilderRedundancyInspection();
        assertNotNull(inspection.getGroupDisplayName());
    }
}
