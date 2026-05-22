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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NotNull;

/**
 * Detects annotation style violations: empty parentheses on no-argument annotations,
 * explicit {@code value=} where compact form is allowed, single-element arrays with braces,
 * and trailing commas in array values.
 *
 * @author Akmal Sarhan
 */
public class SpringAnnotationUseStyleInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                checkClosingParens(annotation, holder);
                checkElementStyle(annotation, holder);
                checkTrailingArrayComma(annotation, holder);
            }
        };
    }

    private static void checkClosingParens(PsiAnnotation annotation, ProblemsHolder holder) {
        if (annotation.getParameterList().getAttributes().length == 0
                && annotation.getParameterList().getText().contains("(")) {
            holder.registerProblem(annotation.getParameterList(),
                    "Annotation without arguments should omit parentheses",
                    ProblemHighlightType.WARNING,
                    new ReplaceAnnotationFix("Remove empty annotation parentheses"));
        }
    }

    private static void checkElementStyle(PsiAnnotation annotation, ProblemsHolder holder) {
        PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
        if (attributes.length == 1 && "value".equals(attributes[0].getName())) {
            holder.registerProblem(attributes[0],
                    "Annotation value should use compact style",
                    ProblemHighlightType.WARNING,
                    new ReplaceAnnotationFix("Use compact annotation style"));
        }
        for (PsiNameValuePair attribute : attributes) {
            PsiAnnotationMemberValue value = attribute.getValue();
            if (isSingleElementArray(value)) {
                holder.registerProblem(value,
                        "Single-element annotation array should omit braces",
                        ProblemHighlightType.WARNING,
                        new ReplaceAnnotationFix("Remove single-element annotation array braces"));
            }
        }
    }

    private static void checkTrailingArrayComma(PsiAnnotation annotation, ProblemsHolder holder) {
        for (PsiNameValuePair attribute : annotation.getParameterList().getAttributes()) {
            PsiAnnotationMemberValue value = attribute.getValue();
            if (value instanceof PsiArrayInitializerMemberValue array && hasTrailingComma(array)) {
                holder.registerProblem(array,
                        "Annotation array should not have a trailing comma",
                        ProblemHighlightType.WARNING,
                        new ReplaceAnnotationFix("Remove annotation array trailing comma"));
            }
        }
    }

    private static boolean isSingleElementArray(PsiAnnotationMemberValue value) {
        return value instanceof PsiArrayInitializerMemberValue array && array.getInitializers().length == 1;
    }

    private static boolean hasTrailingComma(PsiArrayInitializerMemberValue array) {
        String text = array.getText().trim();
        return text.length() > 2 && text.matches("(?s).*,\\s*}");
    }

    private static class ReplaceAnnotationFix implements LocalQuickFix {

        private final String name;

        ReplaceAnnotationFix(String name) {
            this.name = name;
        }

        @Override
        @NotNull
        public String getName() {
            return this.name;
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return "Normalize annotation style";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiAnnotation annotation = findAnnotation(descriptor);
            if (annotation == null) {
                return;
            }
            PsiAnnotation replacement = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText(normalizedText(annotation), annotation);
            annotation.replace(replacement);
        }

        private static PsiAnnotation findAnnotation(ProblemDescriptor descriptor) {
            com.intellij.psi.PsiElement element = descriptor.getPsiElement();
            while (element != null && !(element instanceof PsiAnnotation)) {
                element = element.getParent();
            }
            return (PsiAnnotation) element;
        }

        private static String normalizedText(PsiAnnotation annotation) {
            String qualifiedName = annotation.getQualifiedName();
            String name = (qualifiedName != null) ? qualifiedName : annotation.getNameReferenceElement().getText();
            PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
            if (attributes.length == 0) {
                return "@" + name;
            }
            if (attributes.length == 1 && (attributes[0].getName() == null || "value".equals(attributes[0].getName()))) {
                return "@" + name + "(" + normalizedValue(attributes[0].getValue()) + ")";
            }
            StringBuilder result = new StringBuilder("@").append(name).append("(");
            for (int i = 0; i < attributes.length; i++) {
                if (i > 0) {
                    result.append(", ");
                }
                result.append(attributes[i].getName()).append(" = ").append(normalizedValue(attributes[i].getValue()));
            }
            return result.append(")").toString();
        }

        private static String normalizedValue(PsiAnnotationMemberValue value) {
            if (value instanceof PsiArrayInitializerMemberValue array) {
                PsiAnnotationMemberValue[] initializers = array.getInitializers();
                if (initializers.length == 1) {
                    return initializers[0].getText();
                }
                String text = array.getText();
                return text.replaceAll(",\\s*}", "}");
            }
            return (value != null) ? value.getText() : "";
        }
    }
}
