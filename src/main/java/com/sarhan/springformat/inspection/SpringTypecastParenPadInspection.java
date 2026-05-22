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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects whitespace padding immediately inside the parentheses of a type cast expression.
 * <p>
 * Whitespace between the opening parenthesis and the cast type, or between the cast type and the
 * closing parenthesis, is a style inconsistency that should be removed.
 *
 * @author Akmal Sarhan
 */
public class SpringTypecastParenPadInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
                if (expression.getCastType() == null) {
                    return;
                }
                PsiElement beforeType = PsiTreeUtil.prevLeaf(expression.getCastType());
                PsiElement afterType = PsiTreeUtil.nextLeaf(expression.getCastType());
                registerIfWhitespace(beforeType, holder);
                registerIfWhitespace(afterType, holder);
            }
        };
    }

    private static void registerIfWhitespace(PsiElement element, ProblemsHolder holder) {
        if (element instanceof PsiWhiteSpace ws && ws.getText().indexOf('\n') < 0) {
            holder.registerProblem(element, "Whitespace inside typecast parentheses is not allowed",
                    ProblemHighlightType.WARNING, new RemoveTypecastParenPaddingFix());
        }
    }

    private static final class RemoveTypecastParenPaddingFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() { return "Remove typecast parenthesis padding"; }

        @Override
        @NotNull
        public String getFamilyName() { return this.getName(); }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            descriptor.getPsiElement().delete();
        }
    }
}
