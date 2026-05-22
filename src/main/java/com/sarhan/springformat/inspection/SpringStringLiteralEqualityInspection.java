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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Detects {@code ==} and {@code !=} comparisons that involve at least one string literal.
 * <p>
 * Using reference equality for {@code String} objects is unreliable because strings may not be
 * interned. {@code equals()} should be used for value comparison instead.
 *
 * @author Akmal Sarhan
 */
public class SpringStringLiteralEqualityInspection extends AbstractBaseJavaLocalInspectionTool {
    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
                if ((expression.getOperationTokenType() == JavaTokenType.EQEQ
                        || expression.getOperationTokenType() == JavaTokenType.NE)
                        && hasStringLiteral(expression)) {
                    holder.registerProblem(expression.getOperationSign(),
                            "Use equals() to compare strings",
                            ProblemHighlightType.WARNING,
                            new ReplaceStringEqualityFix());
                }
            }
        };
    }

    private static boolean hasStringLiteral(PsiBinaryExpression expression) {
        PsiExpression right = expression.getROperand();
        return expression.getLOperand().getText().startsWith("\"") || (right != null && right.getText().startsWith("\""));
    }

    private static final class ReplaceStringEqualityFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() { return "Replace string comparison with equals"; }

        @Override
        @NotNull
        public String getFamilyName() { return this.getName(); }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiBinaryExpression binary = descriptor.getPsiElement().getParent() instanceof PsiBinaryExpression b ? b : null;
            if (binary == null || binary.getROperand() == null) {
                return;
            }
            PsiExpression left = binary.getLOperand();
            PsiExpression right = binary.getROperand();
            boolean not = binary.getOperationTokenType() == JavaTokenType.NE;
            String text = (not ? "!" : "") + left.getText() + ".equals(" + right.getText() + ")";
            binary.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(text, binary));
        }
    }
}
