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

package com.sarhan.springformat.inspection;

import java.util.HashSet;
import java.util.Set;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * SpringJpaSaveInLoop: flags calls to a Spring Data {@code repository.save(...)} or
 * {@code saveAndFlush(...)} placed inside a loop body. Recommends batching with
 * {@code saveAll(...)} instead.
 *
 * <p>Uses DataFlow tracking to follow repository references through local-variable
 * assignments and detect save calls even when the repository was aliased.
 *
 * @author Akmal Sarhan
 */
public class SpringJpaSaveInLoopInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                if (method.getBody() == null) {
                    return;
                }
                RepositoryReferenceTracker tracker = new RepositoryReferenceTracker();

                // Pass 1: seed repository references (fields, parameters, local vars assigned from repos)
                method.getBody().accept(new RepositorySeedingVisitor(tracker));

                // Pass 2: propagate aliases
                tracker.propagateAliases(method.getBody());

                // Pass 3: detect save/saveAndFlush inside loops on tracked repos
                method.getBody().accept(new SaveInLoopSinkVisitor(tracker, holder));
            }
        };
    }

    // -----------------------------------------------------------------------
    // Pass 1: seed repository references
    // -----------------------------------------------------------------------

    private static boolean isInsideLoop(PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null && !(parent instanceof PsiMethod) && !(parent instanceof PsiClass)) {
            if (parent instanceof PsiForStatement
                    || parent instanceof PsiForeachStatement
                    || parent instanceof PsiWhileStatement
                    || parent instanceof PsiDoWhileStatement) {
                return true;
            }
            if (parent instanceof PsiLambdaExpression lambda) {
                PsiElement lambdaParent = lambda.getParent();
                if (lambdaParent instanceof PsiExpressionList argList
                        && argList.getParent() instanceof PsiMethodCallExpression outerCall) {
                    String outerName = outerCall.getMethodExpression().getReferenceName();
                    if (outerName != null && (outerName.equals("forEach")
                            || outerName.equals("map") || outerName.equals("flatMap")
                            || outerName.equals("peek") || outerName.equals("filter")
                            || outerName.equals("forEachOrdered"))) {
                        return true;
                    }
                }
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static boolean isRepositoryType(PsiType type) {
        if (!(type instanceof PsiClassType classType)) {
            return false;
        }
        PsiClass resolved = classType.resolve();
        if (resolved == null) {
            // Heuristic fallback: class name contains "Repository" or "Repo"
            String name = classType.getClassName();
            return name != null && (name.endsWith("Repository") || name.endsWith("Repo"));
        }
        if (SpringInspectionUtil.isSpringDataRepository(resolved)) {
            return true;
        }
        // Heuristic fallback on resolved class
        String className = resolved.getName();
        return className != null && (className.endsWith("Repository") || className.endsWith("Repo"));
    }

    private static final class RepositorySeedingVisitor
            extends JavaRecursiveElementWalkingVisitor {

        private final RepositoryReferenceTracker tracker;

        RepositorySeedingVisitor(@NotNull RepositoryReferenceTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {
            super.visitDeclarationStatement(statement);
            for (PsiElement declared : statement.getDeclaredElements()) {
                if (declared instanceof PsiLocalVariable var) {
                    seedFromInitializer(var, var.getInitializer());
                }
            }
        }

        @Override
        public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            seedFromInitializer(variable, variable.getInitializer());
        }

        @Override
        public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expr) {
            super.visitAssignmentExpression(expr);
            PsiExpression rhs = expr.getRExpression();
            if (rhs != null && isRepositoryReference(rhs)) {
                PsiExpression lhs = expr.getLExpression();
                if (lhs instanceof PsiReferenceExpression ref
                        && ref.resolve() instanceof PsiVariable var) {
                    this.tracker.taint(var);
                }
            }
        }

        private void seedFromInitializer(@NotNull PsiVariable variable,
                                          PsiExpression initializer) {
            if (this.tracker.isTainted(variable)) {
                return;
            }
            if (initializer != null && isRepositoryReference(initializer)) {
                this.tracker.taint(variable);
            }
        }

        private boolean isRepositoryReference(@NotNull PsiExpression expr) {
            if (expr instanceof PsiReferenceExpression ref) {
                PsiElement resolved = ref.resolve();
                if (resolved instanceof PsiField field) {
                    PsiType type = field.getType();
                    return type != null && isRepositoryType(type);
                }
                if (resolved instanceof PsiParameter param) {
                    PsiType type = param.getType();
                    return type != null && isRepositoryType(type);
                }
            }
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Pass 3: detect save/saveAndFlush in loops
    // -----------------------------------------------------------------------

    private static final class SaveInLoopSinkVisitor extends JavaRecursiveElementWalkingVisitor {

        private final RepositoryReferenceTracker tracker;
        private final ProblemsHolder holder;

        SaveInLoopSinkVisitor(@NotNull RepositoryReferenceTracker tracker,
                              @NotNull ProblemsHolder holder) {
            this.tracker = tracker;
            this.holder = holder;
        }

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            String name = call.getMethodExpression().getReferenceName();
            if (!"save".equals(name) && !"saveAndFlush".equals(name)) {
                return;
            }
            if (!isInsideLoop(call)) {
                return;
            }
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (!this.tracker.isTaintedExpression(qualifier)) {
                return;
            }
            this.holder.registerProblem(call,
                    "Calling " + name + "() in a loop is inefficient — use saveAll(...) instead",
                    ProblemHighlightType.WARNING);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
// -----------------------------------------------------------------------
    // Repository reference tracker (mini TaintedEntityTracker variant)
    // -----------------------------------------------------------------------

    private static final class RepositoryReferenceTracker {

        private final Set<PsiVariable> taintedVars = new HashSet<>();

        void taint(@NotNull PsiVariable var) {
            this.taintedVars.add(var);
        }

        boolean isTainted(@NotNull PsiVariable var) {
            return this.taintedVars.contains(var);
        }

        boolean isTaintedExpression(@Nullable PsiExpression expr) {
            if (expr == null) {
                return false;
            }
            if (expr instanceof PsiReferenceExpression ref) {
                PsiElement resolved = ref.resolve();
                // Field first: PsiField extends PsiVariable, so field check
                // must come before the variable check or it is dead code.
                if (resolved instanceof PsiField field) {
                    PsiType type = field.getType();
                    if (type != null && isRepositoryType(type)) {
                        return true;
                    }
                }
                if (resolved instanceof PsiVariable var) {
                    return this.taintedVars.contains(var);
                }
            }
            return false;
        }

        void propagateAliases(@NotNull PsiCodeBlock body) {
            boolean changed = true;
            while (changed) {
                AliasVisitor visitor = new AliasVisitor();
                body.accept(visitor);
                changed = visitor.didTaint;
            }
        }

        private final class AliasVisitor extends JavaRecursiveElementWalkingVisitor {

            boolean didTaint = false;

            @Override
            public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {
                super.visitDeclarationStatement(statement);
                for (PsiElement declared : statement.getDeclaredElements()) {
                    if (declared instanceof PsiLocalVariable var) {
                        if (!RepositoryReferenceTracker.this.taintedVars.contains(var) && isTaintedExpression(var.getInitializer())) {
                            RepositoryReferenceTracker.this.taintedVars.add(var);
                            this.didTaint = true;
                        }
                    }
                }
            }

            @Override
            public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expr) {
                super.visitAssignmentExpression(expr);
                if (!isTaintedExpression(expr.getRExpression())) {
                    return;
                }
                PsiExpression lhs = expr.getLExpression();
                if (lhs instanceof PsiReferenceExpression ref
                        && ref.resolve() instanceof PsiVariable var
                        && !RepositoryReferenceTracker.this.taintedVars.contains(var)) {
                    RepositoryReferenceTracker.this.taintedVars.add(var);
                    this.didTaint = true;
                }
            }
        }
    }
}
