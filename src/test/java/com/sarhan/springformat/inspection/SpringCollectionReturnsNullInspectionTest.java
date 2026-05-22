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
public class SpringCollectionReturnsNullInspectionTest extends BasePlatformTestCase {

    public void testFlagsListReturningNullAndQuickFixReplaces() {
        this.myFixture.enableInspections(new SpringCollectionReturnsNullInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    java.util.List<String> get() {
                        return nu<caret>ll;
                    }
                }
                """);
        this.myFixture.doHighlighting();
        java.util.List<IntentionAction> fixes = this.myFixture.getAllQuickFixes();
        IntentionAction fix = null;
        for (IntentionAction f : fixes) {
            if ("Replace null with empty collection".equals(f.getFamilyName())) {
                fix = f;
                break;
            }
        }
        assertNotNull("expected quick-fix not found, got: " + fixes, fix);
        this.myFixture.launchAction(fix);
        String result = this.myFixture.getFile().getText();
        assertTrue(result, result.contains("Collections.emptyList()"));
    }

    public void testFlagsMapReturningNull() {
        this.myFixture.enableInspections(new SpringCollectionReturnsNullInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    java.util.Map<String,String> get() {
                        return <warning descr="Method returning a collection should not return null; return an empty collection instead">null</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagStringReturningNull() {
        this.myFixture.enableInspections(new SpringCollectionReturnsNullInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    String get() { return null; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
