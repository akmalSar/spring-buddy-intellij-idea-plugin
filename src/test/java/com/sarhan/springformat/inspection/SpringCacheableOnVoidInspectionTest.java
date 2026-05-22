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
 * Tests for {@link SpringCacheableOnVoidInspection}.
 *
 * @author Akmal Sarhan
 */
public class SpringCacheableOnVoidInspectionTest extends BasePlatformTestCase {

    public void testReportsCacheableOnVoid() {
        this.myFixture.enableInspections(new SpringCacheableOnVoidInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Cacheable { }
                class Service {
                    @Cacheable
                    void doWork() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("void method caches nothing"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsCachePutOnVoid() {
        this.myFixture.enableInspections(new SpringCacheableOnVoidInspection());
        this.myFixture.configureByText("Service.java", """
                @interface CachePut { }
                class Service {
                    @CachePut
                    void update() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("void method caches nothing"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportForNonVoidCacheable() {
        this.myFixture.enableInspections(new SpringCacheableOnVoidInspection());
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
                        && h.getDescription().contains("void method caches nothing"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportForCacheEvictOnVoid() {
        this.myFixture.enableInspections(new SpringCacheableOnVoidInspection());
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
                        && h.getDescription().contains("void method caches nothing"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportForSimilarlyNamedCacheableAnnotation() {
        this.myFixture.enableInspections(new SpringCacheableOnVoidInspection());
        this.myFixture.configureByText("Service.java", """
                @interface CacheableCustom { }
                class Service {
                    @CacheableCustom
                    void doWork() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("void method caches nothing"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportWithoutCacheAnnotation() {
        this.myFixture.enableInspections(new SpringCacheableOnVoidInspection());
        this.myFixture.configureByText("Service.java", """
                class Service {
                    void doWork() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("void method caches nothing"))
                .count();
        assertEquals(0, (int) count);
    }
}
