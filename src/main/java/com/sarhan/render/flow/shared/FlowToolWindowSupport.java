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

package com.sarhan.render.flow.shared;

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
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.jetbrains.annotations.NotNull;

/**
 * Helper that hosts a {@link FlowGraph} in a JCEF browser tab inside a
 * {@link ToolWindow}.
 *
 * <p>Each Spring Buddy data-flow inspection has its own tool window
 * (registered via {@code <toolWindow>} in {@code plugin.xml}) and a tiny
 * factory class. The factories delegate to {@link #showFlow} so all the
 * JCEF + theme + JS-bridge plumbing lives in one place.
 *
 * @author Akmal Sarhan
 */
public final class FlowToolWindowSupport {

    private FlowToolWindowSupport() {
    }

    /**
     * Renders {@code flow} into the named tool window, replacing any prior
     * content. Falls back to a plain label when JCEF is not supported.
     *
     * @param project        the open project
     * @param toolWindowId   id of the tool window to populate (must be
     *                       registered in {@code plugin.xml})
     * @param tabTitle       title for the new content tab
     * @param flow           the flow graph to render
     */
    public static void showFlow(@NotNull Project project,
                                  @NotNull String toolWindowId,
                                  @NotNull String tabTitle,
                                  @NotNull FlowGraph flow) {
        ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
        if (tw == null) {
            return;
        }

        boolean dark = com.intellij.ide.ui.LafManager.getInstance()
                .getCurrentUIThemeLookAndFeel().isDark();
        String html = new FlowSvgRenderer().renderHtml(flow, dark);

        JComponent root;
        Disposable contentDisposable = Disposer.newDisposable("FlowToolWindowContent");
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

        Content content = ContentFactory.getInstance().createContent(root, tabTitle, false);
        content.setDisposer(contentDisposable);

        tw.getContentManager().removeAllContents(true);
        tw.getContentManager().addContent(content);
        tw.show(null);
    }

    /**
     * Standard placeholder content shown when the tool window is opened but
     * no inspection problem has yet been visualised.
     */
    @NotNull
    public static JComponent placeholder(@NotNull String message) {
        return new JLabel("<html><center>" + message + "</center></html>",
                SwingConstants.CENTER);
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
