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

import java.util.regex.Pattern;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * Detects non-final fields inside exception, error, or throwable classes.
 * <p>
 * Exception objects should be immutable after construction; mutable fields can lead to inconsistent
 * error state if modified after the exception is thrown and caught.
 *
 * @author Akmal Sarhan
 */
public class SpringMutableExceptionInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final Pattern EXCEPTION_NAME = Pattern.compile("^.*Exception$|^.*Error$|^.*Throwable$");

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitField(@NotNull PsiField field) {
                PsiClass containingClass = field.getContainingClass();
                if (containingClass == null || !isCheckedExceptionType(containingClass)
                        || field.hasModifierProperty(PsiModifier.FINAL)) {
                    return;
                }
                holder.registerProblem(field,
                        "Exception field should be final",
                        ProblemHighlightType.WARNING,
                        new MakeFieldFinalFix());
            }
        };
    }

    private static boolean isCheckedExceptionType(PsiClass psiClass) {
        String name = psiClass.getName();
        if (name == null || !EXCEPTION_NAME.matcher(name).matches()) {
            return false;
        }
        PsiClass superClass = psiClass.getSuperClass();
        String superName = (superClass != null) ? superClass.getName() : null;
        return superName != null && EXCEPTION_NAME.matcher(superName).matches();
    }

    private static final class MakeFieldFinalFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Make exception field final";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiField field = element instanceof PsiField f ? f
                    : element.getParent() instanceof PsiField f ? f : null;
            if (field == null || field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null) {
                return;
            }
            modifierList.add(com.intellij.psi.JavaPsiFacade.getElementFactory(project).createKeyword(PsiKeyword.FINAL));
        }
    }
}
