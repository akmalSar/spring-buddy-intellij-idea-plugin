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
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects {@code @Cacheable}, {@code @CachePut}, or {@code @CacheEvict}
 * annotations in a project that has no {@code @EnableCaching} configuration.
 * Spring Cache requires an explicit {@code @EnableCaching} annotation on a
 * {@code @Configuration} class to activate its proxy infrastructure; without
 * it, cache annotations are silently ignored and no caching happens at runtime.
 *
 * @author Spring Buddy
 * @author Akmal Sarhan
 */
public class SpringCacheableWithoutEnableCachingInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        Project project = holder.getProject();
        if (hasEnableCachingInProject(project)) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiAnnotation cacheAnn = findCacheAnnotation(method);
                if (cacheAnn == null) {
                    return;
                }
                PsiIdentifier nameIdentifier = method.getNameIdentifier();
                holder.registerProblem(
                        (nameIdentifier != null) ? nameIdentifier : method,
                        "@" + shortName(cacheAnn) + " is ignored because no @EnableCaching configuration was found in the project",
                        ProblemHighlightType.ERROR,
                        new AddEnableCachingToClassFix());
            }

            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                PsiModifierList modifiers = psiClass.getModifierList();
                if (modifiers == null) {
                    return;
                }
                for (PsiAnnotation annotation : modifiers.getAnnotations()) {
                    if (isCacheAnnotation(annotation)) {
                        PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
                        holder.registerProblem(
                                (nameIdentifier != null) ? nameIdentifier : psiClass,
                                "@" + shortName(annotation) + " is ignored because no @EnableCaching configuration was found in the project",
                                ProblemHighlightType.ERROR);
                        break;
                    }
                }
            }
        };
    }

    private static boolean hasEnableCachingInProject(@NotNull Project project) {
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
                    if (isEnableCachingAnnotation(a)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static PsiAnnotation findCacheAnnotation(@NotNull PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (isCacheAnnotation(annotation)) {
                return annotation;
            }
        }
        return null;
    }

    private static boolean isCacheAnnotation(@NotNull PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return "org.springframework.cache.annotation.Cacheable".equals(name)
                || "org.springframework.cache.annotation.CachePut".equals(name)
                || "org.springframework.cache.annotation.CacheEvict".equals(name)
                || "Cacheable".equals(name)
                || "CachePut".equals(name)
                || "CacheEvict".equals(name);
    }

    private static boolean isEnableCachingAnnotation(@NotNull PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return "org.springframework.cache.annotation.EnableCaching".equals(name)
                || "EnableCaching".equals(name);
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

    private static final class AddEnableCachingToClassFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Add @EnableCaching to the containing class";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethod method = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
            PsiClass containingClass = (method != null) ? method.getContainingClass() : null;
            if (containingClass == null) {
                containingClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
            }
            if (containingClass == null) {
                return;
            }
            PsiModifierList modifiers = containingClass.getModifierList();
            if (modifiers == null) {
                return;
            }
            PsiAnnotation enableCaching = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText("@org.springframework.cache.annotation.EnableCaching", containingClass);
            modifiers.addBefore(enableCaching, modifiers.getFirstChild());
        }
    }
}
