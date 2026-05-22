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
public class AddBuilderDefaultFix implements LocalQuickFix {

    @Override
    @NotNull
    public String getName() {
        return MyMessageBundle.message("inspection.builderdefault.value.fix.name");
    }

    @Override
    @NotNull
    public String getFamilyName() {
        return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (!(element instanceof PsiField field)) {
            return;
        }

        PsiModifierList modifierList = field.getModifierList();
        if (modifierList == null) {
            return;
        }

        PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project)
                .createAnnotationFromText("@Builder.Default", field);
        modifierList.addBefore(annotation, modifierList.getFirstChild());

        PsiClass containingClass = field.getContainingClass();
        if (containingClass != null) {
            LombokAnnotationUtil.ensureImport(project, containingClass, "lombok.Builder");
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();
        if (containingClass != null) {
            LombokAnnotationUtil.reformatSafely(project, containingClass);
        }
    }
}
