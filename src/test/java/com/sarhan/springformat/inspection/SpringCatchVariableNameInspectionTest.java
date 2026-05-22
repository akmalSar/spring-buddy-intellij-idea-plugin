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
public class SpringCatchVariableNameInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("java/lang/Object.java", """
                package java.lang;
                public class Object {
                }
                """);
        this.myFixture.addFileToProject("java/lang/Throwable.java", """
                package java.lang;
                public class Throwable {
                }
                """);
        this.myFixture.addFileToProject("java/lang/Exception.java", """
                package java.lang;
                public class Exception extends Throwable {
                }
                """);
        this.myFixture.addFileToProject("java/lang/RuntimeException.java", """
                package java.lang;
                public class RuntimeException extends Exception {
                }
                """);
    }

    public void testReportsSingleLetterCatchVariables() {
        this.myFixture.enableInspections(new SpringCatchVariableNameInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test() {
                        try {
                            work();
                        }
                        catch (Exception <warning descr="Catch variable should not use a single-letter name">e</warning>) {
                            log(e);
                        }
                        try {
                            work();
                        }
                        catch (RuntimeException <warning descr="Catch variable should not use a single-letter name">x</warning>) {
                            log(x);
                        }
                    }

                    void work() {
                    }

                    void log(Object value) {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
    }

    public void testReportsWideEyeCatchVariables() {
        this.myFixture.enableInspections(new SpringCatchVariableNameInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test() {
                        try {
                            work();
                        }
                        catch (Exception <warning descr="Catch variable should not be named 'o_o'">o_o</warning>) {
                            log(o_o);
                        }
                        try {
                            work();
                        }
                        catch (RuntimeException <warning descr="Catch variable should not be named 'o_o'">O_O</warning>) {
                            log(O_O);
                        }
                    }

                    void work() {
                    }

                    void log(Object value) {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
    }

    public void testAcceptsDescriptiveCatchVariables() {
        this.myFixture.enableInspections(new SpringCatchVariableNameInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test() {
                        try {
                            work();
                        }
                        catch (Exception ex) {
                            log(ex);
                        }
                        try {
                            work();
                        }
                        catch (RuntimeException ignored) {
                            log(ignored);
                        }
                    }

                    void work() {
                    }

                    void log(Object value) {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
    }

    public void testFixRenamesSingleLetterCatchVariableAndUsages() {
        this.myFixture.enableInspections(new SpringCatchVariableNameInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test() {
                        try {
                            work();
                        }
                        catch (Exception <caret>e) {
                            log(e);
                        }
                    }

                    void work() {
                    }

                    void log(Object value) {
                    }
                }
                """);
        IntentionAction fix = this.myFixture.findSingleIntention("Rename to 'ex'");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    void test() {
                        try {
                            work();
                        }
                        catch (Exception ex) {
                            log(ex);
                        }
                    }

                    void work() {
                    }

                    void log(Object value) {
                    }
                }
                """);
    }

    public void testFixRenamesWideEyeCatchVariableAndUsages() {
        this.myFixture.enableInspections(new SpringCatchVariableNameInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test() {
                        try {
                            work();
                        }
                        catch (Exception <caret>o_o) {
                            log(o_o);
                        }
                    }

                    void work() {
                    }

                    void log(Object value) {
                    }
                }
                """);
        IntentionAction fix = this.myFixture.findSingleIntention("Rename to 'ex'");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    void test() {
                        try {
                            work();
                        }
                        catch (Exception ex) {
                            log(ex);
                        }
                    }

                    void work() {
                    }

                    void log(Object value) {
                    }
                }
                """);
    }

    public void testFixAvoidsExistingExVariableInCatchBody() {
        this.myFixture.enableInspections(new SpringCatchVariableNameInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test() {
                        try {
                            work();
                        }
                        catch (Exception <caret>e) {
                            Object ex = e;
                            log(e);
                            log(ex);
                        }
                    }

                    void work() {
                    }

                    void log(Object value) {
                    }
                }
                """);
        IntentionAction fix = this.myFixture.findSingleIntention("Rename to 'ex'");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    void test() {
                        try {
                            work();
                        }
                        catch (Exception exception) {
                            Object ex = exception;
                            log(exception);
                            log(ex);
                        }
                    }

                    void work() {
                    }

                    void log(Object value) {
                    }
                }
                """);
    }

}
