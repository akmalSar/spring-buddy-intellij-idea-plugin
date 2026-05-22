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

package com.sarhan.dataFlow;

import java.util.HashSet;
import java.util.Set;

import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks which PsiVariables hold "detached" entity references within a single
 * method scope, with full alias propagation via a fixed-point loop.
 *
 * Handles:
 *   User u2 = u1;                     — direct alias
 *   u2 = u1;                          — reassignment alias
 *   User u2 = opt.get();              — Optional unwrap
 *   User u2 = opt.orElseThrow();      — Optional unwrap variant
 *   User u2 = (User) rawRef;          — cast passthrough
 *   condition ? tainted : other       — conservative ternary
 *
 * @author Akmal Sarhan
 */
public class TaintedEntityTracker {

    private final Set<PsiVariable> taintedVars = new HashSet<>();

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void taint(@NotNull PsiVariable var) {
        this.taintedVars.add(var);
    }

    public boolean isTainted(@NotNull PsiVariable var) {
        return this.taintedVars.contains(var);
    }

    /**
     * Central expression taint check — used by all visitors.
     * Handles direct references, Optional chains, collection element access,
     * stream operations, casts, and ternaries.
     */
    public boolean isTaintedExpression(@Nullable PsiExpression expr) {
        if (expr == null) {
            return false;
        }

        // Direct variable reference: u, param, field access on 'this'
        if (expr instanceof PsiReferenceExpression ref) {
            PsiElement resolved = ref.resolve();
            if (resolved instanceof PsiVariable var) {
                return this.taintedVars.contains(var);
            }
        }

        // Optional unwrap: opt.get(), opt.orElseThrow(), opt.orElse(default)
        // Collection element access: list.get(i), set.iterator().next()
        if (expr instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            if (name != null && (name.equals("get") || name.startsWith("orElse"))) {
                return isTaintedExpression(
                        call.getMethodExpression().getQualifierExpression());
            }
            // Stream creation from collection: orders.stream(), orders.parallelStream()
            if (name != null && (name.equals("stream") || name.equals("parallelStream"))) {
                return isTaintedExpression(
                        call.getMethodExpression().getQualifierExpression());
            }
            // Stream intermediate operations that preserve or transform elements
            // (filter, map, flatMap, peek, distinct, sorted, limit, skip)
            if (name != null && (name.equals("filter") || name.equals("map")
                    || name.equals("flatMap") || name.equals("peek")
                    || name.equals("distinct") || name.equals("sorted")
                    || name.equals("limit") || name.equals("skip"))) {
                return isTaintedExpression(
                        call.getMethodExpression().getQualifierExpression());
            }
        }

        // Cast: (User) someExpr — taint passes through
        if (expr instanceof PsiTypeCastExpression cast) {
            return isTaintedExpression(cast.getOperand());
        }

        // Ternary: condition ? a : b — conservative: tainted if either branch is
        if (expr instanceof PsiConditionalExpression ternary) {
            return isTaintedExpression(ternary.getThenExpression())
                    || isTaintedExpression(ternary.getElseExpression());
        }

        return false;
    }

    /**
     * Fixed-point alias propagation pass over a method body.
     * Repeats until no new variables are added to the taint set.
     * Must be called after initial seeding from repository sources.
     */
    public void propagateAliases(@NotNull PsiCodeBlock body) {
        boolean changed = true;
        while (changed) {
            AliasPropagationVisitor visitor = new AliasPropagationVisitor();
            body.accept(visitor);
            changed = visitor.didTaint;
        }
    }

    // -----------------------------------------------------------------------
    // Internal fixed-point visitor
    // -----------------------------------------------------------------------

    private final class AliasPropagationVisitor extends JavaRecursiveElementWalkingVisitor {

        boolean didTaint = false;

        // Handles: User u2 = u1;  or  User u2 = opt.get();
        @Override
        public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {
            super.visitDeclarationStatement(statement);
            for (PsiElement declared : statement.getDeclaredElements()) {
                if (declared instanceof PsiLocalVariable var) {
                    if (!TaintedEntityTracker.this.taintedVars.contains(var)
                            && isTaintedExpression(var.getInitializer())) {
                        TaintedEntityTracker.this.taintedVars.add(var);
                        this.didTaint = true;
                    }
                }
            }
        }

        // Handles: u2 = u1;  or  u2 = opt.get();
        @Override
        public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expr) {
            super.visitAssignmentExpression(expr);
            if (!isTaintedExpression(expr.getRExpression())) {
                return;
            }

            PsiExpression lhs = expr.getLExpression();
            if (lhs instanceof PsiReferenceExpression ref
                    && ref.resolve() instanceof PsiVariable var
                    && !TaintedEntityTracker.this.taintedVars.contains(var)) {
                TaintedEntityTracker.this.taintedVars.add(var);
                this.didTaint = true;
            }
        }

        // Handles: for (Order order : orders)  — taint the loop variable
        @Override
        public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
            super.visitForeachStatement(statement);
            PsiExpression iterated = statement.getIteratedValue();
            if (isTaintedExpression(iterated)) {
                PsiParameter param = statement.getIterationParameter();
                if (param != null && !TaintedEntityTracker.this.taintedVars.contains(param)) {
                    TaintedEntityTracker.this.taintedVars.add(param);
                    this.didTaint = true;
                }
            }
        }

        // Handles: orders.forEach(order -> ...)  and  orders.stream().map(order -> ...)
        // Also filter, peek, anyMatch, allMatch, noneMatch lambdas
        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            String name = call.getMethodExpression().getReferenceName();
            if (name == null) {
                return;
            }
            if (!(name.equals("forEach") || name.equals("map") || name.equals("flatMap")
                    || name.equals("filter") || name.equals("peek")
                    || name.equals("anyMatch") || name.equals("allMatch")
                    || name.equals("noneMatch"))) {
                return;
            }
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (!isTaintedExpression(qualifier)) {
                return;
            }
            PsiExpression[] args = call.getArgumentList().getExpressions();
            if (args.length == 0) {
                return;
            }
            PsiExpression arg = args[0];
            if (!(arg instanceof PsiLambdaExpression lambda)) {
                return;
            }
            PsiParameterList params = lambda.getParameterList();
            for (PsiParameter param : params.getParameters()) {
                if (!TaintedEntityTracker.this.taintedVars.contains(param)) {
                    TaintedEntityTracker.this.taintedVars.add(param);
                    this.didTaint = true;
                }
            }
        }
    }
}
