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
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */

public abstract class LombokDataAnnotationInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public String getDisplayName() {
        return MyMessageBundle.message("inspection.data.display.name");
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
                PsiAnnotation dataAnn = LombokAnnotationUtil.findAnnotation(aClass, "lombok.Data", "Data");
                if (dataAnn != null) {
                    inspect(aClass, dataAnn, holder);
                }
            }
        };
    }

    protected abstract void inspect(PsiClass aClass, PsiAnnotation dataAnn, ProblemsHolder holder);

    public static class DataWithManualConstructor extends LombokDataAnnotationInspection {
        @Override
        protected void inspect(PsiClass aClass, PsiAnnotation dataAnn, ProblemsHolder holder) {
            for (PsiMethod ctor : aClass.getConstructors()) {
                if (ctor.isPhysical() && ctor.getTextLength() > 0) {
                    holder.registerProblem(dataAnn,
                            MyMessageBundle.message("inspection.data.001.problem.descriptor"),
                            ProblemHighlightType.WARNING);
                    return;
                }
            }
        }
    }

    public static class DataOnJpaEntity extends LombokDataAnnotationInspection {
        @Override
        protected void inspect(PsiClass aClass, PsiAnnotation dataAnn, ProblemsHolder holder) {
            if (LombokAnnotationUtil.isJpaEntity(aClass)) {
                holder.registerProblem(dataAnn,
                        MyMessageBundle.message("inspection.data.jpa.problem.descriptor"),
                        ProblemHighlightType.WARNING,
                        new ReplaceDataWithGetterSetterFix());
            }
        }
    }

    public static class RedundantRequiredArgsConstructor extends LombokDataAnnotationInspection {
        @Override
        protected void inspect(PsiClass aClass, PsiAnnotation dataAnn, ProblemsHolder holder) {
            if (LombokAnnotationUtil.hasRequiredArgsConstructor(aClass)
                    && !LombokAnnotationUtil.hasNoArgsConstructor(aClass)
                    && !LombokAnnotationUtil.hasAllArgsConstructor(aClass)) {
                PsiAnnotation reqArgsAnn = LombokAnnotationUtil.findAnnotation(
                        aClass, "lombok.RequiredArgsConstructor", "RequiredArgsConstructor");
                if (reqArgsAnn != null) {
                    holder.registerProblem(reqArgsAnn,
                            MyMessageBundle.message("inspection.data.002.problem.descriptor"),
                            ProblemHighlightType.WEAK_WARNING,
                            new RemoveRedundantRequiredArgsConstructorFix());
                }
            }
        }
    }

    public static class DataWithAllArgsConstructor extends LombokDataAnnotationInspection {
        @Override
        protected void inspect(PsiClass aClass, PsiAnnotation dataAnn, ProblemsHolder holder) {
            if (LombokAnnotationUtil.hasAllArgsConstructor(aClass)) {
                PsiAnnotation allArgsAnn = LombokAnnotationUtil.findAnnotation(
                        aClass, "lombok.AllArgsConstructor", "AllArgsConstructor");
                if (allArgsAnn != null) {
                    holder.registerProblem(allArgsAnn,
                            MyMessageBundle.message("inspection.data.003.problem.descriptor"),
                            ProblemHighlightType.WEAK_WARNING);
                }
            }
        }
    }
}
