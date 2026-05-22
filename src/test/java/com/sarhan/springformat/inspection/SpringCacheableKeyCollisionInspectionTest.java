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
 * Tests for {@link SpringCacheableKeyCollisionInspection}.
 *
 * @author Akmal Sarhan
 */
public class SpringCacheableKeyCollisionInspectionTest extends BasePlatformTestCase {

    public void testReportsCollisionForTwoMethodsWithSameCacheName() {
        this.myFixture.enableInspections(new SpringCacheableKeyCollisionInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Cacheable {
                    String value();
                }
                class Service {
                    @Cacheable("users")
                    String findById(Long id) { return ""; }
                    @Cacheable("users")
                    String findByEmail(String email) { return ""; }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("shares cache name"))
                .count();
        assertEquals(2, (int) count);
    }

    public void testNoReportForDifferentCacheNames() {
        this.myFixture.enableInspections(new SpringCacheableKeyCollisionInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Cacheable {
                    String value();
                }
                class Service {
                    @Cacheable("users-by-id")
                    String findById(Long id) { return ""; }
                    @Cacheable("users-by-email")
                    String findByEmail(String email) { return ""; }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("shares cache name"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testReportsCollisionForCachePutAndCacheable() {
        this.myFixture.enableInspections(new SpringCacheableKeyCollisionInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Cacheable {
                    String value();
                }
                @interface CachePut {
                    String value();
                }
                class Service {
                    @Cacheable("users")
                    String findById(Long id) { return ""; }
                    @CachePut("users")
                    String update(Long id, String name) { return ""; }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("shares cache name"))
                .count();
        assertEquals(2, (int) count);
    }

    public void testNoReportForSingleCacheableMethod() {
        this.myFixture.enableInspections(new SpringCacheableKeyCollisionInspection());
        this.myFixture.configureByText("Service.java", """
                @interface Cacheable {
                    String value();
                }
                class Service {
                    @Cacheable("users")
                    String findById(Long id) { return ""; }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("shares cache name"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportForCacheEvictOnly() {
        this.myFixture.enableInspections(new SpringCacheableKeyCollisionInspection());
        this.myFixture.configureByText("Service.java", """
                @interface CacheEvict {
                    String value();
                }
                class Service {
                    @CacheEvict("users")
                    void clear() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("shares cache name"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportForSimilarlyNamedCacheableAnnotation() {
        this.myFixture.enableInspections(new SpringCacheableKeyCollisionInspection());
        this.myFixture.configureByText("Service.java", """
                @interface CacheableCustom {
                    String value();
                }
                class Service {
                    @CacheableCustom("users")
                    String findById(Long id) { return ""; }
                    @CacheableCustom("users")
                    String findByEmail(String email) { return ""; }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("shares cache name"))
                .count();
        assertEquals(0, (int) count);
    }
}
