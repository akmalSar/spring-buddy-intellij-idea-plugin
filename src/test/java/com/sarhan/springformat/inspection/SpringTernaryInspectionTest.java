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
public class SpringTernaryInspectionTest extends BasePlatformTestCase {

    public void testReportsAndFixesMissingParens() {
        this.myFixture.enableInspections(new SpringTernaryInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    int test(boolean a, boolean b) {
                        return <warning descr="Ternary condition should be wrapped in parentheses"><caret>a && b</warning> ? 1 : 0;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Wrap ternary condition in parentheses");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    int test(boolean a, boolean b) {
                        return (a && b) ? 1 : 0;
                    }
                }
                """);
    }

    public void testReportsAndFixesNullEquals() {
        this.myFixture.enableInspections(new SpringTernaryInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    int test(int[] value) {
                        return <warning descr="Use '!=' for ternary null checks"><caret>value == null</warning> ? 1 : 0;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Replace '==' null check with '!='");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    int test(int[] value) {
                        return value != null ? 0 : 1;
                    }
                }
                """);
    }

    public void testNullEqualsFixSwapsBranchesToPreserveLogic() {
        this.myFixture.enableInspections(new SpringTernaryInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    Result test(PsiClass psiClass, PsiMethod copy, PsiMethod anchor, PsiMethod current) {
                        return <warning descr="Use '!=' for ternary null checks"><caret>current == null</warning> ? psiClass.addBefore(copy, anchor) : psiClass.addAfter(copy, current);
                    }
                    interface PsiClass {
                        Result addBefore(PsiMethod copy, PsiMethod anchor);
                        Result addAfter(PsiMethod copy, PsiMethod current);
                    }
                    interface PsiMethod { }
                    interface Result { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Replace '==' null check with '!='");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    Result test(PsiClass psiClass, PsiMethod copy, PsiMethod anchor, PsiMethod current) {
                        return current != null ? psiClass.addAfter(copy, current) : psiClass.addBefore(copy, anchor);
                    }
                    interface PsiClass {
                        Result addBefore(PsiMethod copy, PsiMethod anchor);
                        Result addAfter(PsiMethod copy, PsiMethod current);
                    }
                    interface PsiMethod { }
                    interface Result { }
                }
                """);
    }

    public void testNullOnLeftFixUsesExpressionNotNullAndSwapsBranches() {
        this.myFixture.enableInspections(new SpringTernaryInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    int test(Value value) {
                        return <warning descr="Use '!=' for ternary null checks"><caret>null == value</warning> ? 1 : 0;
                    }
                    interface Value { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Replace '==' null check with '!='");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    int test(Value value) {
                        return value != null ? 0 : 1;
                    }
                    interface Value { }
                }
                """);
    }

    public void testReportsQualifiedNullEquals() {
        this.myFixture.enableInspections(new SpringTernaryInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    int test(Source source) {
                        return <warning descr="Use '!=' for ternary null checks"><caret>source.items == null</warning> ? 1 : source.items.length;
                    }
                    static class Source { int[] items; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
    }

    public void testReportsParenthesizedQualifiedNullEquals() {
        this.myFixture.enableInspections(new SpringTernaryInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    int test(Source source) {
                        return (<warning descr="Use '!=' for ternary null checks"><caret>source.items == null</warning>) ? 1 : source.items.length;
                    }
                    static class Source { int[] items; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
    }

    public void testReportsParenthesizedLocalListNullEquals() {
        this.myFixture.enableInspections(new SpringTernaryInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    int[] test(int[] replacements) {
                        final int[] replacementIds = (<warning descr="Use '!=' for ternary null checks"><caret>replacements == null</warning>) ? null
                                : replacements;
                        return replacementIds;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
    }
}
