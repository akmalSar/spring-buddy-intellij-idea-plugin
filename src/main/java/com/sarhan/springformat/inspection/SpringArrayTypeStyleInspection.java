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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

/**
 * Detects array declarations where brackets are placed after the variable name
 * rather than with the type (e.g. {@code String args[]}), which is discouraged style.
 *
 * @author Akmal Sarhan
 */
public class SpringArrayTypeStyleInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitField(@NotNull PsiField field) { check(field, holder); }

            @Override
            public void visitLocalVariable(@NotNull PsiLocalVariable variable) { check(variable, holder); }

            @Override
            public void visitParameter(@NotNull PsiParameter parameter) { check(parameter, holder); }
        };
    }

    private static void check(PsiVariable variable, ProblemsHolder holder) {
        PsiTypeElement typeElement = variable.getTypeElement();
        if (typeElement != null && variable.getText().contains(variable.getName() + "[]")) {
            holder.registerProblem(typeElement, "Array brackets should be declared with the type",
                    ProblemHighlightType.WARNING, new MoveArrayBracketsToTypeFix());
        }
    }

    private static final class MoveArrayBracketsToTypeFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() { return "Move array brackets to type"; }

        @Override
        @NotNull
        public String getFamilyName() { return this.getName(); }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiVariable variable = descriptor.getPsiElement().getParent() instanceof PsiVariable v ? v : null;
            if (variable == null || variable.getTypeElement() == null) {
                return;
            }
            PsiTypeElement replacement = JavaPsiFacade.getElementFactory(project)
                    .createTypeElement(variable.getType());
            variable.getTypeElement().replace(replacement);
        }
    }
}
