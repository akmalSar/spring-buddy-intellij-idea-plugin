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

import java.util.ArrayList;
import java.util.List;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects string concatenation inside SLF4J log statements.
 * <p>
 * SLF4J supports parameterized messages (e.g. {@code log.info("user={}", user)}).
 * String concatenation forces eager evaluation even when the log level is disabled,
 * wasting CPU and memory. Use placeholders instead.
 *
 * @author Akmal Sarhan
 */
public class SpringSlf4jStringConcatenationInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String SLF4J_LOGGER = "org.slf4j.Logger";

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                if (!isLogMethodCall(call)) {
                    return;
                }
                PsiExpression[] args = call.getArgumentList().getExpressions();
                if (args.length == 0) {
                    return;
                }
                PsiExpression first = args[0];
                if (containsStringConcatenation(first)) {
                    holder.registerProblem(first,
                            "SLF4J log message uses String concatenation; use '[{}]' placeholders instead",
                            ProblemHighlightType.WARNING,
                            new ConvertToPlaceholderFix());
                }
            }
        };
    }

    static boolean isLogMethodCall(PsiMethodCallExpression call) {
        String name = call.getMethodExpression().getReferenceName();
        if (name == null) {
            return false;
        }
        if (!("trace".equals(name) || "debug".equals(name) || "info".equals(name)
                || "warn".equals(name) || "error".equals(name))) {
            return false;
        }
        PsiMethod method = call.resolveMethod();
        if (method != null) {
            PsiClass containing = method.getContainingClass();
            if (containing != null && SLF4J_LOGGER.equals(containing.getQualifiedName())) {
                return true;
            }
        }
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier != null && qualifier.getType() != null
                && SLF4J_LOGGER.equals(qualifier.getType().getCanonicalText())) {
            return true;
        }
        // Lombok-aware fallback: a `log.<level>(...)` call inside a class annotated with @Slf4j
        // (or other Lombok logger annotations) is a logger call even when Lombok processing isn't
        // visible to the analyzer.
        return isLombokGeneratedLoggerCall(call);
    }

    private static boolean isLombokGeneratedLoggerCall(PsiMethodCallExpression call) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression reference)) {
            return false;
        }
        if (!"log".equals(reference.getReferenceName())) {
            return false;
        }
        PsiClass containingClass = PsiTreeUtil.getParentOfType(call, PsiClass.class);
        while (containingClass != null) {
            if (hasLombokLoggerAnnotation(containingClass)) {
                return true;
            }
            containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
        }
        return false;
    }

    private static boolean hasLombokLoggerAnnotation(PsiModifierListOwner owner) {
        if (owner.getModifierList() == null) {
            return false;
        }
        for (PsiAnnotation annotation : owner.getModifierList().getAnnotations()) {
            String fqn = annotation.getQualifiedName();
            if (fqn == null) {
                continue;
            }
            if ("lombok.extern.slf4j.Slf4j".equals(fqn)
                    || "lombok.extern.slf4j.XSlf4j".equals(fqn)
                    || fqn.equals("Slf4j") || fqn.equals("XSlf4j")) {
                return true;
            }
        }
        return false;
    }

    static boolean containsStringConcatenation(PsiExpression expression) {
        if (expression instanceof PsiPolyadicExpression polyadic
                && polyadic.getOperationTokenType() == JavaTokenType.PLUS) {
            for (PsiExpression operand : polyadic.getOperands()) {
                if (operand.getType() != null && "java.lang.String".equals(operand.getType().getCanonicalText())) {
                    // At least one operand is a String -> this is string concatenation
                    return true;
                }
            }
            return polyadic.getType() != null
                    && "java.lang.String".equals(polyadic.getType().getCanonicalText());
        }
        if (expression instanceof PsiBinaryExpression binary
                && binary.getOperationTokenType() == JavaTokenType.PLUS) {
            return binary.getType() != null
                    && "java.lang.String".equals(binary.getType().getCanonicalText());
        }
        return false;
    }

    private static final class ConvertToPlaceholderFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Convert to SLF4J placeholder ('[{}]') message";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiExpression expression)) {
                return;
            }
            PsiMethodCallExpression call = findEnclosingLogCall(expression);
            if (call == null) {
                return;
            }
            PsiExpression[] args = call.getArgumentList().getExpressions();
            if (args.length == 0 || args[0] != expression) {
                return;
            }
            List<PsiExpression> operands = flatten(expression);
            if (operands.isEmpty()) {
                return;
            }
            FormatParts format = new FormatParts();
            List<String> mergedArgs = new ArrayList<>();
            int existingArgumentIndex = 1;
            boolean hasNewPlaceholderArgument = false;
            for (PsiExpression operand : operands) {
                if (operand instanceof PsiLiteralExpression literal && literal.getValue() instanceof String text) {
                    format.appendLiteral(text);
                    int placeholders = placeholderCount(text);
                    for (int i = 0; i < placeholders && existingArgumentIndex < args.length; i++) {
                        mergedArgs.add(args[existingArgumentIndex++].getText());
                    }
                } else if (isStaticFinalConstant(operand)) {
                    String constantValue = staticFinalStringValue(operand);
                    if (constantValue != null && constantValue.contains("{}")) {
                        format.appendLiteral(constantValue);
                        int placeholders = placeholderCount(constantValue);
                        for (int i = 0; i < placeholders && existingArgumentIndex < args.length; i++) {
                            mergedArgs.add(args[existingArgumentIndex++].getText());
                        }
                    } else {
                        format.appendExpression(operand.getText());
                    }
                } else {
                    format.appendLiteral("[{}]");
                    mergedArgs.add(operand.getText());
                    hasNewPlaceholderArgument = true;
                }
            }
            StringBuilder newCall = new StringBuilder();
            newCall.append(call.getMethodExpression().getText()).append("(");
            newCall.append(format.text());
            while (existingArgumentIndex < args.length) {
                mergedArgs.add(args[existingArgumentIndex++].getText());
            }
            for (String value : mergedArgs) {
                newCall.append(", ").append(value);
            }
            newCall.append(")");
            call.replace(JavaPsiFacade.getElementFactory(project)
                    .createExpressionFromText(newCall.toString(), call));
        }

        private static PsiMethodCallExpression findEnclosingLogCall(PsiExpression expression) {
            PsiElement parent = expression.getParent();
            while (parent != null && !(parent instanceof PsiMethodCallExpression)) {
                parent = parent.getParent();
            }
            return (parent instanceof PsiMethodCallExpression call && isLogMethodCall(call)) ? call : null;
        }

        private static List<PsiExpression> flatten(PsiExpression expression) {
            List<PsiExpression> result = new ArrayList<>();
            collect(expression, result);
            return result;
        }

        private static void collect(PsiExpression expression, List<PsiExpression> result) {
            if (expression instanceof PsiPolyadicExpression polyadic
                    && polyadic.getOperationTokenType() == JavaTokenType.PLUS) {
                for (PsiExpression operand : polyadic.getOperands()) {
                    collect(operand, result);
                }
                return;
            }
            if (expression instanceof PsiBinaryExpression binary
                    && binary.getOperationTokenType() == JavaTokenType.PLUS) {
                collect(binary.getLOperand(), result);
                if (binary.getROperand() != null) {
                    collect(binary.getROperand(), result);
                }
                return;
            }
            result.add(expression);
        }

        private static String escape(String text) {
            return text.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }

        private static int placeholderCount(String text) {
            int count = 0;
            int index = 0;
            while ((index = text.indexOf("{}", index)) >= 0) {
                count++;
                index += 2;
            }
            return count;
        }

        private static boolean isStaticFinalConstant(PsiExpression expression) {
            if (!(expression instanceof PsiReferenceExpression reference)
                    || !(reference.resolve() instanceof PsiVariable variable)) {
                return false;
            }
            return variable instanceof PsiField
                    && variable.hasModifierProperty(PsiModifier.STATIC)
                    && variable.hasModifierProperty(PsiModifier.FINAL);
        }

        private static String staticFinalStringValue(PsiExpression expression) {
            if (!(expression instanceof PsiReferenceExpression reference)
                    || !(reference.resolve() instanceof PsiField field)
                    || !field.hasModifierProperty(PsiModifier.STATIC)
                    || !field.hasModifierProperty(PsiModifier.FINAL)
                    || !(field.getInitializer() instanceof PsiLiteralExpression literal)
                    || !(literal.getValue() instanceof String value)) {
                return null;
            }
            return value;
        }

        private static final class FormatParts {

            private final List<String> parts = new ArrayList<>();

            void appendLiteral(String text) {
                if (text.isEmpty()) {
                    return;
                }
                if (!this.parts.isEmpty() && isQuotedLiteral(this.parts.get(this.parts.size() - 1))) {
                    String previous = this.parts.remove(this.parts.size() - 1);
                    String unquoted = previous.substring(1, previous.length() - 1);
                    this.parts.add("\"" + unquoted + escape(text) + "\"");
                    return;
                }
                this.parts.add("\"" + escape(text) + "\"");
            }

            void appendExpression(String text) {
                this.parts.add(text);
            }

            String text() {
                return String.join(" + ", this.parts);
            }

            private static boolean isQuotedLiteral(String text) {
                return text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"");
            }
        }
    }
}
