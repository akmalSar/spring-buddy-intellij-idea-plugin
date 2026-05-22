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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Detects {@code @Retryable} methods in a project that has no {@code @EnableRetry}
 * configuration. Spring Retry requires an explicit {@code @EnableRetry} annotation
 * on a {@code @Configuration} class to activate its proxy infrastructure; without
 * it, {@code @Retryable} is silently ignored and no retry happens at runtime.
 *
 * @author Spring Buddy
 * @author Akmal Sarhan
 */
public class SpringRetryableWithoutEnableRetryInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        Project project = holder.getProject();
        if (hasEnableRetryInProject(project)) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiAnnotation retryable = findRetryableAnnotation(method);
                if (retryable == null) {
                    return;
                }
                PsiIdentifier nameIdentifier = method.getNameIdentifier();
                holder.registerProblem((nameIdentifier != null) ? nameIdentifier : method,
                        "@Retryable is ignored because no @EnableRetry configuration was found in the project",
                        ProblemHighlightType.ERROR, new AddEnableRetryToClassFix());
            }

            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                PsiModifierList modifiers = psiClass.getModifierList();
                if (modifiers == null) {
                    return;
                }
                for (PsiAnnotation annotation : modifiers.getAnnotations()) {
                    if (isRetryableAnnotation(annotation)) {
                        PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
                        holder.registerProblem((nameIdentifier != null) ? nameIdentifier : psiClass,
                                "@Retryable is ignored because no @EnableRetry configuration was found in the project",
                                ProblemHighlightType.ERROR);
                        break;
                    }
                }
            }
        };
    }

    private static boolean hasEnableRetryInProject(@NotNull Project project) {
        // Scan all Java files for @EnableRetry annotation.
        // In production this uses the FilenameIndex; in light tests the index
        // covers all configureByText files.
        java.util.Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, "java",
                GlobalSearchScope.projectScope(project));
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile vf : files) {
            PsiJavaFile javaFile = (PsiJavaFile) psiManager.findFile(vf);
            if (javaFile == null) {
                continue;
            }
            for (PsiClass cls : javaFile.getClasses()) {
                PsiModifierList mods = cls.getModifierList();
                if (mods == null) {
                    continue;
                }
                for (PsiAnnotation a : mods.getAnnotations()) {
                    if (isEnableRetryAnnotation(a)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return "org.springframework.retry.annotation.Retryable".equals(name)
                || "Retryable".equals(name);
    }

    private static boolean isEnableRetryAnnotation(@NotNull PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return "org.springframework.retry.annotation.EnableRetry".equals(name)
                || "EnableRetry".equals(name);
    }

    private static final class AddEnableRetryToClassFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Add @EnableRetry to the containing class";
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
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            PsiModifierList modifiers = containingClass.getModifierList();
            if (modifiers == null) {
                return;
            }
            PsiAnnotation enableRetry = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText("@org.springframework.retry.annotation.EnableRetry", containingClass);
            modifiers.addBefore(enableRetry, modifiers.getFirstChild());
        }
    }
}
