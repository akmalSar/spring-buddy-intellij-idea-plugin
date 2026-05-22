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

import java.util.concurrent.atomic.AtomicBoolean;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiTryStatement;
import org.jetbrains.annotations.NotNull;

/**
 * Detects @Transactional methods that call another @Transactional method and catch
 * RuntimeException without re-throwing it. The nested transaction may already be
 * marked rollback-only, causing an UnexpectedRollbackException when the outer
 * transaction commits.
 *
 * @author Akmal Sarhan
 */
public class SpringUnexpectedRollbackExceptionInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                PsiAnnotation outerTx = findTransactionalAnnotation(method);
                if (outerTx == null) {
                    return;
                }
                if (method.getBody() == null) {
                    return;
                }
                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null) {
                    return;
                }
                method.getBody().accept(new JavaRecursiveElementVisitor() {
                    @Override
                    public void visitTryStatement(@NotNull PsiTryStatement tryStatement) {
                        super.visitTryStatement(tryStatement);

                        AtomicBoolean callsTransactionalExternalMethod = new AtomicBoolean(false);

                        if (tryStatement.getTryBlock() != null) {
                            tryStatement.getTryBlock().accept(new JavaRecursiveElementVisitor() {
                                @Override
                                public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                                    super.visitMethodCallExpression(expression);
                                    PsiMethod target = expression.resolveMethod();
                                    if (target == null) {
                                        return;
                                    }
                                    PsiClass targetClass = target.getContainingClass();
                                    if (targetClass == null || targetClass.equals(containingClass)) {
                                        return;
                                    }
                                    PsiAnnotation targetTx = findTransactionalAnnotation(target);
                                    if (targetTx != null && participatesInOuterTransaction(targetTx)) {
                                        callsTransactionalExternalMethod.set(true);
                                    }
                                }
                            });
                        }

                        if (!callsTransactionalExternalMethod.get()) {
                            return;
                        }

                        PsiCatchSection[] catches = tryStatement.getCatchSections();
                        boolean catchesRuntimeEx = false;
                        boolean rethrowsInCatch = false;
                        boolean explicitlyMarksRollbackOnly = false;

                        for (PsiCatchSection catchSection : catches) {
                            if (catchSection.getCatchType() == null) {
                                continue;
                            }
                            String typeName = catchSection.getCatchType().getCanonicalText();
                            if (isRuntimeException(typeName)) {
                                catchesRuntimeEx = true;
                                if (catchSection.getCatchBlock() != null) {
                                    AtomicBoolean hasThrow = new AtomicBoolean(false);
                                    AtomicBoolean hasSetRollbackOnly = new AtomicBoolean(false);
                                    catchSection.getCatchBlock().accept(new JavaRecursiveElementVisitor() {
                                        @Override
                                        public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
                                            hasThrow.set(true);
                                        }

                                        @Override
                                        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                                            super.visitMethodCallExpression(expression);
                                            String name = expression.getMethodExpression().getReferenceName();
                                            if ("setRollbackOnly".equals(name)) {
                                                hasSetRollbackOnly.set(true);
                                            }
                                        }
                                    });
                                    if (hasThrow.get()) {
                                        rethrowsInCatch = true;
                                    }
                                    if (hasSetRollbackOnly.get()) {
                                        explicitlyMarksRollbackOnly = true;
                                    }
                                }
                            }
                        }

                        if (catchesRuntimeEx && !rethrowsInCatch && !explicitlyMarksRollbackOnly) {
                            PsiCatchSection firstCatch = catches[0];
                            holder.registerProblem(firstCatch.getFirstChild(),
                                    "Catching a RuntimeException from a @Transactional method call without re-throwing may cause an UnexpectedRollbackException; the transaction was already marked rollback-only",
                                    ProblemHighlightType.ERROR);
                        }
                    }
                });
            }
        };
    }

    private static boolean isRuntimeException(String typeName) {
        return "RuntimeException".equals(typeName)
                || "java.lang.RuntimeException".equals(typeName)
                || "Exception".equals(typeName)
                || "java.lang.Exception".equals(typeName)
                || "Throwable".equals(typeName)
                || "java.lang.Throwable".equals(typeName);
    }

    private static PsiAnnotation findTransactionalAnnotation(PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers != null) {
            PsiAnnotation annotation = SpringInspectionUtil.findAnyAnnotation(modifiers,
                "org.springframework.transaction.annotation.Transactional",
                "jakarta.transaction.Transactional",
                "javax.transaction.Transactional");
            if (annotation != null) {
                return annotation;
            }
        }
        PsiClass cls = method.getContainingClass();
        if (cls != null) {
            PsiModifierList classModifiers = cls.getModifierList();
            if (classModifiers != null) {
                return SpringInspectionUtil.findAnyAnnotation(classModifiers,
                    "org.springframework.transaction.annotation.Transactional",
                    "jakarta.transaction.Transactional",
                    "javax.transaction.Transactional");
            }
        }
        return null;
    }

    private static boolean participatesInOuterTransaction(PsiAnnotation annotation) {
        for (com.intellij.psi.PsiNameValuePair attribute : annotation.getParameterList().getAttributes()) {
            String text = (attribute.getValue() != null) ? attribute.getValue().getText() : "";
            if (text.contains("REQUIRES_NEW") || text.contains("NOT_SUPPORTED")
                    || text.contains("NEVER")) {
                return false;
            }
        }
        return true;
    }
}
