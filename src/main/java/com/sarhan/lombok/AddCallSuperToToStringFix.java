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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */
public class AddCallSuperToToStringFix implements LocalQuickFix {

    @Override
    @NotNull
    public String getName() {
        return MyMessageBundle.message("inspection.tostring.001.fix.name");
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

        PsiAnnotationParameterList paramList = annotation.getParameterList();
        PsiNameValuePair[] existing = paramList.getAttributes();

        PsiElement parent = annotation.getParent();

        if (existing.length == 0) {
            String text = "@" + annotation.getQualifiedName()
                    + "(callSuper = true, onlyExplicitlyIncluded = true)";
            try {
                PsiAnnotation newAnn = com.intellij.psi.JavaPsiFacade.getElementFactory(project)
                        .createAnnotationFromText(text, element);
                annotation.replace(newAnn);
            } catch (Exception ex) {
                return;
            }
        } else {
            boolean hasCallSuper = false;
            boolean hasOnlyExplicitlyIncluded = false;
            for (PsiNameValuePair pair : existing) {
                if ("callSuper".equals(pair.getAttributeName())) {
                    hasCallSuper = true;
                }
                if ("onlyExplicitlyIncluded".equals(pair.getAttributeName())) {
                    hasOnlyExplicitlyIncluded = true;
                }
            }
            if (!hasCallSuper || !hasOnlyExplicitlyIncluded) {
                String attrs = annotation.getParameterList().getText().substring(
                        1, annotation.getParameterList().getTextLength() - 1);
                StringBuilder text = new StringBuilder("@" + annotation.getQualifiedName() + "(")
                        .append(attrs);
                if (!hasCallSuper) {
                    text.append(", callSuper = true");
                }
                if (!hasOnlyExplicitlyIncluded) {
                    text.append(", onlyExplicitlyIncluded = true");
                }
                text.append(")");
                try {
                    PsiAnnotation newAnn = com.intellij.psi.JavaPsiFacade.getElementFactory(project)
                            .createAnnotationFromText(text.toString(), element);
                    annotation.replace(newAnn);
                } catch (Exception ex) {
                    return;
                }
            }
        }

        if (parent != null) {
            PsiDocumentManager.getInstance(project).commitAllDocuments();
            LombokAnnotationUtil.reformatSafely(project, parent);
        }
    }
}
