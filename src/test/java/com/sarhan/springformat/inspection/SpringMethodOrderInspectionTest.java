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
import org.assertj.core.api.Assertions;

/**
 * @author Akmal Sarhan
 */
public class SpringMethodOrderInspectionTest extends BasePlatformTestCase {

    public void testReportsAndFixesOutOfOrderMethods() {
        this.myFixture.enableInspections(new SpringMethodOrderInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    public int <warning descr="Method 'toString' is not ordered according to spring-javaformat conventions">toString</warning>() { return 0; }
                    public int hashCode() { return 1; }
                    public boolean <caret><warning descr="Method 'equals' is not ordered according to spring-javaformat conventions">equals</warning>(int other) { return false; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Reorder equals, hashCode, and toString methods");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        Assertions.assertThat(text.indexOf("equals(int other)") < text.indexOf("hashCode()")).isTrue();
        Assertions.assertThat(text.indexOf("hashCode()") < text.indexOf("toString()")).isTrue();
    }

    public void testDoesNotReportLombokGeneratedEqualsAndHashCode() {
        this.myFixture.enableInspections(new SpringMethodOrderInspection());
        this.myFixture.configureByText("ApartmentInfoImpl.java", """
                @Data
                class ApartmentInfoImpl {
                    public String toString() { return ""; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
