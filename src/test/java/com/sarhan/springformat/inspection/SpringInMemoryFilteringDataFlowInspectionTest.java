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
 * Tests for {@link SpringInMemoryFilteringDataFlowInspection}.
 *
 * @author Akmal Sarhan
 */
public class SpringInMemoryFilteringDataFlowInspectionTest extends BasePlatformTestCase {

    public void testReportsFindAllStreamFilter() {
        this.myFixture.enableInspections(new SpringInMemoryFilteringDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Repository { }
                interface OrderRepository extends Repository {
                    java.util.List<Order> findAll();
                }
                class Order {
                    String getStatus() { return ""; }
                }
                class Service {
                    OrderRepository repo;
                    void process() {
                        repo.findAll().stream().filter(o -> o.getStatus().equals("NEW")).count();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("filtered"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsFindAllStreamSorted() {
        this.myFixture.enableInspections(new SpringInMemoryFilteringDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Repository { }
                interface OrderRepository extends Repository {
                    java.util.List<Order> findAll();
                }
                class Order implements Comparable<Order> {
                    public int compareTo(Order o) { return 0; }
                }
                class Service {
                    OrderRepository repo;
                    void process() {
                        repo.findAll().stream().sorted().count();
                    }
                }
                """);
        long count = countWarnings();
        assertEquals(1, (int) count);
    }

    public void testReportsStreamMethodWithoutStreamCall() {
        this.myFixture.enableInspections(new SpringInMemoryFilteringDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Repository { }
                interface OrderRepository extends Repository {
                    java.util.stream.Stream<Order> streamAll();
                }
                class Order {
                    String getStatus() { return ""; }
                }
                class Service {
                    OrderRepository repo;
                    void process() {
                        repo.streamAll().filter(o -> o.getStatus().equals("NEW")).count();
                    }
                }
                """);
        long count = countWarnings();
        assertEquals(1, (int) count);
    }

    public void testDoesNotReportWithoutStreamChain() {
        this.myFixture.enableInspections(new SpringInMemoryFilteringDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Repository { }
                interface OrderRepository extends Repository {
                    java.util.List<Order> findAll();
                }
                class Order { }
                class Service {
                    OrderRepository repo;
                    void process() {
                        java.util.List<Order> orders = repo.findAll();
                        System.out.println(orders.size());
                    }
                }
                """);
        long count = countWarnings();
        assertEquals(0, (int) count);
    }

    private long countWarnings() {
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        return highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("filtered"))
                .count();
    }
}
