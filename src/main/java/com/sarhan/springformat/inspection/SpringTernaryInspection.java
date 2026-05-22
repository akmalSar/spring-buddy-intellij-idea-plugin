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
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Detects formatting and style issues inside ternary (conditional) expressions.
 * <p>
 * It flags complex conditions that are not wrapped in parentheses (hurting readability) and
 * {@code == null} checks that should be rewritten as {@code != null} with swapped branches.
 *
 * @author Akmal Sarhan
 */
public class SpringTernaryInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
                PsiExpression condition = expression.getCondition();
                PsiExpression unwrappedCondition = unwrap(condition);
                if (requiresParens(unwrappedCondition) && !isParenthesized(condition)) {
                    holder.registerProblem(condition, "Ternary condition should be wrapped in parentheses",
                            ProblemHighlightType.WARNING, new AddConditionParensFix());
                }
                if (isSimpleNullEquals(unwrappedCondition)) {
                    holder.registerProblem(unwrappedCondition, "Use '!=' for ternary null checks",
                            ProblemHighlightType.WARNING, new ReplaceNullEqualsFix());
                }
            }
        };
    }

    private static boolean requiresParens(PsiExpression condition) {
        if (!(condition instanceof PsiBinaryExpression binary)) {
            return false;
        }
        return binary.getOperationTokenType() != JavaTokenType.EQEQ || !isSimpleNullEquals(condition);
    }

    private static boolean isParenthesized(PsiExpression condition) {
        String text = condition.getText().trim();
        return text.startsWith("(") && text.endsWith(")");
    }

    private static PsiExpression unwrap(PsiExpression condition) {
        PsiExpression result = condition;
        while (result instanceof PsiParenthesizedExpression parenthesized && parenthesized.getExpression() != null) {
            result = parenthesized.getExpression();
        }
        return result;
    }

    private static boolean isSimpleNullEquals(PsiExpression condition) {
        if (!(condition instanceof PsiBinaryExpression binary) || binary.getOperationTokenType() != JavaTokenType.EQEQ) {
            return false;
        }
        PsiExpression left = binary.getLOperand();
        PsiExpression right = binary.getROperand();
        return left != null && right != null
                && ("null".equals(left.getText().trim()) || "null".equals(right.getText().trim()));
    }

    private static final class AddConditionParensFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Wrap ternary condition in parentheses";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiExpression condition = (PsiExpression) descriptor.getPsiElement();
            PsiExpression replacement = JavaPsiFacade.getElementFactory(project)
                    .createExpressionFromText("(" + condition.getText() + ")", condition);
            condition.replace(replacement);
        }
    }

    private static final class ReplaceNullEqualsFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Replace '==' null check with '!='";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiExpression condition = (PsiExpression) descriptor.getPsiElement();
            if (!(condition instanceof PsiBinaryExpression binary)) {
                return;
            }
            PsiConditionalExpression ternary = condition.getParent() instanceof PsiConditionalExpression c ? c
                    : (condition.getParent() instanceof PsiParenthesizedExpression p
                    && p.getParent() instanceof PsiConditionalExpression c) ? c : null;
            if (ternary == null) {
                return;
            }
            PsiExpression left = binary.getLOperand();
            PsiExpression right = binary.getROperand();
            PsiExpression thenExpression = ternary.getThenExpression();
            PsiExpression elseExpression = ternary.getElseExpression();
            if (left == null || right == null || thenExpression == null || elseExpression == null) {
                return;
            }
            PsiExpression checkedExpression = "null".equals(left.getText().trim()) ? right : left;
            PsiExpression replacement = JavaPsiFacade.getElementFactory(project)
                    .createExpressionFromText(checkedExpression.getText() + " != null ? "
                            + elseExpression.getText() + " : " + thenExpression.getText(), ternary);
            ternary.replace(replacement);
        }
    }
}
