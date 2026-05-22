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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
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
public class AllArgsConstructorToBuilderFix implements LocalQuickFix {

    private static boolean isBuilderAnnotation(String qName) {
        return "lombok.Builder".equals(qName)
                || "Builder".equals(qName)
                || "lombok.SuperBuilder".equals(qName)
                || "lombok.experimental.SuperBuilder".equals(qName)
                || "SuperBuilder".equals(qName);
    }

    private static boolean extendsPlainBuilderParent(PsiClass psiClass) {
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

    private static boolean hasParamForInheritedField(PsiClass psiClass, PsiParameter[] params) {
        for (PsiParameter param : params) {
            PsiField field = psiClass.findFieldByName(param.getName(), true);
            if ((field != null) && (field.getContainingClass() != psiClass)) {
                return true;
            }
        }
        return false;
    }

    private static String qualifiedName(PsiClass psiClass) {
        StringBuilder name = new StringBuilder(psiClass.getName());
        PsiClass parent = psiClass.getContainingClass();
        while (parent != null) {
            name.insert(0, parent.getName() + ".");
            parent = parent.getContainingClass();
        }
        return name.toString();
    }

    private static boolean isNonStaticInner(PsiClass psiClass) {
        return (psiClass.getContainingClass() != null)
                && !psiClass.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC);
    }

    private static boolean hasTypeParameters(PsiClass psiClass) {
        return (psiClass.getTypeParameterList() != null)
                && (psiClass.getTypeParameterList().getTypeParameters().length > 0);
    }

    @Override
    @NotNull
    public String getName() {
        return MyMessageBundle.message("inspection.constructor.to.builder.fix.name");
    }

    @Override
    @NotNull
    public String getFamilyName() {
        return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiNewExpression expression)) {
            return;
        }

        PsiExpressionList argList = expression.getArgumentList();
        if (argList == null) {
            return;
        }

        PsiJavaCodeReferenceElement classRef = expression.getClassReference();
        if (classRef == null) {
            return;
        }

        PsiClass psiClass = (PsiClass) classRef.resolve();
        if (psiClass == null) {
            return;
        }

        // Never convert non-static inner classes — Lombok @Builder is unsupported.
        if (isNonStaticInner(psiClass)) {
            return;
        }

        // Never convert generic classes — builder() loses type parameters.
        if (hasTypeParameters(psiClass)) {
            return;
        }

        // Never convert if the parent has plain @Builder — adding @Builder
        // to the subclass would cause "builder() cannot hide builder()".
        if (extendsPlainBuilderParent(psiClass)) {
            return;
        }

        // Never convert if constructor params map to inherited fields —
        // plain @Builder does not generate builder methods for them.
        PsiMethod constructor = expression.resolveConstructor();
        if (constructor != null) {
            PsiParameter[] params = constructor.getParameterList().getParameters();
            if (hasParamForInheritedField(psiClass, params)) {
                return;
            }
        }

        String className = classRef.getReferenceName();
        PsiExpression[] args = argList.getExpressions();

        // Build builder expression — qualify static inner classes
        String builderClassName = qualifiedName(psiClass);

        StringBuilder builder = new StringBuilder();
        builder.append(builderClassName).append(".builder()");

        if (constructor != null) {
            PsiParameter[] params = constructor.getParameterList().getParameters();
            for (int i = 0; i < params.length; i++) {
                String fieldName = params[i].getName();
                PsiExpression arg = (i < args.length) ? args[i] : null;
                if (arg != null) {
                    builder.append("\n    .").append(fieldName)
                            .append("(").append(arg.getText()).append(")");
                }
            }
        }
        builder.append("\n    .build()");

        // Replace constructor call with builder
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiExpression builderExpr = factory.createExpressionFromText(builder.toString(), expression);
        expression.replace(builderExpr);

        // Add @Builder if not present — never remove existing annotations.
        // Removing @AllArgsConstructor or @NoArgsConstructor eagerly can break
        // remaining constructor call sites (static factory methods, Spring DI,
        // JPA proxying, etc.). Annotations become truly redundant only when
        // ALL constructor call sites for the class have been converted.
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList == null) {
            return;
        }

        boolean hasBuilder = false;
        for (PsiAnnotation ann : modifierList.getAnnotations()) {
            String qName = ann.getQualifiedName();
            if (isBuilderAnnotation(qName)) {
                hasBuilder = true;
                break;
            }
        }

        if (!hasBuilder) {
            PsiAnnotation builderAnnotation = factory.createAnnotationFromText(
                    "@Builder", psiClass);
            modifierList.addBefore(builderAnnotation, modifierList.getFirstChild());
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();
        if (!hasBuilder) {
            LombokAnnotationUtil.ensureImport(project, psiClass, "lombok.Builder");
        }
        LombokAnnotationUtil.reformatSafely(project, psiClass);
    }
}
