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
public class SpringLambdaInspectionTest extends BasePlatformTestCase {

    public void testFlagsSingleArgWithoutParens() {
        this.myFixture.enableInspections(new SpringLambdaInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        java.util.stream.Stream.of(1).map(<warning descr="Lambda with single argument should use parentheses"><caret>i -> i + 1</warning>);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagSingleArgWithParens() {
        this.myFixture.enableInspections(new SpringLambdaInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        java.util.stream.Stream.of(1).map((i) -> i + 1);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagMultipleArgs() {
        this.myFixture.enableInspections(new SpringLambdaInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        java.util.Map.of(1, 2).forEach((k, v) -> { });
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsSingleStatementWithBraces() {
        this.myFixture.enableInspections(new SpringLambdaInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        java.util.stream.Stream.of(1).map(i -> <warning descr="Lambda with single statement should not use curly braces"><caret>{ return i + 1; }</warning>);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagMultiStatementWithBraces() {
        this.myFixture.enableInspections(new SpringLambdaInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        java.util.stream.Stream.of(1).map(i -> { System.out.println(i); return i + 1; });
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsSingleLocalDeclarationWithBraces() {
        this.myFixture.enableInspections(new SpringLambdaInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        assertThrows(() -> <warning descr="Lambda with single statement should not use curly braces"><caret>{ int i = 1 / 0; }</warning>);
                    }

                    void assertThrows(Runnable runnable) {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testAddParenthesesFix() {
        this.myFixture.enableInspections(new SpringLambdaInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        java.util.stream.Stream.of(1).map(<warning descr="Lambda with single argument should use parentheses"><caret>i -> i + 1</warning>);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Add parentheses around lambda parameter");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText(), this.myFixture.getFile().getText().contains("(i) ->"));
    }

    public void testRemoveBracesFix() {
        this.myFixture.enableInspections(new SpringLambdaInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        java.util.stream.Stream.of(1).map(i -> <warning descr="Lambda with single statement should not use curly braces"><caret>{ return i + 1; }</warning>);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Remove unnecessary curly braces");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText(), this.myFixture.getFile().getText().contains("i + 1"));
    }
}
