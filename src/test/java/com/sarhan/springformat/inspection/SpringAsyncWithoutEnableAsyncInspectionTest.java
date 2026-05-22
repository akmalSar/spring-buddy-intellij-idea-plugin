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
public class SpringAsyncWithoutEnableAsyncInspectionTest extends BasePlatformTestCase {

    public void testReportsAndFixesAsyncWithoutEnableAsync() {
        this.myFixture.enableInspections(new SpringAsyncWithoutEnableAsyncInspection());
        this.myFixture.configureByText("App.java", """
                @interface SpringBootApplication { }
                @SpringBootApplication
                class App { }
                """);
        this.myFixture.configureByText("Service.java", """
                @interface Async { }
                class Service {
                    <error descr="@Async is ignored because no @EnableAsync configuration was found in the project"><caret>@Async</error>
                    void work() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Add @EnableAsync to configuration class");
        this.myFixture.launchAction(fix);
        this.myFixture.openFileInEditor(this.myFixture.findFileInTempDir("App.java"));
        assertTrue(this.myFixture.getFile().getText()
                .contains("@org.springframework.scheduling.annotation.EnableAsync"));
    }

    public void testDoesNotReportWhenEnableAsyncPresent() {
        this.myFixture.enableInspections(new SpringAsyncWithoutEnableAsyncInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Async { }
                @interface EnableAsync { }
                @EnableAsync
                class Config { }
                class Service {
                    @Async
                    void work() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
