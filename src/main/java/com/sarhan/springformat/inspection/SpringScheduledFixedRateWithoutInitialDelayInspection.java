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
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * Detects @Scheduled with fixedRate or fixedDelay but without initialDelay.
 * Without initialDelay the task starts immediately on context startup,
 * which can cause a startup stampede if the method is I/O heavy.
 *
 * @author Akmal Sarhan
 */
public class SpringScheduledFixedRateWithoutInitialDelayInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiAnnotation annotation = findScheduled(method);
                if (annotation == null) {
                    return;
                }
                if (!hasRateOrDelay(annotation)) {
                    return;
                }
                if (hasInitialDelay(annotation)) {
                    return;
                }
                holder.registerProblem(
                        (annotation.getNameReferenceElement() != null) ? annotation.getNameReferenceElement() : annotation,
                        "@Scheduled with fixedRate/fixedDelay should specify initialDelay to avoid startup stampede",
                        ProblemHighlightType.WARNING,
                        new AddInitialDelayFix()
                );
            }
        };
    }

    private static PsiAnnotation findScheduled(PsiMethod method) {
        for (PsiAnnotation a : method.getModifierList().getAnnotations()) {
            if (SpringInspectionUtil.isAnnotation(a, "org.springframework.scheduling.annotation.Scheduled", "Scheduled")) {
                return a;
            }
        }
        return null;
    }

    private static boolean hasRateOrDelay(PsiAnnotation annotation) {
        String text = annotation.getText();
        if (text == null) {
            return false;
        }
        return text.contains("fixedRate") || text.contains("fixedDelay");
    }

    private static boolean hasInitialDelay(PsiAnnotation annotation) {
        String text = annotation.getText();
        if (text == null) {
            return false;
        }
        return text.contains("initialDelay");
    }

    private static final class AddInitialDelayFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Add initialDelay = 1000";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiAnnotation annotation = descriptor.getPsiElement() instanceof PsiAnnotation a ? a
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiAnnotation.class);
            if (annotation == null) {
                return;
            }
            PsiAnnotationMemberValue value = JavaPsiFacade.getElementFactory(project)
                    .createExpressionFromText("1000", annotation);
            annotation.setDeclaredAttributeValue("initialDelay", value);
        }
    }
}
