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

package com.sarhan.dataFlow.base;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.sarhan.dataFlow.AddTransactionalFix;
import com.sarhan.dataFlow.TaintedEntityTracker;
import org.jetbrains.annotations.NotNull;

/**
 * Inter-procedural taint engine.
 *
 * Architecture:
 *   - One engine instance per public entry-point method
 *   - Worklist of (method, taintedParamIndices) pairs to analyse
 *   - Each method body is analysed in two passes:
 *       Pass 1 (SourceSeedingVisitor):  seed taint from repo/EM sources
 *       Alias pass (TaintedEntityTracker): fixed-point alias propagation
 *       Pass 2 (SinkAndCallVisitor):    detect sinks, propagate inter-procedurally
 *   - processedWith guards against re-analysing a method with the same input
 *
 * Inter-procedural propagation:
 *   - A tainted argument at a call site taints the matching parameter in the callee
 *   - The callee is enqueued (depth-first via addFirst) with its new taint input
 *   - If the callee was already analysed with fewer taints, it is re-analysed
 *
 * @author Akmal Sarhan
 */
public class InterproceduralTaintEngine {

    private final SpringEntitySourceDetector sourceDetector;
    private final ProblemsHolder holder;

    // Shared across all entry-point engines in the same inspection run —
    // prevents duplicate problem registration when two entry paths reach the same sink
    private final Set<PsiElement> reportedElements;

    // method → set of parameter indices known to be tainted by callers
    private final Map<PsiMethod, Set<Integer>> taintedParamsByMethod = new HashMap<>();

    // method → snapshot of taintedParamsByMethod at the time it was last analysed
    // Re-analysis is triggered only when the current input differs from this snapshot
    private final Map<PsiMethod, Set<Integer>> processedWith = new HashMap<>();

    private final Deque<PsiMethod> worklist = new ArrayDeque<>();

    // The file being inspected. Inter-procedural analysis must NOT follow
    // calls into other files because ProblemsHolder can only register problems
    // on PsiElements belonging to its own file.
    private com.intellij.psi.PsiFile entryFile;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public InterproceduralTaintEngine(@NotNull SpringEntitySourceDetector sourceDetector,
                                      @NotNull ProblemsHolder holder,
                                      @NotNull Set<PsiElement> reportedElements) {
        this.sourceDetector   = sourceDetector;
        this.holder           = holder;
        this.reportedElements = reportedElements;
    }

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    public void analyse(@NotNull PsiMethod entryMethod, boolean entryIsTransactional) {
        if (entryIsTransactional) {
            return;
        }
        if (this.entryFile == null) {
            this.entryFile = entryMethod.getContainingFile();
        }
        this.worklist.add(entryMethod);

        while (!this.worklist.isEmpty()) {
            PsiMethod current = this.worklist.poll();

            // Snapshot the current taint input BEFORE any mutation
            Set<Integer> currentInput = Collections.unmodifiableSet(
                    new HashSet<>(this.taintedParamsByMethod.getOrDefault(current, Set.of())));

            // Skip if we already processed this exact input
            Set<Integer> lastInput = this.processedWith.get(current);
            if (currentInput.equals(lastInput)) {
                continue;
            }

            // Record what we are processing now
            this.processedWith.put(current, new HashSet<>(currentInput));

            analyseMethod(current, currentInput);
        }
    }

    // -----------------------------------------------------------------------
    // Single-method analysis
    // -----------------------------------------------------------------------

    private void analyseMethod(@NotNull PsiMethod method,
                               @NotNull Set<Integer> taintedParamIndices) {
        // Safety: only analyse methods that live in the same file as the
        // entry point. ProblemsHolder cannot register problems on elements
        // from other files, and following cross-file calls causes crashes.
        if (method.getContainingFile() != this.entryFile) {
            return;
        }

        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return;
        }

        TaintedEntityTracker tracker = new TaintedEntityTracker();

        // Seed tainted parameters
        PsiParameter[] params = method.getParameterList().getParameters();
        for (int i = 0; i < params.length; i++) {
            if (taintedParamIndices.contains(i)) {
                tracker.taint(params[i]);
            }
        }

        // Pass 1: seed from repository / EntityManager sources
        body.accept(new SourceSeedingVisitor(tracker));

        // Fixed-point alias propagation
        tracker.propagateAliases(body);

        // Pass 2: detect sinks, propagate inter-procedurally
        body.accept(new SinkAndCallVisitor(tracker));
    }

    // -----------------------------------------------------------------------
    // Pass 1: seed taint from detached entity sources
    // -----------------------------------------------------------------------

    /**
     * Registers a problem only if this exact PsiElement has not already been
     * reported — prevents duplicate warnings when two entry-point methods both
     * reach the same sink via different call paths.
     */
    private void registerIfNew(@NotNull PsiElement element, @NotNull String message) {
        if (element.getContainingFile() != this.entryFile) {
            return; // ProblemsHolder is scoped to a single file
        }
        if (this.reportedElements.add(element)) {
            this.holder.registerProblem(element, message,
                    new AddTransactionalFix(),
                    new com.sarhan.render.flow.ShowGhostSaveFlowFix());
        }
    }

    private class SourceSeedingVisitor extends JavaRecursiveElementWalkingVisitor {

        private final TaintedEntityTracker tracker;

        SourceSeedingVisitor(@NotNull TaintedEntityTracker tracker) {
            this.tracker = tracker;
        }

        /**
         * Explicit entry via PsiDeclarationStatement — guarantees visitLocalVariable
         * fires for all local variable declarations regardless of nesting depth.
         * (JavaRecursiveElementWalkingVisitor.visitLocalVariable alone is unreliable
         * in some IntelliJ versions when variables are inside block statements.)
         */
        @Override
        public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {
            super.visitDeclarationStatement(statement);
            for (PsiElement declared : statement.getDeclaredElements()) {
                if (declared instanceof PsiLocalVariable var) {
                    seedFromInitializer(var, var.getInitializer());
                }
            }
        }

        // Belt-and-suspenders: also override visitLocalVariable directly
        @Override
        public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            seedFromInitializer(variable, variable.getInitializer());
        }

        // Handles: u = userRepository.findById(1L);  (declaration was: User u;)
        @Override
        public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expr) {
            super.visitAssignmentExpression(expr);
            PsiExpression rhs = expr.getRExpression();
            if (rhs == null || !isDetachedSource(rhs)) {
                return;
            }

            PsiExpression lhs = expr.getLExpression();
            if (lhs instanceof PsiReferenceExpression ref
                    && ref.resolve() instanceof PsiVariable var) {
                this.tracker.taint(var);
            }
        }

        private void seedFromInitializer(@NotNull PsiVariable variable,
                                         PsiExpression initializer) {
            if (this.tracker.isTainted(variable)) {
                return;
            }
            if (initializer != null && isDetachedSource(initializer)) {
                this.tracker.taint(variable);
            }
        }

        /**
         * Returns true if this expression produces a detached entity value.
         * Handles direct repo/EM calls and chained Optional-unwrap calls.
         */
        private boolean isDetachedSource(@NotNull PsiExpression expr) {
            if (expr instanceof PsiMethodCallExpression call) {
                PsiMethod resolved = call.resolveMethod();

                // Direct source: repo.findById(...) or em.find(...)
                if (resolved != null && InterproceduralTaintEngine.this.sourceDetector.isDetachedEntitySource(resolved)) {
                    return true;
                }

                // Optional unwrap chain: opt.get() / opt.orElseThrow()
                // where opt itself was produced by a source
                String name = call.getMethodExpression().getReferenceName();
                if (name != null && (name.equals("get") || name.startsWith("orElse"))) {
                    PsiExpression qualifier =
                            call.getMethodExpression().getQualifierExpression();
                    return qualifier != null && isDetachedSource(qualifier);
                }
            }

            // Reference to an already-tainted variable — seeded transitively
            // (alias propagation handles this, but catching it here avoids a pass)
            if (expr instanceof PsiReferenceExpression ref
                    && ref.resolve() instanceof PsiVariable var) {
                return this.tracker.isTainted(var);
            }

            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Pass 2: detect sinks and propagate taint inter-procedurally
    // -----------------------------------------------------------------------

    private class SinkAndCallVisitor extends JavaRecursiveElementWalkingVisitor {

        private final TaintedEntityTracker tracker;

        SinkAndCallVisitor(@NotNull TaintedEntityTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);

            PsiMethod resolved = call.resolveMethod();
            if (resolved == null) {
                return;
            }

            PsiExpression qualifier =
                    call.getMethodExpression().getQualifierExpression();

            // ── SINK: tainted.setXxx(...) ──────────────────────────────────
            if (EntityMutationDetector.isMutationMethod(resolved) && qualifier != null) {
                if (this.tracker.isTaintedExpression(qualifier)) {
                    registerIfNew(call,
                            "Ghost Save: modification of a detached entity will not be " +
                                    "persisted. The entity was fetched outside an active " +
                                    "Hibernate session. Wrap this method in @Transactional.");
                    return; // no need to also treat as a call site
                }
            }

            // ── INTER-PROCEDURAL: propagate tainted args into callee ────────
            // Only follow calls where we can see the body (not library code)
            // and where the callee lives in the same file — cross-file
            // inter-procedural analysis causes crashes because ProblemsHolder
            // is scoped to a single PsiFile.
            if (resolved.getBody() == null
                    || resolved.getContainingFile() != InterproceduralTaintEngine.this.entryFile) {
                return;
            }

            PsiExpression[] args = call.getArgumentList().getExpressions();
            Set<Integer> newlyTainted = new HashSet<>();
            for (int i = 0; i < args.length; i++) {
                if (this.tracker.isTaintedExpression(args[i])) {
                    newlyTainted.add(i);
                }
            }

            if (newlyTainted.isEmpty()) {
                return;
            }

            Set<Integer> existing =
                    InterproceduralTaintEngine.this.taintedParamsByMethod.computeIfAbsent(resolved, (PsiMethod k) -> new HashSet<>());
            boolean learned = existing.addAll(newlyTainted);

            // Re-enqueue depth-first only if we learned something new
            if (learned) {
                InterproceduralTaintEngine.this.worklist.addFirst(resolved);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
}
