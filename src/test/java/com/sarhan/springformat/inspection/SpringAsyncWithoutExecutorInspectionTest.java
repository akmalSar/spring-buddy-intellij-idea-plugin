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
public class SpringAsyncWithoutExecutorInspectionTest extends BasePlatformTestCase {

    public void testReportsMissingExecutor() {
        this.myFixture.enableInspections(new SpringAsyncWithoutExecutorInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Async { }
                @interface Component { }

                @Component
                class Sample {
                    @<error descr="@Async without explicit executor falls back to SimpleAsyncTaskExecutor — specify executor='...' or implement AsyncConfigurer">Async</error>
                    void work() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithExecutorAttribute() {
        this.myFixture.enableInspections(new SpringAsyncWithoutExecutorInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Async { String executor() default ""; }
                @interface Component { }

                @Component
                class Sample {
                    @Async(executor = "taskExecutor")
                    void work() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsClassLevelAsyncWithoutExecutor() {
        this.myFixture.enableInspections(new SpringAsyncWithoutExecutorInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Async { }
                @interface Component { }

                @Component
                @<error descr="@Async without explicit executor falls back to SimpleAsyncTaskExecutor — specify executor='...' or implement AsyncConfigurer">Async</error>
                class Sample {
                    void work() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWhenAsyncConfigurerImplemented() {
        this.myFixture.enableInspections(new SpringAsyncWithoutExecutorInspection());
        this.myFixture.addFileToProject("AsyncConfigurer.java", """
                package org.springframework.scheduling.annotation;
                public interface AsyncConfigurer {
                    java.util.concurrent.Executor getAsyncExecutor();
                }
                """);
        this.myFixture.configureByText("Config.java", """
                import org.springframework.scheduling.annotation.AsyncConfigurer;

                class Config implements AsyncConfigurer {
                    public java.util.concurrent.Executor getAsyncExecutor() { return null; }
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                @interface Async { }
                @interface Component { }

                @Component
                class Sample {
                    @Async
                    void work() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportForNonSpringAsyncFqn() {
        this.myFixture.enableInspections(new SpringAsyncWithoutExecutorInspection());
        this.myFixture.addFileToProject("com/example/Async.java", """
                package com.example;
                public @interface Async { }
                """);
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    @com.example.Async
                    void work() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFixPreservesExistingNonExecutorAttribute() {
        this.myFixture.enableInspections(new SpringAsyncWithoutExecutorInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Async { String executor() default ""; boolean fallback() default false; }
                @interface Component { }

                @Component
                class Sample {
                    @<error descr="@Async without explicit executor falls back to SimpleAsyncTaskExecutor — specify executor='...' or implement AsyncConfigurer">Async<caret></error>(fallback = true)
                    void work() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);

        IntentionAction fix = this.myFixture.findSingleIntention("Add executor = \"taskExecutor\"");
        this.myFixture.launchAction(fix);

        this.myFixture.checkResult("""
                @interface Async { String executor() default ""; boolean fallback() default false; }
                @interface Component { }

                @Component
                class Sample {
                    @Async(fallback = true, executor = "taskExecutor")
                    void work() { }
                }
                """);
    }
}
