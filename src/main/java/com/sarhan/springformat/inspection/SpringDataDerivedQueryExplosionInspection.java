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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * Detects overly complex Spring Data derived query method names in repositories.
 * Methods with many conditions or mixed AND/OR logic are hard to read and can
 * generate inefficient or incorrect queries; explicit {@code @Query} is preferred.
 *
 * @author Akmal Sarhan
 */
public class SpringDataDerivedQueryExplosionInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final java.util.Set<String> QUERY_PREFIXES = java.util.Set.of(
            "find", "read", "get", "query", "search", "stream", "count", "exists", "delete", "remove");

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
                if (!isRepositoryMethod(method) || hasAnnotation(method.getModifierList(), "Query")) {
                    return;
                }
                String name = method.getName();
                DerivedQuery query = derivedQuery(name);
                if (query == null) {
                    return;
                }
                if (query.conditionCount() > 2 || query.mixesAndOr()) {
                    PsiElement target = (method.getNameIdentifier() != null) ? method.getNameIdentifier() : method;
                    holder.registerProblem(target,
                            "Spring Data derived query is complex; use @Query to make operator precedence and joins explicit",
                            ProblemHighlightType.WARNING);
                }
            }
        };
    }

    private static DerivedQuery derivedQuery(String methodName) {
        int byIndex = methodName.indexOf("By");
        if (byIndex <= 0 || byIndex + 2 >= methodName.length()) {
            return null;
        }
        String prefix = methodName.substring(0, byIndex);
        boolean supportedPrefix = QUERY_PREFIXES.stream().anyMatch(prefix::startsWith);
        if (!supportedPrefix) {
            return null;
        }
        String criteria = methodName.substring(byIndex + 2);
        if (criteria.isBlank()) {
            return null;
        }
        int andCount = tokenCount(criteria, "And");
        int orCount = tokenCount(criteria, "Or");
        int conditionCount = andCount + orCount + 1;
        return new DerivedQuery(conditionCount, andCount > 0 && orCount > 0);
    }

    private static int tokenCount(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            int after = index + token.length();
            if (index > 0 && after < text.length()
                    && Character.isUpperCase(text.charAt(index))
                    && Character.isUpperCase(text.charAt(after))) {
                count++;
            }
            index = after;
        }
        return count;
    }

    private static boolean isRepositoryMethod(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        return containingClass != null && isRepositoryType(containingClass);
    }

    private static boolean isRepositoryType(PsiClass psiClass) {
        return SpringInspectionUtil.isSpringDataRepository(psiClass);
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

    private record DerivedQuery(int conditionCount, boolean mixesAndOr) {
    }
}
