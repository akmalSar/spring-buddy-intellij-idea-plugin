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

package com.sarhan.render.flow.rollback;

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.sarhan.render.flow.shared.FlowGraph;
import com.sarhan.render.flow.shared.FlowPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds a flow diagram for an UnexpectedRollbackException risk.
 *
 * @author Akmal Sarhan
 */
public final class UnexpectedRollbackFlowBuilder {

    public UnexpectedRollbackFlowBuilder(@NotNull Project project) {
    }

    @NotNull
    public FlowGraph buildFromProblem(@NotNull PsiElement problemElement) {
        PsiMethodCallExpression call = problemElement instanceof PsiMethodCallExpression methodCall
                ? methodCall : PsiTreeUtil.getParentOfType(problemElement, PsiMethodCallExpression.class);
        if (call == null) {
            call = findCallNear(problemElement);
        }
        PsiMethod outer = PsiTreeUtil.getParentOfType(problemElement, PsiMethod.class);
        if (outer == null && call != null) {
            outer = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
        }
        if (outer == null) {
            return emptyFlow(problemElement);
        }

        FlowGraph.Node entry = new FlowGraph.Node("entry", outer.getName() + "()",
                "Outer @Transactional method tries to commit", FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(outer), FlowPsiUtil.lineOf(outer));
        List<FlowGraph.Node> nodes = new ArrayList<>();
        List<FlowGraph.Edge> edges = new ArrayList<>();
        nodes.add(entry);

        if (call != null) {
            PsiMethod target = call.resolveMethod();
            FlowGraph.Node inner = new FlowGraph.Node("inner", innerLabel(call, target),
                    "Inner transactional call participates in outer transaction",
                    FlowGraph.NodeKind.TRANSACTIONAL_RETURN,
                    FlowPsiUtil.fileName(call), FlowPsiUtil.lineOf(call));
            nodes.add(inner);
            edges.add(new FlowGraph.Edge("entry", "inner", "calls"));
        }

        PsiCatchSection catchSection = PsiTreeUtil.getParentOfType(problemElement, PsiCatchSection.class);
        FlowGraph.Node caught = new FlowGraph.Node("catch", "catch(RuntimeException)",
                "Exception is swallowed; rollback-only flag remains", FlowGraph.NodeKind.PROXY_BYPASS,
                FlowPsiUtil.fileName(catchSection != null ? catchSection : problemElement),
                FlowPsiUtil.lineOf(catchSection != null ? catchSection : problemElement));
        FlowGraph.Node commit = new FlowGraph.Node("commit", "commit()",
                "Spring detects rollback-only and throws UnexpectedRollbackException", FlowGraph.NodeKind.SINK,
                FlowPsiUtil.fileName(outer), FlowPsiUtil.lineOf(outer));
        nodes.add(caught);
        nodes.add(commit);
        edges.add(new FlowGraph.Edge(call != null ? "inner" : "entry", "catch", "rollback-only"));
        edges.add(new FlowGraph.Edge("catch", "commit", "throws"));

        return FlowGraph.builder(entry)
                .nodes(nodes)
                .edges(edges)
                .title("UnexpectedRollbackException")
                .headerWarning("Unexpected rollback: inner RuntimeException marks the shared transaction rollback-only; catching it does not make commit safe.")
                .build();
    }

    @Nullable
    private static PsiMethodCallExpression findCallNear(@NotNull PsiElement element) {
        PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(element, PsiTryStatement.class);
        if (tryStatement == null || tryStatement.getTryBlock() == null) {
            return null;
        }
        PsiMethodCallExpression[] found = {null};
        tryStatement.getTryBlock().accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                if (found[0] == null) {
                    found[0] = expression;
                }
            }
        });
        return found[0];
    }

    @NotNull
    private static String innerLabel(@NotNull PsiMethodCallExpression call, @Nullable PsiMethod target) {
        if (target == null || target.getContainingClass() == null || target.getContainingClass().getName() == null) {
            return call.getText();
        }
        return target.getContainingClass().getName() + "." + target.getName() + "(...)";
    }

    @NotNull
    private static FlowGraph emptyFlow(@NotNull PsiElement element) {
        FlowGraph.Node entry = new FlowGraph.Node("entry", "Unknown", "", FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(element), FlowPsiUtil.lineOf(element));
        return FlowGraph.builder(entry).nodes(List.of(entry)).edges(List.of()).title("unexpected-rollback").build();
    }
}
