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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiResourceList;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects Spring Data repository {@code Stream} calls that are not wrapped in
 * try-with-resources or explicitly closed, which can leak database connections.
 *
 * @author Akmal Sarhan
 */
public class SpringTransactionalStreamResourceLeakInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                if (method.getBody() == null) {
                    return;
                }
                method.getBody().accept(new JavaRecursiveElementVisitor() {
                    @Override
                    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                        super.visitMethodCallExpression(expression);
                        PsiMethod target = expression.resolveMethod();
                        if (target == null) {
                            return;
                        }
                        PsiType returnType = target.getReturnType();
                        if (returnType == null) {
                            return;
                        }
                        String typeText = returnType.getCanonicalText();
                        if (!typeText.startsWith("java.util.stream.Stream")) {
                            return;
                        }
                        PsiClass containingClass = target.getContainingClass();
                        if (containingClass == null || !SpringInspectionUtil.isSpringDataRepository(containingClass)) {
                            return;
                        }
                        // Skip if the call is already the initializer of a try-with-resources resource variable
                        if (PsiTreeUtil.getParentOfType(expression, PsiResourceList.class) != null) {
                            return;
                        }
                        boolean safeToFix = isSafeForTryWithResourcesFix(expression);
                        if (safeToFix) {
                            holder.registerProblem(expression,
                                    "Repository Stream should be used with try-with-resources or explicitly closed to prevent database connection leaks",
                                    ProblemHighlightType.ERROR,
                                    new WrapInTryWithResourcesFix());
                        } else {
                            holder.registerProblem(expression,
                                    "Repository Stream should be used with try-with-resources or explicitly closed to prevent database connection leaks",
                                    ProblemHighlightType.ERROR);
                        }
                    }
                });
            }
        };
    }

    /**
     * The "Wrap in try-with-resources" quick fix is only safe when the stream
     * call is used directly inside an expression statement (e.g. a method chain
     * or a call passed to another method). It is NOT safe for declarations,
     * assignments, or return statements because the stream variable would be
     * out of scope or closed before the caller can use it.
     */
    private static boolean isSafeForTryWithResourcesFix(PsiMethodCallExpression streamCall) {
        PsiStatement statement = PsiTreeUtil.getParentOfType(streamCall, PsiStatement.class);
        if (!(statement instanceof com.intellij.psi.PsiExpressionStatement exprStmt)) {
            return false;
        }
        com.intellij.psi.PsiExpression expr = exprStmt.getExpression();
        // Avoid assignments:  s = repo.findAllStream();
        return !(expr instanceof com.intellij.psi.PsiAssignmentExpression);
    }

    private static final class WrapInTryWithResourcesFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Wrap Stream in try-with-resources";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiMethodCallExpression streamCall)) {
                return;
            }

            PsiType streamType = streamCall.getType();
            if (streamType == null) {
                return;
            }

            PsiStatement statement = PsiTreeUtil.getParentOfType(streamCall, PsiStatement.class);
            if (statement == null) {
                return;
            }

            String varName = "stream";
            String typeText = streamType.getCanonicalText();
            String callText = streamCall.getText();

            // Replace the stream call with a variable reference in the original tree
            PsiExpression varRef = JavaPsiFacade.getElementFactory(project)
                    .createExpressionFromText(varName, streamCall);
            streamCall.replace(varRef);

            // Capture the modified statement text
            String bodyText = statement.getText();

            // Build try-with-resources and replace the original statement
            String tryText = "try (" + typeText + " " + varName + " = " + callText + ") {\n"
                    + bodyText + "\n}";
            PsiTryStatement tryStatement = (PsiTryStatement) JavaPsiFacade.getElementFactory(project)
                    .createStatementFromText(tryText, statement);
            statement.replace(tryStatement);
        }
    }
}
