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

package com.sarhan.render.flow.boundary;

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
 * Tests for {@link BoundaryEscapeFlowBuilder}.
 *
 * <p>The builder is the visualisation backend for
 * {@code SpringEntityTransactionBoundaryEscapeDataFlowInspection}.
 * Tests verify that each PSI shape produces the expected node sequence and
 * edge labels.
 *
 * @author Akmal Sarhan
 */
public class BoundaryEscapeFlowBuilderTest extends BasePlatformTestCase {

    public void testBuildsTxReturnVarSinkChain() {
        PsiFile file = this.myFixture.configureByText("Sample.java", """
                @interface Transactional { }
                @interface Service { }
                @interface Entity { }

                @Entity class Order {
                    void setStatus(int s) { }
                }

                @Service
                class OrderService {
                    @Transactional
                    Order findById(int id) { return new Order(); }
                }

                class Caller {
                    OrderService service;

                    void process(int id) {
                        Order order = service.findById(id);
                        order.setStatus(2);
                    }
                }
                """);

        FlowGraph flow = ReadAction.compute(() -> {
            PsiMethodCallExpression sink = findCall(file, "setStatus");
            return new BoundaryEscapeFlowBuilder(getProject()).buildFromSink(sink);
        });

        // Expected: entry -> tx -> var -> sink
        List<FlowGraph.Node> nodes = flow.getNodes();
        assertEquals(4, nodes.size());
        assertEquals(FlowGraph.NodeKind.ENTRY_METHOD, nodes.get(0).kind());
        assertEquals(FlowGraph.NodeKind.TRANSACTIONAL_RETURN, nodes.get(1).kind());
        assertEquals(FlowGraph.NodeKind.VARIABLE, nodes.get(2).kind());
        assertEquals(FlowGraph.NodeKind.SINK, nodes.get(3).kind());

        List<FlowGraph.Edge> edges = flow.getEdges();
        assertEquals(3, edges.size());
        assertEquals("calls",   edges.get(0).label());
        assertEquals("returns", edges.get(1).label());
        assertEquals("escape",  edges.get(2).label());
    }

    public void testFallsBackToParameterPathWhenSourceMissing() {
        PsiFile file = this.myFixture.configureByText("Sample.java", """
                @interface Service { }
                @interface Entity { }

                @Entity class Order {
                    void setStatus(int s) { }
                }

                @Service
                class Caller {
                    void process(Order order) {
                        order.setStatus(2);
                    }
                }
                """);

        FlowGraph flow = ReadAction.compute(() -> {
            PsiMethodCallExpression sink = findCall(file, "setStatus");
            return new BoundaryEscapeFlowBuilder(getProject()).buildFromSink(sink);
        });

        // Expected: entry -> param (HELPER_CALL) -> var -> sink
        List<FlowGraph.Node> nodes = flow.getNodes();
        assertEquals(4, nodes.size());
        assertEquals(FlowGraph.NodeKind.ENTRY_METHOD, nodes.get(0).kind());
        assertEquals(FlowGraph.NodeKind.HELPER_CALL,  nodes.get(1).kind());
        assertEquals(FlowGraph.NodeKind.VARIABLE,     nodes.get(2).kind());
        assertEquals(FlowGraph.NodeKind.SINK,         nodes.get(3).kind());
    }

    public void testHeaderWarningMentionsBoundaryEscape() {
        PsiFile file = this.myFixture.configureByText("Sample.java", """
                @interface Transactional { }
                @interface Service { }

                class Order { void setStatus(int s) { } }

                @Service
                class OrderService {
                    @Transactional
                    Order findById(int id) { return new Order(); }
                }

                class Caller {
                    OrderService service;
                    void process(int id) {
                        Order order = service.findById(id);
                        order.setStatus(2);
                    }
                }
                """);

        FlowGraph flow = ReadAction.compute(() -> {
            PsiMethodCallExpression sink = findCall(file, "setStatus");
            return new BoundaryEscapeFlowBuilder(getProject()).buildFromSink(sink);
        });

        assertNotNull(flow.getHeaderWarning());
        assertTrue("expected boundary-escape wording in: " + flow.getHeaderWarning(),
                flow.getHeaderWarning().contains("Transactional")
                        || flow.getHeaderWarning().contains("transaction"));
    }

    /** Locate a method call expression by its target method name within the test fixture. */
    private static PsiMethodCallExpression findCall(PsiFile file, String methodName) {
        for (PsiClass cls : PsiTreeUtil.findChildrenOfType(file, PsiClass.class)) {
            for (PsiMethod m : cls.getMethods()) {
                for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(m, PsiMethodCallExpression.class)) {
                    if (methodName.equals(call.getMethodExpression().getReferenceName())) {
                        return call;
                    }
                }
            }
        }
        throw new AssertionError("call to " + methodName + "() not found");
    }
}
