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
public class SpringProxyAnnotationPitfallInspectionTest extends BasePlatformTestCase {

    public void testReportsAndFixesPrivateSecurityMethod() {
        this.myFixture.enableInspections(new SpringProxyAnnotationPitfallInspection.Security());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    @PreAuthorize("hasRole('ADMIN')")
                    private void <warning descr="Spring Security annotation on private method is not proxied"><caret>admin</warning>() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Make method public");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    @PreAuthorize("hasRole('ADMIN')")
                    public void admin() { }
                }
                """);
    }

    public void testReportsCacheSelfInvocation() {
        this.myFixture.enableInspections(new SpringProxyAnnotationPitfallInspection.Cache());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void outer() { <warning descr="Spring Cache self-invocation bypasses the Spring proxy">cached()</warning>; }
                    @Cacheable("items")
                    String cached() { return ""; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsAsyncSelfInvocationOnCallSite() {
        this.myFixture.enableInspections(new SpringProxyAnnotationPitfallInspection.Async());
        this.myFixture.configureByText("Sample.java", """
                @Service
                class AsyncReturnIgnoredDemo {
                    @Async
                    public java.util.concurrent.CompletableFuture<String> fetchDataAsync() {
                        return java.util.concurrent.CompletableFuture.completedFuture("data");
                    }
                    public void orchestrate() {
                        <warning descr="@Async self-invocation bypasses the Spring proxy">fetchDataAsync()</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsAsyncVoidAndFinalMethod() {
        this.myFixture.enableInspections(new SpringProxyAnnotationPitfallInspection.Async());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    @Async
                    final void <warning descr="@Async annotation on final method cannot be subclass-proxied"><warning descr="@Async void method exceptions are only handled by AsyncUncaughtExceptionHandler">work</warning></warning>() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFixesAsyncVoidToCompletableFuture() {
        this.myFixture.enableInspections(new SpringProxyAnnotationPitfallInspection.Async());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    @Async
                    void <warning descr="@Async void method exceptions are only handled by AsyncUncaughtExceptionHandler"><caret>work</warning>() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Change return type to CompletableFuture<Void>");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("CompletableFuture<Void>"));
        assertTrue(text.contains("return ") && text.contains("CompletableFuture.completedFuture(null);"));
    }
}
