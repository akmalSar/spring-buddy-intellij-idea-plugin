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
import org.jetbrains.annotations.NotNull;

/**
 * Detects @Transactional self-invocation through indirection within the same class.
 * When a non-transactional method calls another method that eventually calls a
 * @Transactional method of the same class, the Spring proxy is bypassed at every step.
 * @author Akmal Sarhan
 */
public class SpringTransactionalSelfInvocationIndirectionInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final int MAX_DEPTH = 3;

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                if (hasTransactionalAnnotation(method)) {
                    return;
                }
                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null || method.getBody() == null) {
                    return;
                }
                Set<PsiMethod> visited = new HashSet<>();
                IndirectionResult result = findTransactionalThroughIndirection(method, containingClass, visited, 0);
                if (result != null && result.depth() >= 2) {
                    PsiElement element = (method.getNameIdentifier() != null) ? method.getNameIdentifier() : method;
                    holder.registerProblem(element,
                            "Self-invocation bypasses @Transactional proxy through " + result.depth()
                                    + " method calls; the transaction on '" + result.target().getName() + "' is ignored",
                            ProblemHighlightType.ERROR,
                            new com.sarhan.render.flow.selfinvocation.ShowSelfInvocationFlowFix());
                }
            }
        };
    }

    private static IndirectionResult findTransactionalThroughIndirection(PsiMethod current, PsiClass containingClass,
                                                                          Set<PsiMethod> visited, int recursionDepth) {
        if (!visited.add(current) || recursionDepth >= MAX_DEPTH || current.getBody() == null) {
            return null;
        }
        PsiMethod[] target = {null};
        int[] minHops = {Integer.MAX_VALUE};
        current.getBody().accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                if (minHops[0] == 1) {
                    return; // Can't get better than 1 hop
                }
                PsiMethod called = expression.resolveMethod();
                if (called == null || called.getContainingClass() != containingClass) {
                    return;
                }
                PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
                if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
                    return;
                }
                if (hasTransactionalAnnotation(called)) {
                    target[0] = called;
                    minHops[0] = 1;
                    return;
                }
                IndirectionResult deeper = findTransactionalThroughIndirection(called, containingClass, visited, recursionDepth + 1);
                if (deeper != null && deeper.depth() + 1 < minHops[0]) {
                    target[0] = deeper.target();
                    minHops[0] = deeper.depth() + 1;
                }
            }
        });
        if (target[0] != null) {
            return new IndirectionResult(target[0], minHops[0]);
        }
        return null;
    }

    private static boolean hasTransactionalAnnotation(PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers != null && hasTransactionalOnModifiers(modifiers)) {
            return true;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        PsiModifierList classModifiers = containingClass.getModifierList();
        return classModifiers != null && hasTransactionalOnModifiers(classModifiers);
    }

    private static boolean hasTransactionalOnModifiers(PsiModifierList modifiers) {
        return SpringInspectionUtil.findAnyAnnotation(modifiers,
                "org.springframework.transaction.annotation.Transactional",
                "jakarta.transaction.Transactional",
                "javax.transaction.Transactional") != null;
    }

    private record IndirectionResult(PsiMethod target, int depth) {
    }
}
