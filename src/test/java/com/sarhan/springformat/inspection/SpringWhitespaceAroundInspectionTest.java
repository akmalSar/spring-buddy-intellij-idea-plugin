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
public class SpringWhitespaceAroundInspectionTest extends BasePlatformTestCase {

    public void testFlagsMissingWhitespaceAroundOperators() {
        this.myFixture.enableInspections(new SpringWhitespaceAroundInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        int value = 1<warning descr="Whitespace is required before '+'">+</warning><warning descr="Whitespace is required after '+'">+</warning>2;
                        boolean ok = value<warning descr="Whitespace is required before '=='">==</warning><warning descr="Whitespace is required after '=='">==</warning>3;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsMissingWhitespaceAroundLambdaAndTernary() {
        this.myFixture.enableInspections(new SpringWhitespaceAroundInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        java.util.function.Function<String, String> f = s<warning descr="Whitespace is required before '->'">-></warning><warning descr="Whitespace is required after '->'">-></warning>s.trim();
                        String value = true<warning descr="Whitespace is required before '?'">?</warning><warning descr="Whitespace is required after '?'">?</warning>"yes"<warning descr="Whitespace is required before ':'">:</warning><warning descr="Whitespace is required after ':'">:</warning>"no";
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagEnhancedForColonOrGenerics() {
        this.myFixture.enableInspections(new SpringWhitespaceAroundInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;

                class Sample<T extends Number> {
                    void doIt(List<String> values) {
                        for (String value : values) {
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsMissingWhitespaceAfterRightBraceBeforeLineComment() {
        this.myFixture.enableInspections(new SpringWhitespaceAroundInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static class Inner {
                    <warning descr="Whitespace is required after '}'">}</warning>// comment
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagRightBraceBeforeLambdaArgumentComma() {
        this.myFixture.enableInspections(new SpringWhitespaceAroundInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt(Repo repo) {
                        repo.find().ifPresentOrElse((String value) -> {
                            System.out.println(value);
                        }, () -> System.out.println("missing"));
                    }

                    interface Repo {
                        Result find();
                    }

                    interface Result {
                        void ifPresentOrElse(java.util.function.Consumer<String> consumer, Runnable runnable);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testAddWhitespaceAfterFix() {
        this.myFixture.enableInspections(new SpringWhitespaceAroundInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        int value = 1<warning descr="Whitespace is required after '+'"><caret>+</warning>(2);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Add whitespace after token");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText(), this.myFixture.getFile().getText().contains("1+ (2)"));
    }
}
