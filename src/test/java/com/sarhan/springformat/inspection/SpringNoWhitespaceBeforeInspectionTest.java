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
public class SpringNoWhitespaceBeforeInspectionTest extends BasePlatformTestCase {

    public void testFlagsWhitespaceBeforeCommaAndSemicolon() {
        this.myFixture.enableInspections(new SpringNoWhitespaceBeforeInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        call(1 <warning descr="Whitespace is not allowed before ','">,</warning> 2)<warning descr="Whitespace is not allowed before ';'">;</warning>
                    }

                    void call(int one, int two) {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsWhitespaceBeforePostfixOperators() {
        this.myFixture.enableInspections(new SpringNoWhitespaceBeforeInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        int i = 0;
                        i <warning descr="Whitespace is not allowed before '++'">++</warning>;
                        i <warning descr="Whitespace is not allowed before '--'">--</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsWhitespaceBeforeEllipsisAndLabelColon() {
        this.myFixture.enableInspections(new SpringNoWhitespaceBeforeInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt(String <warning descr="Whitespace is not allowed before '...'">...</warning> args) {
                        label <warning descr="Whitespace is not allowed before ':'">:</warning> for (String arg : args) {
                            break label;
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagEnhancedForColonOrEmptyForSemicolon() {
        this.myFixture.enableInspections(new SpringNoWhitespaceBeforeInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt(String[] args) {
                        for (String arg : args) {
                        }
                        for ( ; ; ) {
                            break;
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testRemoveWhitespaceBeforeFix() {
        this.myFixture.enableInspections(new SpringNoWhitespaceBeforeInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        call(1 <warning descr="Whitespace is not allowed before ','"><caret>,</warning> 2);
                    }

                    void call(int one, int two) {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Remove whitespace before token");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("call(1, 2);"));
    }

    public void testMovesEnumSemicolonBeforeTrailingLineComment() {
        this.myFixture.enableInspections(new SpringNoWhitespaceBeforeInspection());
        this.myFixture.configureByText("B2bMessage2.java", """
                public enum B2bMessage2 {


                    REFUND_EVENT_PERSISTED("The submitted refund event was valid and has been persisted"),

                    DUNNING_RESEND_OK("A new dunning email has been created."),

                    // DATA_EXPORT_INSERTED("The submitted data export has been added successfully")
                    <warning descr="Whitespace is not allowed before ';'"><caret>;</warning>

                    B2bMessage2(String s) {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Remove whitespace before token");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text, text.contains("; // DATA_EXPORT_INSERTED"));
        assertFalse(text, text.contains("\n    ;\n"));
    }
}
