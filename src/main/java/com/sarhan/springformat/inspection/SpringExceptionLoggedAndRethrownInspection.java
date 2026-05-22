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

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * SpringExceptionLoggedAndRethrown: catch block that calls `log.error(...)` (or other SLF4J levels)
 * and then rethrows the same exception. Quick-fix removes the log statement (caller will log).
 *
 * @author Akmal Sarhan
 */
public class SpringExceptionLoggedAndRethrownInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String SLF4J_LOGGER = "org.slf4j.Logger";

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitCatchSection(@NotNull PsiCatchSection section) {
                super.visitCatchSection(section);
                PsiCodeBlock body = section.getCatchBlock();
                PsiParameter param = section.getParameter();
                if (body == null || param == null) {
                    return;
                }
                PsiStatement[] statements = body.getStatements();
                if (statements.length < 2) {
                    return;
                }
                PsiStatement last = statements[statements.length - 1];
                if (!(last instanceof PsiThrowStatement throwStatement)) {
                    return;
                }
                if (!throwsSameVariable(throwStatement, param.getName())) {
                    return;
                }
                for (int i = 0; i < statements.length - 1; i++) {
                    PsiMethodCallExpression logCall = asLogCall(statements[i]);
                    if (logCall == null) {
                        continue;
                    }
                    holder.registerProblem(statements[i],
                            "Exception is logged and rethrown — the caller will log it again",
                            ProblemHighlightType.WARNING, new RemoveLogStatementFix());
                }
            }
        };
    }

    private static boolean throwsSameVariable(PsiThrowStatement statement, String variableName) {
        PsiExpression thrown = statement.getException();
        return thrown instanceof PsiReferenceExpression reference
                && variableName.equals(reference.getReferenceName());
    }

    private static PsiMethodCallExpression asLogCall(PsiStatement statement) {
        if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
            return null;
        }
        if (!(expressionStatement.getExpression() instanceof PsiMethodCallExpression call)) {
            return null;
        }
        String name = call.getMethodExpression().getReferenceName();
        if (name == null || !isLogLevel(name)) {
            return null;
        }
        if (isResolvedSlf4jCall(call) || isLombokGeneratedLoggerCall(call)) {
            return call;
        }
        return null;
    }

    private static boolean isLogLevel(String name) {
        return "trace".equals(name) || "debug".equals(name) || "info".equals(name)
                || "warn".equals(name) || "error".equals(name);
    }

    private static boolean isResolvedSlf4jCall(PsiMethodCallExpression call) {
        PsiMethod method = call.resolveMethod();
        if (method == null) {
            return false;
        }
        PsiClass containing = method.getContainingClass();
        return containing != null && SLF4J_LOGGER.equals(containing.getQualifiedName());
    }

    private static boolean isLombokGeneratedLoggerCall(PsiMethodCallExpression call) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression reference)) {
            return false;
        }
        if (!"log".equals(reference.getReferenceName())) {
            return false;
        }
        PsiClass containingClass = PsiTreeUtil.getParentOfType(call, PsiClass.class);
        while (containingClass != null) {
            if (hasLombokLoggerAnnotation(containingClass)) {
                return true;
            }
            containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
        }
        return false;
    }

    private static boolean hasLombokLoggerAnnotation(PsiModifierListOwner owner) {
        if (owner.getModifierList() == null) {
            return false;
        }
        for (PsiAnnotation annotation : owner.getModifierList().getAnnotations()) {
            String fqn = annotation.getQualifiedName();
            if (fqn == null) {
                continue;
            }
            if ("lombok.extern.slf4j.Slf4j".equals(fqn)
                    || "lombok.extern.slf4j.XSlf4j".equals(fqn)
                    || fqn.equals("Slf4j") || fqn.equals("XSlf4j")) {
                return true;
            }
        }
        return false;
    }

    private static final class RemoveLogStatementFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Remove redundant log statement";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element instanceof PsiStatement statement) {
                statement.delete();
            }
        }
    }
}
