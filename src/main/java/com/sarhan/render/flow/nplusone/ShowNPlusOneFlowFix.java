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

package com.sarhan.render.flow.nplusone;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/** Opens the N+1 query flow diagram for the flagged lazy association access. */
/**
 * @author Akmal Sarhan
 */
public final class ShowNPlusOneFlowFix implements LocalQuickFix {

    @Override
    @Nls
    @NotNull
    public String getFamilyName() {
        return "Show N+1 query flow diagram";
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PsiMethodCallExpression || element instanceof PsiReferenceExpression) {
            NPlusOneFlowToolWindowFactory.showFlow(project, element);
            return;
        }
        PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
        if (call != null) {
            NPlusOneFlowToolWindowFactory.showFlow(project, call);
            return;
        }
        PsiReferenceExpression ref = PsiTreeUtil.getParentOfType(element, PsiReferenceExpression.class);
        if (ref != null) {
            NPlusOneFlowToolWindowFactory.showFlow(project, ref);
        }
    }
}
