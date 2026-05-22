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
public class SpringSystemOutErrInspectionTest extends BasePlatformTestCase {

    public void testFlagsSystemOutAndErr() {
        this.myFixture.enableInspections(new SpringSystemOutErrInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void t() {
                        int x = 1;
                        <warning descr="Use logger instead of 'System.out'">System.out.println(x)</warning>;
                        <warning descr="Use logger instead of 'System.err'">System.err.println("err")</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReplaceObjectArgUsesPlaceholder() {
        this.myFixture.enableInspections(new SpringSystemOutErrInspection());
        this.myFixture.configureByText("Sample.java", """
                import lombok.extern.slf4j.Slf4j;
                @Slf4j
                class Sample {
                    void t() {
                        Object obj = new Object();
                        <warning descr="Use logger instead of 'System.out'">System.out.<caret>println(obj)</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with logger call"));
        String text = this.myFixture.getFile().getText();
        assertFalse("Should not contain System.out", text.contains("System.out.println"));
        assertTrue("Should contain placeholder: " + text, text.contains("log.info(\"{}\", obj)"));
    }

    public void testReplaceStringLiteralUsesDirectCall() {
        this.myFixture.enableInspections(new SpringSystemOutErrInspection());
        this.myFixture.configureByText("Sample.java", """
                import lombok.extern.slf4j.Slf4j;
                @Slf4j
                class Sample {
                    void t() {
                        <warning descr="Use logger instead of 'System.out'">System.out.<caret>println("msg")</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with logger call"));
        String text = this.myFixture.getFile().getText();
        assertFalse("Should not contain System.out", text.contains("System.out.println"));
        assertTrue("Should contain direct call: " + text, text.contains("log.info(\"msg\")"));
    }
}

