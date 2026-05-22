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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * Detects {@code @Retryable} annotations on methods that Spring cannot proxy:
 * {@code private}, {@code static}, or {@code final} methods, as well as methods
 * declared inside a {@code final} class.
 * <p>
 * Spring Retry creates CGLIB proxies around Spring beans, just like
 * {@code @Transactional}. Private methods are invisible to the proxy, static
 * methods have no {@code this} reference to wrap, and final methods/classes
 * cannot be subclassed. In all cases the retry interceptor is silently bypassed
 * and the method executes without any retry logic.
 *
 * @author Spring Buddy
 * @author Akmal Sarhan
 */
public class SpringRetryableProxyPitfallInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiAnnotation retryable = findRetryableAnnotation(method);
                if (retryable == null) {
                    return;
                }
                PsiModifierList modifiers = method.getModifierList();
                PsiIdentifier nameIdentifier = method.getNameIdentifier();
                PsiElement target = (nameIdentifier != null) ? nameIdentifier : method;

                if (modifiers.hasModifierProperty(PsiModifier.PRIVATE)) {
                    holder.registerProblem(target,
                            "@Retryable private methods are not proxied by Spring; make the method public",
                            ProblemHighlightType.ERROR, new MakeMethodPublicFix());
                } else if (modifiers.hasModifierProperty(PsiModifier.STATIC)) {
                    holder.registerProblem(target,
                            "@Retryable static methods are not proxied by Spring",
                            ProblemHighlightType.ERROR, new RemoveModifierFix(PsiModifier.STATIC));
                } else if (modifiers.hasModifierProperty(PsiModifier.FINAL)) {
                    holder.registerProblem(target,
                            "@Retryable final methods cannot be subclass-proxied by Spring",
                            ProblemHighlightType.ERROR, new RemoveModifierFix(PsiModifier.FINAL));
                } else {
                    PsiClass containingClass = method.getContainingClass();
                    if (containingClass != null && containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                        holder.registerProblem(target,
                                "@Retryable methods in final classes cannot be subclass-proxied by Spring",
                                ProblemHighlightType.ERROR);
                    }
                }
            }
        };
    }

    private static PsiAnnotation findRetryableAnnotation(@NotNull PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (isRetryableAnnotation(annotation)) {
                return annotation;
            }
        }
        return null;
    }

    private static boolean isRetryableAnnotation(@NotNull PsiAnnotation annotation) {
        return SpringInspectionUtil.isAnnotation(annotation, "org.springframework.retry.annotation.Retryable", "Retryable");
    }

    private static final class MakeMethodPublicFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Make method public";
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
            modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
            modifiers.setModifierProperty(PsiModifier.PUBLIC, true);
        }
    }

    private static class RemoveModifierFix implements LocalQuickFix {
        private final String modifier;

        RemoveModifierFix(@NotNull String modifier) {
            this.modifier = modifier;
        }

        @Override
        @NotNull
        public String getName() {
            return "Remove " + this.modifier + " modifier";
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
            method.getModifierList().setModifierProperty(this.modifier, false);
        }
    }
}
