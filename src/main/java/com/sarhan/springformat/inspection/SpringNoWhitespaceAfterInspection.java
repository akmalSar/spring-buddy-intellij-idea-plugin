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
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects whitespace after tokens where it is not allowed (e.g., {@code .}, {@code !}, {@code ~},
 * prefix {@code ++}, prefix {@code --}, prefix {@code +}, prefix {@code -}).
 * <p>
 * Extra whitespace in these positions breaks consistent formatting and can hinder readability.
 *
 * @author Akmal Sarhan
 */
public class SpringNoWhitespaceAfterInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof PsiJavaToken token) || !isChecked(token)) {
                    return;
                }
                PsiElement next = PsiTreeUtil.nextLeaf(token);
                if (next instanceof PsiWhiteSpace && next.getText().indexOf('\n') < 0) {
                    holder.registerProblem(token, "Whitespace is not allowed after '" + token.getText() + "'",
                            ProblemHighlightType.WARNING, new RemoveWhitespaceAfterFix());
                }
            }
        };
    }

    private static boolean isChecked(PsiJavaToken token) {
        IElementType type = token.getTokenType();
        if (type == JavaTokenType.DOT || type == JavaTokenType.EXCL || type == JavaTokenType.TILDE) {
            return true;
        }
        if ((type == JavaTokenType.PLUSPLUS || type == JavaTokenType.MINUSMINUS) && token.getParent() instanceof PsiPrefixExpression) {
            return true;
        }
        return (type == JavaTokenType.PLUS || type == JavaTokenType.MINUS) && token.getParent() instanceof PsiPrefixExpression;
    }

    private static final class RemoveWhitespaceAfterFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Remove whitespace after token";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement next = PsiTreeUtil.nextLeaf(descriptor.getPsiElement());
            if (next instanceof PsiWhiteSpace) {
                next.delete();
            }
        }
    }
}
