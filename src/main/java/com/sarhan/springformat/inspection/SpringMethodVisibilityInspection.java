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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * Detects methods with unnecessarily broad visibility in non-public or final classes.
 * <p>
 * Public methods in package-private or private classes, and protected methods in final classes,
 * cannot be accessed from outside the package or subclassed, making the wider visibility misleading.
 * Package-private is sufficient and preferred in these cases.
 *
 * @author Akmal Sarhan
 */
public class SpringMethodVisibilityInspection extends AbstractBaseJavaLocalInspectionTool {

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
                if (method.isConstructor() || hasOverrideAnnotation(method) || isInterfaceMethod(method)
                        || implementsSuperMethod(method)) {
                    return;
                }
                if (modifiers.hasModifierProperty(PsiModifier.PUBLIC) && isInPackagePrivateOrPrivateClass(method)) {
                    register(method, holder, "Public method should be package-private in a non-public class",
                            new MakePackagePrivateFix());
                } else if (modifiers.hasModifierProperty(PsiModifier.PROTECTED) && isInFinalClass(method)) {
                    register(method, holder, "Protected method should be package-private in a final class",
                            new MakePackagePrivateFix());
                }
            }
        };
    }

    private static boolean hasOverrideAnnotation(PsiMethod method) {
        return method.hasAnnotation("java.lang.Override") || method.hasAnnotation("Override");
    }

    private static boolean isInterfaceMethod(PsiMethod method) {
        PsiClass psiClass = method.getContainingClass();
        return psiClass != null && psiClass.isInterface();
    }

    private static boolean implementsSuperMethod(PsiMethod method) {
        return method.findSuperMethods().length > 0;
    }

    private static boolean isInPackagePrivateOrPrivateClass(PsiMethod method) {
        PsiClass psiClass = method.getContainingClass();
        if (psiClass == null || isPublicOrProtected(psiClass)) {
            return false;
        }
        PsiClass containing = psiClass.getContainingClass();
        return containing == null || !containing.isInterface() || !isPublicOrProtected(containing);
    }

    private static boolean isInFinalClass(PsiMethod method) {
        PsiClass psiClass = method.getContainingClass();
        return psiClass != null && psiClass.hasModifierProperty(PsiModifier.FINAL);
    }

    private static boolean isPublicOrProtected(PsiClass psiClass) {
        return psiClass.hasModifierProperty(PsiModifier.PUBLIC) || psiClass.hasModifierProperty(PsiModifier.PROTECTED);
    }

    private static void register(PsiMethod method, ProblemsHolder holder, String message, LocalQuickFix fix) {
        PsiIdentifier nameIdentifier = method.getNameIdentifier();
        holder.registerProblem((nameIdentifier != null) ? nameIdentifier : method, message, ProblemHighlightType.WARNING, fix);
    }

    private static final class MakePackagePrivateFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Make method package-private";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethod method = descriptor.getPsiElement() instanceof PsiMethod m ? m
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
            if (method == null) {
                return;
            }
            PsiModifierList modifiers = method.getModifierList();
            modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
            modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
            modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
        }
    }
}
