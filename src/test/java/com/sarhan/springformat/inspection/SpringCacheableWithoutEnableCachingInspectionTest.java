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
 * Tests for {@link SpringCacheableWithoutEnableCachingInspection}.
 *
 * @author Akmal Sarhan
 */
public class SpringCacheableWithoutEnableCachingInspectionTest extends BasePlatformTestCase {

    public void testReportsCacheableWithoutEnableCaching() {
        this.myFixture.enableInspections(new SpringCacheableWithoutEnableCachingInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Cacheable { }
                class Service {
                    @Cacheable
                    String doWork() { return ""; }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("no @EnableCaching"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportWhenEnableCachingPresent() {
        this.myFixture.enableInspections(new SpringCacheableWithoutEnableCachingInspection());
        this.myFixture.configureByText("Config.java", """
                @interface EnableCaching { }
                @EnableCaching
                class Config { }
                """);
        this.myFixture.configureByText("Service.java", """
                @interface Cacheable { }
                class Service {
                    @Cacheable
                    String doWork() { return ""; }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("no @EnableCaching"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testReportsCacheEvictWithoutEnableCaching() {
        this.myFixture.enableInspections(new SpringCacheableWithoutEnableCachingInspection());
        this.myFixture.configureByText("Service.java", """
                @interface CacheEvict { }
                class Service {
                    @CacheEvict
                    void clear() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("no @EnableCaching"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportForSimilarlyNamedCacheAnnotation() {
        this.myFixture.enableInspections(new SpringCacheableWithoutEnableCachingInspection());
        this.myFixture.configureByText("Service.java", """
                @interface CacheableCustom { }
                class Service {
                    @CacheableCustom
                    String doWork() { return ""; }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("no @EnableCaching"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testDoesNotTreatSimilarlyNamedEnableCachingAsConfiguration() {
        this.myFixture.enableInspections(new SpringCacheableWithoutEnableCachingInspection());
        this.myFixture.configureByText("Config.java", """
                @interface EnableCachingCustom { }
                @EnableCachingCustom
                class Config { }
                """);
        this.myFixture.configureByText("Service.java", """
                @interface Cacheable { }
                class Service {
                    @Cacheable
                    String doWork() { return ""; }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("no @EnableCaching"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportWithoutCacheAnnotation() {
        this.myFixture.enableInspections(new SpringCacheableWithoutEnableCachingInspection());
        this.myFixture.configureByText("Service.java", """
                class Service {
                    String doWork() { return ""; }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("no @EnableCaching"))
                .count();
        assertEquals(0, (int) count);
    }
}
