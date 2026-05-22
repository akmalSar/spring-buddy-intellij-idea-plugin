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
public class SpringUpperEllInspectionTest extends BasePlatformTestCase {

    public void testFlagsLowercaseLongSuffix() {
        this.myFixture.enableInspections(new SpringUpperEllInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    long value = <warning descr="Long literal should use uppercase 'L' suffix">1l</warning>;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagUppercaseLongSuffix() {
        this.myFixture.enableInspections(new SpringUpperEllInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    long value = 1L;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReplaceLowercaseLongSuffixFix() {
        this.myFixture.enableInspections(new SpringUpperEllInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    long value = <warning descr="Long literal should use uppercase 'L' suffix"><caret>1l</warning>;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Replace 'l' with 'L'");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("1L"));
    }
}
