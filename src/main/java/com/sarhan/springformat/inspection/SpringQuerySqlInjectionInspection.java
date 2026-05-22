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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Detects potential SQL injection in @Query annotations where the value
 * is built via string concatenation, String.format, or contains a '+' operator.
 * Native queries and SpEL with #{} are harder to analyze statically, but
 * plain string concatenation of query text is a clear runtime risk.
 *
 * @author Akmal Sarhan
 */
public class SpringQuerySqlInjectionInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                String name = annotation.getQualifiedName();
                if (name == null && annotation.getNameReferenceElement() != null) {
                    name = annotation.getNameReferenceElement().getText();
                }
                if (name == null) {
                    return;
                }
                boolean isQuery = "Query".equals(name) || name.endsWith(".Query");
                boolean isNativeQuery = "NativeQuery".equals(name) || name.endsWith(".NativeQuery");
                if (!isQuery && !isNativeQuery) {
                    return;
                }

                PsiAnnotationMemberValue valueAttr = annotation.findAttributeValue("value");
                if (valueAttr == null) {
                    valueAttr = annotation.findAttributeValue(null); // default attribute
                }
                if (valueAttr == null) {
                    return;
                }

                if (containsConcatenationOrFormat(valueAttr)) {
                    holder.registerProblem(
                            valueAttr,
                            "@Query built with string concatenation is vulnerable to SQL injection — use parameter placeholders (?1, :name) instead",
                            ProblemHighlightType.ERROR
                    );
                }
            }
        };
    }

    private static boolean containsConcatenationOrFormat(PsiAnnotationMemberValue value) {
        if (value instanceof PsiExpression expr) {
            return hasConcatenation(expr);
        }
        return false;
    }

    private static boolean hasConcatenation(PsiExpression expr) {
        boolean[] found = {false};
        expr.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
                super.visitPolyadicExpression(expression);
                if (expression.getOperationTokenType() == JavaTokenType.PLUS) {
                    // Only flag if at least one operand is not a compile-time string literal.
                    // Literal + literal is folded by the compiler and is not an injection vector.
                    for (PsiExpression operand : expression.getOperands()) {
                        if (!(operand instanceof PsiLiteralExpression)) {
                            found[0] = true;
                            break;
                        }
                    }
                }
            }

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                PsiReferenceExpression methodExpr = expression.getMethodExpression();
                String methodName = methodExpr.getReferenceName();
                if ("format".equals(methodName) || "formatted".equals(methodName) || "concat".equals(methodName)) {
                    found[0] = true;
                }
                // StringBuilder / StringBuffer append chains
                PsiExpression qualifier = methodExpr.getQualifierExpression();
                if (qualifier != null) {
                    String type = (qualifier.getType() != null) ? qualifier.getType().getCanonicalText() : null;
                    if (type != null && (type.contains("StringBuilder") || type.contains("StringBuffer"))) {
                        found[0] = true;
                    }
                }
            }

            @Override
            public void visitNewExpression(@NotNull PsiNewExpression expression) {
                super.visitNewExpression(expression);
                PsiJavaCodeReferenceElement classRef = expression.getClassReference();
                if (classRef != null) {
                    String className = classRef.getQualifiedName();
                    if (className != null && (className.contains("StringBuilder") || className.contains("StringBuffer"))) {
                        found[0] = true;
                    }
                }
            }
        });
        return found[0];
    }
}
