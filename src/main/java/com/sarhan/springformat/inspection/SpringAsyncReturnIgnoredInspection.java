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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

/**
 * Detects call sites where the result of an {@code @Async} method that returns a
 * {@code Future}/{@code CompletableFuture}/{@code ListenableFuture} is silently discarded
 * (used as an expression statement). When the returned future is ignored, exceptions
 * thrown inside the async task are swallowed and never surface to the caller.
 *
 * @author Akmal Sarhan
 */
public class SpringAsyncReturnIgnoredInspection extends AbstractBaseJavaLocalInspectionTool {

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
                PsiElement parent = call.getParent();
                if (!(parent instanceof PsiExpressionStatement)) {
                    return;
                }
                PsiMethod resolved = call.resolveMethod();
                if (resolved == null || !hasAsync(resolved)) {
                    return;
                }
                PsiType ret = resolved.getReturnType();
                if (ret == null || PsiTypes.voidType().equals(ret)) {
                    return;
                }
                if (isFutureType(ret)) {
                    holder.registerProblem(call,
                            "Result of @Async method is discarded; exceptions inside the async task will be swallowed",
                            ProblemHighlightType.ERROR, new HandleDiscardedAsyncResultFix());
                }
            }
        };
    }

    private static boolean hasAsync(PsiMethod method) {
        for (PsiAnnotation annotation : method.getAnnotations()) {
            String fqn = annotation.getQualifiedName();
            if (fqn == null) {
                continue;
            }
            if ("org.springframework.scheduling.annotation.Async".equals(fqn) || "Async".equals(fqn)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFutureType(PsiType type) {
        if (!(type instanceof PsiClassType classType)) {
            return false;
        }
        String canonical = classType.rawType().getCanonicalText();
        if (canonical == null) {
            return false;
        }
        return "java.util.concurrent.Future".equals(canonical)
                || "java.util.concurrent.CompletableFuture".equals(canonical)
                || "java.util.concurrent.CompletionStage".equals(canonical)
                || "org.springframework.util.concurrent.ListenableFuture".equals(canonical)
                || "Future".equals(canonical)
                || "CompletableFuture".equals(canonical)
                || "CompletionStage".equals(canonical)
                || "ListenableFuture".equals(canonical);
    }

    private static boolean isChainableFutureType(PsiType type) {
        if (!(type instanceof PsiClassType classType)) {
            return false;
        }
        String canonical = classType.rawType().getCanonicalText();
        if (canonical == null) {
            return false;
        }
        return "java.util.concurrent.CompletableFuture".equals(canonical)
                || "java.util.concurrent.CompletionStage".equals(canonical)
                || "CompletableFuture".equals(canonical)
                || "CompletionStage".equals(canonical);
    }

    static final class HandleDiscardedAsyncResultFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Chain exception handler so async failures are not swallowed";
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
            if (call == null) {
                return;
            }
            PsiElement parent = call.getParent();
            if (!(parent instanceof PsiExpressionStatement statement)) {
                return;
            }
            PsiMethod resolved = call.resolveMethod();
            if (resolved == null) {
                return;
            }
            PsiType ret = resolved.getReturnType();
            if (ret == null) {
                return;
            }
            if (isChainableFutureType(ret)) {
                String replacement = call.getText()
                        + ".exceptionally((Throwable ex) -> {\n"
                        + "    log.error(\"An unexpected Exception occurred during Async [{}]\", ex.getMessage(), ex);\n"
                        + "    return null;\n"
                        + "});";
                PsiStatement newStatement = JavaPsiFacade.getElementFactory(project).createStatementFromText(replacement, statement);
                statement.replace(newStatement);
            } else {
                String methodName = resolved.getName();
                String varName = (methodName != null) ? methodName + "Result" : "future";
                if (!PsiNameHelper.getInstance(project).isIdentifier(varName)) {
                    varName = "future";
                }
                String typeText = ret.getCanonicalText();
                String declaration = typeText + " " + varName + " = " + call.getText() + ";";
                PsiStatement newStatement = JavaPsiFacade.getElementFactory(project).createStatementFromText(declaration, statement);
                statement.replace(newStatement);
            }
            if (call.getContainingFile() instanceof PsiJavaFile javaFile) {
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(javaFile);
            }
        }
    }
}
