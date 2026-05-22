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
public class SpringMethodVisibilityInspectionTest extends BasePlatformTestCase {

    public void testReportsAndFixesPublicMethodInPackagePrivateClass() {
        this.myFixture.enableInspections(new SpringMethodVisibilityInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    public void <warning descr="Public method should be package-private in a non-public class"><caret>test</warning>() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Make method package-private");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    void test() { }
                }
                """);
    }

    public void testReportsAndFixesProtectedMethodInFinalClass() {
        this.myFixture.enableInspections(new SpringMethodVisibilityInspection());
        this.myFixture.configureByText("Sample.java", """
                final class Sample {
                    protected void <warning descr="Protected method should be package-private in a final class"><caret>test</warning>() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Make method package-private");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                final class Sample {
                    void test() { }
                }
                """);
    }

    public void testDoesNotReportMethodsDeclaredInNonPublicInterfaces() {
        this.myFixture.enableInspections(new SpringMethodVisibilityInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    private interface ValidationRule {
                        boolean matches(Object target);
                        void validate(Object target, Object errors, Object context);
                    }
                
                    @FunctionalInterface
                    interface MvcResultSupplier {
                        Object get() throws Exception;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportPublicRecordMethodsImplementingPrivateInterface() {
        this.myFixture.enableInspections(new SpringMethodVisibilityInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    private interface ValidationRule {
                        boolean matches(Object target);
                        void validate(Object target, Object errors, Object context);
                    }

                    private record TypedValidationRule() implements ValidationRule {
                        public boolean matches(Object target) { return true; }
                        public void validate(Object target, Object errors, Object context) { }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
