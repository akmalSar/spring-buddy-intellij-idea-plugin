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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sarhan.dataFlow.SpringJpaNPlusOnePredictionDataFlowInspection;

/**
 * @author Akmal Sarhan
 */
public class SpringJpaNPlusOnePredictionDataFlowInspectionTest extends BasePlatformTestCase {

    private static final String ORDER_STUB = """
            import java.util.List;
            @interface Entity { }
            @interface OneToMany { }
            @Entity
            class Order {
                @OneToMany
                List<Item> getItems() { return null; }
            }
            class Item { }
            """;

    public void testReportsForEachLoop() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Order.java", ORDER_STUB);
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface OrderRepository {
                    List<Order> findAll();
                }

                @Service
                class OrderService {
                    OrderRepository repo;

                    @Transactional
                    void processOrders() {
                        List<Order> orders = repo.findAll();
                        for (Order order : orders) {
                            order.getItems().size();
                        }
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("N+1 query predicted"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsStreamForEach() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                @interface Entity { }
                @interface OneToMany { }
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface OrderRepository {
                    List<Order> findAll();
                }

                @Entity
                class Order {
                    @OneToMany
                    List<Item> getItems() { return null; }
                }
                class Item { }

                @Service
                class OrderService {
                    OrderRepository repo;

                    @Transactional
                    void processOrders() {
                        List<Order> orders = repo.findAll();
                        orders.forEach(order -> <warning descr="N+1 query predicted: lazy association loaded inside a loop or stream for each element of a repository result — use fetch join, EntityGraph, or batch loading">order.getItems()</warning>.size());
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsStreamMap() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                import java.util.stream.Collectors;
                @interface Entity { }
                @interface OneToMany { }
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface OrderRepository {
                    List<Order> findAll();
                }

                @Entity
                class Order {
                    @OneToMany
                    List<Item> getItems() { return null; }
                }
                class Item { }

                @Service
                class OrderService {
                    OrderRepository repo;

                    @Transactional
                    void processOrders() {
                        List<Order> orders = repo.findAll();
                        orders.stream().map(order -> <warning descr="N+1 query predicted: lazy association loaded inside a loop or stream for each element of a repository result — use fetch join, EntityGraph, or batch loading">order.getItems()</warning>).collect(Collectors.toList());
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithoutLoop() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Order.java", ORDER_STUB);
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface OrderRepository {
                    List<Order> findAll();
                }

                @Service
                class OrderService {
                    OrderRepository repo;

                    @Transactional
                    void processOrders() {
                        List<Order> orders = repo.findAll();
                        Order order = orders.get(0);
                        order.getItems().size();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("N+1 query predicted"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportWhenNotFromRepository() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Order.java", ORDER_STUB);
        this.myFixture.configureByText("Sample.java", """
                import java.util.ArrayList;
                import java.util.List;
                @interface Transactional { }
                @interface Service { }

                @Service
                class OrderService {

                    @Transactional
                    void processOrders() {
                        List<Order> orders = new ArrayList<>();
                        for (Order order : orders) {
                            order.getItems().size();
                        }
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("N+1 query predicted"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportOutsideTransactionalContext() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Order.java", ORDER_STUB);
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                @interface Service { }
                @interface Repository { }

                @Repository
                interface OrderRepository {
                    List<Order> findAll();
                }

                @Service
                class OrderService {
                    OrderRepository repo;

                    void processOrders() {
                        List<Order> orders = repo.findAll();
                        for (Order order : orders) {
                            order.getItems().size();
                        }
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("N+1 query predicted"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testReportsWithOpenSessionInView() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Order.java", ORDER_STUB);
        this.myFixture.configureByText("Sample.java", """
                // spring.jpa.open-in-view=true
                import java.util.List;
                @interface Service { }
                @interface Repository { }

                @Repository
                interface OrderRepository {
                    List<Order> findAll();
                }

                @Service
                class OrderService {
                    OrderRepository repo;

                    void processOrders() {
                        List<Order> orders = repo.findAll();
                        for (Order order : orders) {
                            order.getItems().size();
                        }
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("N+1 query predicted"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsParameterProvenance() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Order.java", ORDER_STUB);
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                @interface Transactional { }
                @interface Service { }

                @Service
                class OrderService {

                    @Transactional
                    void processOrders(List<Order> orders) {
                        for (Order order : orders) {
                            order.getItems().size();
                        }
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("N+1 query predicted"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsDirectFieldAccess() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                @interface Entity { }
                @interface OneToMany { }
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface OrderRepository {
                    List<Order> findAll();
                }

                @Entity
                class Order {
                    @OneToMany
                    List<Item> items;
                }
                class Item { }

                @Service
                class OrderService {
                    OrderRepository repo;

                    @Transactional
                    void processOrders() {
                        List<Order> orders = repo.findAll();
                        for (Order order : orders) {
                            order.items.size();
                        }
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("N+1 query predicted"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsPageSource() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                @interface Entity { }
                @interface OneToMany { }
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface OrderRepository {
                    Page<Order> findAll(Pageable pageable);
                }
                class Pageable { }
                class Page<T> implements Iterable<T> {
                    List<T> getContent() { return null; }
                }

                @Entity
                class Order {
                    @OneToMany
                    List<Item> getItems() { return null; }
                }
                class Item { }

                @Service
                class OrderService {
                    OrderRepository repo;

                    @Transactional
                    void processOrders() {
                        Page<Order> page = repo.findAll(new Pageable());
                        for (Order order : page) {
                            order.getItems().size();
                        }
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("N+1 query predicted"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportNonLazyFieldAccess() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                @interface Entity { }
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface OrderRepository {
                    List<Order> findAll();
                }

                @Entity
                class Order {
                    String name;
                }

                @Service
                class OrderService {
                    OrderRepository repo;

                    @Transactional
                    void processOrders() {
                        List<Order> orders = repo.findAll();
                        for (Order order : orders) {
                            order.name.length();
                        }
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("N+1 query predicted"))
                .count();
        assertEquals(0, (int) count);
    }

    /**
     * Stream intermediate operations (filter) must propagate taint so that
     * the subsequent forEach lambda parameter is also tainted.
     */
    public void testReportsStreamFilterForEach() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                import java.util.stream.Collectors;
                @interface Entity { }
                @interface OneToMany { }
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface OrderRepository {
                    List<Order> findAll();
                }

                @Entity
                class Order {
                    @OneToMany
                    List<Item> getItems() { return null; }
                }
                class Item { }

                @Service
                class OrderService {
                    OrderRepository repo;

                    @Transactional
                    void processOrders() {
                        List<Order> orders = repo.findAll();
                        orders.stream()
                            .filter(order -> <warning descr="N+1 query predicted: lazy association loaded inside a loop or stream for each element of a repository result — use fetch join, EntityGraph, or batch loading">order.getItems()</warning>.size() > 0)
                            .forEach(order -> <warning descr="N+1 query predicted: lazy association loaded inside a loop or stream for each element of a repository result — use fetch join, EntityGraph, or batch loading">order.getItems()</warning>.size());
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testShowNPlusOneFlowFixRegisteredOnProblem() {
        this.myFixture.enableInspections(new SpringJpaNPlusOnePredictionDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                @interface Entity { }
                @interface OneToMany { }
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface OrderRepository {
                    List<Order> findAll();
                }

                @Entity
                class Order {
                    @OneToMany
                    List<Item> getItems() { return null; }
                }
                class Item { }

                @Service
                class OrderService {
                    OrderRepository repo;

                    @Transactional
                    void processOrders() {
                        List<Order> orders = repo.findAll();
                        for (Order order : orders) {
                            order.getItems().size();
                        }
                    }
                }
                """);
        this.myFixture.doHighlighting();
        boolean hasDiagramFix = this.myFixture.getAllQuickFixes().stream()
                .map(IntentionAction::getText)
                .anyMatch((String text) -> text.contains("N+1 query flow diagram"));
        assertTrue("ShowNPlusOneFlowFix must be offered on flagged lazy access", hasDiagramFix);
    }

}
