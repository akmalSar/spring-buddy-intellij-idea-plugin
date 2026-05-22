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
public class SpringInnerTypeLastInspectionTest extends BasePlatformTestCase {

    public void testFlagsFieldAfterInnerType() {
        this.myFixture.enableInspections(new SpringInnerTypeLastInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {

                    static class Nested {
                    }

                    private String <warning descr="Class members should be declared before inner types">name</warning>;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsMethodAfterInnerType() {
        this.myFixture.enableInspections(new SpringInnerTypeLastInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {

                    interface Nested {
                    }

                    void <warning descr="Class members should be declared before inner types">doIt</warning>() {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagInnerTypeLast() {
        this.myFixture.enableInspections(new SpringInnerTypeLastInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {

                    private String name;

                    void doIt() {
                    }

                    static class Nested {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagMembersAfterInnerTypeInsideNestedClass() {
        this.myFixture.enableInspections(new SpringInnerTypeLastInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {

                    static class Nested {
                        static class InnerNested {
                        }

                        void doIt() {
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testMoveFieldBeforeInnerTypeFix() {
        this.myFixture.enableInspections(new SpringInnerTypeLastInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {

                    static class Nested {
                    }

                    private String <warning descr="Class members should be declared before inner types"><caret>name</warning>;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Move members before inner types");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.indexOf("private String name;") < text.indexOf("static class Nested"));
    }

    public void testMoveMembersBeforeInnerTypesPreservesFieldOrder() {
        this.myFixture.enableInspections(new SpringInnerTypeLastInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {

                    enum Type { OK }

                    enum Reason { USER }

                    private final <warning descr="Class members should be declared before inner types"><caret>Type outcome</warning>;
                    private final String remark;
                    private final Reason reason;

                    static Sample of() {
                        return new Sample(Type.OK, "ok", Reason.USER);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Move members before inner types");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text, text.indexOf("private final Type outcome;") < text.indexOf("private final String remark;"));
        assertTrue(text, text.indexOf("private final String remark;") < text.indexOf("private final Reason reason;"));
        assertTrue(text, text.indexOf("private final Reason reason;") < text.indexOf("static Sample of()"));
        assertTrue(text, text.indexOf("static Sample of()") < text.indexOf("enum Type"));
    }
}
