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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

/**
 * Detects Spring Data JPA query methods annotated with {@code @Modifying} that are
 * not also annotated with {@code @Transactional}. Modifying queries require an active
 * transaction at runtime; without one they throw an exception.
 *
 * @author Akmal Sarhan
 */
public class SpringJpaModifyingWithoutTransactionalInspection extends AbstractBaseJavaLocalInspectionTool {

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
                PsiModifierList modifiers = method.getModifierList();
                PsiAnnotation modifying = findAnnotation(modifiers, "Modifying");
                if (modifying == null || hasTransactional(method)) {
                    return;
                }
                holder.registerProblem(modifying,
                        "@Modifying query requires an active transaction; add @Transactional",
                        ProblemHighlightType.ERROR,
                        new AddTransactionalFix());
            }
        };
    }

    private static boolean hasTransactional(PsiMethod method) {
        if (findAnnotation(method.getModifierList(), "Transactional") != null) {
            return true;
        }
        return method.getContainingClass() != null
                && findAnnotation(method.getContainingClass().getModifierList(), "Transactional") != null;
    }

    private static PsiAnnotation findAnnotation(PsiModifierList modifiers, String shortName) {
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if (shortName.equals(name) || (name != null && name.endsWith("." + shortName))) {
                return annotation;
            }
        }
        return null;
    }

    private static final class AddTransactionalFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Add @Transactional";
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
            if (method == null || method.getModifierList() == null || findAnnotation(method.getModifierList(), "Transactional") != null) {
                return;
            }
            PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText("@Transactional", method);
            method.getModifierList().addBefore(annotation, method.getModifierList().getFirstChild());
            if (method.getContainingFile() instanceof PsiJavaFile javaFile) {
                SpringImportUtil.ensureImport(project, javaFile, "org.springframework.transaction.annotation.Transactional");
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(javaFile);
            }
        }
    }
}
