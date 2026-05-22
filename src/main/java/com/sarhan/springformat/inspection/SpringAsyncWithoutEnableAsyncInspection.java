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
import java.util.Collection;
import java.util.List;

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
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Reports {@code @Async} usage when no {@code @EnableAsync} configuration exists.
 *
 * @author Akmal Sarhan
 */
public class SpringAsyncWithoutEnableAsyncInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile()) || hasAnnotationInProject(holder.getProject(), "EnableAsync")) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        PsiClass fixTarget = singleConfigurationCandidate(holder.getProject());
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiAnnotation async = findAnnotation(method.getModifierList(), "Async");
                if (async != null) {
                    register(async, holder, fixTarget);
                }
            }

            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                PsiAnnotation async = findAnnotation(psiClass.getModifierList(), "Async");
                if (async != null) {
                    register(async, holder, fixTarget);
                }
            }
        };
    }

    private static void register(PsiAnnotation annotation, ProblemsHolder holder, PsiClass fixTarget) {
        LocalQuickFix fix = (fixTarget != null) ? new AddEnableAsyncFix(fixTarget) : null;
        if (fix != null) {
            holder.registerProblem(annotation,
                    "@Async is ignored because no @EnableAsync configuration was found in the project",
                    ProblemHighlightType.ERROR, fix);
        }
        else {
            holder.registerProblem(annotation,
                    "@Async is ignored because no @EnableAsync configuration was found in the project",
                    ProblemHighlightType.ERROR);
        }
    }

    private static boolean hasAnnotationInProject(Project project, String shortName) {
        for (PsiClass psiClass : allProjectClasses(project)) {
            if (findAnnotation(psiClass.getModifierList(), shortName) != null) {
                return true;
            }
        }
        return false;
    }

    private static PsiClass singleConfigurationCandidate(Project project) {
        List<PsiClass> candidates = new ArrayList<>();
        for (PsiClass psiClass : allProjectClasses(project)) {
            PsiModifierList modifiers = psiClass.getModifierList();
            if (findAnnotation(modifiers, "SpringBootApplication") != null
                    || findAnnotation(modifiers, "Configuration") != null) {
                candidates.add(psiClass);
            }
        }
        return (candidates.size() == 1) ? candidates.get(0) : null;
    }

    private static List<PsiClass> allProjectClasses(Project project) {
        List<PsiClass> classes = new ArrayList<>();
        Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, "java",
                GlobalSearchScope.projectScope(project));
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile file : files) {
            if (psiManager.findFile(file) instanceof PsiJavaFile javaFile) {
                for (PsiClass psiClass : javaFile.getClasses()) {
                    classes.add(psiClass);
                }
            }
        }
        return classes;
    }

    private static PsiAnnotation findAnnotation(PsiModifierList modifiers, String shortName) {
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (isAnnotation(annotation, shortName)) {
                return annotation;
            }
        }
        return null;
    }

    private static boolean isAnnotation(PsiAnnotation annotation, String shortName) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return shortName.equals(name) || (name != null && name.endsWith("." + shortName));
    }

    private static final class AddEnableAsyncFix implements LocalQuickFix {

        private final com.intellij.psi.SmartPsiElementPointer<PsiClass> targetPointer;

        private AddEnableAsyncFix(PsiClass target) {
            this.targetPointer = com.intellij.psi.SmartPointerManager.createPointer(target);
        }

        @Override
        @NotNull
        public String getName() {
            return "Add @EnableAsync to configuration class";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiClass target = this.targetPointer.getElement();
            PsiModifierList modifiers = (target != null) ? target.getModifierList() : null;
            if (modifiers == null || findAnnotation(modifiers, "EnableAsync") != null) {
                return;
            }
            PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText("@org.springframework.scheduling.annotation.EnableAsync", target);
            modifiers.addBefore(annotation, modifiers.getFirstChild());
        }
    }
}
