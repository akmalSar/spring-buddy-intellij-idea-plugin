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
public class SpringMockitoStyleInspectionTest extends BasePlatformTestCase {

    public void testConvertsQualifiedBddMockitoWhenToMockitoWhen() {
        this.myFixture.enableInspections(new SpringMockitoStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test(Service service) {
                        <warning descr="Use Mockito.when(...) instead of BDDMockito static imports"><caret>BDDMockito.when(service.call())</warning>.thenReturn("ok");
                    }
                    interface Service { String call(); }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Use Mockito.when");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("Mockito.when(service.call()).thenReturn(\"ok\");"));
    }
}
