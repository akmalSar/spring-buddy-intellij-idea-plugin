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
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiStatement;
import org.jetbrains.annotations.NotNull;

/**
 * Detects unnecessary nested block statements inside code blocks,
 * which add no semantic value and reduce readability.
 *
 * @author Akmal Sarhan
 */
public class SpringAvoidNestedBlocksInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitBlockStatement(@NotNull PsiBlockStatement statement) {
                if (!(statement.getParent() instanceof PsiCodeBlock)) {
                    return;
                }
                holder.registerProblem(statement.getCodeBlock(),
                        "Avoid nested blocks",
                        ProblemHighlightType.WARNING,
                        new RemoveNestedBlockFix());
            }
        };
    }

    private static final class RemoveNestedBlockFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Remove nested block braces";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PsiCodeBlock block)
                    || !(block.getParent() instanceof PsiBlockStatement blockStatement)) {
                return;
            }
            PsiStatement[] statements = block.getStatements();
            if (statements.length == 0) {
                blockStatement.delete();
                return;
            }
            StringBuilder replacement = new StringBuilder();
            for (PsiStatement statement : statements) {
                replacement.append(statement.getText()).append('\n');
            }
            PsiStatement newStatement = JavaPsiFacade.getElementFactory(project)
                    .createStatementFromText("if (true) {\n" + replacement + "}", blockStatement);
            PsiCodeBlock newBlock = ((com.intellij.psi.PsiIfStatement) newStatement).getThenBranch() instanceof PsiBlockStatement bs
                    ? bs.getCodeBlock() : null;
            if (newBlock == null) {
                return;
            }
            PsiStatement[] newStatements = newBlock.getStatements();
            for (int i = newStatements.length - 1; i >= 0; i--) {
                blockStatement.getParent().addAfter(newStatements[i], blockStatement);
            }
            blockStatement.delete();
        }
    }
}
