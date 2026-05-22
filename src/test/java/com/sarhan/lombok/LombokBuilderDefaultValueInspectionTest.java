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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class LombokBuilderDefaultValueInspectionTest extends BasePlatformTestCase {

    private static final String BUILDER_STUB = """
            package lombok;
            import java.lang.annotation.*;
            @Target(ElementType.TYPE) @Retention(RetentionPolicy.SOURCE)
            public @interface Builder {
                @Target(ElementType.FIELD) @Retention(RetentionPolicy.SOURCE)
                @interface Default {}
            }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("lombok/Builder.java", BUILDER_STUB);
    }

    public void testFlagsFieldWithInitializerWithoutBuilderDefault() {
        this.myFixture.enableInspections(new LombokBuilderDefaultValueInspection());
        this.myFixture.configureByText("User.java", """
                import lombok.Builder;

                @Builder
                class User {
                    <warning descr="Field initializer is erased by @Builder; add @Builder.Default to preserve the default value">String role = "GUEST"</warning>;
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testDoesNotFlagFieldWithBuilderDefault() {
        this.myFixture.enableInspections(new LombokBuilderDefaultValueInspection());
        this.myFixture.configureByText("User.java", """
                import lombok.Builder;

                @Builder
                class User {
                    @Builder.Default
                    String role = "GUEST";
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testDoesNotFlagFieldWithoutBuilderAnnotation() {
        this.myFixture.enableInspections(new LombokBuilderDefaultValueInspection());
        this.myFixture.configureByText("User.java", """
                class User {
                    String role = "GUEST";
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testDoesNotFlagStaticField() {
        this.myFixture.enableInspections(new LombokBuilderDefaultValueInspection());
        this.myFixture.configureByText("User.java", """
                import lombok.Builder;

                @Builder
                class User {
                    static String DEFAULT_ROLE = "GUEST";
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testDoesNotFlagFieldWithoutInitializer() {
        this.myFixture.enableInspections(new LombokBuilderDefaultValueInspection());
        this.myFixture.configureByText("User.java", """
                import lombok.Builder;

                @Builder
                class User {
                    String role;
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testFixAddsBuilderDefaultAnnotation() {
        this.myFixture.enableInspections(new LombokBuilderDefaultValueInspection());
        this.myFixture.configureByText("User.java", """
                import lombok.Builder;

                @Builder
                class User {
                    String role = "GUEST";
                }
                """);
        this.myFixture.doHighlighting();
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("String role");
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset);
        IntentionAction fix = this.myFixture.findSingleIntention("Add @Builder.Default");
        this.myFixture.launchAction(fix);
        String result = this.myFixture.getFile().getText();
        assertTrue("Should add @Builder.Default: " + result, result.contains("@Builder.Default"));
    }
}
