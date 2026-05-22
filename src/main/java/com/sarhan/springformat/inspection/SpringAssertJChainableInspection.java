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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiStatement;
import org.jetbrains.annotations.NotNull;

/**
 * SpringAssertJChainable: detects sibling statements that all call
 * {@code assertThat(sameSubject)...} on the same subject expression and suggests chaining
 * them into a single fluent assertion.
 *
 * @author Akmal Sarhan
 */
public class SpringAssertJChainableInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitCodeBlock(@NotNull PsiCodeBlock block) {
                super.visitCodeBlock(block);
                PsiStatement[] statements = block.getStatements();
                String previousSubject = null;
                int run = 0;
                int runStart = -1;
                for (int i = 0; i < statements.length; i++) {
                    String subject = assertThatSubjectText(statements[i]);
                    if (subject != null && subject.equals(previousSubject)) {
                        run++;
                    } else {
                        flushRun(holder, statements, runStart, run);
                        previousSubject = subject;
                        runStart = i;
                        run = (subject != null) ? 1 : 0;
                    }
                }
                flushRun(holder, statements, runStart, run);
            }
        };
    }

    private static void flushRun(ProblemsHolder holder, PsiStatement[] statements, int start, int run) {
        if (run < 2 || start < 0) {
            return;
        }
        for (int i = start; i < start + run; i++) {
            holder.registerProblem(statements[i],
                    "Multiple assertThat() calls on the same subject — consider chaining them",
                    ProblemHighlightType.WEAK_WARNING,
                    new ChainAssertThatFix());
        }
    }

    private static String assertThatSubjectText(PsiStatement statement) {
        if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
            return null;
        }
        if (hasExtractingStepBeforeTerminalAssertion(assertThatChainSuffix(statement))) {
            return null;
        }
        PsiExpression expression = expressionStatement.getExpression();
        // Walk down the call chain to find the assertThat(...) at the head.
        while (expression instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            if ("assertThat".equals(name)) {
                PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
                if (qualifier != null && !isAssertJQualifier(qualifier)) {
                    return null;
                }
                PsiExpression[] arguments = call.getArgumentList().getExpressions();
                if (arguments.length == 1) {
                    return arguments[0].getText();
                }
                return null;
            }
            expression = call.getMethodExpression().getQualifierExpression();
        }
        return null;
    }

    private static boolean isAssertJQualifier(PsiExpression qualifier) {
        String text = qualifier.getText();
        return "Assertions".equals(text) || "BDDAssertions".equals(text)
                || text.endsWith(".Assertions") || text.endsWith(".BDDAssertions")
                || text.startsWith("org.assertj.core.api.Assertions")
                || text.startsWith("org.assertj.core.api.BDDAssertions");
    }

    private static PsiMethodCallExpression assertThatCall(PsiStatement statement) {
        if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
            return null;
        }
        PsiExpression expression = expressionStatement.getExpression();
        while (expression instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            if ("assertThat".equals(name)) {
                return call;
            }
            expression = call.getMethodExpression().getQualifierExpression();
        }
        return null;
    }

    private static String assertThatChainSuffix(PsiStatement statement) {
        if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
            return null;
        }
        PsiMethodCallExpression assertThatCall = assertThatCall(statement);
        if (assertThatCall == null) {
            return null;
        }
        String expressionText = expressionStatement.getExpression().getText();
        String assertThatText = assertThatCall.getText();
        if (!expressionText.startsWith(assertThatText)) {
            return null;
        }
        String suffix = expressionText.substring(assertThatText.length());
        return hasExtractingStepBeforeTerminalAssertion(suffix) ? null : suffix;
    }

    private static boolean hasExtractingStepBeforeTerminalAssertion(String suffix) {
        if (suffix == null) {
            return false;
        }
        int terminalAssertionIndex = firstTerminalAssertionIndex(suffix);
        return terminalAssertionIndex > 0 && suffix.substring(0, terminalAssertionIndex).contains(".extracting");
    }

    private static int firstTerminalAssertionIndex(String suffix) {
        String[] terminalAssertions = {".is", ".has", ".contains", ".does", ".startsWith", ".endsWith", ".matches"};
        int index = -1;
        for (String terminalAssertion : terminalAssertions) {
            int current = suffix.indexOf(terminalAssertion);
            if (current >= 0 && (index < 0 || current < index)) {
                index = current;
            }
        }
        return index;
    }

    private static final class ChainAssertThatFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Chain AssertJ assertions";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiStatement statement) || !(statement.getParent() instanceof PsiCodeBlock block)) {
                return;
            }
            String subject = assertThatSubjectText(statement);
            if (subject == null) {
                return;
            }
            PsiStatement[] statements = block.getStatements();
            int index = statementIndex(statements, statement);
            if (index < 0) {
                return;
            }
            int start = index;
            while (start > 0 && subject.equals(assertThatSubjectText(statements[start - 1]))) {
                start--;
            }
            int end = index;
            while (end + 1 < statements.length && subject.equals(assertThatSubjectText(statements[end + 1]))) {
                end++;
            }
            if (end <= start || !(statements[start] instanceof PsiExpressionStatement firstStatement)) {
                return;
            }

            StringBuilder chainedExpression = new StringBuilder(firstStatement.getExpression().getText());
            for (int i = start + 1; i <= end; i++) {
                String suffix = assertThatChainSuffix(statements[i]);
                if (suffix == null || suffix.isEmpty()) {
                    return;
                }
                chainedExpression.append(suffix);
            }
            PsiStatement replacement = JavaPsiFacade.getElementFactory(project)
                    .createStatementFromText(chainedExpression + ";", statements[start]);
            statements[start].replace(replacement);
            for (int i = end; i > start; i--) {
                statements[i].delete();
            }
        }

        private static int statementIndex(PsiStatement[] statements, PsiStatement statement) {
            for (int i = 0; i < statements.length; i++) {
                if (statements[i] == statement) {
                    return i;
                }
            }
            return -1;
        }
    }
}
