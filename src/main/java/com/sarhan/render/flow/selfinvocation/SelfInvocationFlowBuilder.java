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

package com.sarhan.render.flow.selfinvocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiThisExpression;
import com.sarhan.render.flow.shared.FlowGraph;
import com.sarhan.render.flow.shared.FlowPsiUtil;
import com.sarhan.springformat.inspection.SpringInspectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds a {@link FlowGraph} that visualises a transactional self-invocation
 * indirection chain.
 *
 * <p>Spring's {@code @Transactional} relies on a CGLIB / JDK proxy. When code
 * calls a method on {@code this} (or implicitly), the proxy is bypassed and
 * the transaction annotation is silently ignored.  This builder walks the
 * call chain from the flagged caller method to the {@code @Transactional}
 * target via {@code this.}-qualified or implicit same-class calls and
 * produces:
 *
 * <pre>
 *   ENTRY_METHOD (caller, no @Transactional)
 *       │ this.hop()
 *       ▼
 *   HELPER_CALL (hop method, also same class)
 *       │ this.deeper()
 *       ▼
 *   PROXY_BYPASS (the @Transactional target whose annotation is ignored)
 * </pre>
 *
 * <p>Must be called inside a {@code ReadAction.compute { }} block.
 *
 * @author Akmal Sarhan
 */
public final class SelfInvocationFlowBuilder {

    private static final int MAX_DEPTH = 4;

    /** For symmetry with the other builders. */
    public SelfInvocationFlowBuilder(@NotNull Project project) {
    }

    /**
     * Builds the flow starting from the non-transactional caller method
     * flagged by {@code SpringTransactionalSelfInvocationIndirectionInspection}.
     */
    @NotNull
    public FlowGraph buildFromCaller(@NotNull PsiMethod caller) {
        PsiClass containingClass = caller.getContainingClass();
        if (containingClass == null || caller.getBody() == null) {
            return emptyFlow(caller);
        }

        FlowGraph.Node entryNode = new FlowGraph.Node(
                "entry",
                caller.getName() + "()",
                "No @Transactional \u2014 entry point",
                FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(caller),
                FlowPsiUtil.lineOf(caller));

        // Find the shortest chain of same-class this-calls leading to a @Transactional method.
        List<Hop> chain = findShortestChain(caller, containingClass);
        if (chain == null) {
            return FlowGraph.builder(entryNode)
                    .nodes(List.of(entryNode))
                    .edges(List.of())
                    .title(caller.getName())
                    .headerWarning("No transactional self-invocation chain detected.")
                    .build();
        }

        List<FlowGraph.Node> nodes = new ArrayList<>();
        List<FlowGraph.Edge> edges = new ArrayList<>();
        nodes.add(entryNode);

        String prevId = "entry";
        // chain[0] is the first hop from caller; the last entry is the @Transactional target.
        for (int i = 0; i < chain.size(); i++) {
            Hop hop = chain.get(i);
            boolean isLast = (i == chain.size() - 1);
            String id = isLast ? "tx" : ("hop" + i);
            FlowGraph.NodeKind kind = isLast
                    ? FlowGraph.NodeKind.PROXY_BYPASS
                    : FlowGraph.NodeKind.HELPER_CALL;
            String detail = isLast
                    ? "@Transactional \u2014 proxy bypassed, annotation ignored"
                    : "Same-class call \u2014 proxy not involved";
            FlowGraph.Node n = new FlowGraph.Node(
                    id,
                    hop.method().getName() + "()",
                    detail,
                    kind,
                    FlowPsiUtil.fileName(hop.method()),
                    FlowPsiUtil.lineOf(hop.method()));
            nodes.add(n);
            edges.add(new FlowGraph.Edge(prevId, id, isLast ? "bypass" : "this.call"));
            prevId = id;
        }

        return FlowGraph.builder(entryNode)
                .nodes(nodes)
                .edges(edges)
                .title(caller.getName())
                .headerWarning("Self-invocation through " + chain.size()
                        + ((chain.size() == 1) ? " hop" : " hops")
                        + " bypasses the Spring proxy \u2014 the @Transactional "
                        + "annotation is silently ignored.")
                .build();
    }

    // -----------------------------------------------------------------------
    // Chain search — BFS would also work; DFS with depth limit mirrors the
    // original inspection's algorithm and produces the same target.
    // -----------------------------------------------------------------------

    @Nullable
    private static List<Hop> findShortestChain(@NotNull PsiMethod start,
                                                  @NotNull PsiClass containingClass) {
        Result result = new Result();
        searchChain(start, containingClass, new HashSet<>(), new ArrayList<>(), result, 0);
        return result.bestChain;
    }

    private static void searchChain(@NotNull PsiMethod current,
                                      @NotNull PsiClass containingClass,
                                      @NotNull Set<PsiMethod> visited,
                                      @NotNull List<Hop> path,
                                      @NotNull Result result,
                                      int depth) {
        if (depth >= MAX_DEPTH || !visited.add(current)) {
            return;
        }
        PsiCodeBlock body = current.getBody();
        if (body == null) {
            visited.remove(current);
            return;
        }
        body.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                if (result.bestChain != null && path.size() + 1 >= result.bestChain.size()) {
                    return;
                }
                PsiMethod called = expression.resolveMethod();
                if (called == null || called.getContainingClass() != containingClass) {
                    return;
                }
                PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
                if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
                    return;
                }
                Hop hop = new Hop(called, expression);
                path.add(hop);
                if (hasTransactionalAnnotation(called)) {
                    if (result.bestChain == null
                            || path.size() < result.bestChain.size()) {
                        result.bestChain = new ArrayList<>(path);
                    }
                } else {
                    searchChain(called, containingClass, visited, path, result, depth + 1);
                }
                path.remove(path.size() - 1);
            }
        });
        visited.remove(current);
    }

    // -----------------------------------------------------------------------
    // Annotation handling — same logic as the inspection, kept local to avoid
    // adding test-incompatible cross-package dependencies.
    // -----------------------------------------------------------------------

    private static boolean hasTransactionalAnnotation(@NotNull PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        if (hasTransactionalOnModifiers(modifiers)) {
            return true;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        PsiModifierList classModifiers = containingClass.getModifierList();
        return hasTransactionalOnModifiers(classModifiers);
    }

    private static boolean hasTransactionalOnModifiers(@Nullable PsiModifierList modifiers) {
        if (modifiers == null) {
            return false;
        }
        return SpringInspectionUtil.findAnyAnnotation(modifiers,
                "org.springframework.transaction.annotation.Transactional",
                "jakarta.transaction.Transactional",
                "javax.transaction.Transactional") != null;
    }

    // -----------------------------------------------------------------------

    @NotNull
    private static FlowGraph emptyFlow(@NotNull PsiElement element) {
        FlowGraph.Node entry = new FlowGraph.Node(
                "entry", "Unknown", "", FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(element), FlowPsiUtil.lineOf(element));
        return FlowGraph.builder(entry)
                .nodes(List.of(entry))
                .edges(List.of())
                .build();
    }

    private record Hop(PsiMethod method, PsiMethodCallExpression callSite) {
    }

    private static final class Result {
        @Nullable List<Hop> bestChain;
    }
}
