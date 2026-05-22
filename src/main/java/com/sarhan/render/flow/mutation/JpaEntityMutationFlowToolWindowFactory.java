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

package com.sarhan.render.flow.mutation;

import javax.swing.JComponent;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.ui.content.ContentFactory;
import com.sarhan.render.flow.shared.FlowGraph;
import com.sarhan.render.flow.shared.FlowToolWindowSupport;
import org.jetbrains.annotations.NotNull;

/**
 * Tool window for read-only and dirty-checking entity mutation flows.
 *
 * @author Akmal Sarhan
 */
public final class JpaEntityMutationFlowToolWindowFactory implements ToolWindowFactory {

    public static final String TOOL_WINDOW_ID = "Spring Buddy Entity Mutation Flow";

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("Entity Mutation Flow");
        toolWindow.setStripeTitle("Entity Mut");
        JComponent placeholder = FlowToolWindowSupport.placeholder(
                "Click \u26A0 on a flagged JPA entity mutation<br/>to see the managed-entity flow.");
        toolWindow.getContentManager().addContent(ContentFactory.getInstance().createContent(placeholder, "", false));
    }

    public static void showFlow(@NotNull Project project,
                                @NotNull PsiElement element,
                                @NotNull MutationFlowMode mode) {
        FlowGraph flow = ReadAction.compute(() -> new JpaEntityMutationFlowBuilder(project).build(element, mode));
        FlowToolWindowSupport.showFlow(project, TOOL_WINDOW_ID, tabTitle(element, mode), flow);
    }

    @NotNull
    private static String tabTitle(@NotNull PsiElement element, @NotNull MutationFlowMode mode) {
        if (element instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            if (name != null) {
                return name;
            }
        }
        return mode == MutationFlowMode.READ_ONLY ? "read-only mutation" : "dirty checking";
    }
}
