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
public class SpringInterfaceIsTypeInspectionTest extends BasePlatformTestCase {

    public void testFlagsConstantOnlyInterface() {
        this.myFixture.enableInspections(new SpringInterfaceIsTypeInspection());
        this.myFixture.configureByText("Sample.java", """
                interface <warning descr="Interface should define a type, not only constants">Sample</warning> {
                    String NAME = "sample";
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagMarkerInterface() {
        this.myFixture.enableInspections(new SpringInterfaceIsTypeInspection());
        this.myFixture.configureByText("Sample.java", """
                interface Sample {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagInterfaceWithMethod() {
        this.myFixture.enableInspections(new SpringInterfaceIsTypeInspection());
        this.myFixture.configureByText("Sample.java", """
                interface Sample {
                    String NAME = "sample";

                    void doIt();
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testAddMarkerMethodFix() {
        this.myFixture.enableInspections(new SpringInterfaceIsTypeInspection());
        this.myFixture.configureByText("Sample.java", """
                interface <warning descr="Interface should define a type, not only constants"><caret>Sample</warning> {
                    String NAME = "sample";
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Add method to make interface a type");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("void marker();"));
    }
}
