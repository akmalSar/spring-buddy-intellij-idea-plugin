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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Checks that commas and semicolons are followed by whitespace.
 * Missing whitespace after these tokens reduces readability and is against Spring formatting conventions.
 *
 * @author Akmal Sarhan
 */
public class SpringWhitespaceAfterInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof PsiJavaToken token) || !isChecked(token.getTokenType())) {
                    return;
                }
                PsiElement next = PsiTreeUtil.nextLeaf(token);
                if (next != null && !(next instanceof PsiWhiteSpace) && !")".equals(next.getText()) && !"}".equals(next.getText())) {
                    holder.registerProblem(token, "Whitespace is required after '" + token.getText() + "'",
                            ProblemHighlightType.WARNING, new AddWhitespaceAfterFix());
                }
            }
        };
    }

    private static boolean isChecked(IElementType type) {
        return type == JavaTokenType.COMMA || type == JavaTokenType.SEMICOLON;
    }

    private static final class AddWhitespaceAfterFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Add whitespace after token";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            Document document = PsiDocumentManager.getInstance(project).getDocument(descriptor.getPsiElement().getContainingFile());
            if (document != null) {
                document.insertString(descriptor.getPsiElement().getTextRange().getEndOffset(), " ");
                PsiDocumentManager.getInstance(project).commitDocument(document);
            }
        }
    }
}
