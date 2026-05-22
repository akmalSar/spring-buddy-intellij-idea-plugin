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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiModificationTracker;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.SupervisorKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Project-level light service.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Owns the plugin coroutine scope ({@code SupervisorJob + Dispatchers.Default})</li>
 *   <li>Dispatches graph construction off the EDT via coroutines</li>
 *   <li>Caches {@link TransactionGraph} instances keyed by method signature fingerprint</li>
 *   <li>Invalidates cache on  events</li>
 * </ol>
 *
 * <h3>Registration in plugin.xml</h3>
 * <pre>{@code
 * <extensions defaultExtensionNs="com.intellij">
 *   <projectService serviceImplementation="com.sarhan.transactionDiagram.service.TransactionDiagramService"/>
 * </extensions>
 * }</pre>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * TransactionDiagramService svc = project.getService(TransactionDiagramService.class);
 * svc.requestGraph(psiMethod, graph -> renderer.load(graph));
 * }</pre>
 *
 * @author Akmal Sarhan
 */
@Service(Service.Level.PROJECT)
public final class TransactionDiagramService implements Disposable {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final Project project;
    private final TransactionGraphBuilder builder;
    private final TransactionSvgRenderer renderer;

    /**
     * Cache keyed by a stable method signature fingerprint.
     * Cleared on every PSI modification to avoid stale results.
     */
    private final ConcurrentHashMap<String, TransactionGraph> cache =
            new ConcurrentHashMap<>();

    /**
     * Plugin-owned coroutine scope.  All graph-building coroutines are children
     * of this scope and are cancelled automatically when the project closes.
     */
    private final CoroutineScope cs;

    // -----------------------------------------------------------------------
    // Constructor (called by the platform)
    // -----------------------------------------------------------------------

    public TransactionDiagramService(@NotNull Project project) {
        this.project  = project;
        this.builder  = new TransactionGraphBuilder(project);
        this.renderer = new TransactionSvgRenderer();

        // SupervisorJob: a failing child does not cancel other children.
        this.cs = CoroutineScopeKt.CoroutineScope(SupervisorKt.SupervisorJob((Job) null).plus(Dispatchers.getDefault()));

        // Invalidate cache on any PSI change
        project.getMessageBus()
               .connect(this)       // disposable = this service itself
               .subscribe(PsiModificationTracker.TOPIC,
                       () -> this.cache.clear());
    }

    // -----------------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------------->

    /**
     * Asynchronously builds or retrieves from cache the {@link TransactionGraph}
     * for {@code method} and delivers the result on the EDT via {@code onReady}.
     *
     * <p>The returned {@link Job} can be cancelled if the popup is closed before
     * the graph is ready.
     *
     * @param method  the PSI method at the caret
     * @param onReady called on the EDT with the completed graph
     * @return the coroutine {@link Job} (cancel to abort)
     */
    @NotNull
    public Job requestGraph(@NotNull PsiMethod method,
                             @NotNull Consumer<TransactionGraph> onReady) {
        String cacheKey = computeCacheKey(method);

        return kotlinx.coroutines.BuildersKt.launch(
                this.cs,
                this.cs.getCoroutineContext(),
                kotlinx.coroutines.CoroutineStart.DEFAULT,
                (CoroutineScope scope, Continuation<? super Unit> continuation) -> {
                    // Off EDT: build the graph
                    TransactionGraph graph;
                    try {
                        graph = kotlinx.coroutines.BuildersKt.runBlocking(
                                Dispatchers.getDefault(),
                                (CoroutineScope innerScope, Continuation<? super TransactionGraph> innerContinuation) -> {
                                    TransactionGraph cached = this.cache.get(cacheKey);
                                    if (cached != null) {
                                        return cached;
                                    }

                                    TransactionGraph built = ReadAction.compute(
                                            () -> this.builder.buildGraph(method));
                                    this.cache.put(cacheKey, built);
                                    return built;
                                });
                    } catch (InterruptedException ex) {
                        // Thread interrupted while building graph — abort
                        Thread.currentThread().interrupt();
                        return kotlin.Unit.INSTANCE;
                    }

                    // Back on EDT: deliver result
                    ApplicationManager.getApplication().invokeLater(
                            () -> onReady.accept(graph));

                    return kotlin.Unit.INSTANCE;
                });
    }

    /**
     * Synchronously retrieves a cached graph if available, or returns
     * {@code null} to indicate that an async build is needed.
     */
    @Nullable
    public TransactionGraph getCached(@NotNull PsiMethod method) {
        return this.cache.get(computeCacheKey(method));
    }

    /**
     * Renders the graph as a full HTML page string suitable for
     * {@code JBCefBrowser.loadHTML(html, "about:blank")}.
     */
    @NotNull
    public String renderHtml(@NotNull TransactionGraph graph, boolean darkTheme) {
        return this.renderer.renderHtml(graph, darkTheme);
    }

    // -----------------------------------------------------------------------
    // Disposable
    // -----------------------------------------------------------------------

    @Override
    public void dispose() {
        // Cancel all coroutines in the plugin scope
        kotlinx.coroutines.JobKt.cancel(
                (Job) this.cs.getCoroutineContext()
                        .get(Job.Key),
                null);
        this.cache.clear();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Computes a stable cache key for a PSI method using
     * {@link MethodSignatureUtil#createMethodSignature}.
     *
     * <p>Must be called inside a read action.
     */
    @NotNull
    private static String computeCacheKey(@NotNull PsiMethod method) {
        // MethodSignatureUtil.createMethodSignature returns a MethodSignature
        // that implements equals/hashCode correctly — we use its string form.
        com.intellij.psi.util.MethodSignature sig =
                MethodSignatureUtil.createMethodSignature(
                        method.getName(),
                        method.getParameterList(),
                        method.getTypeParameterList(),
                        com.intellij.psi.PsiSubstitutor.EMPTY,
                        method.isConstructor());
        PsiClass clazz = method.getContainingClass();
        String className = (clazz != null)
                ? ((clazz.getQualifiedName() != null) ? clazz.getQualifiedName() : clazz.getName())
                : "Unknown";
        return className + "::" + sig;
    }
}
