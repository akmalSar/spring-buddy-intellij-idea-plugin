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

import java.util.Map;

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
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import org.jetbrains.annotations.NotNull;

/**
 * Detects primitive types used for JPA entity IDs or Spring Data repository
 * parameters and return types where wrapper types are safer. Primitives cannot
 * represent {@code null}, yet identifiers are null before persist and query results
 * may be absent.
 *
 * @author Akmal Sarhan
 */
public class SpringJpaPrimitiveRepositoryTypesInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final Map<String, String> WRAPPERS = Map.of(
            "boolean", "Boolean",
            "byte", "Byte",
            "short", "Short",
            "int", "Integer",
            "long", "Long",
            "float", "Float",
            "double", "Double",
            "char", "Character");

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitField(@NotNull PsiField field) {
                if (isPrimitiveWrapperCandidate(field.getType()) && isEntityIdField(field)) {
                    registerTypeProblem(holder, field.getTypeElement(),
                            "JPA entity ID uses primitive '" + field.getType().getCanonicalText()
                                    + "'; use wrapper '" + wrapperType(field.getType()) + "'");
                }
            }

            @Override
            public void visitParameter(@NotNull PsiParameter parameter) {
                PsiMethod method = method(parameter);
                if (method != null && isRepositoryMethod(method) && isPrimitiveWrapperCandidate(parameter.getType())) {
                    registerTypeProblem(holder, parameter.getTypeElement(),
                            "Spring Data repository parameter uses primitive '" + parameter.getType().getCanonicalText()
                                    + "'; use wrapper '" + wrapperType(parameter.getType()) + "'");
                }
            }

            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiType returnType = method.getReturnType();
                if (returnType != null && isRepositoryMethod(method) && hasAnnotation(method.getModifierList(), "Query")
                        && isPrimitiveWrapperCandidate(returnType)) {
                    registerTypeProblem(holder, method.getReturnTypeElement(),
                            "Spring Data repository query returns primitive '" + returnType.getCanonicalText()
                                    + "'; use wrapper '" + wrapperType(returnType) + "' for nullable results");
                }
            }
        };
    }

    private static void registerTypeProblem(ProblemsHolder holder, PsiTypeElement typeElement, String message) {
        if (typeElement != null) {
            holder.registerProblem(typeElement, message, ProblemHighlightType.WARNING, new UseWrapperTypeFix());
        }
    }

    private static boolean isEntityIdField(PsiField field) {
        return hasAnnotation(field.getModifierList(), "Id") && isJpaEntityType(field.getContainingClass());
    }

    private static boolean isJpaEntityType(PsiClass psiClass) {
        return psiClass != null && SpringInspectionUtil.isJpaEntity(psiClass);
    }

    private static PsiMethod method(PsiParameter parameter) {
        return parameter.getDeclarationScope() instanceof PsiMethod method ? method : null;
    }

    private static boolean isRepositoryMethod(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        return containingClass != null && isRepositoryType(containingClass);
    }

    private static boolean isRepositoryType(PsiClass psiClass) {
        return SpringInspectionUtil.isSpringDataRepository(psiClass);
    }

    private static boolean hasAnnotation(PsiModifierList modifiers, String shortName) {
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if (shortName.equals(name) || (name != null && name.endsWith("." + shortName))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrimitiveWrapperCandidate(PsiType type) {
        return WRAPPERS.containsKey(type.getCanonicalText());
    }

    private static String wrapperType(PsiType type) {
        return WRAPPERS.get(type.getCanonicalText());
    }

    private static final class UseWrapperTypeFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Use wrapper type";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PsiTypeElement typeElement)) {
                return;
            }
            String wrapper = wrapperType(typeElement.getType());
            if (wrapper == null) {
                return;
            }
            typeElement.replace(JavaPsiFacade.getElementFactory(project).createTypeElementFromText(wrapper, typeElement));
        }
    }
}
