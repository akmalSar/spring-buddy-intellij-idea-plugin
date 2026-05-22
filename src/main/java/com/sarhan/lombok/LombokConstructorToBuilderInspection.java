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

package com.sarhan.lombok;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */
public class LombokConstructorToBuilderInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(
            @NotNull ProblemsHolder holder,
            boolean isOnTheFly) {

                if (!LombokInspectionUtil.isInJavaFile(holder.getFile())) {
                    return PsiElementVisitor.EMPTY_VISITOR;
                }

        return new JavaElementVisitor() {

            @Override
            public void visitNewExpression(@NotNull PsiNewExpression expression) {
                super.visitNewExpression(expression);

                // Must have arguments — skip no-arg constructors
                PsiExpressionList argList = expression.getArgumentList();
                if ((argList == null) || (argList.getExpressions().length == 0)) {
                    return;
                }

                // Resolve the constructed class
                PsiJavaCodeReferenceElement classRef = expression.getClassReference();
                if (classRef == null) {
                    return;
                }

                PsiElement resolved = classRef.resolve();
                if (!(resolved instanceof PsiClass psiClass)) {
                    return;
                }

                // Skip non-static inner classes — Lombok @Builder is not supported.
                if (isNonStaticInner(psiClass)) {
                    return;
                }

                // Skip generic classes — Lombok @Builder interacts poorly with
                // generics. The raw builder() call loses type parameters, and
                // diamond-inferred types cannot be propagated to the builder.
                if (hasTypeParameters(psiClass)) {
                    return;
                }

                // Check that the class has @Builder, @SuperBuilder, or @AllArgsConstructor
                boolean hasBuilder = hasAnnotation(psiClass, "lombok.Builder")
                        || hasAnnotation(psiClass, "Builder")
                        || hasAnnotation(psiClass, "lombok.SuperBuilder")
                        || hasAnnotation(psiClass, "lombok.experimental.SuperBuilder")
                        || hasAnnotation(psiClass, "SuperBuilder");
                boolean hasAllArgsConstructor = hasAnnotation(psiClass, "lombok.AllArgsConstructor")
                        || hasAnnotation(psiClass, "AllArgsConstructor");

                if (!hasBuilder && !hasAllArgsConstructor) {
                    return;
                }

                // Resolve the constructor being called
                PsiMethod constructor = expression.resolveConstructor();
                if (constructor == null) {
                    return;
                }

                // Make sure we can map params → fields
                PsiParameter[] params = constructor.getParameterList().getParameters();
                if (params.length == 0) {
                    return;
                }

                // Check each param name matches an accessible field
                for (PsiParameter param : params) {
                    PsiField field = psiClass.findFieldByName(param.getName(), true);
                    if (field == null) {
                        return; // Can't safely map — bail out
                    }
                }

                // Safety: @Builder (not @SuperBuilder) does not generate builder methods
                // for inherited fields. Converting constructor args for inherited fields
                // would produce non-compiling code like .createdAt(...) when the field is
                // declared in a superclass.
                if (!hasSuperBuilderAnnotation(psiClass)
                        && hasParamForInheritedField(psiClass, params)) {
                    return;
                }

                // Safety: don't offer to convert when the parent has plain @Builder.
                // Adding @Builder to a subclass of a @Builder parent causes the
                // "builder() cannot hide builder()" compile error.
                if (!hasBuilder && extendsPlainBuilderParent(psiClass)) {
                    return;
                }

                holder.registerProblem(
                        expression,
                        MyMessageBundle.message("inspection.constructor.to.builder.problem.descriptor"),
                        ProblemHighlightType.WEAK_WARNING,
                        hasBuilder ? new ConstructorToBuilderFix() : new AllArgsConstructorToBuilderFix()
                );
            }

            private boolean hasAnnotation(PsiClass psiClass, String annotationFqn) {
                PsiModifierList modifierList = psiClass.getModifierList();
                if (modifierList == null) {
                    return false;
                }
                for (PsiAnnotation annotation : modifierList.getAnnotations()) {
                    String qName = annotation.getQualifiedName();
                    if (annotationFqn.equals(qName)) {
                        return true;
                    }
                    if ((qName != null) && qName.endsWith("." + annotationFqn)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean isNonStaticInner(PsiClass psiClass) {
                return (psiClass.getContainingClass() != null)
                        && !psiClass.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC);
            }

            private boolean hasTypeParameters(PsiClass psiClass) {
                return (psiClass.getTypeParameterList() != null)
                        && (psiClass.getTypeParameterList().getTypeParameters().length > 0);
            }

            private boolean hasSuperBuilderAnnotation(PsiClass psiClass) {
                PsiModifierList modifiers = psiClass.getModifierList();
                if (modifiers == null) {
                    return false;
                }
                for (PsiAnnotation a : modifiers.getAnnotations()) {
                    String fqn = a.getQualifiedName();
                    if ("lombok.SuperBuilder".equals(fqn)
                            || "lombok.experimental.SuperBuilder".equals(fqn)
                            || "SuperBuilder".equals(fqn)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean hasParamForInheritedField(PsiClass psiClass, PsiParameter[] params) {
                for (PsiParameter param : params) {
                    PsiField field = psiClass.findFieldByName(param.getName(), true);
                    if ((field != null) && (field.getContainingClass() != psiClass)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean extendsPlainBuilderParent(PsiClass psiClass) {
                for (PsiClass sup : psiClass.getSupers()) {
                    if ("java.lang.Object".equals(sup.getQualifiedName())) {
                        continue;
                    }
                    PsiModifierList supMods = sup.getModifierList();
                    if (supMods == null) {
                        continue;
                    }
                    for (PsiAnnotation a : supMods.getAnnotations()) {
                        String fqn = a.getQualifiedName();
                        if ("lombok.Builder".equals(fqn) || "Builder".equals(fqn)) {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

}
