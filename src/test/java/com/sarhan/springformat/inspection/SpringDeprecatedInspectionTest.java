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
public class SpringDeprecatedInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("java/lang/Deprecated.java", """
                package java.lang;

                public @interface Deprecated {

                    String since() default "";

                    boolean forRemoval() default false;

                }
                """);
    }

    public void testReportsMissingSinceAttribute() {
        this.myFixture.enableInspections(new SpringDeprecatedInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    <warning descr="@Deprecated should declare a since attribute">@Deprecated</warning>
                    void oldMethod() {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
    }

    public void testReportsMissingSinceWhenOnlyForRemovalIsDeclared() {
        this.myFixture.enableInspections(new SpringDeprecatedInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    <warning descr="@Deprecated should declare a since attribute">@Deprecated(forRemoval = true)</warning>
                    void oldMethod() {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
    }

    public void testReportsEmptySinceAttribute() {
        this.myFixture.enableInspections(new SpringDeprecatedInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    <warning descr="@Deprecated since attribute must not be empty">@Deprecated(since = "")</warning>
                    void oldMethod() {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
    }

    public void testAcceptsNonEmptySinceAttribute() {
        this.myFixture.enableInspections(new SpringDeprecatedInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    @Deprecated(since = "1.2")
                    void oldMethod() {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
    }

    public void testHandlesQualifiedDeprecatedAnnotation() {
        this.myFixture.enableInspections(new SpringDeprecatedInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    <warning descr="@Deprecated should declare a since attribute">@java.lang.Deprecated</warning>
                    void oldMethod() {
                    }

                    @java.lang.Deprecated(since = "1.2")
                    void olderMethod() {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
    }

    public void testFixAddsSinceAttribute() {
        this.myFixture.enableInspections(new SpringDeprecatedInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    <caret>@Deprecated
                    void oldMethod() {
                    }
                }
                """);
        IntentionAction fix = this.myFixture.findSingleIntention("Add @Deprecated since attribute");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    @Deprecated(since = "TODO")
                    void oldMethod() {
                    }
                }
                """);
    }

    public void testFixAddsSinceBeforeExistingForRemovalAttribute() {
        this.myFixture.enableInspections(new SpringDeprecatedInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    <caret>@Deprecated(forRemoval = true)
                    void oldMethod() {
                    }
                }
                """);
        IntentionAction fix = this.myFixture.findSingleIntention("Add @Deprecated since attribute");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    @Deprecated(since = "TODO", forRemoval = true)
                    void oldMethod() {
                    }
                }
                """);
    }

    public void testFixReplacesEmptySinceAttribute() {
        this.myFixture.enableInspections(new SpringDeprecatedInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    <caret>@Deprecated(since = "")
                    void oldMethod() {
                    }
                }
                """);
        IntentionAction fix = this.myFixture.findSingleIntention("Add @Deprecated since attribute");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    @Deprecated(since = "TODO")
                    void oldMethod() {
                    }
                }
                """);
    }

    public void testFixPreservesQualifiedDeprecatedAnnotation() {
        this.myFixture.enableInspections(new SpringDeprecatedInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    <caret>@java.lang.Deprecated
                    void oldMethod() {
                    }
                }
                """);
        IntentionAction fix = this.myFixture.findSingleIntention("Add @Deprecated since attribute");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    @java.lang.Deprecated(since = "TODO")
                    void oldMethod() {
                    }
                }
                """);
    }

}
