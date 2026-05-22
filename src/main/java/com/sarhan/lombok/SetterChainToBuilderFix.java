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

import java.util.List;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */
public class SetterChainToBuilderFix implements LocalQuickFix {

    private static String qualifiedName(PsiClass psiClass) {
        StringBuilder name = new StringBuilder(psiClass.getName());
        PsiClass parent = psiClass.getContainingClass();
        while (parent != null) {
            name.insert(0, parent.getName() + ".");
            parent = parent.getContainingClass();
        }
        return name.toString();
    }

    @Override
    @NotNull
    public String getName() {
        return MyMessageBundle.message("inspection.setter.chain.to.builder.fix.name");
    }

    @Override
    @NotNull
    public String getFamilyName() {
        return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiLocalVariable variable)) {
            return;
        }

        PsiNewExpression newExpr = (PsiNewExpression) variable.getInitializer();
        if (newExpr == null) {
            return;
        }

        PsiJavaCodeReferenceElement classRef = newExpr.getClassReference();
        if (classRef == null) {
            return;
        }
        PsiElement resolved = classRef.resolve();
        PsiClass psiClass = (resolved instanceof PsiClass c) ? c : null;
        String className = classRef.getReferenceName();

        List<PsiExpressionStatement> setters =
                LombokSetterChainToBuilderInspection.collectSetterStatements(variable);
        if (setters.isEmpty()) {
            return;
        }

        // Build:  Outer.InnerClass.builder().fieldA(x).fieldB(y).build()
        String builderClassName = (psiClass != null) ? qualifiedName(psiClass) : className;
        StringBuilder builder = new StringBuilder();
        builder.append(builderClassName).append(".builder()");

        for (PsiExpressionStatement stmt : setters) {
            PsiMethodCallExpression call =
                    (PsiMethodCallExpression) stmt.getExpression();
            String methodName = call.getMethodExpression().getReferenceName();
            PsiExpression arg = call.getArgumentList().getExpressions()[0];

            // setVatAmount → vatAmount
            String fieldName = null;
            if (methodName != null) {
                fieldName = Character.toLowerCase(methodName.charAt(3))
                        + methodName.substring(4);
            }

            builder.append("\n    .").append(fieldName)
                    .append("(").append(arg.getText()).append(")");
        }

        builder.append("\n    .build()");

        // Replace initializer of the declaration
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        PsiExpression builderExpr = factory.createExpressionFromText(
                builder.toString(), variable);
        variable.setInitializer(builderExpr);

        // Delete the now-redundant setter statements
        for (PsiExpressionStatement stmt : setters) {
            stmt.delete();
        }

        // Reformat
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        LombokAnnotationUtil.reformatSafely(project, variable.getParent());
    }
}
