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

package com.sarhan.render.flow.lazyinit;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the lazy-init flow diagram for the flagged getter call. Does not
 * modify source code.
 *
 * @author Akmal Sarhan
 */
public final class ShowLazyInitFlowFix implements LocalQuickFix {

    @Override
    @Nls
    @NotNull
    public String getFamilyName() {
        return "Show lazy-initialization flow diagram";
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
            LazyInitFlowToolWindowFactory.showFlow(project, call);
        }
    }
}
