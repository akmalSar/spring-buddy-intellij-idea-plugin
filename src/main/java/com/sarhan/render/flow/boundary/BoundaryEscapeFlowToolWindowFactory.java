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

package com.sarhan.render.flow.boundary;

import javax.swing.JComponent;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.ui.content.ContentFactory;
import com.sarhan.render.flow.shared.FlowGraph;
import com.sarhan.render.flow.shared.FlowToolWindowSupport;
import org.jetbrains.annotations.NotNull;

/**
 * Tool window that renders the transaction-boundary-escape flow diagram.
 *
 * <p>Mirrors {@code GhostSaveFlowToolWindowFactory} but uses the shared
 * {@link FlowToolWindowSupport} infra so all four flow tool windows are
 * consistent.
 *
 * @author Akmal Sarhan
 */
public final class BoundaryEscapeFlowToolWindowFactory implements ToolWindowFactory {

    public static final String TOOL_WINDOW_ID = "Spring Buddy Boundary-Escape Flow";

    @Override
    public void createToolWindowContent(@NotNull Project project,
                                          @NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("Boundary-Escape Flow");
        toolWindow.setStripeTitle("Tx-Escape");

        ContentFactory cf = ContentFactory.getInstance();
        JComponent placeholder = FlowToolWindowSupport.placeholder(
                "Click \u26A0 on a flagged boundary-escape access<br/>"
                        + "to see the entity flow.");
        toolWindow.getContentManager().addContent(cf.createContent(placeholder, "", false));
    }

    /**
     * Builds and shows the flow for a sink call (mutator or lazy getter)
     * flagged by {@code SpringEntityTransactionBoundaryEscapeDataFlowInspection}.
     */
    public static void showFlow(@NotNull Project project,
                                  @NotNull PsiMethodCallExpression sinkCall) {
        FlowGraph flow = ReadAction.compute(() ->
                new BoundaryEscapeFlowBuilder(project).buildFromSink(sinkCall));
        String tabTitle = sinkCall.getMethodExpression().getReferenceName();
        FlowToolWindowSupport.showFlow(project, TOOL_WINDOW_ID,
                (tabTitle != null) ? tabTitle : "boundary-escape", flow);
    }
}
