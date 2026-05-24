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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reports Hibernate/JPA query strings that should be formatted as Java text blocks.
 *
 * @author Akmal Sarhan
 */
public class SpringHibernateQueryTextBlockInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final int TEXT_BLOCK_THRESHOLD = 50;

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                super.visitAnnotation(annotation);
                SpringHibernateQueryTextBlockInspection.this.visitQueryAnnotation(annotation, holder);
            }

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                SpringHibernateQueryTextBlockInspection.this.visitQueryCall(expression, holder);
            }
        };
    }

    private void visitQueryAnnotation(@NotNull PsiAnnotation annotation, @NotNull ProblemsHolder holder) {
        if (!SpringInspectionUtil.isAnnotation(annotation, "org.springframework.data.jpa.repository.Query", "Query")) {
            return;
        }
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value == null) {
            return;
        }
        this.registerIfNeeded(value, constantString(value), holder);
    }

    private void visitQueryCall(@NotNull PsiMethodCallExpression expression, @NotNull ProblemsHolder holder) {
        if (!isQueryFactoryCall(expression)) {
            return;
        }
        PsiExpression[] args = expression.getArgumentList().getExpressions();
        if (args.length == 0) {
            return;
        }
        this.registerIfNeeded(args[0], constantString(args[0]), holder);
    }

    private void registerIfNeeded(@NotNull PsiElement element, @Nullable String query, @NotNull ProblemsHolder holder) {
        if (query == null || element.getText().startsWith("\"\"\"") || !shouldUseTextBlock(element, query)) {
            return;
        }
        holder.registerProblem(element, "Format query string as a Java text block",
                ProblemHighlightType.WARNING, new ConvertQueryToTextBlockFix(query));
    }

    private static boolean shouldUseTextBlock(@NotNull PsiElement element, @NotNull String query) {
        return query.length() > TEXT_BLOCK_THRESHOLD || element.getText().contains("+");
    }

    @Nullable
    private static String constantString(@NotNull PsiElement element) {
        if (!(element instanceof PsiExpression expression)) {
            return null;
        }
        Object constant = JavaPsiFacade.getInstance(element.getProject()).getConstantEvaluationHelper()
                .computeConstantExpression(expression);
        return constant instanceof String query ? query : null;
    }

    private static boolean isQueryFactoryCall(@NotNull PsiMethodCallExpression expression) {
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        String methodName = methodExpression.getReferenceName();
        if (!"createQuery".equals(methodName) && !"createNativeQuery".equals(methodName)) {
            return false;
        }
        PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null) {
            return false;
        }
        PsiType type = qualifier.getType();
        if (type == null) {
            return false;
        }
        String canonical = type.getCanonicalText();
        return "org.hibernate.Session".equals(canonical)
                || "javax.persistence.EntityManager".equals(canonical)
                || "jakarta.persistence.EntityManager".equals(canonical);
    }

    private static final class ConvertQueryToTextBlockFix implements LocalQuickFix {

        private final String query;

        private ConvertQueryToTextBlockFix(@NotNull String query) {
            this.query = query;
        }

        @Override
        @NotNull
        public String getName() {
            return "Format query as text block";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element == null) {
                return;
            }
            PsiExpression replacement = JavaPsiFacade.getElementFactory(project)
                    .createExpressionFromText(textBlockLiteral(
                            SpringInspectionUtil.formatQueryText(project, this.query), element), element);
            element.replace(replacement);
        }

        @NotNull
        private static String textBlockLiteral(@NotNull String content, @NotNull PsiElement element) {
            String indent = lineIndent(element) + "        ";
            String escaped = content.replace("\\", "\\\\").replace("\"\"\"", "\\\"\"\"");
            StringBuilder literal = new StringBuilder("\"\"\"\n");
            for (String line : escaped.split("\\n", -1)) {
                literal.append(indent).append(line).append('\n');
            }
            literal.append(indent).append("\"\"\"");
            return literal.toString();
        }

        @NotNull
        private static String lineIndent(@NotNull PsiElement element) {
            Document document = PsiDocumentManager.getInstance(element.getProject())
                    .getDocument(element.getContainingFile());
            if (document == null) {
                return "";
            }
            int lineNumber = document.getLineNumber(element.getTextOffset());
            int lineStart = document.getLineStartOffset(lineNumber);
            int offset = lineStart;
            while (offset < document.getTextLength()) {
                char c = document.getCharsSequence().charAt(offset);
                if (c != ' ' && c != '\t') {
                    break;
                }
                offset++;
            }
            return document.getText().substring(lineStart, offset);
        }
    }
}
