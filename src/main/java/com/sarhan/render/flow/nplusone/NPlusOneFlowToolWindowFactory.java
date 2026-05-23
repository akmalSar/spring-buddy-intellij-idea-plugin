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

import javax.swing.JComponent;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.ui.content.ContentFactory;
import com.sarhan.render.flow.shared.FlowGraph;
import com.sarhan.render.flow.shared.FlowToolWindowSupport;
import org.jetbrains.annotations.NotNull;

/** Tool window that renders predicted N+1 query flows. */
/**
 * @author Akmal Sarhan
 */
public final class NPlusOneFlowToolWindowFactory implements ToolWindowFactory {

    public static final String TOOL_WINDOW_ID = "Spring Buddy Flow";

    @Override
    public void createToolWindowContent(@NotNull Project project,
                                        @NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("N+1 Flow");
        toolWindow.setStripeTitle("N+1");

        ContentFactory cf = ContentFactory.getInstance();
        JComponent placeholder = FlowToolWindowSupport.placeholder(
                "Click \u26A0 on a flagged lazy association<br/>"
                        + "to see the repository result flow into the loop.");
        toolWindow.getContentManager().addContent(cf.createContent(placeholder, "", false));
    }

    public static void showFlow(@NotNull Project project, @NotNull PsiElement sinkElement) {
        FlowGraph flow = ReadAction.compute(() ->
                new NPlusOneFlowBuilder(project).buildFromSink(sinkElement));
        String tabTitle = ReadAction.compute(() -> tabTitle(sinkElement));
        FlowToolWindowSupport.showFlow(project, TOOL_WINDOW_ID, tabTitle, flow);
    }

    @NotNull
    private static String tabTitle(@NotNull PsiElement sinkElement) {
        if (sinkElement instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            return (name != null) ? name : "N+1";
        }
        if (sinkElement instanceof PsiReferenceExpression ref) {
            String name = ref.getReferenceName();
            return (name != null) ? name : "N+1";
        }
        return "N+1";
    }
}
