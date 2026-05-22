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
public class SpringRedundantModifierInspectionTest extends BasePlatformTestCase {

    public void testFlagsInterfaceMemberModifiers() {
        this.myFixture.enableInspections(new SpringRedundantModifierInspection());
        this.myFixture.configureByText("Sample.java", """
                interface Sample {
                    <warning descr="Modifier 'public' is redundant">public</warning> <warning descr="Modifier 'static' is redundant">static</warning> <warning descr="Modifier 'final' is redundant">final</warning> String NAME = "sample";

                    <warning descr="Modifier 'public' is redundant">public</warning> <warning descr="Modifier 'abstract' is redundant">abstract</warning> void doIt();
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsNestedTypeModifiersInInterface() {
        this.myFixture.enableInspections(new SpringRedundantModifierInspection());
        this.myFixture.configureByText("Sample.java", """
                interface Sample {
                    <warning descr="Modifier 'public' is redundant">public</warning> <warning descr="Modifier 'static' is redundant">static</warning> class Nested {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsFinalMethodInFinalClassAndPrivateMethod() {
        this.myFixture.enableInspections(new SpringRedundantModifierInspection());
        this.myFixture.configureByText("Sample.java", """
                final class Sample {
                    <warning descr="Modifier 'final' is redundant">final</warning> void doIt() {
                    }

                    private <warning descr="Modifier 'final' is redundant">final</warning> void helper() {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsPublicConstructorInPackagePrivateClass() {
        this.myFixture.enableInspections(new SpringRedundantModifierInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    <warning descr="Modifier 'public' is redundant">public</warning> Sample() {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsPublicConstructorInPublicNestedClassInsidePackagePrivateClass() {
        this.myFixture.enableInspections(new SpringRedundantModifierInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    public static class WrapperException extends RuntimeException {
                        <warning descr="Modifier 'public' is redundant">public</warning> WrapperException(Throwable cause) {
                            super(cause);
                        }

                        <warning descr="Modifier 'public' is redundant">public</warning> WrapperException(Throwable cause, String message) {
                            super(message, cause);
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagPublicConstructorInPublicClass() {
        this.myFixture.enableInspections(new SpringRedundantModifierInspection());
        this.myFixture.configureByText("Sample.java", """
                public class Sample {
                    public Sample() {
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagPublicConstructorInNestedPublicClassInsidePublicClass() {
        this.myFixture.enableInspections(new SpringRedundantModifierInspection());
        this.myFixture.configureByText("Sample.java", """
                public class Sample {
                    public static class Nested {
                        public Nested() {
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsFinalResourceVariables() {
        this.myFixture.enableInspections(new SpringRedundantModifierInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void doIt(AutoCloseable closeable) throws Exception {
                        try (<warning descr="Modifier 'final' is redundant">final</warning> AutoCloseable ignored = closeable) {
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testRemoveRedundantModifierFix() {
        this.myFixture.enableInspections(new SpringRedundantModifierInspection());
        this.myFixture.configureByText("Sample.java", """
                interface Sample {
                    <warning descr="Modifier 'public' is redundant"><caret>public</warning> abstract void doIt();
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Remove redundant 'public' modifier");
        this.myFixture.launchAction(fix);
        assertFalse(this.myFixture.getFile().getText().contains("public abstract void"));
    }
}
