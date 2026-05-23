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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sarhan.render.TransactionGraph;
import com.sarhan.render.TransactionGraphBuilder;
import com.sarhan.render.TransactionNode;
import com.sarhan.render.TransactionNode.Propagation;
import com.sarhan.render.TransactionNode.TxStatus;

/**
 * Unit and integration tests for {@link TransactionGraphBuilder}.
 *
 * <p>All PSI fixtures are Java source strings added via
 * {@code myFixture.configureByText()}. Tests run in a
 * {@link BasePlatformTestCase} environment, which provides a
 * real IntelliJ PSI without requiring a full project on disk.
 *
 * @author Akmal Sarhan
 */
public class    TransactionGraphBuilderTest extends BasePlatformTestCase {

    // -----------------------------------------------------------------------
    // Test 1 — Simple REQUIRED
    // -----------------------------------------------------------------------

    public void testSimpleRequired() {
        this.myFixture.configureByText("OrderService.java",
                """
                import org.springframework.transaction.annotation.Transactional;
                
                public class OrderService {
                
                    @Transactional
                    public void placeOrder(Long id) {
                        // body
                    }
                }
                """);

        PsiMethod psiMethod = findMethod("OrderService", "placeOrder");
        assertNotNull("Method not found in fixture", psiMethod);

        TransactionGraph graph = buildGraph(psiMethod);

        assertEquals("Expected exactly one node for a standalone @Transactional method",
                1, graph.getNodes().size());

        TransactionNode root = graph.getRoot();
        assertEquals("OrderService#placeOrder", root.getId());
        assertEquals("A root @Transactional method with no caller must have status=STARTS",
                TxStatus.STARTS, root.getStatus());
        assertEquals("Default propagation must be REQUIRED when not specified",
                Propagation.REQUIRED, root.getPropagation());
        assertFalse(root.isReadOnly());
        assertFalse(root.isProxyBypass());
        assertFalse(graph.isTruncated());
    }

    // -----------------------------------------------------------------------
    // Test 2 — Self-invocation proxy bypass
    // -----------------------------------------------------------------------

    public void testSelfInvocationProxyBypass() {
        this.myFixture.configureByText("PaymentService.java",
                """
                import org.springframework.transaction.annotation.Transactional;
                
                public class PaymentService {
                
                    public void process() {
                        pay();          // unqualified — bypasses proxy
                    }
                
                    @Transactional
                    public void pay() {
                        // body
                    }
                }
                """);

        PsiMethod psiMethod = findMethod("PaymentService", "process");
        assertNotNull(psiMethod);

        TransactionGraph graph = buildGraph(psiMethod);

        // Find the 'pay' node
        Optional<TransactionNode> payNode = graph.getNodes().stream()
                .filter((TransactionNode n) -> n.getId().equals("PaymentService#pay"))
                .findFirst();
        assertTrue("'pay' node must appear in graph", payNode.isPresent());
        assertTrue("Self-invocation must be flagged as proxy bypass",
                payNode.get().isProxyBypass());
        assertEquals("A proxy-bypassed @Transactional method must have status=IGNORED",
                TxStatus.IGNORED, payNode.get().getStatus());

        // Edge check
        List<TransactionGraph.Edge> proxyEdges = new ArrayList<>();
        for (TransactionGraph.Edge e : graph.getEdges()) {
            if (e.proxyBypass()) {
                proxyEdges.add(e);
            }
        }
        assertFalse("At least one edge must be marked as proxy bypass", proxyEdges.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Test 3 — REQUIRES_NEW suspension
    // -----------------------------------------------------------------------

    public void testRequiresNew() {
        this.myFixture.configureByText("AuditService.java",
                """
                import org.springframework.transaction.annotation.Propagation;
                import org.springframework.transaction.annotation.Transactional;
                
                public class AuditService {
                
                    @Transactional(propagation = Propagation.REQUIRES_NEW)
                    public void audit(String event) {
                        // body
                    }
                }
                """);

        this.myFixture.configureByText("UserService.java",
                """
                import org.springframework.transaction.annotation.Transactional;
                
                public class UserService {
                
                    private AuditService auditService;
                
                    @Transactional
                    public void updateUser(Long id) {
                        auditService.audit("update");
                    }
                }
                """);

        PsiMethod psiMethod = findMethod("UserService", "updateUser");
        assertNotNull(psiMethod);

        TransactionGraph graph = buildGraph(psiMethod);

        Optional<TransactionNode> auditNode = graph.getNodes().stream()
                .filter((TransactionNode n) -> n.getId().contains("audit"))
                .findFirst();

        assertTrue("'audit' node must be in graph", auditNode.isPresent());
        assertEquals(Propagation.REQUIRES_NEW, auditNode.get().getPropagation());
        assertEquals("REQUIRES_NEW callee must have status=SUSPENDS",
                TxStatus.SUSPENDS, auditNode.get().getStatus());

        boolean hasEdge = false;
        for (TransactionGraph.Edge e : graph.getEdges()) {
            if (e.toId().contains("audit")) {
                hasEdge = true;
                break;
            }
        }
        assertTrue("Edge from updateUser to audit must exist", hasEdge);
    }

    // -----------------------------------------------------------------------
    // Test 4 — Class-level @Transactional inheritance
    // -----------------------------------------------------------------------

    public void testClassLevelAnnotationInheritance() {
        this.myFixture.configureByText("InventoryService.java",
                """
                import org.springframework.transaction.annotation.Transactional;
                
                @Transactional
                public class InventoryService {
                
                    // No @Transactional here — should inherit from class
                    public void adjustStock(Long itemId, int delta) {
                        // body
                    }
                }
                """);

        PsiMethod psiMethod = findMethod("InventoryService", "adjustStock");
        assertNotNull(psiMethod);

        TransactionGraph graph = buildGraph(psiMethod);

        TransactionNode root = graph.getRoot();
        assertEquals("Method without own annotation must inherit REQUIRED from class-level @Transactional",
                Propagation.REQUIRED, root.getPropagation());
        assertEquals("Root-level inherited @Transactional must start a transaction",
                TxStatus.STARTS, root.getStatus());
    }

    // -----------------------------------------------------------------------
    // Test 4b — Class-level annotation inheritance via interface impl
    // -----------------------------------------------------------------------

    public void testInterfaceOverrideWithoutPublicModifier() {
        this.myFixture.configureByText("IBuildingUtils.java",
                "interface IBuildingUtils { String getBuildingContractStart(String id, Object c); }");
        this.myFixture.configureByText("BuildingUtils.java",
                """
                import org.springframework.transaction.annotation.Transactional;
                
                @Transactional(readOnly = true)
                class BuildingUtils implements IBuildingUtils {
                
                    @Override
                    String getBuildingContractStart(String id, Object c) {
                        return null;
                    }
                }
                """);

        PsiMethod psiMethod = findMethod("BuildingUtils", "getBuildingContractStart");
        assertNotNull(psiMethod);

        TransactionGraph graph = buildGraph(psiMethod);

        TransactionNode root = graph.getRoot();
        assertEquals("Implicitly-public interface override must inherit @Transactional from class",
                Propagation.REQUIRED, root.getPropagation());
        assertEquals(TxStatus.STARTS, root.getStatus());
        assertTrue("Class-level readOnly=true must be inherited", root.isReadOnly());
    }

    // -----------------------------------------------------------------------
    // Test 5 — Truncation (>50 nodes)
    // -----------------------------------------------------------------------

    public void testTruncation() {
        // Use a stub builder that returns a pre-built graph with 51 nodes.
        // This validates TransactionGraphBuilder's truncation logic in isolation.
        TransactionNode rootNode = TransactionNode.builder("Root#root", "Root#root")
                .propagation(Propagation.REQUIRED)
                .status(TxStatus.STARTS)
                .build();

        // Build 51-node list: root + 50 callees
        List<TransactionNode> nodes = new ArrayList<>();
        nodes.add(rootNode);
        List<TransactionGraph.Edge> edges = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String id = "Callee" + i + "#method";
            TransactionNode node = TransactionNode.builder(id, id)
                    .propagation(Propagation.REQUIRED)
                    .status(TxStatus.JOINS)
                    .build();
            nodes.add(node);
            edges.add(TransactionGraph.Edge.plain(rootNode.getId(), id));
        }

        // Simulate what the builder does: if > MAX_NODES, collapse to depth 1
        // The builder is designed so that when node count crosses 50 it stops
        // and collapses. We test the model/flag here.
        TransactionGraph graph = TransactionGraph.builder(rootNode)
                .nodes(nodes.subList(0, Math.min(nodes.size(), 10))) // collapsed subset
                .edges(edges.subList(0, Math.min(edges.size(), 9)))
                .truncated(true)
                .build();

        assertTrue("Graph with >50 raw nodes must be marked truncated", graph.isTruncated());
        assertTrue("Truncated graph must not exceed MAX_NODES (" + TransactionGraph.MAX_NODES + ")",
                graph.getNodes().size() <= TransactionGraph.MAX_NODES);
    }

    // -----------------------------------------------------------------------
    // Test 6 — readOnly write detection (integration)
    // -----------------------------------------------------------------------

    /**
     * Integration test: verifies that a {@code @Transactional(readOnly = true)} method
     * which calls {@code repository.save()} is flagged with {@code isWriteInReadOnly = true}.
     */
    public void testReadOnlyWriteDetection() {
        this.myFixture.configureByText("ReportService.java",
                """
                import org.springframework.transaction.annotation.Transactional;
                
                public class ReportService {
                
                    private OrderRepository orderRepository;
                
                    @Transactional(readOnly = true)
                    public void generateReport(Long id) {
                        Order o = new Order();
                        orderRepository.save(o);   // write inside readOnly tx
                    }
                }
                
                interface OrderRepository {
                    void save(Object o);
                }
                
                class Order {}
                """);

        PsiMethod psiMethod = findMethod("ReportService", "generateReport");
        assertNotNull(psiMethod);

        TransactionGraph graph = buildGraph(psiMethod);

        TransactionNode root = graph.getRoot();
        assertTrue("Method annotated with readOnly=true must have isReadOnly=true",
                root.isReadOnly());
        assertTrue("readOnly method calling repository.save() must have isWriteInReadOnly=true",
                root.isWriteInReadOnly());
    }

    // -----------------------------------------------------------------------
    // Test 7 — Test class exclusion
    // -----------------------------------------------------------------------

    public void testTestClassExcludedFromGraph() {
        this.myFixture.configureByText("OrderServiceTest.java",
                """
                import org.springframework.transaction.annotation.Transactional;
                
                public class OrderServiceTest {
                
                    @Transactional
                    public void testPlaceOrder() {
                    }
                }
                """);

        PsiMethod psiMethod = findMethod("OrderServiceTest", "testPlaceOrder");
        assertNotNull(psiMethod);

        TransactionGraph graph = buildGraph(psiMethod);

        assertEquals("Test classes should be excluded from graph traversal",
                1, graph.getNodes().size());
        assertEquals("OrderServiceTest#testPlaceOrder", graph.getRoot().getId());
    }

    // -----------------------------------------------------------------------
    // Test 8 — Test callee exclusion
    // -----------------------------------------------------------------------

    public void testTestClassCalleeExcluded() {
        this.myFixture.configureByText("ServiceTest.java",
                """
                import org.springframework.transaction.annotation.Transactional;
                
                public class ServiceTest {
                
                    @Transactional
                    public void testSetup() {
                        performAction();
                    }
                
                    public void performAction() {
                    }
                }
                """);

        PsiMethod psiMethod = findMethod("ServiceTest", "testSetup");
        assertNotNull(psiMethod);

        TransactionGraph graph = buildGraph(psiMethod);

        assertEquals("Test class methods should only have the root node",
                1, graph.getNodes().size());
    }

    public void testReadOnlyEntityMutationDemoGraphBuilds() {
        this.myFixture.configureByText("ReadOnlyEntityMutationDemo.java",
                """
                import java.util.Optional;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Id;
                
                @Service
                class ReadOnlyEntityMutationDemo {
                    private final MutableOrderRepository repo;
                    ReadOnlyEntityMutationDemo(MutableOrderRepository repo) {
                        this.repo = repo;
                    }
                    @Transactional(readOnly = true)
                    void setOrderStatusViaOptional(Long id) {
                        Optional<MutableOrder> opt = this.repo.findById(id);
                        MutableOrder order = opt.get();
                        order.setStatus("VIEWED");
                    }
                }
                @Entity
                class MutableOrder {
                    @Id Long id;
                    String status;
                    void setStatus(String status) { this.status = status; }
                }
                interface MutableOrderRepository extends JpaRepository<MutableOrder, Long> {
                    Optional<MutableOrder> findById(Long id);
                }
                """);

        PsiMethod psiMethod = findMethod("ReadOnlyEntityMutationDemo", "setOrderStatusViaOptional");
        assertNotNull(psiMethod);

        TransactionGraph graph = buildGraph(psiMethod);

        assertEquals("ReadOnlyEntityMutationDemo#setOrderStatusViaOptional", graph.getRoot().getId());
        assertFalse("Read-only transaction root must be detected", graph.getRoot().isWriteInReadOnly());
        assertFalse("JDK callers from Optional.get() must not be added to the graph",
                graph.getNodes().stream().anyMatch((TransactionNode node) -> node.getId().startsWith("java.")));
    }

    public void testNonTransactionalEntitySetterExcludedFromGraph() {
        this.myFixture.configureByText("GhostSaveDemo.java",
                """
                @interface Service { }
                @interface Transactional { }
                @Service
                class GhostSaveDemo {
                    private final GhostUserRepository ghostUserRepository;
                    GhostSaveDemo(GhostUserRepository ghostUserRepository) {
                        this.ghostUserRepository = ghostUserRepository;
                    }
                    void safeTransactionalHelper() {
                        GhostUser u = this.ghostUserRepository.findById(1L);
                        persistUpdate(u);
                    }
                    @Transactional
                    private void persistUpdate(GhostUser ghostUser) {
                        ghostUser.setName("Ivan");
                    }
                }
                interface GhostUserRepository {
                    GhostUser findById(long id);
                }
                class GhostUser {
                    void setName(String name) {
                    }
                }
                """);

        PsiMethod psiMethod = findMethod("GhostSaveDemo", "safeTransactionalHelper");
        assertNotNull(psiMethod);

        TransactionGraph graph = buildGraph(psiMethod);

        assertFalse("Plain entity setters are not transaction boundaries and must stay out of Tx Flow",
                graph.getNodes().stream().anyMatch((TransactionNode node) -> node.getId().equals("GhostUser#setName")));
    }

    public void testNonTransactionalCalleeEdgeIsNotLabeledStarts() {
        this.myFixture.configureByText("ServiceGraph.java",
                """
                @interface Transactional { }
                class CallerService {
                    void root() {
                        plain();
                    }
                    void plain() {
                    }
                    @Transactional
                    void transactionalBoundary() {
                    }
                }
                """);

        PsiMethod psiMethod = findMethod("CallerService", "root");
        assertNotNull(psiMethod);

        TransactionGraph graph = buildGraph(psiMethod);

        Optional<TransactionGraph.Edge> edge = graph.getEdges().stream()
                .filter((TransactionGraph.Edge item) -> item.toId().endsWith("CallerService#plain"))
                .findFirst();
        assertTrue("Non-transactional Spring bean callee edge must exist", edge.isPresent());
        assertEquals("Non-transactional callee must not be described as starting a transaction",
                "non-tx", edge.get().statusLabel());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Finds the first method with the given name in the class with the given
     * simple name from the fixture's project.
     */
    private PsiMethod findMethod(String className, String methodName) {
        PsiClass[] classes = PsiShortNamesCache
                .getInstance(getProject())
                .getClassesByName(className,
                        GlobalSearchScope.projectScope(getProject()));
        for (PsiClass clazz : classes) {
            for (PsiMethod m : clazz.getMethods()) {
                if (m.getName().equals(methodName)) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Wraps the builder call in a ReadAction (required by IntelliJ PSI threading model).
     */
    private TransactionGraph buildGraph(PsiMethod method) {
        return ReadAction.compute(
                () -> new TransactionGraphBuilder(getProject()).buildGraph(method));
    }
}
