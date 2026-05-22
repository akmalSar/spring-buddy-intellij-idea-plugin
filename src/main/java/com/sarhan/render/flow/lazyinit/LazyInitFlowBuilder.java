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

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.sarhan.dataFlow.base.SpringEntitySourceDetector;
import com.sarhan.render.flow.shared.FlowGraph;
import com.sarhan.render.flow.shared.FlowPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds a {@link FlowGraph} that explains a predicted
 * {@code LazyInitializationException}.
 *
 * <p>Topology:
 * <pre>
 *   ENTRY_METHOD (no @Transactional, no live Hibernate session)
 *       │ executes
 *       ▼
 *   SOURCE (repo.findById(...) / em.find(...))     ← loads detached entity
 *       │ returns
 *       ▼
 *   VARIABLE (the local that holds the entity)
 *       │ lazy
 *       ▼
 *   LAZY_GETTER (e.g. order.getItems().size())     ← throws LazyInitializationException
 * </pre>
 *
 * <p>Must be called inside a {@code ReadAction.compute { }} block.
 *
 * @author Akmal Sarhan
 */
public final class LazyInitFlowBuilder {

    private final SpringEntitySourceDetector sourceDetector;

    public LazyInitFlowBuilder(@NotNull Project project) {
        this.sourceDetector = new SpringEntitySourceDetector(project);
    }

    @NotNull
    public FlowGraph buildFromSink(@NotNull PsiMethodCallExpression lazyCall) {
        PsiMethod enclosing = PsiTreeUtil.getParentOfType(lazyCall, PsiMethod.class);
        if (enclosing == null) {
            return emptyFlow(lazyCall);
        }

        FlowGraph.Node entryNode = new FlowGraph.Node(
                "entry",
                enclosing.getName() + "()",
                "No @Transactional \u2014 Hibernate session is closed",
                FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(enclosing),
                FlowPsiUtil.lineOf(enclosing));

        String lazyName = lazyCall.getMethodExpression().getReferenceName();
        FlowGraph.Node sinkNode = new FlowGraph.Node(
                "lazy",
                ((lazyName != null) ? lazyName : "lazy") + "(\u2026)",
                "\u26A0 LazyInitializationException at runtime",
                FlowGraph.NodeKind.LAZY_GETTER,
                FlowPsiUtil.fileName(lazyCall),
                FlowPsiUtil.lineOf(lazyCall));

        List<FlowGraph.Node> nodes = new ArrayList<>();
        List<FlowGraph.Edge> edges = new ArrayList<>();
        nodes.add(entryNode);

        PsiExpression qualifier = lazyCall.getMethodExpression().getQualifierExpression();
        PsiVariable taintedVar = resolveVariable(qualifier);

        if (taintedVar != null) {
            FlowGraph.Node varNode = new FlowGraph.Node(
                    "var",
                    taintedVar.getName(),
                    typeOf(taintedVar) + " (detached)",
                    FlowGraph.NodeKind.VARIABLE,
                    FlowPsiUtil.fileName(taintedVar),
                    FlowPsiUtil.lineOf(taintedVar));

            PsiMethodCallExpression source = findSourceForVariable(taintedVar, enclosing);
            if (source != null) {
                PsiMethod resolved = source.resolveMethod();
                String label = (resolved != null)
                        ? formatSourceLabel(resolved)
                        : source.getText();
                FlowGraph.Node sourceNode = new FlowGraph.Node(
                        "source",
                        label,
                        "Loads entity \u2014 session closes when this returns",
                        FlowGraph.NodeKind.SOURCE,
                        FlowPsiUtil.fileName(source),
                        FlowPsiUtil.lineOf(source));
                nodes.add(sourceNode);
                nodes.add(varNode);
                nodes.add(sinkNode);
                edges.add(new FlowGraph.Edge("entry", "source", "executes"));
                edges.add(new FlowGraph.Edge("source", "var", "returns"));
                edges.add(new FlowGraph.Edge("var", "lazy", "lazy"));
            } else if (taintedVar instanceof PsiParameter) {
                FlowGraph.Node paramNode = new FlowGraph.Node(
                        "param",
                        "param: " + taintedVar.getName(),
                        "Detached entity received from caller",
                        FlowGraph.NodeKind.HELPER_CALL,
                        FlowPsiUtil.fileName(taintedVar),
                        FlowPsiUtil.lineOf(taintedVar));
                nodes.add(paramNode);
                nodes.add(varNode);
                nodes.add(sinkNode);
                edges.add(new FlowGraph.Edge("entry", "param", "called with"));
                edges.add(new FlowGraph.Edge("param", "var", "binds"));
                edges.add(new FlowGraph.Edge("var", "lazy", "lazy"));
            } else {
                nodes.add(varNode);
                nodes.add(sinkNode);
                edges.add(new FlowGraph.Edge("entry", "var", "declares"));
                edges.add(new FlowGraph.Edge("var", "lazy", "lazy"));
            }
        } else {
            nodes.add(sinkNode);
            edges.add(new FlowGraph.Edge("entry", "lazy", "lazy"));
        }

        return FlowGraph.builder(entryNode)
                .nodes(nodes)
                .edges(edges)
                .title((lazyName != null) ? lazyName : "lazy-init")
                .headerWarning("Lazy access on a detached entity \u2014 wrap the caller "
                        + "in @Transactional or fetch the association eagerly.")
                .build();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @Nullable
    private PsiVariable resolveVariable(@Nullable PsiExpression expr) {
        if (expr instanceof PsiReferenceExpression ref
                && ref.resolve() instanceof PsiVariable var) {
            return var;
        }
        if (expr instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            if (name != null && (name.equals("get") || name.startsWith("orElse"))) {
                return resolveVariable(call.getMethodExpression().getQualifierExpression());
            }
        }
        return null;
    }

    @Nullable
    private PsiMethodCallExpression findSourceForVariable(@NotNull PsiVariable var,
                                                            @NotNull PsiMethod scope) {
        if (var.getInitializer() instanceof PsiMethodCallExpression call) {
            PsiMethodCallExpression unwrapped = unwrapOptional(call);
            PsiMethodCallExpression candidate = (unwrapped != null) ? unwrapped : call;
            if (isEntitySource(candidate)) {
                return candidate;
            }
        }
        PsiCodeBlock body = scope.getBody();
        if (body == null) {
            return null;
        }
        PsiMethodCallExpression[] found = {null};
        body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assign) {
                super.visitAssignmentExpression(assign);
                if (found[0] != null) {
                    return;
                }
                if (assign.getLExpression() instanceof PsiReferenceExpression lref
                        && var.equals(lref.resolve())
                        && assign.getRExpression() instanceof PsiMethodCallExpression rcall) {
                    PsiMethodCallExpression unwrapped = unwrapOptional(rcall);
                    PsiMethodCallExpression candidate = (unwrapped != null) ? unwrapped : rcall;
                    if (isEntitySource(candidate)) {
                        found[0] = candidate;
                    }
                }
            }
        });
        return found[0];
    }

    @Nullable
    private PsiMethodCallExpression unwrapOptional(@NotNull PsiMethodCallExpression call) {
        String name = call.getMethodExpression().getReferenceName();
        if (name != null && (name.equals("get") || name.startsWith("orElse"))) {
            PsiExpression q = call.getMethodExpression().getQualifierExpression();
            if (q instanceof PsiMethodCallExpression inner) {
                return inner;
            }
        }
        return null;
    }

    private boolean isEntitySource(@NotNull PsiMethodCallExpression call) {
        PsiMethod target = call.resolveMethod();
        return target != null && this.sourceDetector.isDetachedEntitySource(target);
    }

    @NotNull
    private static String formatSourceLabel(@NotNull PsiMethod target) {
        PsiClass owner = target.getContainingClass();
        String type = (owner != null && owner.getName() != null) ? owner.getName() : "?";
        return type + "." + target.getName() + "(\u2026)";
    }

    @NotNull
    private static String typeOf(@NotNull PsiVariable var) {
        return var.getType().getPresentableText();
    }

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
}
