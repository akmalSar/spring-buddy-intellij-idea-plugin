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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Reports usages of System.out / System.err method calls (println/print/printf)
 * and offers a quick-fix to replace them with logger calls (log.info / log.error).
 *
 * @author Akmal Sarhan
 */
public class SpringSystemOutErrInspection extends AbstractBaseJavaLocalInspectionTool {

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
                PsiReferenceExpression methodExpr = call.getMethodExpression();
                PsiExpression qualifier = methodExpr.getQualifierExpression();
                if (!(qualifier instanceof PsiReferenceExpression ref)) {
                    return;
                }
                String qualifierText = ref.getText();
                if (!("System.out".equals(qualifierText) || "System.err".equals(qualifierText))) {
                    return;
                }
                String methodName = methodExpr.getReferenceName();
                if (methodName == null) {
                    return;
                }
                if (!("print".equals(methodName) || "println".equals(methodName) || "printf".equals(methodName))) {
                    return;
                }
                String message = "Use logger instead of '" + qualifierText + "'";
                holder.registerProblem(call.getMethodExpression(), message, ProblemHighlightType.WARNING,
                        new ReplaceWithLoggerFix(qualifierText));
            }
        };
    }

    private static class ReplaceWithLoggerFix implements LocalQuickFix {

        private final String systemQualifier;

        ReplaceWithLoggerFix(String systemQualifier) {
            this.systemQualifier = systemQualifier;
        }

        @Override
        @NotNull
        public String getName() {
            return "Replace with logger call";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (!(element.getParent() instanceof PsiMethodCallExpression call)) {
                return;
            }
            // Ensure enclosing class has @Slf4j annotation (Lombok)
            PsiClass containingClass = PsiTreeUtil.getParentOfType(call, PsiClass.class);
            if (containingClass != null && !hasLombokSlf4j(containingClass)) {
                if (containingClass.getModifierList() != null) {
                    containingClass.getModifierList().addAnnotation("lombok.extern.slf4j.Slf4j");
                }
            }

            String level = this.systemQualifier.equals("System.err") ? "error" : "info";

            String methodName = call.getMethodExpression().getReferenceName();
            PsiExpression[] args = call.getArgumentList().getExpressions();

            String newText;
            if ("printf".equals(methodName) && args.length >= 1) {
                // Convert printf(format, args...) -> log.level(String.format(format, args...))
                StringBuilder sb = new StringBuilder();
                sb.append("log.").append(level).append("(String.format(");
                // use original arguments inside String.format
                sb.append(joinTexts(args));
                sb.append("))");
                newText = sb.toString();
            } else if (args.length == 1 && containsStringConcatenation(args[0])) {
                // Convert concatenation to SLF4J placeholders
                PsiExpression expr = args[0];
                PlaceholderResult result = buildPlaceholder(expr);
                StringBuilder sb = new StringBuilder();
                sb.append("log.").append(level).append("(");
                sb.append('"').append(escape(result.format)).append('"');
                for (String arg : result.args) {
                    sb.append(", ").append(arg);
                }
                sb.append(")");
                newText = sb.toString();
            } else {
                // Default: preserve argument list but switch to log.level(...)
                if (args.length == 1 && !producesString(args[0])) {
                    newText = "log." + level + "(\"{}\", " + args[0].getText() + ")";
                } else {
                    String argsText = call.getArgumentList().getText();
                    newText = "log." + level + argsText;
                }
            }

            call.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(newText, call));
        }

        private static boolean hasLombokSlf4j(PsiClass psiClass) {
            if (psiClass.getModifierList() == null) {
                return false;
            }
            for (PsiAnnotation ann : psiClass.getModifierList().getAnnotations()) {
                String fqn = ann.getQualifiedName();
                if (fqn == null) {
                    continue;
                }
                if ("lombok.extern.slf4j.Slf4j".equals(fqn) || "Slf4j".equals(fqn)) {
                    return true;
                }
            }
            return false;
        }

        private static String joinTexts(PsiExpression[] expressions) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < expressions.length; idx++) {
                if (idx > 0) {
                    sb.append(", ");
                }
                sb.append(expressions[idx].getText());
            }
            return sb.toString();
        }

        private static class PlaceholderResult {
            final String format;
            final java.util.List<String> args = new java.util.ArrayList<>();

            PlaceholderResult(String format) {
                this.format = format;
            }
        }

        private static PlaceholderResult buildPlaceholder(PsiExpression expression) {
            StringBuilder fmt = new StringBuilder();
            java.util.List<String> args = new java.util.ArrayList<>();
            collectForPlaceholder(expression, fmt, args);
            PlaceholderResult pr = new PlaceholderResult(fmt.toString());
            pr.args.addAll(args);
            return pr;
        }

        private static void collectForPlaceholder(PsiExpression expression, StringBuilder fmt, java.util.List<String> args) {
            if (expression instanceof PsiPolyadicExpression poly && poly.getOperationTokenType() == com.intellij.psi.JavaTokenType.PLUS) {
                for (PsiExpression op : poly.getOperands()) {
                    collectForPlaceholder(op, fmt, args);
                }
                return;
            }
            if (expression instanceof PsiLiteralExpression literal && literal.getValue() instanceof String text) {
                fmt.append(text);
                return;
            }
            // non-literal -> placeholder; wrap placeholder in brackets for clearer output (e.g. "test[{}]")
            fmt.append("[{}]");
            args.add(expression.getText());
        }

        private static boolean containsStringConcatenation(PsiExpression expression) {
            if (expression instanceof PsiPolyadicExpression poly && poly.getOperationTokenType() == com.intellij.psi.JavaTokenType.PLUS) {
                for (PsiExpression operand : poly.getOperands()) {
                    if (operand.getType() != null && "java.lang.String".equals(operand.getType().getCanonicalText())) {
                        return true;
                    }
                }
                return poly.getType() != null && "java.lang.String".equals(poly.getType().getCanonicalText());
            }
            return false;
        }

        private static boolean producesString(PsiExpression expr) {
            if (expr instanceof PsiLiteralExpression lit && lit.getValue() instanceof String) {
                return true;
            }
            PsiType type = expr.getType();
            return type != null && "java.lang.String".equals(type.getCanonicalText());
        }

        private static String escape(String text) {
            return text.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }
    }
}

