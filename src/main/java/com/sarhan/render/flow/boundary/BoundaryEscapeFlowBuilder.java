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

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.sarhan.render.flow.shared.FlowGraph;
import com.sarhan.render.flow.shared.FlowPsiUtil;
import com.sarhan.springformat.inspection.SpringInspectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds a {@link FlowGraph} that visualises a transaction-boundary escape:
 * an entity returned by a {@code @Transactional} method is accessed
 * (mutated or lazy-loaded) outside that boundary by a non-transactional caller.
 *
 * <p>Topology:
 * <pre>
 *   ENTRY_METHOD (caller, no @Transactional)
 *       │ executes
 *       ▼
 *   TRANSACTIONAL_RETURN (e.g. orderService.findById(id))   ← the @Transactional source
 *       │ returns
 *       ▼
 *   VARIABLE (the local that holds the now-detached entity)
 *       │ escape
 *       ▼
 *   SINK (the lazy access or mutator call that breaks)
 * </pre>
 *
 * <p>Must be called inside a {@code ReadAction.compute { }} block.
 *
 * @author Akmal Sarhan
 */
public final class BoundaryEscapeFlowBuilder {

    /** Used for symmetry with the other builders (cheap, no project state needed today). */
    public BoundaryEscapeFlowBuilder(@NotNull Project project) {
    }

    @NotNull
    public FlowGraph buildFromSink(@NotNull PsiMethodCallExpression sinkCall) {
        PsiMethod enclosing = PsiTreeUtil.getParentOfType(sinkCall, PsiMethod.class);
        if (enclosing == null) {
            return emptyFlow(sinkCall);
        }

        FlowGraph.Node entryNode = new FlowGraph.Node(
                "entry",
                enclosing.getName() + "()",
                "No @Transactional \u2014 entity is detached after the call returns",
                FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(enclosing),
                FlowPsiUtil.lineOf(enclosing));

        String sinkName = sinkCall.getMethodExpression().getReferenceName();
        FlowGraph.Node sinkNode = new FlowGraph.Node(
                "sink",
                ((sinkName != null) ? sinkName : "access") + "(\u2026)",
                "\u26A0 Lazy-init / mutation outside the transaction boundary",
                FlowGraph.NodeKind.SINK,
                FlowPsiUtil.fileName(sinkCall),
                FlowPsiUtil.lineOf(sinkCall));

        List<FlowGraph.Node> nodes = new ArrayList<>();
        List<FlowGraph.Edge> edges = new ArrayList<>();
        nodes.add(entryNode);

        PsiExpression qualifier = sinkCall.getMethodExpression().getQualifierExpression();
        PsiVariable taintedVar = resolveVariable(qualifier);

        if (taintedVar != null) {
            FlowGraph.Node varNode = new FlowGraph.Node(
                    "var",
                    taintedVar.getName(),
                    typeOf(taintedVar) + " (detached after tx returns)",
                    FlowGraph.NodeKind.VARIABLE,
                    FlowPsiUtil.fileName(taintedVar),
                    FlowPsiUtil.lineOf(taintedVar));

            PsiMethodCallExpression source = findTransactionalSource(taintedVar, enclosing);
            if (source != null) {
                PsiMethod resolvedSource = source.resolveMethod();
                String label = (resolvedSource != null)
                        ? formatTxLabel(resolvedSource)
                        : source.getText();
                FlowGraph.Node txNode = new FlowGraph.Node(
                        "tx",
                        label,
                        "@Transactional \u2014 entity managed only inside this method",
                        FlowGraph.NodeKind.TRANSACTIONAL_RETURN,
                        FlowPsiUtil.fileName(source),
                        FlowPsiUtil.lineOf(source));
                nodes.add(txNode);
                nodes.add(varNode);
                nodes.add(sinkNode);
                edges.add(new FlowGraph.Edge("entry", "tx", "calls"));
                edges.add(new FlowGraph.Edge("tx", "var", "returns"));
                edges.add(new FlowGraph.Edge("var", "sink", "escape"));
            } else if (taintedVar instanceof PsiParameter) {
                FlowGraph.Node paramNode = new FlowGraph.Node(
                        "param",
                        "param: " + taintedVar.getName(),
                        "Entity received from caller \u2014 also detached",
                        FlowGraph.NodeKind.HELPER_CALL,
                        FlowPsiUtil.fileName(taintedVar),
                        FlowPsiUtil.lineOf(taintedVar));
                nodes.add(paramNode);
                nodes.add(varNode);
                nodes.add(sinkNode);
                edges.add(new FlowGraph.Edge("entry", "param", "called with"));
                edges.add(new FlowGraph.Edge("param", "var", "binds"));
                edges.add(new FlowGraph.Edge("var", "sink", "escape"));
            } else {
                nodes.add(varNode);
                nodes.add(sinkNode);
                edges.add(new FlowGraph.Edge("entry", "var", "declares"));
                edges.add(new FlowGraph.Edge("var", "sink", "escape"));
            }
        } else {
            nodes.add(sinkNode);
            edges.add(new FlowGraph.Edge("entry", "sink", "escape"));
        }

        return FlowGraph.builder(entryNode)
                .nodes(nodes)
                .edges(edges)
                .title(((sinkName != null) ? sinkName : "boundary-escape"))
                .headerWarning("Entity returned from a @Transactional method is "
                        + "accessed outside that transaction \u2014 lazy fields throw, "
                        + "mutations are silently lost.")
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
    private PsiMethodCallExpression findTransactionalSource(@NotNull PsiVariable var,
                                                              @NotNull PsiMethod scope) {
        if (var.getInitializer() instanceof PsiMethodCallExpression call) {
            PsiMethodCallExpression unwrapped = unwrapOptional(call);
            PsiMethodCallExpression candidate = (unwrapped != null) ? unwrapped : call;
            if (isTransactionalReturn(candidate)) {
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
                    if (isTransactionalReturn(candidate)) {
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

    private boolean isTransactionalReturn(@NotNull PsiMethodCallExpression call) {
        PsiMethod target = call.resolveMethod();
        if (target == null) {
            return false;
        }
        return hasTransactional(target);
    }

    private static boolean hasTransactional(@NotNull PsiMethod method) {
        PsiModifierList ml = method.getModifierList();
        if (hasTransactionalOnModifiers(ml)) {
            return true;
        }
        PsiClass cls = method.getContainingClass();
        if (cls != null) {
            PsiModifierList cml = cls.getModifierList();
            if (cml != null && hasTransactionalOnModifiers(cml)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTransactionalOnModifiers(@Nullable PsiModifierList ml) {
        if (ml == null) {
            return false;
        }
        return SpringInspectionUtil.findAnyAnnotation(ml,
                "org.springframework.transaction.annotation.Transactional",
                "jakarta.transaction.Transactional",
                "javax.transaction.Transactional") != null;
    }

    @NotNull
    private static String formatTxLabel(@NotNull PsiMethod target) {
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
