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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * SpringJpaModifyingWithoutClearAutomatically: flags Spring Data JPA {@code @Modifying} annotations
 * that don't set {@code clearAutomatically = true}. Without it, the persistence context can hold
 * stale entities after the bulk update/delete runs. Quick-fix sets {@code clearAutomatically = true}.
 *
 * @author Akmal Sarhan
 */
public class SpringJpaModifyingWithoutClearAutomaticallyInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                super.visitAnnotation(annotation);
                String fqn = annotation.getQualifiedName();
                if (fqn == null) {
                    return;
                }
                if (!"org.springframework.data.jpa.repository.Modifying".equals(fqn)
                        && !"Modifying".equals(fqn)) {
                    return;
                }
                PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("clearAutomatically");
                if (value instanceof PsiLiteralExpression literal && Boolean.TRUE.equals(literal.getValue())) {
                    return;
                }
                holder.registerProblem(annotation,
                        "@Modifying without clearAutomatically=true may leave stale entities in the persistence context",
                        ProblemHighlightType.ERROR,
                        new AddClearAutomaticallyFix());
            }
        };
    }

    private static final class AddClearAutomaticallyFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Add clearAutomatically = true";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (!(element instanceof PsiAnnotation annotation)) {
                return;
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiAnnotation replacement = factory.createAnnotationFromText(
                    "@" + annotation.getQualifiedName() + "(clearAutomatically = true)", annotation);
            // Preserve any other attributes the user may have set.
            for (com.intellij.psi.PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
                if (!"clearAutomatically".equals(pair.getAttributeName()) && pair.getValue() != null) {
                    replacement.setDeclaredAttributeValue(pair.getAttributeName(), pair.getValue());
                }
            }
            replacement.setDeclaredAttributeValue("clearAutomatically",
                    factory.createExpressionFromText("true", annotation));
            annotation.replace(replacement);
        }
    }
}
