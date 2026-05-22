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
public class SpringAvoidNestedBlocksInspectionTest extends BasePlatformTestCase {

    public void testFlagsNestedBlock() {
        this.myFixture.enableInspections(new SpringAvoidNestedBlocksInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        int value = 0;
                        <warning descr="Avoid nested blocks">{
                            value = 1;
                        }</warning>
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagControlFlowBlocks() {
        this.myFixture.enableInspections(new SpringAvoidNestedBlocksInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt(boolean test) {
                        if (test) {
                            System.out.println(test);
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagIfElseBlocks() {
        this.myFixture.enableInspections(new SpringAvoidNestedBlocksInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt(boolean hasHeader, CsvFormat baseFormat, String[] defaultHeaders) {
                        CsvFormat csvFormat;
                        if (hasHeader) {
                            csvFormat = baseFormat.withFirstRecordAsHeader();
                        }
                        else {
                            csvFormat = baseFormat.withHeader(defaultHeaders);
                        }
                    }

                    interface CsvFormat {
                        CsvFormat withFirstRecordAsHeader();

                        CsvFormat withHeader(String[] headers);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsNestedBlockInsideIfElseFlowStatements() {
        this.myFixture.enableInspections(new SpringAvoidNestedBlocksInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt(boolean test) {
                        if (test) {
                            <warning descr="Avoid nested blocks">{
                                System.out.println("if");
                            }</warning>
                        }
                        else {
                            <warning descr="Avoid nested blocks">{
                                System.out.println("else");
                            }</warning>
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testRemoveNestedBlockFix() {
        this.myFixture.enableInspections(new SpringAvoidNestedBlocksInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt() {
                        int value = 0;
                        <warning descr="Avoid nested blocks"><caret>{
                            value = 1;
                        }</warning>
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Remove nested block braces");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("value = 1;"));
        assertFalse(text.contains("{\n            value = 1;\n        }"));
    }
}
