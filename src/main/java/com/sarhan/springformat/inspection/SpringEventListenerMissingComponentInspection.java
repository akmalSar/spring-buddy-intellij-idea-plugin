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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.sarhan.lombok.LombokAnnotationUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects {@code @EventListener} or {@code @TransactionalEventListener} methods
 * declared in classes that are not Spring-managed beans.
 * Such listeners are never registered with the application context and will
 * never be invoked at runtime.
 *
 * @author Akmal Sarhan
 */
public class SpringEventListenerMissingComponentInspection extends AbstractBaseJavaLocalInspectionTool {

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
                PsiAnnotation listener = findEventListenerAnnotation(method);
                if (listener == null) {
                    return;
                }
                PsiClass containingClass = method.getContainingClass();
                if (containingClass == null || LombokAnnotationUtil.isSpringManaged(containingClass)) {
                    return;
                }
                holder.registerProblem(listener,
                        "@EventListener method in non-Spring-managed class will never be invoked; annotate the class with @Component",
                        ProblemHighlightType.ERROR,
                        new AddComponentFix());
            }
        };
    }

    private static PsiAnnotation findEventListenerAnnotation(PsiMethod method) {
        for (PsiAnnotation annotation : method.getAnnotations()) {
            String fqn = annotation.getQualifiedName();
            if (fqn == null) {
                continue;
            }
            if ("org.springframework.context.event.EventListener".equals(fqn)
                    || "org.springframework.transaction.event.TransactionalEventListener".equals(fqn)
                    || "EventListener".equals(fqn)
                    || "TransactionalEventListener".equals(fqn)) {
                return annotation;
            }
        }
        return null;
    }

    private static final class AddComponentFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Add @Component";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethod method = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                    descriptor.getPsiElement(), PsiMethod.class);
            if (method == null) {
                return;
            }
            PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            PsiAnnotation componentAnn = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText("@org.springframework.stereotype.Component", containingClass);
            containingClass.getModifierList().addBefore(componentAnn,
                    containingClass.getModifierList().getFirstChild());
            LombokAnnotationUtil.ensureImport(project, containingClass,
                    "org.springframework.stereotype.Component");
            LombokAnnotationUtil.reformatSafely(project, containingClass);
        }
    }
}
