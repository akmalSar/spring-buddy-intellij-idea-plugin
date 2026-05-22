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

package com.sarhan.render.flow.lazyinit;

import java.util.List;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sarhan.render.flow.shared.FlowGraph;

/**
 * Tests for {@link LazyInitFlowBuilder} — the visual backend for
 * {@code SpringLazyInitializationPredictionDataFlowInspection}.
 *
 * @author Akmal Sarhan
 */
public class LazyInitFlowBuilderTest extends BasePlatformTestCase {

    public void testBuildsRepoSourceVarLazyChain() {
        PsiFile file = this.myFixture.configureByText("Sample.java", """
                @interface Repository { }
                @interface Service { }
                @interface Entity { }
                @interface OneToMany { }

                @Entity
                class Order {
                    @OneToMany java.util.List<Item> items;
                    java.util.List<Item> getItems() { return items; }
                }
                class Item { }

                @Repository
                interface OrderRepository {
                    Order findById(int id);
                }

                @Service
                class OrderService {
                    OrderRepository repo;
                    void process(int id) {
                        Order order = repo.findById(id);
                        order.getItems();
                    }
                }
                """);

        FlowGraph flow = ReadAction.compute(() -> {
            PsiMethodCallExpression lazy = findCall(file, "getItems");
            return new LazyInitFlowBuilder(getProject()).buildFromSink(lazy);
        });

        // Expected: entry -> source -> var -> lazy
        List<FlowGraph.Node> nodes = flow.getNodes();
        assertEquals(4, nodes.size());
        assertEquals(FlowGraph.NodeKind.ENTRY_METHOD, nodes.get(0).kind());
        assertEquals(FlowGraph.NodeKind.SOURCE,       nodes.get(1).kind());
        assertEquals(FlowGraph.NodeKind.VARIABLE,     nodes.get(2).kind());
        assertEquals(FlowGraph.NodeKind.LAZY_GETTER,  nodes.get(3).kind());

        // Last edge should carry the "lazy" label so the renderer paints it red.
        List<FlowGraph.Edge> edges = flow.getEdges();
        assertEquals(3, edges.size());
        assertEquals("lazy", edges.get(2).label());
    }

    public void testHandlesOptionalUnwrap() {
        PsiFile file = this.myFixture.configureByText("Sample.java", """
                @interface Repository { }
                @interface Service { }
                @interface Entity { }

                @Entity
                class Order {
                    java.util.List<Item> getItems() { return null; }
                }
                class Item { }

                interface Optional<T> {
                    T get();
                    T orElseThrow();
                }

                @Repository
                interface OrderRepository {
                    Optional<Order> findById(int id);
                }

                @Service
                class OrderService {
                    OrderRepository repo;
                    void process(int id) {
                        Order order = repo.findById(id).orElseThrow();
                        order.getItems();
                    }
                }
                """);

        FlowGraph flow = ReadAction.compute(() -> {
            PsiMethodCallExpression lazy = findCall(file, "getItems");
            return new LazyInitFlowBuilder(getProject()).buildFromSink(lazy);
        });

        // Even with the .orElseThrow() unwrap the source is recovered.
        List<FlowGraph.Node> nodes = flow.getNodes();
        assertEquals(4, nodes.size());
        assertEquals(FlowGraph.NodeKind.SOURCE, nodes.get(1).kind());
    }

    public void testFallsBackWhenNoSource() {
        PsiFile file = this.myFixture.configureByText("Sample.java", """
                @interface Service { }
                @interface Entity { }

                @Entity
                class Order {
                    java.util.List<Item> getItems() { return null; }
                }
                class Item { }

                @Service
                class OrderService {
                    void process(Order order) {
                        order.getItems();
                    }
                }
                """);

        FlowGraph flow = ReadAction.compute(() -> {
            PsiMethodCallExpression lazy = findCall(file, "getItems");
            return new LazyInitFlowBuilder(getProject()).buildFromSink(lazy);
        });

        // Falls back to entry -> param -> var -> lazy
        List<FlowGraph.Node> nodes = flow.getNodes();
        assertEquals(4, nodes.size());
        assertEquals(FlowGraph.NodeKind.ENTRY_METHOD, nodes.get(0).kind());
        assertEquals(FlowGraph.NodeKind.HELPER_CALL,  nodes.get(1).kind());
        assertEquals(FlowGraph.NodeKind.VARIABLE,     nodes.get(2).kind());
        assertEquals(FlowGraph.NodeKind.LAZY_GETTER,  nodes.get(3).kind());
    }

    private static PsiMethodCallExpression findCall(PsiFile file, String methodName) {
        for (PsiClass cls : PsiTreeUtil.findChildrenOfType(file, PsiClass.class)) {
            for (PsiMethod m : cls.getMethods()) {
                if (m.getBody() == null) {
                    continue;
                }
                for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(m, PsiMethodCallExpression.class)) {
                    if (methodName.equals(call.getMethodExpression().getReferenceName())) {
                        // Skip calls inside the entity stub itself
                        PsiClass owner = PsiTreeUtil.getParentOfType(call, PsiClass.class);
                        if (owner != null && !"Order".equals(owner.getName())) {
                            return call;
                        }
                    }
                }
            }
        }
        throw new AssertionError("call to " + methodName + "() not found");
    }
}
