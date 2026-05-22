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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Detects calls to repository findAll() or methods returning Stream<Entity>/List<Entity>
 * without a Pageable parameter. On large tables this can exhaust heap memory.
 *
 * @author Akmal Sarhan
 */
public class SpringRepositoryFindAllWithoutPageableInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                PsiMethod target = expression.resolveMethod();
                if (target == null) {
                    return;
                }
                PsiClass containingClass = target.getContainingClass();
                if (containingClass == null) {
                    return;
                }
                if (!SpringInspectionUtil.isSpringDataRepository(containingClass)) {
                    return;
                }
                String methodName = target.getName();
                if (methodName == null) {
                    return;
                }
                // Check for findAll() overload without Pageable
                if ("findAll".equals(methodName)) {
                    PsiExpressionList args = expression.getArgumentList();
                    if (args != null && args.getExpressionCount() == 0) {
                        holder.registerProblem(
                                expression.getMethodExpression().getReferenceNameElement(),
                                "findAll() without Pageable can exhaust heap on large tables — use findAll(Pageable) or Stream with pagination",
                                ProblemHighlightType.WARNING
                        );
                    }
                }
            }
        };
    }
}
