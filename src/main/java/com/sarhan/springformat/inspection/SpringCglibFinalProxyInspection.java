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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects final Spring bean classes and methods that cannot be subclass-proxied by CGLIB,
 * which prevents Spring proxy-bound annotations such as {@code @Transactional} from working.
 *
 * @author Akmal Sarhan
 */
public class SpringCglibFinalProxyInspection extends AbstractBaseJavaLocalInspectionTool {

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
                if (isSpringBean(psiClass) && psiClass.hasModifierProperty(PsiModifier.FINAL)) {
                    PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
                    holder.registerProblem((nameIdentifier != null) ? nameIdentifier : psiClass,
                            "Final Spring bean class cannot be subclass-proxied by CGLIB",
                            ProblemHighlightType.ERROR,
                            new RemoveFinalFromClassFix());
                }
            }

            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                if (method.isConstructor() || !method.hasModifierProperty(PsiModifier.FINAL)) {
                    return;
                }
                PsiClass psiClass = method.getContainingClass();
                if (isSpringBean(psiClass) && isProxyRelevantMethod(method)) {
                    PsiIdentifier nameIdentifier = method.getNameIdentifier();
                    holder.registerProblem((nameIdentifier != null) ? nameIdentifier : method,
                            "Final Spring bean method cannot be subclass-proxied by CGLIB",
                            ProblemHighlightType.ERROR,
                            new RemoveFinalFromMethodFix());
                }
            }
        };
    }

    private static boolean isSpringBean(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        PsiModifierList modifiers = psiClass.getModifierList();
        return hasAnnotation(modifiers, "org.springframework.stereotype.Component", "Component")
                || hasAnnotation(modifiers, "org.springframework.stereotype.Service", "Service")
                || hasAnnotation(modifiers, "org.springframework.stereotype.Repository", "Repository")
                || hasAnnotation(modifiers, "org.springframework.stereotype.Controller", "Controller")
                || hasAnnotation(modifiers, "org.springframework.web.bind.annotation.RestController", "RestController")
                || hasAnnotation(modifiers, "org.springframework.context.annotation.Configuration", "Configuration");
    }

    private static boolean isProxyRelevantMethod(PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        return hasAnnotation(modifiers, "org.springframework.transaction.annotation.Transactional", "Transactional")
                || hasAnnotation(modifiers, "org.springframework.scheduling.annotation.Async", "Async")
                || hasAnnotation(modifiers, "org.springframework.cache.annotation.Cacheable", "Cacheable")
                || hasAnnotation(modifiers, "org.springframework.cache.annotation.CachePut", "CachePut")
                || hasAnnotation(modifiers, "org.springframework.cache.annotation.CacheEvict", "CacheEvict")
                || hasAnnotation(modifiers, "org.springframework.security.access.prepost.PreAuthorize", "PreAuthorize")
                || hasAnnotation(modifiers, "org.springframework.security.access.prepost.PostAuthorize", "PostAuthorize")
                || hasAnnotation(modifiers, "org.springframework.security.access.annotation.Secured", "Secured")
                || hasAnnotation(modifiers, "javax.annotation.security.RolesAllowed", "RolesAllowed")
                || hasAnnotation(modifiers, "jakarta.annotation.security.RolesAllowed", "RolesAllowed");
    }

    private static boolean hasAnnotation(PsiModifierList modifiers, String fqn, String shortName) {
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if (fqn.equals(name) || shortName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static final class RemoveFinalFromClassFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Remove final modifier";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiClass psiClass = descriptor.getPsiElement() instanceof PsiClass c ? c
                    : PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
            if (psiClass != null && psiClass.getModifierList() != null) {
                psiClass.getModifierList().setModifierProperty(PsiModifier.FINAL, false);
            }
        }
    }

    private static final class RemoveFinalFromMethodFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Remove final modifier";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethod method = descriptor.getPsiElement() instanceof PsiMethod m ? m
                    : PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
            if (method != null) {
                method.getModifierList().setModifierProperty(PsiModifier.FINAL, false);
            }
        }
    }
}
