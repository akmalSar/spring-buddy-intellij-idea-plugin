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

package com.sarhan.render;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */

public final class TransactionDiagramToolWindowFactory implements ToolWindowFactory {

    static final String TOOL_WINDOW_ID = "Spring Buddy Flow";

    @Override
    public void createToolWindowContent(@NotNull Project project,
                                         @NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("Tx Flow");
        toolWindow.setStripeTitle("Tx Flow");

        ContentFactory contentFactory = ContentFactory.getInstance();
        toolWindow.getContentManager().addContent(
                contentFactory.createContent(
                        new javax.swing.JLabel("Click ⟳ Tx Flow on a @Transactional method to see its call graph.",
                                javax.swing.SwingConstants.CENTER),
                        "", false));
    }

    public static void showGraph(@NotNull Project project,
                                  @NotNull PsiMethod rootMethod) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow == null) {
            return;
        }

        TransactionDiagramService service =
                project.getService(TransactionDiagramService.class);
        TransactionJcefPanel panel = new TransactionJcefPanel(project, rootMethod, service);

        panel.getComponent().setPreferredSize(new java.awt.Dimension(720, 480));

        Content content = ContentFactory.getInstance().createContent(
                panel.getComponent(),
                rootMethod.getName(),
                false);
        content.setPreferredFocusableComponent(panel.getComponent());

        Disposable disposable = Disposer.newDisposable();
        content.setDisposer(disposable);
        project.getMessageBus()
                .connect(disposable)
                .subscribe(PsiModificationTracker.TOPIC,
                        panel::onPsiModified);

        toolWindow.getContentManager().removeAllContents(true);
        toolWindow.getContentManager().addContent(content);
        toolWindow.show(() -> panel.startLoading());
    }
}