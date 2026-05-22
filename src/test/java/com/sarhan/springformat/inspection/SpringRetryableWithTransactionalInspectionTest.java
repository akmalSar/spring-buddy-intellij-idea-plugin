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
 * Tests for {@link SpringRetryableWithTransactionalInspection}.
 *
 * @author Akmal Sarhan
 */
public class SpringRetryableWithTransactionalInspectionTest extends BasePlatformTestCase {

    public void testReportsRetryableWithTransactional() {
        this.myFixture.enableInspections(new SpringRetryableWithTransactionalInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Retryable { }
                @interface Transactional { }
                class Service {
                    @Retryable
                    @Transactional
                    void doWork() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@Retryable combined with @Transactional"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsClassLevelRetryableWithMethodTransactional() {
        this.myFixture.enableInspections(new SpringRetryableWithTransactionalInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Retryable { }
                @interface Transactional { }
                @Retryable
                class Service {
                    @Transactional
                    void doWork() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@Retryable combined with @Transactional"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportForRetryableOnly() {
        this.myFixture.enableInspections(new SpringRetryableWithTransactionalInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Retryable { }
                class Service {
                    @Retryable
                    void doWork() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@Retryable combined with @Transactional"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportForTransactionalOnly() {
        this.myFixture.enableInspections(new SpringRetryableWithTransactionalInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Transactional { }
                class Service {
                    @Transactional
                    void doWork() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@Retryable combined with @Transactional"))
                .count();
        assertEquals(0, (int) count);
    }
}
