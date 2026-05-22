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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects Spring Data projection interfaces that force full entity loading.
 * SpEL {@code @Value} expressions and methods returning entity-like types
 * defeat projection optimization and can reintroduce N+1 query problems.
 *
 * @author Akmal Sarhan
 */
public class SpringDataProjectionFullLoadInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                super.visitClass(psiClass);
                if (!psiClass.isInterface() || !isUsedAsRepositoryProjection(psiClass)) {
                    return;
                }
                for (PsiMethod method : psiClass.getMethods()) {
                    if (!isProjectionMethodUsed(method)) {
                        continue;
                    }
                    if (hasAnnotation(method.getModifierList(), "Value")) {
                        holder.registerProblem((method.getNameIdentifier() != null) ? method.getNameIdentifier() : method,
                                "SpEL in Spring Data projection can force full entity loading; prefer closed projections",
                                ProblemHighlightType.WARNING);
                        continue;
                    }
                    PsiType returnType = method.getReturnType();
                    if (returnsEntity(returnType)) {
                        holder.registerProblem((method.getNameIdentifier() != null) ? method.getNameIdentifier() : method,
                                "Projection method returns an entity-like type; this can defeat projection optimization and load full entities",
                                ProblemHighlightType.WARNING);
                    }
                }
            }
        };
    }

    private static boolean isUsedAsRepositoryProjection(PsiClass psiClass) {
        for (PsiReference reference : ReferencesSearch.search(psiClass, GlobalSearchScope.projectScope(psiClass.getProject())).findAll()) {
            PsiMethod method = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethod.class);
            if (method != null && SpringInspectionUtil.isSpringDataRepository(method.getContainingClass())) {
                PsiType returnType = method.getReturnType();
                if (returnType != null && containsType(returnType, psiClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isProjectionMethodUsed(PsiMethod projectionMethod) {
        PsiClass projection = projectionMethod.getContainingClass();
        if (projection == null) {
            return true;
        }
        String methodName = projectionMethod.getName();
        for (PsiReference reference : ReferencesSearch.search(projection,
                GlobalSearchScope.projectScope(projection.getProject())).findAll()) {
            PsiMethod repositoryMethod = PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethod.class);
            if (repositoryMethod == null || !SpringInspectionUtil.isSpringDataRepository(repositoryMethod.getContainingClass())) {
                continue;
            }
            for (PsiReference methodReference : ReferencesSearch.search(repositoryMethod,
                    GlobalSearchScope.projectScope(repositoryMethod.getProject())).findAll()) {
                PsiMethod caller = PsiTreeUtil.getParentOfType(methodReference.getElement(), PsiMethod.class);
                if (caller == null || caller.getBody() == null) {
                    continue;
                }
                boolean[] used = {false};
                caller.getBody().accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
                        super.visitReferenceExpression(expression);
                        PsiElement resolved = expression.resolve();
                        if (resolved instanceof PsiMethod method && methodName.equals(method.getName())
                                && method.getContainingClass() == projection) {
                            used[0] = true;
                        }
                    }
                });
                if (used[0]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsType(PsiType type, PsiClass psiClass) {
        if (type instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            if (resolved == psiClass) {
                return true;
            }
            for (PsiType parameter : classType.getParameters()) {
                if (containsType(parameter, psiClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean returnsEntity(PsiType type) {
        if (!(type instanceof PsiClassType classType)) {
            return false;
        }
        PsiClass resolved = classType.resolve();
        return resolved != null && SpringInspectionUtil.isJpaEntity(resolved);
    }

    private static boolean hasAnnotation(PsiModifierList modifiers, String shortName) {
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if (shortName.equals(name) || (name != null && name.endsWith("." + shortName))) {
                return true;
            }
        }
        return false;
    }
}
