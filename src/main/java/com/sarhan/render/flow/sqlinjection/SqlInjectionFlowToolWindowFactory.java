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

package com.sarhan.render.flow.sqlinjection;

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
 * Tool window that renders the SQL-injection taint flow diagram.
 *
 * @author Akmal Sarhan
 */
public final class SqlInjectionFlowToolWindowFactory implements ToolWindowFactory {

    public static final String TOOL_WINDOW_ID = "Spring Buddy SQL Injection Flow";

    @Override
    public void createToolWindowContent(@NotNull Project project,
                                          @NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("SQL Injection Flow");
        toolWindow.setStripeTitle("SQL Inject");

        ContentFactory cf = ContentFactory.getInstance();
        JComponent placeholder = FlowToolWindowSupport.placeholder(
                "Click \u26A0 on a flagged query call<br/>"
                        + "to see how user input reaches query execution.");
        toolWindow.getContentManager().addContent(cf.createContent(placeholder, "", false));
    }

    public static void showFlow(@NotNull Project project,
                                  @NotNull PsiMethodCallExpression sinkCall) {
        FlowGraph flow = ReadAction.compute(() ->
                new SqlInjectionFlowBuilder(project).buildFromSink(sinkCall));
        String tabTitle = sinkCall.getMethodExpression().getReferenceName();
        FlowToolWindowSupport.showFlow(project, TOOL_WINDOW_ID,
                (tabTitle != null) ? tabTitle : "sql-injection", flow);
    }
}
