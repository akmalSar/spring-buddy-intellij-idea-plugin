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
import com.sarhan.dataFlow.SpringReadOnlyEntityMutationDataFlowInspection;

/**
 * @author Akmal Sarhan
 */
public class SpringReadOnlyEntityMutationDataFlowInspectionTest extends BasePlatformTestCase {

    public void testReportsSetterInReadOnly() {
        this.myFixture.enableInspections(new SpringReadOnlyEntityMutationDataFlowInspection());
        this.myFixture.configureByText("Order.java", """
                @interface Entity { }
                @Entity
                class Order {
                    void setStatus(String s) { }
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { boolean readOnly() default false; }
                @interface Repository { }
                @interface Service { }

                @Repository
                interface OrderRepo {
                    Order findById(Long id);
                }

                @Service
                class Sample {
                    OrderRepo repo;

                    @Transactional(readOnly = true)
                    Order getOrder(Long id) {
                        Order order = repo.findById(id);
                        order.setStatus("VIEWED");
                        return order;
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Entity mutation inside @Transactional(readOnly = true)"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportWithoutReadOnly() {
        this.myFixture.enableInspections(new SpringReadOnlyEntityMutationDataFlowInspection());
        this.myFixture.configureByText("Order.java", """
                @interface Entity { }
                @Entity
                class Order {
                    void setStatus(String s) { }
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { boolean readOnly() default false; }
                @interface Repository { }
                @interface Service { }

                @Repository
                interface OrderRepo {
                    Order findById(Long id);
                }

                @Service
                class Sample {
                    OrderRepo repo;

                    @Transactional
                    Order getOrder(Long id) {
                        Order order = repo.findById(id);
                        order.setStatus("VIEWED");
                        return order;
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Entity mutation inside @Transactional(readOnly = true)"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportOnNonEntity() {
        this.myFixture.enableInspections(new SpringReadOnlyEntityMutationDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { boolean readOnly() default false; }
                class Order {
                    void setStatus(String s) { }
                }
                class Sample {
                    @Transactional(readOnly = true)
                    void getOrder() {
                        Order order = new Order();
                        order.setStatus("VIEWED");
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Entity mutation inside @Transactional(readOnly = true)"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testReportsSetterWhenClassIsReadOnlyTransactional() {
        this.myFixture.enableInspections(new SpringReadOnlyEntityMutationDataFlowInspection());
        this.myFixture.configureByText("Order.java", """
                @interface Entity { }
                @Entity
                class Order {
                    void setStatus(String s) { }
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { boolean readOnly() default false; }
                @interface Repository { }
                @interface Service { }

                @Repository
                interface OrderRepo {
                    Order findById(Long id);
                }

                @Service
                @Transactional(readOnly = true)
                class Sample {
                    OrderRepo repo;

                    Order getOrder(Long id) {
                        Order order = repo.findById(id);
                        order.setStatus("VIEWED");
                        return order;
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Entity mutation inside @Transactional(readOnly = true)"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportWhenClassIsNotReadOnlyTransactional() {
        this.myFixture.enableInspections(new SpringReadOnlyEntityMutationDataFlowInspection());
        this.myFixture.configureByText("Order.java", """
                @interface Entity { }
                @Entity
                class Order {
                    void setStatus(String s) { }
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { boolean readOnly() default false; }
                @interface Repository { }
                @interface Service { }

                @Repository
                interface OrderRepo {
                    Order findById(Long id);
                }

                @Service
                @Transactional
                class Sample {
                    OrderRepo repo;

                    Order getOrder(Long id) {
                        Order order = repo.findById(id);
                        order.setStatus("VIEWED");
                        return order;
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Entity mutation inside @Transactional(readOnly = true)"))
                .count();
        assertEquals(0, (int) count);
    }

    /**
     * DataFlow-specific: alias propagation through reassignment.
     * The old PSI-only implementation would miss this because it only checked
     * the direct qualifier variable, not aliases.
     */
    public void testReportsAliasPropagation() {
        this.myFixture.enableInspections(new SpringReadOnlyEntityMutationDataFlowInspection());
        this.myFixture.configureByText("Order.java", """
                @interface Entity { }
                @Entity
                class Order {
                    void setStatus(String s) { }
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { boolean readOnly() default false; }
                @interface Repository { }
                @interface Service { }

                @Repository
                interface OrderRepo {
                    Order findById(Long id);
                }

                @Service
                class Sample {
                    OrderRepo repo;

                    @Transactional(readOnly = true)
                    void getOrder(Long id) {
                        Order order = repo.findById(id);
                        Order alias = order;
                        alias.setStatus("VIEWED");
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Entity mutation inside @Transactional(readOnly = true)"))
                .count();
        assertEquals(1, (int) count);
    }

    /**
     * DataFlow-specific: Optional unwrap chain.
     * The old PSI-only implementation would miss this because the entity
     * flows through Optional.get() before the setter call.
     */
    public void testReportsOptionalUnwrap() {
        this.myFixture.enableInspections(new SpringReadOnlyEntityMutationDataFlowInspection());
        this.myFixture.configureByText("Order.java", """
                @interface Entity { }
                @Entity
                class Order {
                    void setStatus(String s) { }
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                import java.util.Optional;
                @interface Transactional { boolean readOnly() default false; }
                @interface Repository { }
                @interface Service { }

                @Repository
                interface OrderRepo {
                    Optional<Order> findById(Long id);
                }

                @Service
                class Sample {
                    OrderRepo repo;

                    @Transactional(readOnly = true)
                    void getOrder(Long id) {
                        Optional<Order> opt = repo.findById(id);
                        Order order = opt.get();
                        order.setStatus("VIEWED");
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Entity mutation inside @Transactional(readOnly = true)"))
                .count();
        assertEquals(1, (int) count);
    }

    /**
     * DataFlow-specific: chained Optional unwrap without intermediate variable.
     * The initializer is {@code repo.findById(id).orElse(null)} — the source seeding
     * must peel through {@code Optional.orElse()} to reach the repository call.
     */
    public void testReportsChainedOptionalOrElse() {
        this.myFixture.enableInspections(new SpringReadOnlyEntityMutationDataFlowInspection());
        this.myFixture.configureByText("Order.java", """
                @interface Entity { }
                @Entity
                class Order {
                    void setStatus(String s) { }
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                import java.util.Optional;
                @interface Transactional { boolean readOnly() default false; }
                @interface Repository { }
                @interface Service { }

                @Repository
                interface OrderRepo {
                    Optional<Order> findById(Long id);
                }

                @Service
                class Sample {
                    OrderRepo repo;

                    @Transactional(readOnly = true)
                    void setOrderStatus(Long id) {
                        Order order = repo.findById(id).orElse(null);
                        order.setStatus("VIEWED");
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Entity mutation inside @Transactional(readOnly = true)"))
                .count();
        assertEquals(1, (int) count);
    }

    /**
     * DataFlow-specific: EntityManager source should be tracked the same as
     * a Spring Data repository source.
     */
    public void testReportsEntityManagerSource() {
        this.myFixture.enableInspections(new SpringReadOnlyEntityMutationDataFlowInspection());
        this.myFixture.configureByText("Order.java", """
                @interface Entity { }
                @Entity
                class Order {
                    void setStatus(String s) { }
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { boolean readOnly() default false; }
                @interface Service { }

                @Service
                class Sample {
                    EntityManager em;

                    @Transactional(readOnly = true)
                    void process(Long id) {
                        Order order = em.find(Order.class, id);
                        order.setStatus("VIEWED");
                    }
                }
                class EntityManager {
                    Order find(Class<Order> clazz, Long id) { return null; }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Entity mutation inside @Transactional(readOnly = true)"))
                .count();
        assertEquals(1, (int) count);
    }

    /**
     * Multiple mutations on the same loaded entity should each get their own
     * highlight on the call site.
     */
    public void testReportsMultipleMutations() {
        this.myFixture.enableInspections(new SpringReadOnlyEntityMutationDataFlowInspection());
        this.myFixture.configureByText("Order.java", """
                @interface Entity { }
                @Entity
                class Order {
                    void setStatus(String s) { }
                    void setName(String s) { }
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { boolean readOnly() default false; }
                @interface Repository { }
                @interface Service { }

                @Repository
                interface OrderRepo {
                    Order findById(Long id);
                }

                @Service
                class Sample {
                    OrderRepo repo;

                    @Transactional(readOnly = true)
                    void process(Long id) {
                        Order order = repo.findById(id);
                        order.setStatus("A");
                        order.setName("B");
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Entity mutation inside @Transactional(readOnly = true)"))
                .count();
        assertEquals(2, (int) count);
    }

    /**
     * Stream intermediate operations (filter) must propagate taint so that
     * lambda parameters inside filter and forEach are tainted.
     */
    public void testReportsStreamFilterForEach() {
        this.myFixture.enableInspections(new SpringReadOnlyEntityMutationDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                @interface Entity { }
                @Entity
                class Order {
                    void setStatus(String s) { }
                }
                @interface Transactional { boolean readOnly() default false; }
                @interface Repository { }
                @interface Service { }

                @Repository
                interface OrderRepository {
                    List<Order> findAll();
                }

                @Service
                class Sample {
                    OrderRepository repo;

                    @Transactional(readOnly = true)
                    void process() {
                        List<Order> orders = repo.findAll();
                        orders.stream()
                            .filter((Order order) -> { order.setStatus("A"); return true; })
                            .forEach((Order order) -> order.setStatus("B"));
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Entity mutation inside @Transactional(readOnly = true)"))
                .count();
        assertEquals(2, (int) count);
    }

}
