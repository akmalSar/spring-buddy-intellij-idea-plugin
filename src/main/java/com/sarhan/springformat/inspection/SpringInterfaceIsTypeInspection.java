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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

/**
 * Detects interfaces that define only constants without any methods,
 * violating the principle that an interface should represent a type.
 *
 * @author Akmal Sarhan
 */
public class SpringInterfaceIsTypeInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                if (!psiClass.isInterface() || psiClass.isAnnotationType()) {
                    return;
                }
                if (psiClass.getMethods().length == 0 && psiClass.getFields().length > 0) {
                    holder.registerProblem((psiClass.getNameIdentifier() != null) ? psiClass.getNameIdentifier() : psiClass,
                            "Interface should define a type, not only constants",
                            ProblemHighlightType.WARNING,
                            new AddMarkerMethodFix());
                }
            }
        };
    }

    private static final class AddMarkerMethodFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Add method to make interface a type";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiClass psiClass = descriptor.getPsiElement() instanceof PsiClass cls
                    ? cls : descriptor.getPsiElement().getParent() instanceof PsiClass cls ? cls : null;
            if (psiClass == null) {
                return;
            }
            PsiMethod method = JavaPsiFacade.getElementFactory(project)
                    .createMethodFromText("void marker();", psiClass);
            psiClass.add(method);
        }
    }
}
