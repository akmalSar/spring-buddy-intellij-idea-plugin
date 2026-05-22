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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

/**
 * Detects when a collection returned by a Spring Data repository or
 * {@code EntityManager} query is filtered/sorted/skipped in memory instead
 * of delegating to the database via {@code @Query}, derived query methods,
 * or {@code Pageable}.
 *
 * <p>Covers both immediate-chain and variable-assignment patterns:
 * <pre>
 *   repo.findAll().stream().filter(...)          // immediate chain
 *   List&lt;X&gt; all = repo.findAll();
 *   all.stream().filter(...)                     // variable form — previously missed
 * </pre>
 *
 * @author Spring Buddy
 * @author Akmal Sarhan
 */
public class SpringInMemoryFilteringDataFlowInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final Set<String> IN_MEMORY_METHODS = Set.of(
            "filter", "sorted", "skip", "limit", "distinct", "peek");

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiCodeBlock body = method.getBody();
                if (body == null) {
                    return;
                }
                CollectionSourceTracker tracker = new CollectionSourceTracker();
                body.accept(new CollectionSeedingVisitor(tracker));
                tracker.propagateAliases(body);
                body.accept(new InMemoryFilterSinkVisitor(tracker, holder));
            }
        };
    }

    // -----------------------------------------------------------------------
    // Variable tracker: which local variables hold repo-sourced collections
    // -----------------------------------------------------------------------

    static boolean isInMemoryFilteringCall(PsiMethodCallExpression call) {
        String methodName = call.getMethodExpression().getReferenceName();
        if (methodName == null) {
            return false;
        }
        return IN_MEMORY_METHODS.contains(methodName);
    }

    static boolean isCollectionSource(PsiExpression expression) {
        if (expression instanceof PsiMethodCallExpression chainCall) {
            String chainMethod = chainCall.getMethodExpression().getReferenceName();
            if ("stream".equals(chainMethod) || "parallelStream".equals(chainMethod)) {
                PsiExpression streamQualifier = chainCall.getMethodExpression().getQualifierExpression();
                if (streamQualifier instanceof PsiMethodCallExpression innerCall) {
                    PsiMethod innerResolved = innerCall.resolveMethod();
                    if (innerResolved != null) {
                        return isRepositoryOrEntityManagerMethod(innerResolved);
                    }
                    return isLikelyRepositoryExpression(innerCall);
                }
                if (streamQualifier instanceof PsiReferenceExpression ref) {
                    return isSpringDataRepositoryReference(ref);
                }
            }
            PsiMethod resolved = chainCall.resolveMethod();
            if (resolved != null) {
                return isRepositoryOrEntityManagerMethod(resolved) && returnsCollectionOrStream(resolved);
            }
            return isLikelyRepositoryExpression(chainCall);
        }
        if (expression instanceof PsiReferenceExpression ref) {
            return isSpringDataRepositoryReference(ref) && isCollectionVariable(ref);
        }
        return false;
    }

    private static boolean isRepositoryOrEntityManagerMethod(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        if (SpringInspectionUtil.isSpringDataRepository(containingClass)) {
            return true;
        }
        String clsName = containingClass.getQualifiedName();
        if (clsName != null && (clsName.equals("jakarta.persistence.EntityManager")
                || clsName.equals("javax.persistence.EntityManager")
                || clsName.startsWith("org.springframework.data.jpa.repository.")
                || clsName.startsWith("org.springframework.data.repository."))) {
            return true;
        }
        if (hasSpringDataSuperInterface(containingClass)) {
            return true;
        }
        return false;
    }

    private static boolean hasSpringDataSuperInterface(PsiClass psiClass) {
        for (PsiClass superType : psiClass.getSupers()) {
            if (SpringInspectionUtil.isSpringDataRepository(superType)) {
                return true;
            }
            String fqn = superType.getQualifiedName();
            if (fqn != null && (fqn.startsWith("org.springframework.data.jpa.repository.")
                    || fqn.startsWith("org.springframework.data.repository."))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLikelyRepositoryExpression(PsiMethodCallExpression call) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier instanceof PsiReferenceExpression ref) {
            if (isSpringDataRepositoryReference(ref)) {
                return returnsCollectionOrStreamByName(call);
            }
        }
        if (qualifier instanceof PsiMethodCallExpression) {
            return isLikelyRepositoryExpression((PsiMethodCallExpression) qualifier);
        }
        return false;
    }

    private static boolean isSpringDataRepositoryReference(PsiReferenceExpression ref) {
        PsiType type = ref.getType();
        if (type == null) {
            return false;
        }
        PsiClass cls = SpringInspectionUtil.resolvedClass(type);
        if (cls != null) {
            if (SpringInspectionUtil.isSpringDataRepository(cls)) {
                return true;
            }
            String fqn = cls.getQualifiedName();
            if (fqn != null && (fqn.startsWith("org.springframework.data.jpa")
                    || fqn.startsWith("org.springframework.data.repository"))) {
                return true;
            }
            String name = cls.getName();
            if (name != null && name.endsWith("Repository")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCollectionVariable(PsiReferenceExpression ref) {
        PsiType type = ref.getType();
        if (type == null) {
            return false;
        }
        String text = type.getCanonicalText();
        if (text == null) {
            return false;
        }
        return text.startsWith("java.util.List") || text.startsWith("java.util.Set")
                || text.startsWith("java.util.Collection") || text.startsWith("java.util.Map")
                || text.startsWith("java.util.stream.Stream");
    }

    private static boolean returnsCollectionOrStream(PsiMethod method) {
        PsiType returnType = method.getReturnType();
        if (returnType == null) {
            return false;
        }
        String text = returnType.getCanonicalText();
        return text != null && (text.startsWith("java.util.List") || text.startsWith("java.util.Set")
                || text.startsWith("java.util.Collection") || text.startsWith("java.util.Map")
                || text.startsWith("java.util.stream.Stream"));
    }

    private static boolean returnsCollectionOrStreamByName(PsiMethodCallExpression call) {
        PsiMethod resolved = call.resolveMethod();
        if (resolved != null) {
            return returnsCollectionOrStream(resolved);
        }
        String methodName = call.getMethodExpression().getReferenceName();
        if (methodName == null) {
            return false;
        }
        return methodName.startsWith("find") || methodName.startsWith("get") || methodName.equals("findAll")
                || methodName.startsWith("query") || methodName.startsWith("read")
                || methodName.startsWith("search") || methodName.startsWith("stream");
    }

    private static final class CollectionSourceTracker {

        private final Set<PsiVariable> tainted = new HashSet<>();

        void taint(@NotNull PsiVariable var) {
            this.tainted.add(var);
        }

        boolean isTainted(@NotNull PsiVariable var) {
            return this.tainted.contains(var);
        }

        boolean isTaintedExpression(@NotNull PsiExpression expr) {
            if (expr instanceof PsiReferenceExpression ref
                    && ref.resolve() instanceof PsiVariable var) {
                return this.tainted.contains(var);
            }
            return false;
        }

        void propagateAliases(@NotNull PsiCodeBlock body) {
            boolean changed = true;
            while (changed) {
                AliasVisitor v = new AliasVisitor();
                body.accept(v);
                changed = v.didTaint;
            }
        }

        private final class AliasVisitor extends JavaRecursiveElementWalkingVisitor {
            boolean didTaint = false;

            @Override
            public void visitDeclarationStatement(@NotNull PsiDeclarationStatement stmt) {
                super.visitDeclarationStatement(stmt);
                for (PsiElement e : stmt.getDeclaredElements()) {
                    if (e instanceof PsiLocalVariable var
                            && !CollectionSourceTracker.this.tainted.contains(var)
                            && var.getInitializer() != null
                            && isTaintedExpression(var.getInitializer())) {
                        CollectionSourceTracker.this.tainted.add(var);
                        this.didTaint = true;
                    }
                }
            }

            @Override
            public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expr) {
                super.visitAssignmentExpression(expr);
                if (expr.getRExpression() == null) {
                    return;
                }
                if (!isTaintedExpression(expr.getRExpression())) {
                    return;
                }
                if (expr.getLExpression() instanceof PsiReferenceExpression lref
                        && lref.resolve() instanceof PsiVariable var
                        && !CollectionSourceTracker.this.tainted.contains(var)) {
                    CollectionSourceTracker.this.tainted.add(var);
                    this.didTaint = true;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Pass 1: seed local variables whose initialiser / assignment comes from
    //         a repository or EntityManager collection-returning call
    // -----------------------------------------------------------------------

    private static final class CollectionSeedingVisitor extends JavaRecursiveElementWalkingVisitor {

        private final CollectionSourceTracker tracker;

        CollectionSeedingVisitor(@NotNull CollectionSourceTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public void visitDeclarationStatement(@NotNull PsiDeclarationStatement stmt) {
            super.visitDeclarationStatement(stmt);
            for (PsiElement e : stmt.getDeclaredElements()) {
                if (e instanceof PsiLocalVariable var && var.getInitializer() != null) {
                    if (isRepoCollectionCall(var.getInitializer())) {
                        this.tracker.taint(var);
                    }
                }
            }
        }

        @Override
        public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expr) {
            super.visitAssignmentExpression(expr);
            PsiExpression rhs = expr.getRExpression();
            if (rhs != null && isRepoCollectionCall(rhs)
                    && expr.getLExpression() instanceof PsiReferenceExpression lref
                    && lref.resolve() instanceof PsiVariable var) {
                this.tracker.taint(var);
            }
        }

        private static boolean isRepoCollectionCall(@NotNull PsiExpression expr) {
            if (!(expr instanceof PsiMethodCallExpression call)) {
                return false;
            }
            PsiMethod resolved = call.resolveMethod();
            if (resolved != null) {
                return isRepositoryOrEntityManagerMethod(resolved)
                        && returnsCollectionOrStream(resolved);
            }
            return isLikelyRepositoryExpression(call);
        }
    }

    // -----------------------------------------------------------------------
    // Pass 2: detect in-memory filter calls on tracked variables or immediate chains
    // -----------------------------------------------------------------------

    private static final class InMemoryFilterSinkVisitor extends JavaRecursiveElementWalkingVisitor {

        private final CollectionSourceTracker tracker;
        private final ProblemsHolder holder;

        InMemoryFilterSinkVisitor(@NotNull CollectionSourceTracker tracker,
                                    @NotNull ProblemsHolder holder) {
            this.tracker = tracker;
            this.holder = holder;
        }

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            if (!isInMemoryFilteringCall(call)) {
                return;
            }
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (isRepoSourcedQualifier(qualifier)) {
                this.holder.registerProblem(call,
                        "Collection from a repository or EntityManager is filtered/sorted/skipped in memory; prefer database-level filtering via @Query, derived query methods, or Pageable",
                        ProblemHighlightType.WARNING);
            }
        }

        private boolean isRepoSourcedQualifier(@NotNull PsiExpression expr) {
            if (expr instanceof PsiReferenceExpression ref
                    && ref.resolve() instanceof PsiVariable var
                    && this.tracker.isTainted(var)) {
                return true;
            }
            return isCollectionSource(expr);
        }
    }

    // -----------------------------------------------------------------------
    // Static helpers (kept package-visible for tests)
    // -----------------------------------------------------------------------
}
