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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * SpringCollectionReturnsNull: flags {@code return null} inside a method whose declared return
 * type is a collection ({@code List}, {@code Set}, {@code Map}, {@code Collection},
 * {@code Iterable}). Quick-fix replaces with the matching empty collection.
 *
 * @author Akmal Sarhan
 */
public class SpringCollectionReturnsNullInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
                super.visitReturnStatement(statement);
                PsiExpression value = statement.getReturnValue();
                if (!(value instanceof PsiLiteralExpression lit)) {
                    return;
                }
                if (!"null".equals(lit.getText())) {
                    return;
                }
                PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
                if (method == null) {
                    return;
                }
                PsiType ret = method.getReturnType();
                String empty = emptyCollectionExprFor(ret);
                if (empty == null) {
                    return;
                }
                holder.registerProblem(value,
                        "Method returning a collection should not return null; return an empty collection instead",
                        ProblemHighlightType.ERROR,
                        new ReplaceWithEmptyFix(empty));
            }
        };
    }

    private static String emptyCollectionExprFor(PsiType type) {
        if (!(type instanceof PsiClassType ct)) {
            return null;
        }
        String fqn = ct.rawType().getCanonicalText();
        return switch (fqn) {
            case "java.util.List" -> "java.util.Collections.emptyList()";
            case "java.util.Set" -> "java.util.Collections.emptySet()";
            case "java.util.Map" -> "java.util.Collections.emptyMap()";
            case "java.util.Collection", "java.lang.Iterable" -> "java.util.Collections.emptyList()";
            default -> null;
        };
    }

    private static final class ReplaceWithEmptyFix implements LocalQuickFix {
        private final String replacement;

        ReplaceWithEmptyFix(String replacement) {
            this.replacement = replacement;
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return "Replace null with empty collection";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiExpression expr)) {
                return;
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiExpression created = factory.createExpressionFromText(this.replacement, expr);
            expr.replace(created);
        }
    }
}
