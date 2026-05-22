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
import org.jetbrains.annotations.NotNull;

/**
 * Detects long literals that use a lowercase {@code 'l'} suffix instead of uppercase {@code 'L'}.
 * <p>
 * A lowercase {@code 'l'} is easily mistaken for the digit {@code '1'}, which can introduce subtle
 * bugs when reading or reviewing code. The suffix should always be {@code 'L'}.
 *
 * @author Akmal Sarhan
 */
public class SpringUpperEllInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new PsiElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiJavaToken token && token.getTokenType() == JavaTokenType.LONG_LITERAL
                        && token.getText().endsWith("l")) {
                    holder.registerProblem(token,
                            "Long literal should use uppercase 'L' suffix",
                            ProblemHighlightType.WARNING,
                            new ReplaceLowercaseLongSuffixFix());
                }
            }
        };
    }

    private static final class ReplaceLowercaseLongSuffixFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Replace 'l' with 'L'";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            Document document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
            if (document == null || element.getTextLength() == 0) {
                return;
            }
            int endOffset = element.getTextRange().getEndOffset();
            document.replaceString(endOffset - 1, endOffset, "L");
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }
    }
}
