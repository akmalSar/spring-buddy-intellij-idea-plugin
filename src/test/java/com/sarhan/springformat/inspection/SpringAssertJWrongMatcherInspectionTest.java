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
public class SpringAssertJWrongMatcherInspectionTest extends BasePlatformTestCase {

    public void testRewritesIsEqualToEmptyStringToIsEmpty() {
        this.myFixture.enableInspections(new SpringAssertJWrongMatcherInspection.EmptyStringEquality());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(String s) {
                        <warning descr="Use isEmpty() instead of isEqualTo(\\"\\")"><caret>assertThat(s).isEqualTo("")</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Replace with assertThat(s).isEmpty()");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("assertThat(s).isEmpty()"));
    }

    public void testRewritesSizeIsEqualToZeroToIsEmpty() {
        this.myFixture.enableInspections(new SpringAssertJWrongMatcherInspection.SizeEqualsMatcher());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(List<String> list) {
                        <warning descr="Use isEmpty() instead of size().isEqualTo(0)"><caret>assertThat(list.size()).isEqualTo(0)</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Replace with assertThat(list).isEmpty()");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("assertThat(list).isEmpty()"));
    }

    public void testRewritesSizeIsEqualToNToHasSize() {
        this.myFixture.enableInspections(new SpringAssertJWrongMatcherInspection.SizeEqualsMatcher());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(List<String> list) {
                        <warning descr="Use hasSize(n) instead of size().isEqualTo(n)"><caret>assertThat(list.size()).isEqualTo(3)</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Replace with assertThat(list).hasSize(3)");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("assertThat(list).hasSize(3)"));
    }

    public void testRewritesEqualsIsTrueToIsEqualTo() {
        this.myFixture.enableInspections(new SpringAssertJWrongMatcherInspection.BooleanMethodMatcher());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(String a, String b) {
                        <warning descr="Use isEqualTo(...) instead of equals(...).isTrue()"><caret>assertThat(a.equals(b)).isTrue()</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Replace with assertThat(a).isEqualTo(b)");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("assertThat(a).isEqualTo(b)"));
    }

    public void testRewritesIsPresentIsTrueToIsPresent() {
        this.myFixture.enableInspections(new SpringAssertJWrongMatcherInspection.BooleanMethodMatcher());
        this.myFixture.configureByText("Sample.java", """
                import java.util.Optional;
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(Optional<String> opt) {
                        <warning descr="Use AssertJ Optional matcher instead of isPresent().isTrue()/isFalse()"><caret>assertThat(opt.isPresent()).isTrue()</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Replace with assertThat(opt).isPresent()");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("assertThat(opt).isPresent()"));
    }

    public void testPreservesAssertionsQualifierWhenRewritingSize() {
        this.myFixture.enableInspections(new SpringAssertJWrongMatcherInspection.SizeEqualsMatcher());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                class Assertions { public static <T> Object assertThat(T t) { return null; } }
                class Sample {
                    void doIt(List<String> list) {
                        <warning descr="Use hasSize(n) instead of size().isEqualTo(n)"><caret>Assertions.assertThat(list.size()).isEqualTo(3)</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Replace with Assertions.assertThat(list).hasSize(3)");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("Assertions.assertThat(list).hasSize(3)"));
    }

    public void testHashesLongArgWhenConvertingToHasSize() {
        this.myFixture.enableInspections(new SpringAssertJWrongMatcherInspection.SizeEqualsMatcher());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(List<String> list) {
                        <warning descr="Use hasSize(n) instead of size().isEqualTo(n)"><caret>assertThat(list.size()).isEqualTo(5L)</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Replace with assertThat(list).hasSize((int)(5L))");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("Expected hasSize with cast, got: " + text, text.contains("hasSize((int)"));
    }

    public void testDoesNotFlagIdiomaticAssertion() {
        this.myFixture.enableInspections(new SpringAssertJWrongMatcherInspection.EmptyStringEquality(),
                new SpringAssertJWrongMatcherInspection.BooleanMethodMatcher(),
                new SpringAssertJWrongMatcherInspection.SizeEqualsMatcher());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(String s) {
                        assertThat(s).isEqualTo("hello");
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
