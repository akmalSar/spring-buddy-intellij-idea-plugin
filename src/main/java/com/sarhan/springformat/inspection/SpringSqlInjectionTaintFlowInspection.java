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

import java.util.HashSet;
import java.util.Set;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.sarhan.render.flow.sqlinjection.ShowSqlInjectionFlowFix;
import org.jetbrains.annotations.NotNull;

/**
 * Detects intra-procedural SQL injection taint flow: method parameters annotated
 * with @RequestParam or @PathVariable (or HttpServletRequest access) that are
 * concatenated into strings and passed to query execution methods such as
 * EntityManager.createNativeQuery, JdbcTemplate.query, etc.
 *
 * @author Akmal Sarhan
 */
public class SpringSqlInjectionTaintFlowInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                if (method.getBody() == null) {
                    return;
                }
                Set<PsiParameter> tainted = collectTaintedParameters(method);
                if (tainted.isEmpty()) {
                    return;
                }
                Set<PsiLocalVariable> taintedLocals = collectTaintedLocals(method.getBody(), tainted);
                method.getBody().accept(new JavaRecursiveElementVisitor() {
                    @Override
                    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                        super.visitMethodCallExpression(expression);
                        if (!isQueryExecutionMethod(expression)) {
                            return;
                        }
                        PsiExpression[] args = expression.getArgumentList().getExpressions();
                        if (args.length > 0 && containsTaint(args[0], tainted, taintedLocals)) {
                            holder.registerProblem(args[0],
                                    "Potential SQL injection: user input flows into query execution without parameter binding",
                                    ProblemHighlightType.ERROR,
                                    new ShowSqlInjectionFlowFix());
                        }
                    }

                    @Override
                    public void visitNewExpression(@NotNull PsiNewExpression expression) {
                        super.visitNewExpression(expression);
                        PsiJavaCodeReferenceElement classRef = expression.getClassReference();
                        if (classRef == null) {
                            return;
                        }
        String className = classRef.getQualifiedName();
        if (className == null) {
            return;
        }
        boolean isKnownQueryType = className.equals("Query")
                || (className.startsWith("jakarta.persistence.") && className.endsWith("Query"))
                || (className.startsWith("javax.persistence.") && className.endsWith("Query"))
                || className.startsWith("org.springframework.data.jpa");
        if (!isKnownQueryType) {
            return;
        }
                        PsiExpressionList args = expression.getArgumentList();
                        if (args == null) {
                            return;
                        }
                        PsiExpression[] expressions = args.getExpressions();
                        if (expressions.length > 0 && containsTaint(expressions[0], tainted, taintedLocals)) {
                            holder.registerProblem(expressions[0],
                                    "Potential SQL injection: user input flows into query object without parameter binding",
                                    ProblemHighlightType.ERROR,
                                    new ShowSqlInjectionFlowFix());
                        }
                    }
                });
            }
        };
    }

    private static Set<PsiParameter> collectTaintedParameters(PsiMethod method) {
        Set<PsiParameter> tainted = new HashSet<>();
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            if (isTaintedSource(parameter)) {
                tainted.add(parameter);
            }
        }
        return tainted;
    }

    private static Set<PsiLocalVariable> collectTaintedLocals(PsiCodeBlock body,
                                                                Set<PsiParameter> taintedParams) {
        Set<PsiLocalVariable> tainted = new HashSet<>();
        boolean changed = true;
        while (changed) {
            boolean[] didAdd = {false};
            body.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expr) {
                    super.visitAssignmentExpression(expr);
                    PsiExpression rhs = expr.getRExpression();
                    if (rhs == null) {
                        return;
                    }
                    if (expr.getLExpression() instanceof PsiReferenceExpression lref
                            && lref.resolve() instanceof PsiLocalVariable lv
                            && !tainted.contains(lv)
                            && containsTaint(rhs, taintedParams, tainted)) {
                        tainted.add(lv);
                        didAdd[0] = true;
                    }
                }

                @Override
                public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
                    super.visitLocalVariable(variable);
                    PsiExpression init = variable.getInitializer();
                    if (init != null && !tainted.contains(variable)
                            && containsTaint(init, taintedParams, tainted)) {
                        tainted.add(variable);
                        didAdd[0] = true;
                    }
                }
            });
            changed = didAdd[0];
        }
        return tainted;
    }

    private static boolean isTaintedSource(PsiParameter parameter) {
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
        if (canonical != null && (canonical.contains("HttpServletRequest") || canonical.contains("HttpServletResponse"))) {
            return true;
        }
        return false;
    }

    private static boolean containsTaint(PsiExpression expression, Set<PsiParameter> tainted,
                                          Set<PsiLocalVariable> taintedLocals) {
        return containsTaint(expression, tainted, taintedLocals, new HashSet<>());
    }

    private static boolean containsTaint(PsiExpression expression, Set<PsiParameter> tainted,
                                          Set<PsiLocalVariable> taintedLocals, Set<PsiElement> visited) {
        if (!visited.add(expression)) {
            return false;
        }
        boolean[] found = {false};
        expression.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
                super.visitReferenceExpression(ref);
                PsiElement resolved = ref.resolve();
                if (resolved instanceof PsiParameter parameter && tainted.contains(parameter)) {
                    found[0] = true;
                    return;
                }
                if (!found[0] && resolved instanceof PsiLocalVariable localVar) {
                    if (taintedLocals.contains(localVar)) {
                        found[0] = true;
                        return;
                    }
                    PsiExpression initializer = localVar.getInitializer();
                    if (initializer != null && containsTaint(initializer, tainted, taintedLocals, visited)) {
                        found[0] = true;
                    }
                    return;
                }
                if (resolved instanceof PsiMethod method) {
                    String name = method.getName();
                    if ("getParameter".equals(name) || "getHeader".equals(name)
                            || "getQueryString".equals(name) || "getInputStream".equals(name)) {
                        found[0] = true;
                    }
                }
            }

            @Override
            public void visitPolyadicExpression(@NotNull PsiPolyadicExpression polyadic) {
                super.visitPolyadicExpression(polyadic);
                if (polyadic.getOperationTokenType() == JavaTokenType.PLUS) {
                    for (PsiExpression operand : polyadic.getOperands()) {
                        if (!(operand instanceof PsiLiteralExpression)) {
                            operand.accept(this);
                        }
                    }
                }
            }

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                String name = call.getMethodExpression().getReferenceName();
                if ("format".equals(name) || "formatted".equals(name) || "concat".equals(name)
                        || "append".equals(name) || "replace".equals(name)) {
                    for (PsiExpression arg : call.getArgumentList().getExpressions()) {
                        arg.accept(this);
                    }
                }
            }
        });
        return found[0];
    }

    private static boolean isQueryExecutionMethod(PsiMethodCallExpression expression) {
        PsiMethod method = expression.resolveMethod();
        if (method == null) {
            return false;
        }
        String name = method.getName();
        if (name == null) {
            return false;
        }
        if (!(name.startsWith("create") || name.startsWith("execute") || name.startsWith("query")
                || name.startsWith("update") || name.startsWith("batchUpdate"))) {
            return false;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        String className = containingClass.getQualifiedName();
        if (className == null) {
            return false;
        }
        return className.equals("jakarta.persistence.EntityManager")
                || className.equals("javax.persistence.EntityManager")
                || className.equals("EntityManager")
                || className.equals("org.springframework.jdbc.core.JdbcTemplate")
                || className.equals("JdbcTemplate")
                || className.equals("org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate")
                || className.equals("NamedParameterJdbcTemplate")
                || className.startsWith("org.springframework.data.jpa")
                || (className.startsWith("jakarta.persistence.") && className.endsWith("Query"))
                || (className.startsWith("javax.persistence.") && className.endsWith("Query"))
                || className.equals("Query")
                || className.equals("java.sql.PreparedStatement")
                || className.equals("PreparedStatement")
                || className.equals("java.sql.Statement")
                || className.equals("Statement")
                || className.equals("java.sql.CallableStatement")
                || className.equals("CallableStatement");
    }
}
