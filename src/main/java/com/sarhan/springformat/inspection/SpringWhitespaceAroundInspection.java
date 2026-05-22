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

import java.util.Set;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Checks that operators and keywords have required whitespace around them.
 * Missing whitespace around operators or control-flow keywords reduces readability
 * and is against Spring formatting conventions.
 *
 * @author Akmal Sarhan
 */
public class SpringWhitespaceAroundInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final Set<IElementType> OPERATOR_TOKENS = Set.of(
            JavaTokenType.EQ, JavaTokenType.PLUSEQ, JavaTokenType.MINUSEQ, JavaTokenType.ASTERISKEQ,
            JavaTokenType.DIVEQ, JavaTokenType.PERCEQ, JavaTokenType.ANDEQ, JavaTokenType.OREQ,
            JavaTokenType.XOREQ, JavaTokenType.LTLTEQ, JavaTokenType.GTGTEQ, JavaTokenType.GTGTGTEQ,
            JavaTokenType.EQEQ, JavaTokenType.NE, JavaTokenType.GE, JavaTokenType.LE,
            JavaTokenType.GT, JavaTokenType.LT, JavaTokenType.ANDAND, JavaTokenType.OROR,
            JavaTokenType.PLUS, JavaTokenType.MINUS, JavaTokenType.ASTERISK, JavaTokenType.DIV,
            JavaTokenType.PERC, JavaTokenType.AND, JavaTokenType.OR, JavaTokenType.XOR,
            JavaTokenType.LTLT, JavaTokenType.GTGT, JavaTokenType.GTGTGT, JavaTokenType.QUEST,
            JavaTokenType.COLON, JavaTokenType.ARROW);

    private static final Set<IElementType> KEYWORD_TOKENS = Set.of(
            JavaTokenType.IF_KEYWORD, JavaTokenType.FOR_KEYWORD, JavaTokenType.WHILE_KEYWORD,
            JavaTokenType.SWITCH_KEYWORD, JavaTokenType.CATCH_KEYWORD, JavaTokenType.SYNCHRONIZED_KEYWORD,
            JavaTokenType.RETURN_KEYWORD, JavaTokenType.ASSERT_KEYWORD, JavaTokenType.ELSE_KEYWORD,
            JavaTokenType.TRY_KEYWORD, JavaTokenType.FINALLY_KEYWORD, JavaTokenType.DO_KEYWORD);

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
                if (requiresWhitespaceBefore(token) && missingWhitespaceBefore(token)) {
                    holder.registerProblem(token,
                            "Whitespace is required before '" + token.getText() + "'",
                            ProblemHighlightType.WARNING,
                            new AddWhitespaceAroundFix(true));
                }
                if (requiresWhitespaceAfter(token) && missingWhitespaceAfter(token)) {
                    holder.registerProblem(token,
                            "Whitespace is required after '" + token.getText() + "'",
                            ProblemHighlightType.WARNING,
                            new AddWhitespaceAroundFix(false));
                }
            }
        };
    }

    private static boolean isCheckedToken(PsiJavaToken token) {
        IElementType type = token.getTokenType();
        if (OPERATOR_TOKENS.contains(type) || type == JavaTokenType.RBRACE) {
            return isOperatorContext(token);
        }
        return KEYWORD_TOKENS.contains(type);
    }

    private static boolean isOperatorContext(PsiJavaToken token) {
        IElementType type = token.getTokenType();
        if (type == JavaTokenType.ARROW) {
            return token.getParent() instanceof PsiLambdaExpression;
        }
        if (type == JavaTokenType.QUEST) {
            return token.getParent() instanceof PsiConditionalExpression;
        }
        if (type == JavaTokenType.COLON) {
            return token.getParent() instanceof PsiConditionalExpression;
        }
        if (token.getParent() instanceof PsiBinaryExpression || token.getParent() instanceof PsiAssignmentExpression) {
            return true;
        }
        return type == JavaTokenType.RBRACE && token.getParent() instanceof com.intellij.psi.PsiCodeBlock;
    }

    private static boolean requiresWhitespaceBefore(PsiJavaToken token) {
        IElementType type = token.getTokenType();
        return OPERATOR_TOKENS.contains(type) || type == JavaTokenType.RBRACE || type == JavaTokenType.ELSE_KEYWORD
                || type == JavaTokenType.FINALLY_KEYWORD || type == JavaTokenType.WHILE_KEYWORD;
    }

    private static boolean requiresWhitespaceAfter(PsiJavaToken token) {
        IElementType type = token.getTokenType();
        return OPERATOR_TOKENS.contains(type) || type == JavaTokenType.RBRACE || type != JavaTokenType.DO_KEYWORD;
    }

    private static boolean missingWhitespaceBefore(PsiJavaToken token) {
        PsiElement previous = PsiTreeUtil.prevLeaf(token);
        return previous != null && !(previous instanceof PsiWhiteSpace) && sameLine(previous, token);
    }

    private static boolean missingWhitespaceAfter(PsiJavaToken token) {
        PsiElement next = PsiTreeUtil.nextLeaf(token);
        return next != null && !(next instanceof PsiWhiteSpace) && sameLine(token, next)
                && !";".equals(next.getText()) && !",".equals(next.getText())
                && !")".equals(next.getText()) && !"}".equals(next.getText());
    }

    private static boolean sameLine(PsiElement left, PsiElement right) {
        Document document = PsiDocumentManager.getInstance(left.getProject()).getDocument(left.getContainingFile());
        if (document == null) {
            return true;
        }
        return document.getLineNumber(left.getTextRange().getEndOffset())
                == document.getLineNumber(right.getTextRange().getStartOffset());
    }

    private static class AddWhitespaceAroundFix implements LocalQuickFix {

        private final boolean before;

        AddWhitespaceAroundFix(boolean before) {
            this.before = before;
        }

        @Override
        @NotNull
        public String getName() {
            return this.before ? "Add whitespace before token" : "Add whitespace after token";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return "Add whitespace around token";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            Document document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
            if (document == null) {
                return;
            }
            int offset = this.before ? element.getTextRange().getStartOffset() : element.getTextRange().getEndOffset();
            document.insertString(offset, " ");
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }
    }
}
