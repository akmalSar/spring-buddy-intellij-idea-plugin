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
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiResourceVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects redundant modifiers that the Java compiler or language semantics already imply.
 * <p>
 * Examples include {@code public} / {@code static} / {@code final} on interface fields,
 * {@code public} / {@code abstract} on interface methods, {@code final} on methods in a
 * {@code final} class or private methods, and {@code static} on nested enum types.
 * Removing them keeps the declaration concise.
 *
 * @author Akmal Sarhan
 */
public class SpringRedundantModifierInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                checkType(psiClass, holder);
            }

            @Override
            public void visitField(@NotNull PsiField field) {
                PsiClass containingClass = field.getContainingClass();
                if (containingClass != null && (containingClass.isInterface() || containingClass.isAnnotationType())) {
                    register(field, holder, PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL);
                }
            }

            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null) {
                    return;
                }
                if (method.isConstructor()) {
                    checkConstructor(method, containingClass, holder);
                    return;
                }
                if (containingClass.isInterface() || containingClass.isAnnotationType()) {
                    register(method, holder, PsiModifier.PUBLIC, PsiModifier.ABSTRACT);
                }
                if (method.hasModifierProperty(PsiModifier.FINAL)
                        && (method.hasModifierProperty(PsiModifier.PRIVATE)
                        || containingClass.hasModifierProperty(PsiModifier.FINAL))) {
                    register(method, holder, PsiModifier.FINAL);
                }
            }

            @Override
            public void visitParameter(@NotNull PsiParameter parameter) {
                if ("_".equals(parameter.getName())) {
                    register(parameter, holder, PsiModifier.FINAL);
                }
            }

            @Override
            public void visitResourceVariable(@NotNull PsiResourceVariable variable) {
                register(variable, holder, PsiModifier.FINAL);
            }
        };
    }

    private static void checkType(PsiClass psiClass, ProblemsHolder holder) {
        PsiClass containingClass = psiClass.getContainingClass();
        if (psiClass.isInterface() || psiClass.isAnnotationType()) {
            register(psiClass, holder, PsiModifier.ABSTRACT, PsiModifier.STATIC);
        }
        if (containingClass != null && (containingClass.isInterface() || containingClass.isAnnotationType())) {
            register(psiClass, holder, PsiModifier.PUBLIC, PsiModifier.STATIC);
        }
        if (psiClass.isEnum() && containingClass != null) {
            register(psiClass, holder, PsiModifier.STATIC);
        }
        if (psiClass.isRecord()) {
            register(psiClass, holder, PsiModifier.FINAL);
            if (containingClass != null) {
                register(psiClass, holder, PsiModifier.STATIC);
            }
        }
    }

    private static void checkConstructor(PsiMethod constructor, PsiClass containingClass, ProblemsHolder holder) {
        if (containingClass.isEnum()) {
            register(constructor, holder, PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PRIVATE);
            return;
        }
        if (constructor.hasModifierProperty(PsiModifier.PUBLIC)
                && !isPublicOrProtectedClass(containingClass)) {
            register(constructor, holder, PsiModifier.PUBLIC);
        }
    }

    private static boolean isPublicOrProtectedClass(PsiClass psiClass) {
        if (psiClass.hasModifierProperty(PsiModifier.PROTECTED)) {
            return true;
        }
        if (!psiClass.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        PsiElement parent = psiClass.getParent();
        if (parent instanceof PsiClassOwner) {
            return true;
        }
        PsiClass containingClass = psiClass.getContainingClass();
        return containingClass != null && isPublicOrProtectedClass(containingClass);
    }

    private static void register(PsiModifierListOwner owner, ProblemsHolder holder, String... modifiers) {
        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList == null) {
            return;
        }
        for (String modifier : modifiers) {
            PsiElement element = findModifierElement(modifierList, modifier);
            if (element != null) {
                holder.registerProblem(element,
                        "Modifier '" + modifier + "' is redundant",
                        ProblemHighlightType.WARNING,
                        new RemoveRedundantModifierFix(modifier));
            }
        }
    }

    @Nullable
    private static PsiElement findModifierElement(PsiModifierList modifierList, String modifier) {
        for (PsiElement child : modifierList.getChildren()) {
            if (child instanceof PsiKeyword && modifier.equals(child.getText())) {
                return child;
            }
        }
        return null;
    }

    private static class RemoveRedundantModifierFix implements LocalQuickFix {

        private final String modifier;

        RemoveRedundantModifierFix(String modifier) {
            this.modifier = modifier;
        }

        @Override
        @NotNull
        public String getName() {
            return "Remove redundant '" + this.modifier + "' modifier";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return "Remove redundant modifier";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiModifierList modifierList = element.getParent() instanceof PsiModifierList list ? list : null;
            if (modifierList != null) {
                modifierList.setModifierProperty(this.modifier, false);
            }
        }
    }
}
