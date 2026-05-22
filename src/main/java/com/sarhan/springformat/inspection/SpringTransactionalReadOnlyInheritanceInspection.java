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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects write operations inside a {@code @Transactional(readOnly = true)} context,
 * including inherited class-level readOnly settings that make methods read-only.
 *
 * @author Akmal Sarhan
 */
public class SpringTransactionalReadOnlyInheritanceInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final java.util.Set<String> WRITE_METHODS = java.util.Set.of(
            "save", "saveAll", "delete", "deleteAll", "deleteById", "update", "persist", "merge", "flush", "remove");

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
                PsiClass containingClass = method.getContainingClass();
                PsiAnnotation classTransactional = (containingClass != null)
                        ? findTransactionalAnnotation(containingClass.getModifierList()) : null;
                PsiAnnotation methodTransactional = findTransactionalAnnotation(method.getModifierList());

                Boolean classReadOnly = (classTransactional != null) ? explicitReadOnlyValue(classTransactional) : null;
                Boolean methodReadOnly = (methodTransactional != null) ? explicitReadOnlyValue(methodTransactional) : null;

                boolean inReadOnlyContext = false;
                if (Boolean.TRUE.equals(methodReadOnly)) {
                    inReadOnlyContext = true;
                } else if (methodTransactional != null && !Boolean.FALSE.equals(methodReadOnly)
                        && Boolean.TRUE.equals(classReadOnly)) {
                    inReadOnlyContext = true;
                }

                if (!inReadOnlyContext || !hasWriteOperation(method)) {
                    return;
                }
                PsiIdentifier nameIdentifier = method.getNameIdentifier();
                holder.registerProblem((nameIdentifier != null) ? nameIdentifier : method,
                        "Method writes inside @Transactional(readOnly = true); explicitly set readOnly = false",
                        ProblemHighlightType.ERROR,
                        new SetReadOnlyFalseFix());
            }
        };
    }

    private static boolean hasWriteOperation(PsiMethod method) {
        boolean[] found = {false};
        method.accept(new com.intellij.psi.JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                PsiMethod target = call.resolveMethod();
                if (target == null) {
                    return;
                }

                PsiClass targetClass = target.getContainingClass();

                // Case 1: target method itself has @Transactional without readOnly=true.
                // For persistence classes (Spring Data repos / EntityManager) only flag
                // methods whose name indicates an actual write operation. @Transactional
                // on a read-only query method does not make it a write.
                PsiAnnotation methodTx = findTransactionalAnnotation(target.getModifierList());
                if (methodTx != null) {
                    Boolean readOnly = explicitReadOnlyValue(methodTx);
                    if (readOnly == null || !readOnly) {
                        if (targetClass != null && SpringInspectionUtil.isSpringDataRepository(targetClass)) {
                            String name = target.getName();
                            if (name != null && WRITE_METHODS.contains(name)) {
                                found[0] = true;
                                return;
                            }
                        } else if (isLikelyWriteMethodName(target)) {
                            found[0] = true;
                            return;
                        }
                    }
                }

                // Case 2: target method's class has @Transactional without readOnly=true
                // Only flag for REQUIRED/REQUIRES_NEW propagation (default/implicit write context).
                // SUPPORTS/NOT_SUPPORTED/NEVER/MANDATORY do not start a transaction.
                // Also only flag if the method name suggests a write — calling a getter
                // from a class that has @Transactional does not make it a write operation.

                if (targetClass != null) {
                    PsiAnnotation classTx = findTransactionalAnnotation(targetClass.getModifierList());
                    if (classTx != null) {
                        Boolean readOnly = explicitReadOnlyValue(classTx);
                        if ((readOnly == null || !readOnly) && isWritePropagation(classTx)
                                && isLikelyWriteMethodName(target)) {
                            found[0] = true;
                            return;
                        }
                    }
                }

                // Case 3: Spring Data Repository implicit write methods
                if (targetClass != null && SpringInspectionUtil.isSpringDataRepository(targetClass)) {
                    String name = target.getName();
                    if (name != null && WRITE_METHODS.contains(name)) {
                        found[0] = true;
                    }
                }
            }
        });
        return found[0];
    }

    private static @Nullable PsiAnnotation findTransactionalAnnotation(PsiModifierList modifiers) {
        if (modifiers == null) {
            return null;
        }
        return SpringInspectionUtil.findAnyAnnotation(modifiers,
                "org.springframework.transaction.annotation.Transactional",
                "jakarta.transaction.Transactional",
                "javax.transaction.Transactional");
    }

    private static boolean isReadOnly(PsiAnnotation annotation) {
        Boolean value = explicitReadOnlyValue(annotation);
        return Boolean.TRUE.equals(value);
    }

    private static boolean isLikelyWriteMethodName(PsiMethod method) {
        String name = method.getName();
        if (name == null) {
            return false;
        }
        if (WRITE_METHODS.contains(name)) {
            return true;
        }
        if (name.startsWith("set") || name.startsWith("add") || name.startsWith("remove")
                || name.startsWith("update") || name.startsWith("delete")
                || name.startsWith("increment") || name.startsWith("decrement")) {
            return true;
        }
        return false;
    }

    private static boolean isWritePropagation(PsiAnnotation annotation) {
        for (PsiNameValuePair attribute : annotation.getParameterList().getAttributes()) {
            if ("propagation".equals(attribute.getName()) || "value".equals(attribute.getName())) {
                if (attribute.getValue() != null) {
                    String text = attribute.getValue().getText();
                    if (text != null && (text.contains("SUPPORTS") || text.contains("NOT_SUPPORTED")
                            || text.contains("NEVER") || text.contains("MANDATORY"))) {
                        return false;
                    }
                    if (text != null && (text.contains("REQUIRES_NEW") || text.contains("REQUIRED")
                            || text.contains("NESTED"))) {
                        return true;
                    }
                }
            }
        }
        return true;
    }

    private static @Nullable Boolean explicitReadOnlyValue(PsiAnnotation annotation) {
        for (PsiNameValuePair attribute : annotation.getParameterList().getAttributes()) {
            if ("readOnly".equals(attribute.getName()) && attribute.getValue() != null) {
                return "true".equals(attribute.getValue().getText());
            }
        }
        return null;
    }

    private static final class SetReadOnlyFalseFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Set readOnly = false";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethod method = descriptor.getPsiElement() instanceof PsiMethod m ? m
                    : PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
            PsiAnnotation annotation = (method != null) ? findTransactionalAnnotation(method.getModifierList()) : null;
            if (annotation == null) {
                return;
            }
            PsiAnnotationMemberValue value = JavaPsiFacade.getElementFactory(project)
                    .createExpressionFromText("false", annotation);
            annotation.setDeclaredAttributeValue("readOnly", value);
        }
    }
}
