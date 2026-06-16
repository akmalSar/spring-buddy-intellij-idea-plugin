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
 *   <li>On construction: show Swing status text, kick off async graph build.</li>
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

    /** Card layout used to show Swing status text until the JCEF graph is ready. */
    private final CardLayout cardLayout;

    /** Swing status label avoids racing a spinner page against the graph page in JCEF. */
    private final JLabel statusLabel;

    /** JS→Java bridge for node-click navigation. */
    private final JBCefJSQuery navigateQuery;

    /** JS→Java bridge for rebuilding the graph after PSI or theme changes. */
    private final JBCefJSQuery refreshQuery;

    private boolean loading;

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
            this.cardLayout = new CardLayout();
            this.statusLabel = new JLabel("Building transaction graph...", SwingConstants.CENTER);
            JPanel panel = new JPanel(this.cardLayout);
            panel.add(this.statusLabel, "status");
            panel.add(this.browser.getComponent(), "browser");
            this.cardLayout.show(panel, "status");
            this.root    = panel;
            this.root.setPreferredSize(new Dimension(720, 480));
            this.navigateQuery = JBCefJSQuery.create((com.intellij.ui.jcef.JBCefBrowserBase) this.browser);
            this.navigateQuery.addHandler(this::onNavigateRequest);
            this.refreshQuery = JBCefJSQuery.create((com.intellij.ui.jcef.JBCefBrowserBase) this.browser);
            this.refreshQuery.addHandler(this::onRefreshRequest);
        } else {
            this.browser       = null;
            this.cardLayout    = null;
            this.statusLabel   = null;
            this.navigateQuery = null;
            this.refreshQuery  = null;
            JLabel label = new JLabel(
                    "<html><center>JCEF not supported in this environment.<br/>" +
                    "Transaction diagram requires a JetBrains IDE with JCEF enabled.</center></html>",
                    SwingConstants.CENTER);
            this.root = label;
        }
    }

    /**
     * Must be called after the component has been added to the tool window and shown.
     * Shows the spinner and kicks off async graph construction.
     */
    public void startLoading() {
        if (this.browser == null) {
            return;
        }
        this.loading = true;
        loadSpinner();
        buildAndLoad();
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
        showRefreshBanner();
    }

    public void onThemeChanged() {
        showRefreshBanner();
    }

    private void showRefreshBanner() {
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
        showStatus("Building transaction graph...");
    }

    private void loadGraph(@NotNull TransactionGraph graph) {
        if (this.browser == null) {
            return;
        }
        this.loading = false;
        boolean dark = GraphTheme.isDark();
        String html = this.service.renderHtml(graph, dark);
        String bridge = buildNavigationBridgeScript();
        String wrapped = html.replace("</body>", bridge + "</body>");
        this.cardLayout.show(this.root, "browser");
        this.browser.loadHTML(wrapped, "about:blank");
    }

    // -----------------------------------------------------------------------
    // Node-click navigation (JS → Java bridge)
    // -----------------------------------------------------------------------

    @NotNull
    private String buildNavigationBridgeScript() {
        String inject = this.navigateQuery.inject("methodId");
        String refresh = this.refreshQuery.inject("'refresh'");
        return "<script>window.intellijNavigateNode = function(methodId) { "
                + inject + " }; window.intellijRefreshGraph = function() { "
                + refresh + " };</script>";
    }

    @NotNull
    private JBCefJSQuery.Response onRefreshRequest(@NotNull String request) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (this.loading) {
                return;
            }
            this.loading = true;
            loadSpinner();
            this.service.invalidate(this.rootMethod);
            buildAndLoad();
        });
        return new JBCefJSQuery.Response("ok");
    }

    @NotNull
    private JBCefJSQuery.Response onNavigateRequest(@NotNull String request) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            PsiMethod target = ReadAction.computeBlocking(
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
        this.service.requestGraph(this.rootMethod, this::loadGraph, this::loadError);
    }

    private void loadError(@NotNull String message, @NotNull Throwable error) {
        if (this.browser == null) {
            return;
        }
        this.loading = false;
        String details = escapeHtml(error.getClass().getSimpleName() + ": "
                + String.valueOf(error.getMessage()));
        String html =
                "<!DOCTYPE html><html><head><style>" +
                "body{margin:0;display:flex;align-items:center;justify-content:center;" +
                "height:100vh;font-family:monospace;color:#b3261e;padding:24px;box-sizing:border-box;}" +
                ".box{max-width:720px}.title{font-weight:bold;margin-bottom:12px}.details{color:#5f6368}" +
                "</style></head><body>" +
                "<div class=\"box\"><div class=\"title\">" + escapeHtml(message) + "</div>" +
                "<div class=\"details\">" + details + "</div></div>" +
                "</body></html>";
        ApplicationManager.getApplication().invokeLater(() ->
                {
                    this.cardLayout.show(this.root, "browser");
                    this.browser.loadHTML(html, "about:blank");
                });
    }

    @NotNull
    private static String escapeHtml(@NotNull String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void loadIndexingBanner() {
        if (this.browser == null) {
            return;
        }
        showStatus("Waiting for IDE index to be ready...");
    }

    private void showStatus(@NotNull String message) {
        if (this.statusLabel != null && this.cardLayout != null) {
            this.statusLabel.setText(message);
            this.cardLayout.show(this.root, "status");
        }
    }
}
