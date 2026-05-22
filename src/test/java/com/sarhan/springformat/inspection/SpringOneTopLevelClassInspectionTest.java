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

package com.sarhan.springformat.inspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class SpringOneTopLevelClassInspectionTest extends BasePlatformTestCase {

    public void testFlagsSecondNonPublicTopLevelClass() {
        this.myFixture.enableInspections(new SpringOneTopLevelClassInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                }

                class <warning descr="Top-level type should be declared in its own source file">Other</warning> {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsFirstNonPublicTypeWhenPublicTypeExists() {
        this.myFixture.enableInspections(new SpringOneTopLevelClassInspection());
        this.myFixture.configureByText("Sample.java", """
                class <warning descr="Top-level type should be declared in its own source file">Helper</warning> {
                }

                public class Sample {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagSingleTopLevelClass() {
        this.myFixture.enableInspections(new SpringOneTopLevelClassInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    class Nested {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testMoveTopLevelTypeToOwnFileFix() {
        this.myFixture.enableInspections(new SpringOneTopLevelClassInspection());
        this.myFixture.configureByText("Sample.java", """
                package com.example;

                import java.util.List;

                class Sample {
                }

                class <warning descr="Top-level type should be declared in its own source file"><caret>Other</warning> {
                    List<String> names;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Move top-level type to separate file");
        this.myFixture.launchAction(fix);
        assertFalse(this.myFixture.getFile().getText().contains("class Other"));
        assertNotNull(this.myFixture.findFileInTempDir("Other.java"));
    }

    public void testMovesFirstHelperWhenReportedClassMatchesFileName() {
        this.myFixture.enableInspections(new SpringOneTopLevelClassInspection());
        this.myFixture.configureByText("InviteServiceITTests.java", """
                import java.util.List;

                interface <warning descr="Top-level type should be declared in its own source file">InviteConfirmationEmailJobRepository</warning> {
                }

                class <warning descr="Top-level type should be declared in its own source file"><caret>InviteServiceITTests</warning> {
                    List<String> names;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Move top-level type to separate file");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("class InviteServiceITTests"));
        assertFalse(text.contains("interface InviteConfirmationEmailJobRepository"));
        assertNotNull(this.myFixture.findFileInTempDir("InviteConfirmationEmailJobRepository.java"));
    }
}
