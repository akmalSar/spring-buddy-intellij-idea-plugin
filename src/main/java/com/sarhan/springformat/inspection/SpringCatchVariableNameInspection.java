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
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * SpringCatchCheck: catch clause variables must not use single-letter names or
 * the wide-eye {@code o_o} name.
 *
 * @author Akmal Sarhan
 */
public class SpringCatchVariableNameInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String SINGLE_LETTER_MESSAGE = "Catch variable should not use a single-letter name";

    private static final String WIDE_EYE_MESSAGE = "Catch variable should not be named 'o_o'";

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitCatchSection(@NotNull PsiCatchSection section) {
                super.visitCatchSection(section);
                PsiParameter param = section.getParameter();
                if (param == null) {
                    return;
                }
                String name = param.getName();
                String message = problemMessage(name);
                if (message == null) {
                    return;
                }
                PsiElement nameId = param.getNameIdentifier();
                holder.registerProblem(
                        (nameId != null) ? nameId : param,
                        message,
                        ProblemHighlightType.WARNING,
                        new RenameToExFix()
                );
            }
        };
    }

    private static String problemMessage(String name) {
        if (name == null) {
            return null;
        }
        if (name.length() == 1) {
            return SINGLE_LETTER_MESSAGE;
        }
        if ("o_o".equalsIgnoreCase(name)) {
            return WIDE_EYE_MESSAGE;
        }
        return null;
    }

    private static final class RenameToExFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Rename to 'ex'";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement nameId = descriptor.getPsiElement();
            PsiElement paramEl = nameId.getParent();
            if (!(paramEl instanceof PsiParameter param)) {
                return;
            }
            if (!(param.getParent() instanceof PsiCatchSection catchSection)) {
                return;
            }
            PsiCodeBlock body = catchSection.getCatchBlock();
            if (body == null) {
                return;
            }

            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            String replacementName = replacementName(body);

            // Rename all usages inside the catch body first to avoid stale references
            PsiReference[] refs = ReferencesSearch.search(param, new LocalSearchScope(body))
                    .findAll().toArray(PsiReference.EMPTY_ARRAY);
            for (PsiReference ref : refs) {
                PsiElement refEl = ref.getElement();
                if (refEl instanceof PsiReferenceExpression expr && expr.getReferenceNameElement() != null) {
                    expr.getReferenceNameElement().replace(factory.createIdentifier(replacementName));
                }
            }
            // Then rename the declaration
            param.setName(replacementName);
        }

        private static String replacementName(PsiCodeBlock body) {
            return isNameUsed(body, "ex") ? "exception" : "ex";
        }

        private static boolean isNameUsed(PsiCodeBlock body, String name) {
            for (PsiLocalVariable variable : PsiTreeUtil.findChildrenOfType(body, PsiLocalVariable.class)) {
                if (name.equals(variable.getName())) {
                    return true;
                }
            }
            for (PsiParameter parameter : PsiTreeUtil.findChildrenOfType(body, PsiParameter.class)) {
                if (name.equals(parameter.getName())) {
                    return true;
                }
            }
            return false;
        }
    }
}
