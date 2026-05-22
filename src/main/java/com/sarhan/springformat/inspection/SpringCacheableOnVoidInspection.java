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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * Detects {@code @Cacheable} or {@code @CachePut} annotations on {@code void}
 * methods. These annotations store the method's return value in a cache; when
 * the return type is {@code void} there is nothing to cache, so the annotation
 * is silently ignored at runtime.
 *
 * @author Spring Buddy
 * @author Akmal Sarhan
 */
public class SpringCacheableOnVoidInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiModifierList modifiers = method.getModifierList();
                if (modifiers == null) {
                    return;
                }
                if (method.getReturnType() != null && !"void".equals(method.getReturnType().getCanonicalText())) {
                    return;
                }
                for (PsiAnnotation annotation : modifiers.getAnnotations()) {
                    if (isCacheableOrCachePutAnnotation(annotation)) {
                        PsiIdentifier nameIdentifier = method.getNameIdentifier();
                        holder.registerProblem(
                                (nameIdentifier != null) ? nameIdentifier : method,
                                "@" + shortName(annotation) + " on a void method caches nothing — the annotation is silently ignored",
                                ProblemHighlightType.ERROR,
                                new RemoveCacheAnnotationFix(annotation.getText()));
                    }
                }
            }
        };
    }

    private static boolean isCacheableOrCachePutAnnotation(@NotNull PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return "org.springframework.cache.annotation.Cacheable".equals(name)
                || "org.springframework.cache.annotation.CachePut".equals(name)
                || "Cacheable".equals(name)
                || "CachePut".equals(name);
    }

    private static String shortName(@NotNull PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf('.') + 1);
        }
        if (annotation.getNameReferenceElement() != null) {
            return annotation.getNameReferenceElement().getText();
        }
        return "Cacheable";
    }

    private static class RemoveCacheAnnotationFix implements LocalQuickFix {
        private final String annotationText;

        RemoveCacheAnnotationFix(@NotNull String annotationText) {
            this.annotationText = annotationText;
        }

        @Override
        @NotNull
        public String getName() {
            return "Remove " + this.annotationText;
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethod method = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                    descriptor.getPsiElement(), PsiMethod.class);
            if (method == null) {
                return;
            }
            PsiModifierList modifiers = method.getModifierList();
            if (modifiers == null) {
                return;
            }
            for (PsiAnnotation a : modifiers.getAnnotations()) {
                if (isCacheableOrCachePutAnnotation(a)) {
                    a.delete();
                }
            }
        }
    }
}
