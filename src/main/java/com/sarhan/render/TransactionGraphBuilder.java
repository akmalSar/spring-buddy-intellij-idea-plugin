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

package com.sarhan.render;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.sarhan.springformat.inspection.SpringInspectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds a {@link TransactionGraph} from a root {@link PsiMethod} using PSI traversal.
 *
 * <p><b>Must always be called inside a {@code ReadAction.compute { }} block.</b>
 * The caller ({@code TransactionDiagramService}) is responsible for dispatching
 * on {@code Dispatchers.Default} inside a {@code ReadAction.compute}.
 *
 * <h3>Traversal strategy</h3>
 * <ol>
 *   <li>BFS outward to callees (via {@link PsiMethodCallExpression} inside the body)</li>
 *   <li>BFS outward to callers (via {@link MethodReferencesSearch})</li>
 *   <li>Max depth: 3 hops in each direction (configurable via {@link #MAX_DEPTH})</li>
 *   <li>If node count exceeds {@link TransactionGraph#MAX_NODES}, collapse to depth 1</li>
 * </ol>
 *
 * <h3>Annotation resolution order</h3>
 * <ol>
 *   <li>{@code @Transactional} on the method itself</li>
 *   <li>{@code @Transactional} on the enclosing class</li>
 *   <li>{@code NON_TX} if neither is present</li>
 * </ol>
 *
 * <p>Both {@code org.springframework.transaction.annotation.Transactional} and
 * {@code jakarta.transaction.Transactional} are recognised.
 *
 * @author Akmal Sarhan
 */
public final class TransactionGraphBuilder {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Configurable traversal depth (callers + callees). */
    public static int MAX_DEPTH = 3;

    private static final String SPRING_TX_FQN  = "org.springframework.transaction.annotation.Transactional";
    private static final String JAKARTA_TX_FQN = "jakarta.transaction.Transactional";
    private static final String JAVAX_TX_FQN   = "javax.transaction.Transactional";

    private static final Set<String> PROGRAMMATIC_TX_METHODS = Set.of(
            "getTransaction", "commit", "rollback"
    );
    private static final String TRANSACTION_MANAGER_TYPE = "PlatformTransactionManager";
    private static final String SET_PROPAGATION_BEHAVIOR = "setPropagationBehavior";
    private static final String PROPAGATION_REQUIRES_NEW = "PROPAGATION_REQUIRES_NEW";
    private static final String PROPAGATION_NESTED       = "PROPAGATION_NESTED";
    private static final String PROPAGATION_NEVER        = "PROPAGATION_NEVER";
    private static final String PROPAGATION_NOT_SUPPORTED = "PROPAGATION_NOT_SUPPORTED";
    private static final String PROPAGATION_SUPPORTS     = "PROPAGATION_SUPPORTS";
    private static final String PROPAGATION_MANDATORY    = "PROPAGATION_MANDATORY";

    private static final Set<String> WRITE_METHOD_NAMES = Set.of(
            "save", "saveAll", "saveAndFlush", "saveAllAndFlush",
            "delete", "deleteAll", "deleteById", "deleteAllById",
            "persist", "merge", "remove", "flush"
    );

    private static final Set<String> SPRING_DATA_REPOSITORIES = Set.of(
            "org.springframework.data.repository.Repository",
            "org.springframework.data.repository.CrudRepository",
            "org.springframework.data.repository.PagingAndSortingRepository",
            "org.springframework.data.jpa.repository.JpaRepository"
    );

    private static final Set<String> SPRING_STEREOTYPE_FQNS = Set.of(
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.context.annotation.Configuration"
    );

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final Project project;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public TransactionGraphBuilder(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Constructs the full transaction graph for the given entry method.
     *
     * <p>Must be called inside {@code ReadAction.compute { }}.
     */
    @NotNull
    public TransactionGraph buildGraph(@NotNull PsiMethod rootMethod) {
        // ── Phase 0: skip test classes ─────────────────────────────────────
        PsiClass rootClass = rootMethod.getContainingClass();
        if (rootClass != null && isTestClass(rootClass)) {
            TransactionNode rootNode = resolveNode(rootMethod, null, null);
            return TransactionGraph.builder(rootNode)
                    .nodes(List.of(rootNode))
                    .edges(List.of())
                    .truncated(false)
                    .build();
        }

        // ── Phase 1: collect all reachable nodes (BFS, bounded) ────────────
        Map<String, TransactionNode> nodeMap    = new LinkedHashMap<>();
        List<TransactionGraph.Edge>  edges      = new ArrayList<>();
        Set<PsiMethod>               visited    = new HashSet<>();
        boolean                      truncated  = false;

        // Resolve root
        TransactionNode rootNode = resolveNode(rootMethod, null, null);
        nodeMap.put(rootNode.getId(), rootNode);
        visited.add(rootMethod);

        // Full BFS (callers + callees, depth ≤ MAX_DEPTH)
        // Worklist: (method, depth, parentId, isSelfInvocation)
        Deque<WorkItem> queue = new ArrayDeque<>();
        enqueueCallees(rootMethod, rootNode.getId(), queue, 1);
        enqueueCallers(rootMethod, rootNode.getId(), queue, 1);

        while (!queue.isEmpty()) {
            WorkItem item = queue.poll();

            if (nodeMap.size() >= TransactionGraph.MAX_NODES + 1) {
                truncated = true;
                break;
            }

            String methodKey = methodId(item.method());
            if (visited.contains(item.method())) {
                // Still need to record the edge even if node is already visited
                addEdge(edges, item, nodeMap.get(methodKey), nodeMap);
                continue;
            }
            visited.add(item.method());

            TransactionNode callerNode = (item.parentId() != null) ? nodeMap.get(item.parentId()) : null;
            TransactionNode node = resolveNode(item.method(), callerNode,
                    item.proxyBypass() ? Boolean.TRUE : null);
            nodeMap.put(node.getId(), node);
            addEdge(edges, item, node, nodeMap);

            if (item.depth() < MAX_DEPTH) {
                if (item.isCallee()) {
                    enqueueCallees(item.method(), node.getId(), queue, item.depth() + 1);
                } else {
                    enqueueCallers(item.method(), node.getId(), queue, item.depth() + 1);
                }
            }
        }

        // ── Phase 2: truncation — collapse to depth 1 ───────────────────────
        if (truncated) {
            Map<String, TransactionNode> depth1Nodes  = new LinkedHashMap<>();
            List<TransactionGraph.Edge>  depth1Edges  = new ArrayList<>();
            depth1Nodes.put(rootNode.getId(), rootNode);

            Deque<WorkItem> d1Queue = new ArrayDeque<>();
            enqueueCallees(rootMethod, rootNode.getId(), d1Queue, 1);
            enqueueCallers(rootMethod, rootNode.getId(), d1Queue, 1);

            for (WorkItem item : d1Queue) {
                String key  = methodId(item.method());
                if (!depth1Nodes.containsKey(key)) {
                    TransactionNode n = resolveNode(item.method(), rootNode,
                            item.proxyBypass() ? Boolean.TRUE : null);
                    depth1Nodes.put(key, n);
                }
                addEdge(depth1Edges, item, depth1Nodes.get(key), depth1Nodes);
            }

            return TransactionGraph.builder(rootNode)
                    .nodes(new ArrayList<>(depth1Nodes.values()))
                    .edges(depth1Edges)
                    .truncated(true)
                    .build();
        }

        return TransactionGraph.builder(rootNode)
                .nodes(new ArrayList<>(nodeMap.values()))
                .edges(edges)
                .truncated(false)
                .build();
    }

    @NotNull
    private TransactionNode resolveNode(@NotNull PsiMethod method,
                                        @Nullable TransactionNode callerNode,
                                        @Nullable Boolean proxyBypassOverride) {
        String id          = methodId(method);
        String displayName = displayName(method);

        AnnotationInfo ann = resolveAnnotation(method);
        if (ann == null && containsProgrammaticTransaction(method)) {
            ann = new AnnotationInfo(detectProgrammaticPropagation(method),
                    false, List.of(), List.of());
        }
        boolean proxyBypass = (proxyBypassOverride != null)
                ? proxyBypassOverride
                : detectSelfInvocation(method);

        TransactionNode.TxStatus status = computeStatus(ann, callerNode, proxyBypass);
        boolean writeInReadOnly = ann != null && ann.readOnly()
                && containsWriteOperation(method);

        TransactionNode.Builder builder = TransactionNode.builder(id, displayName)
                .propagation((ann != null) ? ann.propagation() : TransactionNode.Propagation.REQUIRED)
                .status(status)
                .readOnly(ann != null && ann.readOnly())
                .rollbackFor((ann != null) ? ann.rollbackFor() : List.of())
                .noRollbackFor((ann != null) ? ann.noRollbackFor() : List.of())
                .proxyBypass(proxyBypass)
                .writeInReadOnly(writeInReadOnly);

        return builder.build();
    }

    @Nullable
    private AnnotationInfo resolveAnnotation(@NotNull PsiMethod method) {
        // 0. Method-level @Transactional (takes priority over repository defaults)
        PsiAnnotation ann = findTxAnnotation(method);
        if (ann != null) {
            return extractAnnotationInfo(ann);
        }

        // 0b. Repository method — Spring Data JPA repos are transactional by default
        AnnotationInfo repoAnnotation = resolveRepositoryAnnotation(method);
        if (repoAnnotation != null) {
            return repoAnnotation;
        }

        // 2. Class-level — always check the method's own class first,
        // then walk super-class hierarchy for inherited @Transactional
        PsiClass ownClass = method.getContainingClass();
        if (ownClass != null) {
            PsiAnnotation classAnn = findTxAnnotation(ownClass);
            if (classAnn == null) {
                classAnn = findTxAnnotationOnSuperClasses(ownClass);
            }
            if (classAnn != null) {
                return extractAnnotationInfo(classAnn);
            }
        }

        return null;
    }

    @Nullable
    private static AnnotationInfo resolveRepositoryAnnotation(@NotNull PsiMethod method) {
        PsiClass clazz = method.getContainingClass();
        if (clazz == null || !isSpringDataRepository(clazz)) {
            return null;
        }
        // CRUD write methods are @Transactional; query methods are @Transactional(readOnly=true)
        String name = method.getName();
        boolean isWrite = name.startsWith("save") || name.startsWith("delete")
                || name.startsWith("persist") || name.startsWith("merge")
                || name.startsWith("remove") || name.startsWith("flush");
        return new AnnotationInfo(TransactionNode.Propagation.REQUIRED,
                !isWrite, List.of(), List.of());
    }

    public static boolean isSpringDataRepositoryClass(@Nullable PsiClass clazz) {
        return clazz != null && isSpringDataRepository(clazz);
    }

    private static boolean isSpringDataRepository(@NotNull PsiClass clazz) {
        if (!clazz.isInterface()) {
            return false;
        }
        String fqn = clazz.getQualifiedName();
        if (fqn != null && SPRING_DATA_REPOSITORIES.contains(fqn)) {
            return true;
        }
        for (PsiClass parent : clazz.getSupers()) {
            if (isSpringDataRepository(parent)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static PsiAnnotation findTxAnnotationOnSuperClasses(@NotNull PsiClass clazz) {
        PsiClass sup = clazz.getSuperClass();
        while (sup != null) {
            PsiAnnotation ann = findTxAnnotation(sup);
            if (ann != null) {
                return ann;
            }
            sup = sup.getSuperClass();
        }
        return null;
    }

    @Nullable
    private static PsiAnnotation findTxAnnotation(@NotNull PsiModifierListOwner owner) {
        for (String fqn : List.of(SPRING_TX_FQN, JAKARTA_TX_FQN, JAVAX_TX_FQN)) {
            PsiAnnotation ann = owner.getAnnotation(fqn);
            if (ann != null) {
                return ann;
            }
        }
        // Fallback: unresolved annotation by simple name (demo/stub files)
        PsiModifierList modList = owner.getModifierList();
        return SpringInspectionUtil.findAnyAnnotation(modList, SPRING_TX_FQN, JAKARTA_TX_FQN, JAVAX_TX_FQN);
    }

    @NotNull
    private AnnotationInfo extractAnnotationInfo(@NotNull PsiAnnotation ann) {
        TransactionNode.Propagation propagation = TransactionNode.Propagation.REQUIRED;
        String propValue = annotationStringValue(ann, "propagation");
        if (propValue != null) {
            for (TransactionNode.Propagation p : TransactionNode.Propagation.values()) {
                if (propValue.endsWith(p.name())) {
                    propagation = p;
                    break;
                }
            }
        }

        boolean readOnly = "true".equals(annotationStringValue(ann, "readOnly"));
        List<String> rollbackFor   = annotationClassListValue(ann, "rollbackFor");
        List<String> noRollbackFor = annotationClassListValue(ann, "noRollbackFor");

        // jakarta.transaction.Transactional uses "rollbackOn" / "dontRollbackOn"
        if (rollbackFor.isEmpty()) {
            rollbackFor = annotationClassListValue(ann, "rollbackOn");
        }
        if (noRollbackFor.isEmpty()) {
            noRollbackFor = annotationClassListValue(ann, "dontRollbackOn");
        }

        return new AnnotationInfo(propagation, readOnly, rollbackFor, noRollbackFor);
    }

    @NotNull
    private TransactionNode.TxStatus computeStatus(@Nullable AnnotationInfo ann,
                                                   @Nullable TransactionNode callerNode,
                                                   boolean proxyBypass) {
        if (ann == null) {
            return TransactionNode.TxStatus.NON_TX;
        }
        if (proxyBypass) {
            return TransactionNode.TxStatus.IGNORED;
        }

        boolean callerIsActive = callerNode != null
                && callerNode.getStatus() != TransactionNode.TxStatus.NON_TX
                && callerNode.getStatus() != TransactionNode.TxStatus.IGNORED;

        return switch (ann.propagation()) {
            case REQUIRED     -> callerIsActive ? TransactionNode.TxStatus.JOINS  : TransactionNode.TxStatus.STARTS;
            case REQUIRES_NEW -> TransactionNode.TxStatus.SUSPENDS;
            case NESTED       -> TransactionNode.TxStatus.NESTED;
            case SUPPORTS     -> callerIsActive ? TransactionNode.TxStatus.JOINS  : TransactionNode.TxStatus.NON_TX;
            case NOT_SUPPORTED -> TransactionNode.TxStatus.NON_TX;
            case NEVER        -> TransactionNode.TxStatus.NON_TX;
            case MANDATORY    -> callerIsActive ? TransactionNode.TxStatus.JOINS  : TransactionNode.TxStatus.NON_TX;
        };
    }

    /**
     * Returns true if the given method is ever called via {@code this.method()}
     * or an unqualified {@code method()} within the same class body — both of
     * which bypass the Spring AOP proxy.
     */
    private boolean detectSelfInvocation(@NotNull PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        // Walk all method bodies in the same class looking for calls to `method`
        for (PsiMethod sibling : containingClass.getMethods()) {
            if (sibling.equals(method)) {
                continue;
            }
            PsiCodeBlock body = sibling.getBody();
            if (body == null) {
                continue;
            }

            boolean[] found = {false};
            body.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                    super.visitMethodCallExpression(call);
                    if (found[0]) {
                        return;
                    }
                    PsiMethod resolved = call.resolveMethod();
                    if (!method.equals(resolved)) {
                        return;
                    }
                    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
                    // No qualifier (unqualified call) or explicit "this." → proxy bypass
                    if (qualifier == null || qualifier instanceof PsiThisExpression) {
                        found[0] = true;
                    }
                }
            });
            if (found[0]) {
                return true;
            }
        }
        return false;
    }

    private boolean containsWriteOperation(@NotNull PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }

        boolean[] found = {false};
        body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                if (found[0]) {
                    return;
                }
                String name = call.getMethodExpression().getReferenceName();
                if (WRITE_METHOD_NAMES.contains(name)) {
                    found[0] = true;
                }
            }
        });
        return found[0];
    }

    private void enqueueCallees(@NotNull PsiMethod method, @NotNull String parentId,
                                Deque<WorkItem> queue, int depth) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return;
        }

        body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                PsiMethod resolved = call.resolveMethod();
                if (resolved == null) {
                    return;
                }
                PsiClass resolvedClass = resolved.getContainingClass();
                if (resolvedClass == null || isTestClass(resolvedClass)
                        || !isProjectClass(resolvedClass)
                        || !isSpringBeanOrTransactional(resolvedClass)) {
                    return;
                }

                PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
                boolean isSelf = qualifier == null || qualifier instanceof PsiThisExpression;

                queue.add(new WorkItem(resolved, depth, parentId, isSelf, true));
            }
        });
    }

    private void enqueueCallers(@NotNull PsiMethod method, @NotNull String parentId,
                                Deque<WorkItem> queue, int depth) {
        MethodReferencesSearch.search(method, GlobalSearchScope.projectScope(this.project), false).forEach((PsiReference ref) -> {
            PsiElement element = ref.getElement();
            PsiMethod caller = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            if (caller != null) {
                PsiClass callerClass = caller.getContainingClass();
                if (callerClass == null || isTestClass(callerClass)
                        || !isProjectClass(callerClass)
                        || !isSpringBeanOrTransactional(callerClass)) {
                    return true;
                }
                queue.add(new WorkItem(caller, depth, parentId, false, false));
            }
            return true;
        });
    }

private void addEdge(@NotNull List<TransactionGraph.Edge> edges,
                          @NotNull WorkItem item,
                          @Nullable TransactionNode toNode,
                          @NotNull Map<String, TransactionNode> nodeMap) {
        if (toNode == null) {
            return;
        }
        String parentId = item.parentId();
        boolean proxyBypass = item.proxyBypass();
        boolean isCallee = item.isCallee();

        String fromId = isCallee ? parentId : toNode.getId();
        String toId   = isCallee ? toNode.getId() : parentId;

        TransactionNode caller = nodeMap.get(fromId);
        String label = computeEdgeLabel(toNode, caller);

        boolean suspension = toNode.getStatus() == TransactionNode.TxStatus.SUSPENDS;
        boolean nested     = toNode.getStatus() == TransactionNode.TxStatus.NESTED;
        edges.add(new TransactionGraph.Edge(fromId, toId, proxyBypass, suspension, nested, label));
    }

    @NotNull
    private static String computeEdgeLabel(@NotNull TransactionNode callee,
                                            @Nullable TransactionNode caller) {
        if (callee.getStatus() == TransactionNode.TxStatus.IGNORED) {
            return "tx ignored";
        }
        boolean callerActive = caller != null
                && caller.getStatus() != TransactionNode.TxStatus.NON_TX
                && caller.getStatus() != TransactionNode.TxStatus.IGNORED;
        return switch (callee.getPropagation()) {
            case REQUIRED     -> callerActive ? "joins" : "starts";
            case REQUIRES_NEW -> "suspends";
            case NESTED       -> "nested";
            case SUPPORTS     -> callerActive ? "joins" : "non-tx";
            case NOT_SUPPORTED -> "non-tx";
            case NEVER        -> "non-tx";
            case MANDATORY    -> callerActive ? "joins" : "non-tx";
        };
    }

    @Nullable
    private String annotationStringValue(@NotNull PsiAnnotation ann, @NotNull String attr) {
        PsiAnnotationMemberValue value = ann.findAttributeValue(attr);
        if (value instanceof PsiLiteralExpression lit && lit.getValue() instanceof String s) {
            return s;
        }
        if (value instanceof PsiReferenceExpression ref) {
            return ref.getText(); // e.g. "Propagation.REQUIRES_NEW"
        }
        if (value != null) {
            return value.getText();
        }
        return null;
    }

    @NotNull
    private List<String> annotationClassListValue(@NotNull PsiAnnotation ann,
                                                   @NotNull String attr) {
        PsiAnnotationMemberValue value = ann.findAttributeValue(attr);
        if (value == null) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        if (value instanceof PsiArrayInitializerMemberValue arr) {
            for (PsiAnnotationMemberValue member : arr.getInitializers()) {
                String text = member.getText();
                if (text != null && !text.isBlank()) {
                    result.add(text.replace(".class", "").trim());
                }
            }
        } else {
            String text = value.getText();
            if (text != null && !text.isBlank()) {
                result.add(text.replace(".class", "").trim());
            }
        }
        return Collections.unmodifiableList(result);
    }

    @NotNull
    public static String methodId(@NotNull PsiMethod method) {
        PsiClass clazz = method.getContainingClass();
        String className = (clazz != null)
                ? ((clazz.getQualifiedName() != null) ? clazz.getQualifiedName() : clazz.getName())
                : "Unknown";
        return className + "#" + method.getName();
    }

    @NotNull
    private static String displayName(@NotNull PsiMethod method) {
        PsiClass clazz = method.getContainingClass();
        String className = (clazz != null && clazz.getName() != null)
                ? clazz.getName()
                : "Unknown";
        return className + "#" + method.getName();
    }

    // -----------------------------------------------------------------------
    // Test-class detection
    // -----------------------------------------------------------------------

    private boolean isTestClass(@Nullable PsiClass clazz) {
        if (clazz == null) {
            return false;
        }
        VirtualFile vf = clazz.getContainingFile().getVirtualFile();
        if (vf != null) {
            ProjectFileIndex index = ProjectFileIndex.getInstance(this.project);
            if (index.isInTestSourceContent(vf)) {
                return true;
            }
        }
        String name = clazz.getName();
        return name != null && (name.endsWith("Test") || name.endsWith("Tests")
                || name.endsWith("IT") || name.endsWith("TestIT"));
    }

    private boolean isProjectClass(@NotNull PsiClass clazz) {
        VirtualFile vf = clazz.getContainingFile().getVirtualFile();
        if (vf != null) {
            ProjectFileIndex index = ProjectFileIndex.getInstance(this.project);
            if (index.isInSourceContent(vf)) {
                return true;
            }
        }
        // Fallback for light test fixtures where ProjectFileIndex may be empty:
        // accept classes whose files are not inside archives (jar/jrt protocols)
        if (vf != null) {
            String protocol = vf.getFileSystem().getProtocol();
            return !"jar".equals(protocol) && !"jrt".equals(protocol);
        }
        return false;
    }

    private boolean isSpringBeanOrTransactional(@NotNull PsiClass clazz) {
        PsiModifierList modifiers = clazz.getModifierList();
        if (modifiers == null) {
            return false;
        }
        if (findTxAnnotation(clazz) != null) {
            return true;
        }
        for (PsiAnnotation ann : modifiers.getAnnotations()) {
            String qname = ann.getQualifiedName();
            if (qname != null && SPRING_STEREOTYPE_FQNS.contains(qname)) {
                return true;
            }
            if (qname == null) {
                PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
                if (ref != null) {
                    String name = ref.getReferenceName();
                    if (name != null && ("Service".equals(name)
                            || "Component".equals(name)
                            || "Repository".equals(name)
                            || "Controller".equals(name)
                            || "Configuration".equals(name))) {
                        return true;
                    }
                }
            }
        }
        for (PsiMethod m : clazz.getMethods()) {
            if (findTxAnnotation(m) != null) {
                return true;
            }
        }
        // Spring Data JPA repositories are implicitly transactional
        if (isSpringDataRepository(clazz)) {
            return true;
        }
        return false;
    }

    public static boolean hasProgrammaticTransaction(@NotNull PsiMethod method) {
        return new TransactionGraphBuilder(method.getProject())
                .containsProgrammaticTransaction(method);
    }

    private boolean containsProgrammaticTransaction(@NotNull PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }
        boolean[] found = {false};
        body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                if (found[0]) {
                    return;
                }
                String methodName = call.getMethodExpression().getReferenceName();
                if (!PROGRAMMATIC_TX_METHODS.contains(methodName)) {
                    return;
                }
                PsiMethod target = call.resolveMethod();
                if (target == null) {
                    return;
                }
                PsiClass targetClass = target.getContainingClass();
                if (targetClass == null) {
                    return;
                }
                // Check if the target class or its supertypes implement PlatformTransactionManager
                if (isPlatformTransactionManager(targetClass)) {
                    found[0] = true;
                }
            }
        });
        return found[0];
    }

    private boolean isPlatformTransactionManager(@NotNull PsiClass clazz) {
        String fqn = clazz.getQualifiedName();
        if (fqn != null && fqn.equals("org.springframework.transaction.PlatformTransactionManager")) {
            return true;
        }
        for (PsiClass iface : clazz.getInterfaces()) {
            if (isPlatformTransactionManager(iface)) {
                return true;
            }
        }
        PsiClass sup = clazz.getSuperClass();
        return sup != null && isPlatformTransactionManager(sup);
    }

    @NotNull
    private TransactionNode.Propagation detectProgrammaticPropagation(
            @NotNull PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return TransactionNode.Propagation.REQUIRED;
        }
        TransactionNode.Propagation[] result = {TransactionNode.Propagation.REQUIRED};
        body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                String name = call.getMethodExpression().getReferenceName();
                if (!SET_PROPAGATION_BEHAVIOR.equals(name)) {
                    return;
                }
                PsiExpression[] args = call.getArgumentList().getExpressions();
                if (args.length == 0) {
                    return;
                }
                String argText = args[0].getText();
                if (argText.contains(PROPAGATION_REQUIRES_NEW)) {
                    result[0] = TransactionNode.Propagation.REQUIRES_NEW;
                } else if (argText.contains(PROPAGATION_NESTED)) {
                    result[0] = TransactionNode.Propagation.NESTED;
                } else if (argText.contains(PROPAGATION_NEVER)) {
                    result[0] = TransactionNode.Propagation.NEVER;
                } else if (argText.contains(PROPAGATION_NOT_SUPPORTED)) {
                    result[0] = TransactionNode.Propagation.NOT_SUPPORTED;
                } else if (argText.contains(PROPAGATION_SUPPORTS)) {
                    result[0] = TransactionNode.Propagation.SUPPORTS;
                } else if (argText.contains(PROPAGATION_MANDATORY)) {
                    result[0] = TransactionNode.Propagation.MANDATORY;
                }
            }
        });
        return result[0];
    }

    /**
     * BFS work item.
     */
    private record WorkItem(PsiMethod method, int depth, String parentId, boolean proxyBypass,
                            boolean isCallee) {
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------
// -----------------------------------------------------------------------
    // Node resolution
    // -----------------------------------------------------------------------
// -----------------------------------------------------------------------
    // Status computation
    // -----------------------------------------------------------------------
// -----------------------------------------------------------------------
    // Proxy-bypass detection
    // -----------------------------------------------------------------------
// -----------------------------------------------------------------------
    // Write-in-readOnly detection
    // -----------------------------------------------------------------------
// -----------------------------------------------------------------------
    // Graph edge helpers
    // -----------------------------------------------------------------------
// -----------------------------------------------------------------------
    // Annotation attribute helpers
    // -----------------------------------------------------------------------
// -----------------------------------------------------------------------
    // ID / display name helpers
    // -----------------------------------------------------------------------
// -----------------------------------------------------------------------
    // Programmatic transaction detection
    // -----------------------------------------------------------------------
// -----------------------------------------------------------------------
    // Internal value type for resolved annotation data
    // -----------------------------------------------------------------------

    private record AnnotationInfo(
            TransactionNode.Propagation propagation,
            boolean      readOnly,
            List<String> rollbackFor,
            List<String> noRollbackFor
    ) {}
}
