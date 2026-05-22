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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */
public class ConstructorToBuilderFix implements LocalQuickFix {

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
                && !psiClass.hasModifierProperty(PsiModifier.STATIC);
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
        if (!(element instanceof PsiNewExpression newExpression)) {
            return;
        }

        PsiJavaCodeReferenceElement classRef = newExpression.getClassReference();
        if (classRef == null) {
            return;
        }

        PsiElement resolved = classRef.resolve();
        if (!(resolved instanceof PsiClass psiClass)) {
            return;
        }

        // Never convert non-static inner classes — Lombok @Builder unsupported.
        if (isNonStaticInner(psiClass)) {
            return;
        }

        PsiExpressionList argList = newExpression.getArgumentList();
        if (argList == null) {
            return;
        }

        PsiMethod constructor = newExpression.resolveConstructor();
        if (constructor == null) {
            return;
        }

        PsiParameter[] params = constructor.getParameterList().getParameters();
        PsiExpression[] args = argList.getExpressions();

        String className = psiClass.getName();

        // Build builder expression — qualify static inner classes
        String builderClassName = qualifiedName(psiClass);

        StringBuilder builder = new StringBuilder();
        builder.append(builderClassName).append(".builder()");

        for (int i = 0; (i < params.length) && (i < args.length); i++) {
            String paramName = params[i].getName();
            String argText = args[i].getText();
            builder.append("\n    .").append(paramName).append("(").append(argText).append(")");
        }

        builder.append("\n    .build()");

        // Create replacement expression from text
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiExpression builderExpression = factory.createExpressionFromText(
                builder.toString(), newExpression.getContext()
        );

        // Replace in-place and reformat
        PsiElement replaced = newExpression.replace(builderExpression);
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        LombokAnnotationUtil.reformatSafely(project, replaced);
    }

}
