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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Mirrors the Checkstyle RegexpSinglelineJava rule:
 * <pre>
 *   format = "org\.mockito\.(Mockito|BDDMockito)\.(when|doThrow|doAnswer)"
 *   message = "Please use BDD-style (given, when, then) using BDDMockito imports."
 * </pre>
 * <p>
 * Flags BDDMockito calls accessed via a qualified class reference and offers a
 * quick-fix to use qualified Mockito calls instead of wildcard static imports.
 *
 * @author Akmal Sarhan
 */
public class SpringMockitoStyleInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final java.util.Set<String> BANNED_METHODS =
            java.util.Set.of("when", "doThrow", "doAnswer");

    private static final java.util.Set<String> BANNED_QUALIFIERS =
            java.util.Set.of(
                    "org.mockito.BDDMockito",
                    "BDDMockito"
            );

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

                PsiReferenceExpression methodRef = call.getMethodExpression();
                String methodName = methodRef.getReferenceName();
                if (methodName == null || !BANNED_METHODS.contains(methodName)) {
                    return;
                }

                // Check if the qualifier resolves to Mockito or BDDMockito class
                PsiExpression qualifier = methodRef.getQualifierExpression();
                if (qualifier == null) {
                    return; // statically imported — already fine if it's BDDMockito
                }

                String qualText = qualifier.getText();
                if (!BANNED_QUALIFIERS.contains(qualText)) {
                    return;
                }

                if (qualifier instanceof PsiReferenceExpression qRef && qRef.resolve() == null) {
                    holder.registerProblem(
                            call,
                            "Use Mockito.when(...) instead of BDDMockito static imports",
                            ProblemHighlightType.WARNING,
                            new ConvertToMockitoWhenFix()
                    );
                    return;
                }

                // Verify it really resolves to the mockito class
                if (qualifier instanceof PsiReferenceExpression qRef) {
                    PsiElement resolved = qRef.resolve();
                    if (!(resolved instanceof PsiClass cls)) {
                        return;
                    }
                    String fqn = cls.getQualifiedName();
                    if (fqn == null) {
                        return;
                    }
                    if (!fqn.startsWith("org.mockito.")) {
                        return;
                    }
                }

                holder.registerProblem(
                        call,
                        "Use Mockito.when(...) instead of BDDMockito static imports",
                        ProblemHighlightType.WARNING,
                        new ConvertToMockitoWhenFix()
                );
            }
        };
    }

    private static final class ConvertToMockitoWhenFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Use Mockito.when";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethodCallExpression call = descriptor.getPsiElement() instanceof PsiMethodCallExpression c ? c
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethodCallExpression.class);
            if (call == null || !(call.getContainingFile() instanceof PsiJavaFile javaFile)) {
                return;
            }
            replaceWithMockitoQualifier(project, call);
        }

        private static void replaceWithMockitoQualifier(Project project, PsiMethodCallExpression call) {
            String methodName = call.getMethodExpression().getReferenceName();
            if (methodName == null) {
                return;
            }
            String text = call.getText();
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            String replacement = text.replaceFirst(java.util.regex.Pattern.quote(qualifier.getText()) + "\\.", "Mockito.");
            call.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacement, call));
        }
    }
}
