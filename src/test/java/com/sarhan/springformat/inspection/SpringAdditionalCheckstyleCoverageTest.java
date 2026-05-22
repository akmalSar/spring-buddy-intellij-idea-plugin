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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class SpringAdditionalCheckstyleCoverageTest extends BasePlatformTestCase {

    public void testMethodParamPad() {
        this.myFixture.enableInspections(new SpringMethodParamPadInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test<warning descr="Whitespace before method parentheses is not allowed"> </warning>() {
                        call<warning descr="Whitespace before method parentheses is not allowed"> </warning>();
                    }

                    void call() {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoWhitespaceAfter() {
        this.myFixture.enableInspections(new SpringNoWhitespaceAfterInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    boolean test(Object value) {
                        return value.<warning descr="Whitespace is not allowed after '.'">.</warning> toString().isEmpty() || <warning descr="Whitespace is not allowed after '!'">!</warning> true;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testWhitespaceAfter() {
        this.myFixture.enableInspections(new SpringWhitespaceAfterInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test() {
                        call(1,<warning descr="Whitespace is required after ','">,</warning>2);
                    }

                    void call(int one, int two) {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testWhitespaceAfterSkipsForLoopSemicolons() {
        this.myFixture.enableInspections(new SpringWhitespaceAfterInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test() {
                        for (int i = 0;i < 1;i++) {
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testTypecastParenPad() {
        this.myFixture.enableInspections(new SpringTypecastParenPadInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test(Object value) {
                        String text = (<warning descr="Whitespace inside typecast parentheses is not allowed"> </warning>String<warning descr="Whitespace inside typecast parentheses is not allowed"> </warning>) value;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testStringLiteralEquality() {
        this.myFixture.enableInspections(new SpringStringLiteralEqualityInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    boolean test(String value) {
                        return value <warning descr="Use equals() to compare strings">==</warning> "test";
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testStringLiteralEqualityOnRightHandLiteral() {
        this.myFixture.enableInspections(new SpringStringLiteralEqualityInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    boolean test(String value) {
                        return value <warning descr="Use equals() to compare strings">!=</warning> "test";
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
