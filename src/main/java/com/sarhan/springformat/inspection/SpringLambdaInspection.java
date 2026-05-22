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
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects lambda style violations: missing parentheses around a single parameter
 * and unnecessary curly braces around a single-expression body.
 *
 * @author Akmal Sarhan
 */
public class SpringLambdaInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitLambdaExpression(@NotNull PsiLambdaExpression lambda) {
                checkParentheses(lambda, holder);
                checkBraces(lambda, holder);
            }
        };
    }

    private static void checkParentheses(PsiLambdaExpression lambda, ProblemsHolder holder) {
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length != 1) {
            return;
        }
        boolean hasParens = lambda.getParameterList().getText().startsWith("(");
        if (!hasParens) {
            holder.registerProblem(lambda.getParameterList(),
                    "Lambda with single argument should use parentheses",
                    ProblemHighlightType.WARNING,
                    new AddParenthesesFix());
        }
    }

    private static void checkBraces(PsiLambdaExpression lambda, ProblemsHolder holder) {
        PsiElement body = lambda.getBody();
        if (!(body instanceof PsiCodeBlock block)) {
            return;
        }
        PsiStatement[] statements = block.getStatements();
        if (statements.length != 1) {
            return;
        }
        PsiStatement stmt = statements[0];
        PsiExpression expression = extractExpression(stmt);
        if (expression == null) {
            return;
        }
        holder.registerProblem(block,
                "Lambda with single statement should not use curly braces",
                ProblemHighlightType.WARNING,
                new RemoveBracesFix());
    }

    @Nullable
    private static PsiExpression extractExpression(PsiStatement stmt) {
        if (stmt instanceof PsiExpressionStatement exprStmt) {
            return exprStmt.getExpression();
        }
        if (stmt instanceof PsiReturnStatement returnStmt) {
            return returnStmt.getReturnValue();
        }
        return null;
    }

    private static final class AddParenthesesFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Add parentheses around lambda parameter";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiLambdaExpression lambda = element instanceof PsiLambdaExpression l
                    ? l : PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
            if (lambda == null) {
                return;
            }
            PsiParameter[] params = lambda.getParameterList().getParameters();
            if (params.length != 1) {
                return;
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiParameter param = params[0];
            PsiTypeElement typeElement = param.getTypeElement();
            String paramText = (typeElement != null)
                    ? typeElement.getText() + " " + param.getName()
                    : param.getName();
            PsiLambdaExpression dummy = (PsiLambdaExpression) factory.createExpressionFromText(
                    "(" + paramText + ") -> 0", lambda);
            lambda.getParameterList().replace(dummy.getParameterList());
        }
    }

    private static final class RemoveBracesFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Remove unnecessary curly braces";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiLambdaExpression lambda = element instanceof PsiLambdaExpression l
                    ? l : PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class);
            if (lambda == null) {
                return;
            }
            PsiElement body = lambda.getBody();
            if (!(body instanceof PsiCodeBlock block)) {
                return;
            }
            PsiStatement[] statements = block.getStatements();
            if (statements.length != 1) {
                return;
            }
            PsiExpression expression = extractExpression(statements[0]);
            if (expression == null) {
                return;
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiExpression exprBody = factory.createExpressionFromText(expression.getText(), lambda);
            body.replace(exprBody);
        }
    }
}
