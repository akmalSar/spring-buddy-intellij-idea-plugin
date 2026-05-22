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

import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.sarhan.render.GraphTheme;
import org.jetbrains.annotations.NotNull;

/**
 * Tool window that renders the Ghost-Save data-flow diagram.
 *
 * <p>Reuses the same JCEF pattern as the transaction flow tool window:
 * a single content panel that is replaced each time a new flow is opened.
 *
 * @author Akmal Sarhan
 */
public final class GhostSaveFlowToolWindowFactory implements ToolWindowFactory {

    public static final String TOOL_WINDOW_ID = "Spring Buddy Ghost-Save Flow";

    @Override
    public void createToolWindowContent(@NotNull Project project,
                                          @NotNull ToolWindow toolWindow) {
        toolWindow.setTitle("Ghost-Save Flow");
        toolWindow.setStripeTitle("Ghost-Save");

        ContentFactory cf = ContentFactory.getInstance();
        toolWindow.getContentManager().addContent(
                cf.createContent(
                        new JLabel(
                                "<html><center>Click ⚠ on a flagged ghost-save mutation<br/>" +
                                        "to see the entity flow.</center></html>",
                                SwingConstants.CENTER),
                        "", false));
    }

    public static void showFlow(@NotNull Project project,
                                 @NotNull PsiMethodCallExpression mutationCall) {
        ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (tw == null) {
            return;
        }

        GhostSaveFlow flow = ReadAction.compute(() ->
                new GhostSaveFlowBuilder(project).buildFromSink(mutationCall));

        boolean dark = GraphTheme.isDark();
        String html = new GhostSaveFlowSvgRenderer().renderHtml(flow, dark);

        JComponent root;
        Disposable contentDisposable = Disposer.newDisposable("GhostSaveFlowContent");
        if (JBCefApp.isSupported()) {
            JBCefBrowser browser = new JBCefBrowser();
            browser.getComponent().setPreferredSize(new Dimension(440, 600));
            JBCefJSQuery navQuery = JBCefJSQuery.create(browser);
            navQuery.addHandler((String request) -> {
                openFileAtLine(project, request);
                return new JBCefJSQuery.Response("ok");
            });
            String bridge = "<script>window.intellijNavigateLine=function(loc){"
                    + navQuery.inject("loc") + "};</script>";
            String wrapped = html.replace("</body>", bridge + "</body>");
            ApplicationManager.getApplication().invokeLater(() ->
                    browser.loadHTML(wrapped, "about:blank"));
            Disposer.register(contentDisposable, browser);
            root = browser.getComponent();
        } else {
            root = new JLabel("JCEF not supported", SwingConstants.CENTER);
        }

        Content content = ContentFactory.getInstance().createContent(
                root, mutationCall.getMethodExpression().getReferenceName(), false);
        content.setDisposer(contentDisposable);

        tw.getContentManager().removeAllContents(true);
        tw.getContentManager().addContent(content);
        tw.show(null);
    }

    private static void openFileAtLine(@NotNull Project project, @NotNull String fileColonLine) {
        int colon = fileColonLine.indexOf(':');
        if (colon < 0) {
            return;
        }
        String fileName = fileColonLine.substring(0, colon);
        int line;
        try {
            line = Integer.parseInt(fileColonLine.substring(colon + 1)) - 1;
        } catch (NumberFormatException ex) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() ->
                ReadAction.run(() -> {
                    for (VirtualFile vf : FilenameIndex.getVirtualFilesByName(
                            fileName, GlobalSearchScope.projectScope(project))) {
                        new OpenFileDescriptor(project, vf, Math.max(0, line), 0)
                                .navigate(true);
                        return;
                    }
                }));
    }
}
