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
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiThisExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Detects {@code @Bean} methods calling other {@code @Bean} methods inside
 * classes that are not annotated with {@code @Configuration}.
 * In Spring's "lite mode" such inter-bean references create a new instance
 * on every call, bypassing the singleton proxy and breaking the container contract.
 *
 * @author Akmal Sarhan
 */
public class SpringConfigurationLiteModeInspection extends AbstractBaseJavaLocalInspectionTool {

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
                if (!hasBeanAnnotation(method)) {
                    return;
                }
                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null) {
                    return;
                }
                if (isConfiguration(containingClass)) {
                    return;
                }
                if (!isSpringStereotype(containingClass)) {
                    return;
                }
                PsiElement body = method.getBody();
                if (body == null) {
                    return;
                }
                body.accept(new JavaRecursiveElementVisitor() {
                    @Override
                    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                        super.visitMethodCallExpression(expression);
                        PsiMethod target = expression.resolveMethod();
                        PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
                        if (target != null && target.getContainingClass() == containingClass
                                && (qualifier == null || qualifier instanceof PsiThisExpression)
                                && hasBeanAnnotation(target)) {
                            holder.registerProblem(expression,
                                    "Calling @Bean method from another @Bean method in a non-@Configuration class uses 'lite mode' and may create a duplicate instance; annotate the class with @Configuration",
                                    ProblemHighlightType.WARNING,
                                    new AnnotateClassWithConfigurationFix());
                        }
                    }
                });
            }
        };
    }

    private static boolean hasBeanAnnotation(PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if ("Bean".equals(name) || "org.springframework.context.annotation.Bean".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConfiguration(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        PsiModifierList modifiers = psiClass.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if ("Configuration".equals(name) || "org.springframework.context.annotation.Configuration".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSpringStereotype(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        PsiModifierList modifiers = psiClass.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if ("Component".equals(name) || "org.springframework.stereotype.Component".equals(name)
                    || "Service".equals(name) || "org.springframework.stereotype.Service".equals(name)
                    || "Repository".equals(name) || "org.springframework.stereotype.Repository".equals(name)
                    || "Controller".equals(name) || "org.springframework.stereotype.Controller".equals(name)
                    || "RestController".equals(name) || "org.springframework.web.bind.annotation.RestController".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static final class AnnotateClassWithConfigurationFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Annotate class with @Configuration";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiClass psiClass = com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
            if (psiClass == null) {
                return;
            }
            PsiModifierList modifierList = psiClass.getModifierList();
            if (modifierList == null) {
                return;
            }
            for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                String name = annotation.getQualifiedName();
                if (name == null && annotation.getNameReferenceElement() != null) {
                    name = annotation.getNameReferenceElement().getText();
                }
                if ("Component".equals(name) || "org.springframework.stereotype.Component".equals(name)
                        || "Service".equals(name) || "org.springframework.stereotype.Service".equals(name)
                        || "Repository".equals(name) || "org.springframework.stereotype.Repository".equals(name)
                        || "Controller".equals(name) || "org.springframework.stereotype.Controller".equals(name)
                        || "RestController".equals(name) || "org.springframework.web.bind.annotation.RestController".equals(name)) {
                    annotation.delete();
                    break;
                }
            }
            modifierList.addAnnotation("Configuration");
            if (psiClass.getContainingFile() instanceof PsiJavaFile javaFile) {
                SpringImportUtil.ensureImport(project, javaFile, "org.springframework.context.annotation.Configuration");
            }
        }
    }
}
