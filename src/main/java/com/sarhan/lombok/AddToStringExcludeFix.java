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
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */
public class AddToStringExcludeFix implements LocalQuickFix {

    private static PsiField findCircularField(PsiClass psiClass) {
        String className = psiClass.getName();
        if (className == null) {
            return null;
        }
        for (PsiField field : psiClass.getFields()) {
            if (!field.isPhysical() || hasToStringExclude(field.getModifierList())) {
                continue;
            }
            String typeName = field.getType().getPresentableText();
            if (typeName.equals(className)
                    || typeName.equals(className + "[]")
                    || typeName.contains("<" + className + ">")) {
                return field;
            }
        }
        return null;
    }

    private static boolean hasToStringExclude(PsiModifierList modifiers) {
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String qName = annotation.getQualifiedName();
            if ("lombok.ToString.Exclude".equals(qName) || "ToString.Exclude".equals(qName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @NotNull
    public String getName() {
        return MyMessageBundle.message("inspection.tostring.002.fix.name");
    }

    @Override
    @NotNull
    public String getFamilyName() {
        return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiAnnotation annotation)) {
            return;
        }
        if (!(annotation.getParent() instanceof PsiModifierList modifierList)) {
            return;
        }
        if (!(modifierList.getParent() instanceof PsiClass psiClass)) {
            return;
        }

        PsiField field = findCircularField(psiClass);
        if (field == null) {
            return;
        }
        PsiModifierList fieldModifiers = field.getModifierList();
        if ((fieldModifiers == null) || hasToStringExclude(fieldModifiers)) {
            return;
        }

        PsiAnnotation exclude = JavaPsiFacade.getElementFactory(project)
                .createAnnotationFromText("@ToString.Exclude", field);
        fieldModifiers.addBefore(exclude, fieldModifiers.getFirstChild());
        LombokAnnotationUtil.ensureImport(project, psiClass, "lombok.ToString");

        PsiDocumentManager.getInstance(project).commitAllDocuments();
        LombokAnnotationUtil.reformatSafely(project, psiClass);
    }
}
