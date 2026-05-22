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
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLabeledStatement;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects whitespace before tokens where it is not allowed (e.g., {@code ,}, {@code ;},
 * {@code ...}, postfix {@code ++}, postfix {@code --}, colon in labeled statements).
 * <p>
 * Extra whitespace in these positions is a formatting inconsistency that reduces code clarity.
 *
 * @author Akmal Sarhan
 */
public class SpringNoWhitespaceBeforeInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (!(element instanceof PsiJavaToken token) || !isCheckedToken(token)) {
                    return;
                }
                PsiElement previous = PsiTreeUtil.prevLeaf(token);
                if (previous instanceof PsiWhiteSpace && previous.getTextLength() > 0) {
                    holder.registerProblem(token,
                            "Whitespace is not allowed before '" + token.getText() + "'",
                            ProblemHighlightType.WARNING,
                            new RemoveWhitespaceBeforeFix());
                }
            }
        };
    }

    private static boolean isCheckedToken(PsiJavaToken token) {
        IElementType type = token.getTokenType();
        if (type == JavaTokenType.COMMA || type == JavaTokenType.ELLIPSIS) {
            return true;
        }
        if (type == JavaTokenType.SEMICOLON) {
            return !isEmptyForInitializerOrConditionSemicolon(token);
        }
        if (type == JavaTokenType.PLUSPLUS || type == JavaTokenType.MINUSMINUS) {
            return token.getParent() instanceof PsiPostfixExpression;
        }
        if (type == JavaTokenType.COLON) {
            return token.getParent() instanceof PsiLabeledStatement;
        }
        return false;
    }

    private static boolean isEmptyForInitializerOrConditionSemicolon(PsiJavaToken token) {
        PsiForStatement forStatement = PsiTreeUtil.getParentOfType(token, PsiForStatement.class);
        if (forStatement == null || token.getParent() != forStatement) {
            return false;
        }
        PsiElement previous = PsiTreeUtil.prevVisibleLeaf(token);
        return previous != null && ("(".equals(previous.getText()) || ";".equals(previous.getText()));
    }

    private static final class RemoveWhitespaceBeforeFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Remove whitespace before token";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element instanceof PsiJavaToken token && token.getTokenType() == JavaTokenType.SEMICOLON
                    && hasLineCommentBefore(token)) {
                moveSemicolonBeforeLineComment(token);
                return;
            }
            PsiElement previous = PsiTreeUtil.prevLeaf(element);
            if (previous instanceof PsiWhiteSpace) {
                previous.delete();
            }
        }

        private static boolean hasLineCommentBefore(PsiElement element) {
            PsiElement sibling = PsiTreeUtil.prevLeaf(element);
            while (sibling instanceof PsiWhiteSpace || sibling instanceof PsiComment) {
                if (sibling instanceof PsiComment comment && comment.getText().startsWith("//")) {
                    return true;
                }
                sibling = PsiTreeUtil.prevLeaf(sibling);
            }
            return false;
        }

        private static void moveSemicolonBeforeLineComment(PsiJavaToken semicolon) {
            PsiComment comment = previousLineComment(semicolon);
            if (comment == null || semicolon.getContainingFile() == null) {
                return;
            }
            Project project = semicolon.getProject();
            Document document = PsiDocumentManager.getInstance(project).getDocument(semicolon.getContainingFile());
            if (document == null) {
                return;
            }
            int insertOffset = comment.getTextRange().getStartOffset();
            int semicolonOffset = semicolon.getTextRange().getStartOffset();
            document.insertString(insertOffset, "; ");
            document.deleteString(semicolonOffset + 1, semicolonOffset + 2);
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }

        private static PsiComment previousLineComment(PsiElement element) {
            PsiElement sibling = PsiTreeUtil.prevLeaf(element);
            while (sibling instanceof PsiWhiteSpace || sibling instanceof PsiComment) {
                if (sibling instanceof PsiComment comment && comment.getText().startsWith("//")) {
                    return comment;
                }
                sibling = PsiTreeUtil.prevLeaf(sibling);
            }
            return null;
        }
    }
}
