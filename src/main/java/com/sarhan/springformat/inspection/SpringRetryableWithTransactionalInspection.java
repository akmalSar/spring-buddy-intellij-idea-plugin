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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * Detects methods annotated with both {@code @Retryable} and {@code @Transactional}.
 * <p>
 * When a {@code @Transactional} method fails mid-transaction, Spring rolls back
 * the current transaction. Spring Retry then catches the exception, waits/backoff,
 * and calls the method again — starting a <b>brand-new</b> transaction. Any work
 * that succeeded before the failure in the first attempt is already undone by the
 * rollback, yet non-transactional side-effects (external API calls, file writes,
 * message publishes) are <b>not</b> rolled back and will be executed again on each
 * retry. This leads to duplicate side-effects, inconsistent state, and data loss.
 *
 * <p><b>Safe alternatives:</b>
 * <ul>
 *   <li>Move retry logic outside the transaction boundary (retry the service call,
 *       not the transactional method).</li>
 *   <li>Make the transactional method idempotent so retries are harmless.</li>
 *   <li>Use a saga/outbox pattern for external side-effects instead of doing them
 *       inside the retried transaction.</li>
 * </ul>
 *
 * @author Spring Buddy
 * @author Akmal Sarhan
 */
public class SpringRetryableWithTransactionalInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiModifierList modifiers = method.getModifierList();
                if (modifiers == null) {
                    return;
                }
                boolean hasRetryable = false;
                boolean hasTransactional = false;
                PsiAnnotation retryableAnnotation = null;
                for (PsiAnnotation annotation : modifiers.getAnnotations()) {
                    if (isRetryableAnnotation(annotation)) {
                        hasRetryable = true;
                        retryableAnnotation = annotation;
                    }
                    if (isTransactionalAnnotation(annotation)) {
                        hasTransactional = true;
                    }
                }
                // Also check class-level @Retryable / @Transactional
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null) {
                    PsiModifierList classMods = containingClass.getModifierList();
                    if (classMods != null) {
                        for (PsiAnnotation annotation : classMods.getAnnotations()) {
                            if (isRetryableAnnotation(annotation)) {
                                hasRetryable = true;
                                if (retryableAnnotation == null) {
                                    retryableAnnotation = annotation;
                                }
                            }
                            if (isTransactionalAnnotation(annotation)) {
                                hasTransactional = true;
                            }
                        }
                    }
                }
                if (hasRetryable && hasTransactional) {
                    PsiIdentifier nameIdentifier = method.getNameIdentifier();
                    holder.registerProblem((nameIdentifier != null) ? nameIdentifier : method,
                            "@Retryable combined with @Transactional: retries start a new transaction, "
                                    + "but side-effects before the failure are not rolled back and will be duplicated",
                            ProblemHighlightType.ERROR);
                }
            }
        };
    }

    private static boolean isRetryableAnnotation(@NotNull PsiAnnotation annotation) {
        return SpringInspectionUtil.isAnnotation(annotation, "org.springframework.retry.annotation.Retryable", "Retryable");
    }

    private static boolean isTransactionalAnnotation(@NotNull PsiAnnotation annotation) {
        return SpringInspectionUtil.isAnyAnnotation(annotation,
                "org.springframework.transaction.annotation.Transactional",
                "jakarta.transaction.Transactional",
                "javax.transaction.Transactional");
    }
}
