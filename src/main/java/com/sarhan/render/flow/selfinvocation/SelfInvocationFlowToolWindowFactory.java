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

package com.sarhan.render.flow.selfinvocation;

import javax.swing.JComponent;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.content.ContentFactory;
import com.sarhan.render.flow.shared.FlowGraph;
import com.sarhan.render.flow.shared.FlowToolWindowSupport;
import org.jetbrains.annotations.NotNull;

/**
 * Tool window that renders the transactional self-invocation flow.
 *
 * @author Akmal Sarhan
 */
public final class SelfInvocationFlowToolWindowFactory implements ToolWindowFactory {

    public static final String TOOL_WINDOW_ID = "Spring Buddy Flow";

    @Override
    public void createToolWindowContent(@NotNull Project project,
                                          @NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("Self-Invocation Flow");
        toolWindow.setStripeTitle("Self-Invoke");

        ContentFactory cf = ContentFactory.getInstance();
        JComponent placeholder = FlowToolWindowSupport.placeholder(
                "Click \u26A0 on a method flagged for self-invocation indirection<br/>"
                        + "to see the call chain that bypasses the Spring proxy.");
        toolWindow.getContentManager().addContent(cf.createContent(placeholder, "", false));
    }

    public static void showFlow(@NotNull Project project, @NotNull PsiMethod caller) {
        FlowGraph flow = ReadAction.compute(() ->
                new SelfInvocationFlowBuilder(project).buildFromCaller(caller));
        FlowToolWindowSupport.showFlow(project, TOOL_WINDOW_ID,
                caller.getName(), flow);
    }
}
