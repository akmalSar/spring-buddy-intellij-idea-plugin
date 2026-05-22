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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * Detects {@code equals}, {@code hashCode}, and {@code toString} methods that are not
 * ordered according to spring-javaformat conventions (equals, hashCode, toString).
 *
 * @author Akmal Sarhan
 */
public class SpringMethodOrderInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final Map<String, Integer> ORDER = Map.of("equals", 0, "hashCode", 1, "toString", 2);

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                if (hasLombokGeneratedOrderedMethod(psiClass)) {
                    return;
                }
                List<PsiMethod> methods = orderedMethods(psiClass);
                List<PsiMethod> expected = sorted(methods);
                for (int i = 0; i < methods.size(); i++) {
                    PsiMethod method = methods.get(i);
                    if (method != expected.get(i)) {
                        if (!canRegisterProblemOn(method)) {
                            continue;
                        }
                        holder.registerProblem(method,
                                "Method '" + method.getName() + "' is not ordered according to spring-javaformat conventions",
                                ProblemHighlightType.WARNING, new ReorderMethodsFix());
                    }
                }
            }
        };
    }

    private static List<PsiMethod> orderedMethods(PsiClass psiClass) {
        List<PsiMethod> methods = new ArrayList<>();
        for (PsiMethod method : psiClass.getMethods()) {
            if (isOrderedMethod(method)) {
                methods.add(method);
            }
        }
        return methods;
    }

    private static boolean isOrderedMethod(PsiMethod method) {
        int parameterCount = method.getParameterList().getParametersCount();
        return ("equals".equals(method.getName()) && parameterCount == 1)
                || ("hashCode".equals(method.getName()) && parameterCount == 0)
                || ("toString".equals(method.getName()) && parameterCount == 0);
    }

    private static boolean hasLombokGeneratedOrderedMethod(PsiClass psiClass) {
        return hasAnnotation(psiClass, "Data") || hasAnnotation(psiClass, "EqualsAndHashCode")
                || hasAnnotation(psiClass, "ToString");
    }

    private static boolean hasAnnotation(PsiClass psiClass, String shortName) {
        PsiModifierList modifiers = psiClass.getModifierList();
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

    private static boolean canRegisterProblemOn(PsiElement element) {
        return element.isPhysical() && !element.getTextRange().isEmpty();
    }

    private static List<PsiMethod> sorted(List<PsiMethod> methods) {
        List<PsiMethod> sorted = new ArrayList<>(methods);
        sorted.sort(Comparator.comparingInt((PsiMethod method) -> ORDER.get(method.getName())));
        return sorted;
    }

    private static final class ReorderMethodsFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Reorder equals, hashCode, and toString methods";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiClass psiClass = com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
            if (psiClass == null) {
                return;
            }
            List<PsiMethod> methods = orderedMethods(psiClass).stream()
                    .filter(SpringMethodOrderInspection::canRegisterProblemOn)
                    .toList();
            List<PsiMethod> expected = sorted(methods);
            if (methods.equals(expected) || methods.isEmpty()) {
                return;
            }
            PsiElement anchor = methods.get(0);
            List<PsiMethod> copies = expected.stream().map((PsiMethod method) -> (PsiMethod) method.copy()).toList();
            PsiElement current = null;
            for (PsiMethod copy : copies) {
                current = (current != null) ? psiClass.addAfter(copy, current) : psiClass.addBefore(copy, anchor);
            }
            for (PsiMethod method : methods) {
                method.delete();
            }
        }
    }
}
