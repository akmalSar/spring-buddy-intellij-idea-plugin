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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class SpringNPlusOneQueryInspectionTest extends BasePlatformTestCase {

    public void testReportsLazyCollectionAccessInLoop() {
        this.myFixture.enableInspections(new SpringNPlusOneQueryInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                
                class Sample {
                    @Transactional
                    void render(List<Order> orders) {
                        for (Order order : orders) {
                            <warning descr="Potential N+1 query: lazy association accessed inside a loop or stream operation; use fetch join, EntityGraph, or DTO projection">order.getLines()</warning>.size();
                        }
                    }
                }
                class Order {
                    @OneToMany
                    private List<Line> lines;
                    List<Line> getLines() { return this.lines; }
                }
                class Line { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsLazyCollectionAccessInStream() {
        this.myFixture.enableInspections(new SpringNPlusOneQueryInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                
                class Sample {
                    @Transactional
                    void render(List<Order> orders) {
                        orders.stream().map(order -> <warning descr="Potential N+1 query: lazy association accessed inside a loop or stream operation; use fetch join, EntityGraph, or DTO projection">order.getLines()</warning>.size()).toList();
                    }
                }
                class Order {
                    @OneToMany(fetch = FetchType.LAZY)
                    private List<Line> lines;
                    List<Line> getLines() { return this.lines; }
                }
                class Line { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportEagerAssociation() {
        this.myFixture.enableInspections(new SpringNPlusOneQueryInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                
                class Sample {
                    @Transactional
                    void render(List<Order> orders) {
                        for (Order order : orders) {
                            order.getLines().size();
                        }
                    }
                }
                class Order {
                    @OneToMany(fetch = FetchType.EAGER)
                    private List<Line> lines;
                    List<Line> getLines() { return this.lines; }
                }
                class Line { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsLazyAccessInClassicForLoop() {
        this.myFixture.enableInspections(new SpringNPlusOneQueryInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                
                class Sample {
                    @Transactional
                    void render(List<Order> orders) {
                        for (int i = 0; i < orders.size(); i++) {
                            <warning descr="Potential N+1 query: lazy association accessed inside a loop or stream operation; use fetch join, EntityGraph, or DTO projection">orders.get(i).getLines()</warning>.size();
                        }
                    }
                }
                class Order {
                    @OneToMany
                    private List<Line> lines;
                    List<Line> getLines() { return this.lines; }
                }
                class Line { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsLazyAccessOutsideTransactionWhenOsivEnabled() {
        this.myFixture.enableInspections(new SpringNPlusOneQueryInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                // spring.jpa.open-in-view=true
                
                class Sample {
                    void render(List<Order> orders) {
                        for (Order order : orders) {
                            <warning descr="Potential N+1 query: lazy association accessed inside a loop or stream operation; use fetch join, EntityGraph, or DTO projection">order.getLines()</warning>.size();
                        }
                    }
                }
                class Order {
                    @OneToMany
                    private List<Line> lines;
                    List<Line> getLines() { return this.lines; }
                }
                class Line { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsDirectFieldAccess() {
        this.myFixture.enableInspections(new SpringNPlusOneQueryInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                
                class Sample {
                    @Transactional
                    void render(List<Order> orders) {
                        for (Order order : orders) {
                            <warning descr="Potential N+1 query: lazy association accessed inside a loop or stream operation; use fetch join, EntityGraph, or DTO projection">order.lines</warning>.size();
                        }
                    }
                }
                class Order {
                    @OneToMany
                    List<Line> lines;
                }
                class Line { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsLombokGetterStyleAccess() {
        this.myFixture.enableInspections(new SpringNPlusOneQueryInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                
                class Sample {
                    @Transactional
                    void render(List<Order> orders) {
                        orders.forEach(order -> <warning descr="Potential N+1 query: lazy association accessed inside a loop or stream operation; use fetch join, EntityGraph, or DTO projection">order.getLines()</warning>.size());
                    }
                }
                @Getter
                class Order {
                    @OneToMany
                    private List<Line> lines;
                    List<Line> getLines() { return this.lines; }
                }
                class Line { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsBasicLazyFieldAccess() {
        this.myFixture.enableInspections(new SpringNPlusOneQueryInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                
                class Sample {
                    @Transactional
                    void render(List<Order> orders) {
                        while (!orders.isEmpty()) {
                            <warning descr="Potential N+1 query: lazy association accessed inside a loop or stream operation; use fetch join, EntityGraph, or DTO projection">orders.get(0).getPayload()</warning>.length();
                            break;
                        }
                    }
                }
                class Order {
                    @Basic(fetch = FetchType.LAZY)
                    private String payload;
                    String getPayload() { return this.payload; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportOptionalMapLazyFieldAccess() {
        this.myFixture.enableInspections(new SpringNPlusOneQueryInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.Optional;
                
                class Sample {
                    @Transactional
                    ByteStream download(Repository repository) {
                        return repository.findFirst()
                            .map(it -> new ByteStream(it.getFileName(), it.getBlob()))
                            .orElseThrow();
                    }
                }
                class Repository {
                    Optional<Document> findFirst() { return Optional.empty(); }
                }
                class Document {
                    @Basic(fetch = FetchType.LAZY)
                    private byte[] blob;
                    String fileName;
                    byte[] getBlob() { return this.blob; }
                    String getFileName() { return this.fileName; }
                }
                class ByteStream {
                    ByteStream(String fileName, byte[] blob) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
