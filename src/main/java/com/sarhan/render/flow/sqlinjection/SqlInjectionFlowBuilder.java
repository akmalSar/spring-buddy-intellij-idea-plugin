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

package com.sarhan.render.flow.sqlinjection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.sarhan.render.flow.shared.FlowGraph;
import com.sarhan.render.flow.shared.FlowPsiUtil;
import com.sarhan.springformat.inspection.SpringInspectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds a {@link FlowGraph} that explains a predicted SQL injection:
 * an HTTP request parameter flows through string concatenation into a raw
 * query execution call.
 *
 * <p>Topology:
 * <pre>
 *   ENTRY_METHOD (the controller / service method receiving user input)
 *       │ executes
 *       ▼
 *   SOURCE (@RequestParam / @PathVariable / @RequestHeader parameter)
 *       │ concatenates
 *       ▼
 *   VARIABLE (local String built by concatenation)   [optional]
 *       │ inject
 *       ▼
 *   SINK (JdbcTemplate.query / EntityManager.createNativeQuery / …)
 * </pre>
 *
 * <p>Must be called inside a {@code ReadAction.compute { }} block.
 *
 * @author Akmal Sarhan
 */
public final class SqlInjectionFlowBuilder {

    public SqlInjectionFlowBuilder(@NotNull Project project) {
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
                "Receives user-controlled input",
                FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(enclosing),
                FlowPsiUtil.lineOf(enclosing));

        String sinkMethodName = sinkCall.getMethodExpression().getReferenceName();
        PsiClass sinkClass = resolveSinkClass(sinkCall);
        String sinkClassLabel = (sinkClass != null && sinkClass.getName() != null)
                ? sinkClass.getName() + "." : "";
        FlowGraph.Node sinkNode = new FlowGraph.Node(
                "sink",
                sinkClassLabel + ((sinkMethodName != null) ? sinkMethodName : "query") + "(…)",
                "⚠ Raw user input reaches query execution — SQL injection risk",
                FlowGraph.NodeKind.SINK,
                FlowPsiUtil.fileName(sinkCall),
                FlowPsiUtil.lineOf(sinkCall));

        List<FlowGraph.Node> nodes = new ArrayList<>();
        List<FlowGraph.Edge> edges = new ArrayList<>();
        nodes.add(entryNode);

        PsiExpression[] args = sinkCall.getArgumentList().getExpressions();
        if (args.length == 0) {
            nodes.add(sinkNode);
            edges.add(new FlowGraph.Edge("entry", "sink", "inject"));
            return buildGraph(entryNode, nodes, edges, sinkMethodName);
        }

        PsiExpression firstArg = args[0];
        PsiVariable taintedVar = resolveVariable(firstArg);
        PsiParameter sourceParam = findTaintSourceParam(enclosing, firstArg, taintedVar);

        if (sourceParam != null) {
            FlowGraph.Node sourceNode = new FlowGraph.Node(
                    "source",
                    sourceParam.getName(),
                    annotationLabel(sourceParam) + " — user-controlled input",
                    FlowGraph.NodeKind.SOURCE,
                    FlowPsiUtil.fileName(sourceParam),
                    FlowPsiUtil.lineOf(sourceParam));

            if (taintedVar != null && !(taintedVar instanceof PsiParameter)) {
                FlowGraph.Node varNode = new FlowGraph.Node(
                        "var",
                        taintedVar.getName(),
                        typeOf(taintedVar) + " (tainted by concatenation)",
                        FlowGraph.NodeKind.VARIABLE,
                        FlowPsiUtil.fileName(taintedVar),
                        FlowPsiUtil.lineOf(taintedVar));
                nodes.add(sourceNode);
                nodes.add(varNode);
                nodes.add(sinkNode);
                edges.add(new FlowGraph.Edge("entry", "source", "executes"));
                edges.add(new FlowGraph.Edge("source", "var", "concatenates"));
                edges.add(new FlowGraph.Edge("var", "sink", "inject"));
            } else {
                nodes.add(sourceNode);
                nodes.add(sinkNode);
                edges.add(new FlowGraph.Edge("entry", "source", "executes"));
                edges.add(new FlowGraph.Edge("source", "sink", "inject"));
            }
        } else {
            nodes.add(sinkNode);
            edges.add(new FlowGraph.Edge("entry", "sink", "inject"));
        }

        return buildGraph(entryNode, nodes, edges, sinkMethodName);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @NotNull
    private static FlowGraph buildGraph(@NotNull FlowGraph.Node entry,
                                          @NotNull List<FlowGraph.Node> nodes,
                                          @NotNull List<FlowGraph.Edge> edges,
                                          @Nullable String sinkName) {
        return FlowGraph.builder(entry)
                .nodes(nodes)
                .edges(edges)
                .title((sinkName != null) ? sinkName : "sql-injection")
                .headerWarning("SQL Injection: user input flows into raw query — "
                        + "use parameterized queries or JPA Criteria API.")
                .build();
    }

    @Nullable
    private static PsiVariable resolveVariable(@Nullable PsiExpression expr) {
        if (expr instanceof PsiReferenceExpression ref
                && ref.resolve() instanceof PsiVariable var) {
            return var;
        }
        return null;
    }

    @Nullable
    private PsiParameter findTaintSourceParam(@NotNull PsiMethod method,
                                                @NotNull PsiExpression firstArg,
                                                @Nullable PsiVariable argVar) {
        Set<PsiParameter> tainted = collectTaintedParameters(method);
        if (tainted.isEmpty()) {
            return null;
        }
        if (argVar instanceof PsiParameter param && tainted.contains(param)) {
            return param;
        }
        if (argVar instanceof PsiLocalVariable localVar) {
            PsiExpression init = localVar.getInitializer();
            if (init != null) {
                PsiParameter found = findTaintInExpr(init, tainted);
                if (found != null) {
                    return found;
                }
            }
            PsiCodeBlock body = method.getBody();
            if (body != null) {
                PsiParameter[] fromAssign = {null};
                body.accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assign) {
                        super.visitAssignmentExpression(assign);
                        if (fromAssign[0] != null) {
                            return;
                        }
                        if (assign.getLExpression() instanceof PsiReferenceExpression lref
                                && localVar.equals(lref.resolve())
                                && assign.getRExpression() != null) {
                            PsiParameter p = findTaintInExpr(assign.getRExpression(), tainted);
                            if (p != null) {
                                fromAssign[0] = p;
                            }
                        }
                    }
                });
                if (fromAssign[0] != null) {
                    return fromAssign[0];
                }
            }
        }
        return findTaintInExpr(firstArg, tainted);
    }

    @Nullable
    private static PsiParameter findTaintInExpr(@NotNull PsiExpression expr,
                                                  @NotNull Set<PsiParameter> tainted) {
        PsiParameter[] found = {null};
        expr.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
                super.visitReferenceExpression(ref);
                if (found[0] != null) {
                    return;
                }
                PsiElement resolved = ref.resolve();
                if (resolved instanceof PsiParameter param && tainted.contains(param)) {
                    found[0] = param;
                } else if (resolved instanceof PsiLocalVariable local) {
                    PsiExpression init = local.getInitializer();
                    if (init != null) {
                        PsiParameter p = findTaintInExpr(init, tainted);
                        if (p != null) {
                            found[0] = p;
                        }
                    }
                }
            }

            @Override
            public void visitPolyadicExpression(@NotNull PsiPolyadicExpression poly) {
                super.visitPolyadicExpression(poly);
                if (poly.getOperationTokenType() == JavaTokenType.PLUS) {
                    for (PsiExpression op : poly.getOperands()) {
                        if (!(op instanceof PsiLiteralExpression)) {
                            op.accept(this);
                        }
                    }
                }
            }
        });
        return found[0];
    }

    @NotNull
    private static Set<PsiParameter> collectTaintedParameters(@NotNull PsiMethod method) {
        Set<PsiParameter> tainted = new HashSet<>();
        for (PsiParameter p : method.getParameterList().getParameters()) {
            if (isTaintedSource(p)) {
                tainted.add(p);
            }
        }
        return tainted;
    }

    private static boolean isTaintedSource(@NotNull PsiParameter parameter) {
        PsiModifierList modifiers = parameter.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation a : modifiers.getAnnotations()) {
            if (SpringInspectionUtil.isAnyAnnotation(a,
                    "org.springframework.web.bind.annotation.RequestParam",
                    "org.springframework.web.bind.annotation.PathVariable",
                    "org.springframework.web.bind.annotation.RequestHeader",
                    "org.springframework.web.bind.annotation.RequestBody",
                    "org.springframework.web.bind.annotation.ModelAttribute")) {
                return true;
            }
        }
        PsiType type = parameter.getType();
        String canonical = type.getCanonicalText();
        return canonical != null
                && (canonical.contains("HttpServletRequest") || canonical.contains("HttpServletResponse"));
    }

    @NotNull
    private static String annotationLabel(@NotNull PsiParameter parameter) {
        PsiModifierList modifiers = parameter.getModifierList();
        if (modifiers != null) {
            for (PsiAnnotation a : modifiers.getAnnotations()) {
                String name = a.getQualifiedName();
                if (name == null) {
                    PsiJavaCodeReferenceElement ref = a.getNameReferenceElement();
                    if (ref != null) {
                        name = ref.getText();
                    }
                }
                if (name != null) {
                    int dot = name.lastIndexOf('.');
                    String simple = (dot >= 0) ? name.substring(dot + 1) : name;
                    if (simple.equals("RequestParam") || simple.equals("PathVariable")
                            || simple.equals("RequestHeader") || simple.equals("RequestBody")
                            || simple.equals("ModelAttribute")) {
                        return "@" + simple;
                    }
                }
            }
        }
        return "param";
    }

    @Nullable
    private static PsiClass resolveSinkClass(@NotNull PsiMethodCallExpression sinkCall) {
        PsiMethod method = sinkCall.resolveMethod();
        return (method != null) ? method.getContainingClass() : null;
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
