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

package com.sarhan.render.flow;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Quick fix that opens the Ghost-Save Flow tool window for the flagged mutation.
 *
 * <p>Despite being registered as a {@link LocalQuickFix}, this does not modify
 * source code — it simply navigates the developer to the visual flow diagram.
 *
 * @author Akmal Sarhan
 */
public final class ShowGhostSaveFlowFix implements LocalQuickFix {

    @Override
    @Nls
    @NotNull
    public String getFamilyName() {
        return "Show ghost-save flow diagram";
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PsiMethodCallExpression call = element instanceof PsiMethodCallExpression mce
                ? mce
                : PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
        if (call != null) {
            GhostSaveFlowToolWindowFactory.showFlow(project, call);
        }
    }
}
