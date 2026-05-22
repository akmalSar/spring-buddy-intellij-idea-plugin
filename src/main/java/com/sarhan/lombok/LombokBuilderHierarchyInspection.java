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

package com.sarhan.lombok;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */

public abstract class LombokBuilderHierarchyInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public String getDisplayName() {
        return MyMessageBundle.message("inspection.builder.hierarchy.display.name");
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
        return "Lombok builder";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!LombokInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                super.visitClass(aClass);
                inspect(aClass, holder);
            }
        };
    }

    protected abstract void inspect(PsiClass aClass, ProblemsHolder holder);

    protected static PsiAnnotation builderAnnotation(PsiClass aClass) {
        return LombokAnnotationUtil.findBuilderAnnotation(aClass);
    }

    protected static boolean isNonObjectClass(PsiClass psiClass) {
        return psiClass != null && !"java.lang.Object".equals(psiClass.getQualifiedName());
    }

    /**
     * Returns {@code true} when the class is part of the project's own source code
     * (not an external library or JDK class). External library classes cannot be
     * modified by the developer, so inspections that require adding annotations to
     * the parent should skip them to avoid false positives.
     */
    protected static boolean isProjectClass(PsiClass psiClass) {
        com.intellij.psi.PsiFile containingFile = psiClass.getContainingFile();
        if (containingFile == null) {
            return false;
        }
        com.intellij.openapi.vfs.VirtualFile vf = containingFile.getVirtualFile();
        if (vf == null) {
            return false;
        }
        com.intellij.openapi.project.Project project = psiClass.getProject();
        com.intellij.openapi.roots.ProjectFileIndex fileIndex =
                com.intellij.openapi.roots.ProjectFileIndex.getInstance(project);
        return fileIndex.isInContent(vf);
    }

    public static class BuilderOnSubclassWithBuilderParent extends LombokBuilderHierarchyInspection {
        @Override
        protected void inspect(PsiClass aClass, ProblemsHolder holder) {
            PsiAnnotation builderAnn = builderAnnotation(aClass);
            if (builderAnn == null || LombokAnnotationUtil.hasSuperBuilderAnnotation(aClass)) {
                return;
            }
            PsiClass firstParent = aClass.getSuperClass();
            if (isNonObjectClass(firstParent) && LombokAnnotationUtil.hasBuilderAnnotation(firstParent)) {
                holder.registerProblem(builderAnn,
                        MyMessageBundle.message("inspection.builder.002.problem.descriptor"),
                        ProblemHighlightType.ERROR);
            }
        }
    }

    public static class BuilderWithPartialConstructor extends LombokBuilderHierarchyInspection {
        @Override
        protected void inspect(PsiClass aClass, ProblemsHolder holder) {
            PsiAnnotation builderAnn = builderAnnotation(aClass);
            if (builderAnn == null) {
                return;
            }
            int fieldCount = LombokAnnotationUtil.countInstanceFields(aClass);
            if (fieldCount == 0 || LombokAnnotationUtil.hasAllArgsConstructor(aClass)) {
                return;
            }
            int maxExplicitParams = 0;
            boolean hasExplicitConstructor = false;
            for (PsiMethod ctor : aClass.getConstructors()) {
                hasExplicitConstructor = true;
                maxExplicitParams = Math.max(maxExplicitParams, ctor.getParameterList().getParametersCount());
            }
            if (hasExplicitConstructor && maxExplicitParams > 0 && maxExplicitParams < fieldCount) {
                holder.registerProblem(builderAnn,
                        MyMessageBundle.message("inspection.builder.003.problem.descriptor", maxExplicitParams, fieldCount),
                        ProblemHighlightType.WARNING);
            }
        }
    }

    public static class SuperBuilderMissingParent extends LombokBuilderHierarchyInspection {
        @Override
        protected void inspect(PsiClass aClass, ProblemsHolder holder) {
            PsiAnnotation builderAnn = builderAnnotation(aClass);
            if (builderAnn == null || !LombokAnnotationUtil.hasSuperBuilderAnnotation(aClass)) {
                return;
            }
            PsiClass firstParent = aClass.getSuperClass();
            if (isNonObjectClass(firstParent) && firstParent.isPhysical()
                    && isProjectClass(firstParent)
                    && !LombokAnnotationUtil.hasSuperBuilderAnnotation(firstParent)) {
                holder.registerProblem(builderAnn,
                        MyMessageBundle.message("inspection.superbuilder.001.problem.descriptor"),
                        ProblemHighlightType.ERROR);
            }
        }
    }

    public static class BuilderSubclassWithoutSuperBuilderParent extends LombokBuilderHierarchyInspection {
        @Override
        protected void inspect(PsiClass aClass, ProblemsHolder holder) {
            PsiAnnotation builderAnn = builderAnnotation(aClass);
            if (builderAnn == null || LombokAnnotationUtil.hasSuperBuilderAnnotation(aClass)) {
                return;
            }
            PsiClass firstParent = aClass.getSuperClass();
            if (isNonObjectClass(firstParent)
                    && !LombokAnnotationUtil.hasSuperBuilderAnnotation(firstParent)
                    && !LombokAnnotationUtil.hasBuilderAnnotation(firstParent)) {
                holder.registerProblem(builderAnn,
                        MyMessageBundle.message("inspection.builder.004.problem.descriptor"),
                        ProblemHighlightType.WARNING);
            }
        }
    }

    public static class BuilderOnParentWithSubclasses extends LombokBuilderHierarchyInspection {
        @Override
        protected void inspect(PsiClass aClass, ProblemsHolder holder) {
            PsiAnnotation builderAnn = builderAnnotation(aClass);
            if (builderAnn == null || LombokAnnotationUtil.hasSuperBuilderAnnotation(aClass)) {
                return;
            }
            GlobalSearchScope scope = GlobalSearchScope.projectScope(aClass.getProject());
            for (PsiClass sub : ClassInheritorsSearch.search(aClass, scope, false)) {
                if (!sub.isInterface() && !sub.isAnnotationType()) {
                    holder.registerProblem(builderAnn,
                            MyMessageBundle.message("inspection.builder.001.problem.descriptor"),
                            ProblemHighlightType.WARNING);
                    return;
                }
            }
        }
    }
}
