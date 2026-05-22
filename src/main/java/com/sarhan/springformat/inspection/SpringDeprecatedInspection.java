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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NotNull;

/**
 * SpringDeprecatedCheck: {@code @Deprecated} annotations should declare a
 * non-empty {@code since} attribute.
 *
 * @author Akmal Sarhan
 */
public class SpringDeprecatedInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String MISSING_SINCE_MESSAGE = "@Deprecated should declare a since attribute";

    private static final String EMPTY_SINCE_MESSAGE = "@Deprecated since attribute must not be empty";

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
                if (!isDeprecatedAnnotation(annotation)) {
                    return;
                }
                PsiNameValuePair since = findSinceAttribute(annotation);
                if (since == null) {
                    holder.registerProblem(annotation, MISSING_SINCE_MESSAGE, ProblemHighlightType.WARNING,
                            new AddDeprecatedSinceFix());
                    return;
                }
                if ("".equals(since.getLiteralValue())) {
                    holder.registerProblem(annotation, EMPTY_SINCE_MESSAGE, ProblemHighlightType.WARNING,
                            new AddDeprecatedSinceFix());
                }
            }
        };
    }

    private static boolean isDeprecatedAnnotation(PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return "Deprecated".equals(name) || "java.lang.Deprecated".equals(name);
    }

    private static PsiNameValuePair findSinceAttribute(PsiAnnotation annotation) {
        for (PsiNameValuePair attribute : annotation.getParameterList().getAttributes()) {
            if ("since".equals(attribute.getName())) {
                return attribute;
            }
        }
        return null;
    }

    private static final class AddDeprecatedSinceFix implements LocalQuickFix {

        private static final String DEFAULT_SINCE = "TODO";

        @Override
        @NotNull
        public String getName() {
            return "Add @Deprecated since attribute";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiAnnotation annotation)) {
                return;
            }
            PsiFile file = annotation.getContainingFile();
            Document document = PsiDocumentManager.getInstance(project).getDocument(file);
            if (document == null) {
                return;
            }
            document.replaceString(annotation.getTextRange().getStartOffset(), annotation.getTextRange().getEndOffset(),
                    buildReplacementText(annotation));
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }

        private static String buildReplacementText(PsiAnnotation annotation) {
            StringBuilder replacement = new StringBuilder("@").append(annotationSourceName(annotation)).append("(");
            PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
            PsiNameValuePair since = findSinceAttribute(annotation);
            if (since == null) {
                replacement.append("since = \"").append(DEFAULT_SINCE).append("\"");
                for (PsiNameValuePair attribute : attributes) {
                    replacement.append(", ").append(attribute.getText());
                }
            }
            else {
                for (int i = 0; i < attributes.length; i++) {
                    if (i > 0) {
                        replacement.append(", ");
                    }
                    PsiNameValuePair attribute = attributes[i];
                    if ("since".equals(attribute.getName())) {
                        replacement.append("since = \"").append(DEFAULT_SINCE).append("\"");
                    }
                    else {
                        replacement.append(attribute.getText());
                    }
                }
            }
            return replacement.append(")").toString();
        }

        private static String annotationSourceName(PsiAnnotation annotation) {
            String text = annotation.getText();
            int end = text.indexOf('(');
            if (end < 0) {
                end = text.length();
            }
            if (text.startsWith("@") && end > 1) {
                return text.substring(1, end);
            }
            if (annotation.getNameReferenceElement() != null) {
                return annotation.getNameReferenceElement().getText();
            }
            return "Deprecated";
        }

    }

}
