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

import java.awt.*;

import javax.swing.*;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps a {@link JBCefBrowser} and manages the HTML diagram lifecycle.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>On construction: show spinner HTML, kick off async graph build.</li>
 *   <li>On graph ready: load full diagram HTML via {@code loadHTML()}.</li>
 *   <li>On PSI modification: call {@code browser.cefBrowser.executeJavaScript}
 *       to reveal the refresh banner without a full page reload.</li>
 *   <li>On refresh click (from JS): rebuild the graph from scratch.</li>
 * </ol>
 *
 * <p><b>Note:</b> JCEF availability is checked lazily — if {@code JBCefBrowser.isSupported()}
 * returns false (headless environments, CI), a fallback {@code JLabel} is shown.
 *
 * @author Akmal Sarhan
 */
public final class TransactionJcefPanel {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final Project project;
    private final PsiMethod rootMethod;
    private final TransactionDiagramService service;

    /** Nullable if JCEF is not supported in this environment. */
    private final JBCefBrowser browser;

    /** Root Swing component — either the JCEF component or a fallback label. */
    private final JComponent root;

    /** JS→Java bridge for node-click navigation. */
    private final JBCefJSQuery navigateQuery;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public TransactionJcefPanel(@NotNull Project project,
                                 @NotNull PsiMethod rootMethod,
                                 @NotNull TransactionDiagramService service) {
        this.project    = project;
        this.rootMethod = rootMethod;
        this.service    = service;

        if (JBCefApp.isSupported()) {
            this.browser = new JBCefBrowser();
            this.root    = this.browser.getComponent();
            this.root.setPreferredSize(new Dimension(720, 480));
            this.navigateQuery = JBCefJSQuery.create(this.browser);
            this.navigateQuery.addHandler(this::onNavigateRequest);

            // Show spinner immediately while graph builds
            loadSpinner();

            // Dispatch async graph construction
            buildAndLoad();
        } else {
            // Headless / no JCEF — degrade gracefully
            this.browser       = null;
            this.navigateQuery = null;
            JLabel label = new JLabel(
                    "<html><center>JCEF not supported in this environment.<br/>" +
                    "Transaction diagram requires a JetBrains IDE with JCEF enabled.</center></html>",
                    SwingConstants.CENTER);
            this.root = label;
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Returns the root Swing component to embed in the {@code JBPopup}. */
    @NotNull
    public JComponent getComponent() { return this.root; }

    /**
     * Called by the popup's message-bus subscription when PSI is modified.
     * Runs the JS snippet that shows the "Code changed — click to refresh" banner.
     */
    public void onPsiModified() {
        if (this.browser == null) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() ->
                this.browser.getCefBrowser().executeJavaScript(
                        "if (typeof showRefreshBanner === 'function') { showRefreshBanner(); }",
                        "about:blank", 0));
    }

    // -----------------------------------------------------------------------
    // HTML loading helpers
    // -----------------------------------------------------------------------

    private void loadSpinner() {
        if (this.browser == null) {
            return;
        }
        String spinnerHtml =
                "<!DOCTYPE html><html><head><style>" +
                "body{margin:0;display:flex;align-items:center;justify-content:center;" +
                "height:100vh;font-family:monospace;}" +
                "@keyframes spin{to{transform:rotate(360deg)}}" +
                ".s{font-size:24px;animation:spin 1s linear infinite;display:inline-block;}" +
                "</style></head><body>" +
                "<div><span class=\"s\">⟳</span>&nbsp;Building transaction graph…</div>" +
                "</body></html>";
        this.browser.loadHTML(spinnerHtml, "about:blank");
    }

    private void loadGraph(@NotNull TransactionGraph graph) {
        if (this.browser == null) {
            return;
        }
        boolean dark = com.intellij.ide.ui.LafManager.getInstance().getCurrentUIThemeLookAndFeel()
                .isDark();
        String html = this.service.renderHtml(graph, dark);
        String bridge = buildNavigationBridgeScript();
        String wrapped = html.replace("</body>", bridge + "</body>");
        // loadHTML must be called on the EDT
        ApplicationManager.getApplication().invokeLater(() ->
                this.browser.loadHTML(wrapped, "about:blank"));
    }

    // -----------------------------------------------------------------------
    // Node-click navigation (JS → Java bridge)
    // -----------------------------------------------------------------------

    @NotNull
    private String buildNavigationBridgeScript() {
        String inject = this.navigateQuery.inject("methodId");
        return "<script>window.intellijNavigateNode = function(methodId) { "
                + inject + " };</script>";
    }

    @NotNull
    private JBCefJSQuery.Response onNavigateRequest(@NotNull String request) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            PsiMethod target = ReadAction.compute(
                    () -> findMethodById(request));
            if (target != null) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (target instanceof Navigatable navigatable
                            && navigatable.canNavigate()) {
                        navigatable.navigate(true);
                    }
                });
            }
        });
        return new JBCefJSQuery.Response("ok");
    }

    @Nullable
    private PsiMethod findMethodById(@NotNull String methodId) {
        int hash = methodId.indexOf('#');
        if (hash < 0) {
            return null;
        }
        String classFqn = methodId.substring(0, hash);
        String methodName = methodId.substring(hash + 1);
        PsiClass clazz = JavaPsiFacade.getInstance(this.project)
                .findClass(classFqn, GlobalSearchScope.projectScope(this.project));
        if (clazz == null) {
            return null;
        }
        for (PsiMethod m : clazz.getMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Async graph construction
    // -----------------------------------------------------------------------

    private void buildAndLoad() {
        if (DumbService.isDumb(this.project)) {
            // PSI index not ready — wait until smart mode
            loadIndexingBanner();
            DumbService.getInstance(this.project).runWhenSmart(this::buildAndLoad);
            return;
        }

        // Check cache first (synchronous, no IO)
        TransactionGraph cached = this.service.getCached(this.rootMethod);
        if (cached != null) {
            loadGraph(cached);
            return;
        }

        // Async build
        this.service.requestGraph(this.rootMethod, this::loadGraph);
    }

    private void loadIndexingBanner() {
        if (this.browser == null) {
            return;
        }
        String html =
                "<!DOCTYPE html><html><head><style>" +
                "body{margin:0;display:flex;align-items:center;justify-content:center;" +
                "height:100vh;font-family:monospace;}" +
                "</style></head><body>" +
                "<div>⏳ Waiting for IDE index to be ready…</div>" +
                "</body></html>";
        this.browser.loadHTML(html, "about:blank");
    }
}
