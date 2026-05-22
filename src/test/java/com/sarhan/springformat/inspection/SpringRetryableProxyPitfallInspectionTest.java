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

import java.util.List;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * Tests for {@link SpringRetryableProxyPitfallInspection}.
 *
 * @author Akmal Sarhan
 */
public class SpringRetryableProxyPitfallInspectionTest extends BasePlatformTestCase {

    public void testReportsPrivateRetryable() {
        this.myFixture.enableInspections(new SpringRetryableProxyPitfallInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Retryable { }
                class Service {
                    @Retryable
                    private void doWork() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("private methods are not proxied"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsStaticRetryable() {
        this.myFixture.enableInspections(new SpringRetryableProxyPitfallInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Retryable { }
                class Service {
                    @Retryable
                    static void doWork() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("static methods are not proxied"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsFinalRetryable() {
        this.myFixture.enableInspections(new SpringRetryableProxyPitfallInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Retryable { }
                class Service {
                    @Retryable
                    final void doWork() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("final methods cannot be subclass-proxied"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsRetryableInFinalClass() {
        this.myFixture.enableInspections(new SpringRetryableProxyPitfallInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Retryable { }
                final class Service {
                    @Retryable
                    public void doWork() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("final classes cannot be subclass-proxied"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportForPublicRetryable() {
        this.myFixture.enableInspections(new SpringRetryableProxyPitfallInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Retryable { }
                class Service {
                    @Retryable
                    public void doWork() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("Retryable"))
                .count();
        assertEquals(0, (int) count);
    }
}
