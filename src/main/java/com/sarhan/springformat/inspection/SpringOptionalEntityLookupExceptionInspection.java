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
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

/**
 * Detects bare {@code Optional.get()} or no-arg {@code Optional.orElseThrow()} calls
 * on Optional values sourced from Spring Data repository find-methods, both in
 * immediate-chain form and when the Optional is first stored in a variable:
 * <pre>
 *   repo.findById(id).get()                          // immediate chain
 *   Optional&lt;User&gt; opt = repo.findById(id);
 *   User u = opt.get();                              // variable form — now also detected
 * </pre>
 *
 * @author Akmal Sarhan
 */
public class SpringOptionalEntityLookupExceptionInspection extends AbstractBaseJavaLocalInspectionTool {

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
                OptionalSourceTracker tracker = new OptionalSourceTracker();
                body.accept(new OptionalSeedingVisitor(tracker));
                tracker.propagateAliases(body);
                body.accept(new BareGetSinkVisitor(tracker, holder));
            }
        };
    }

    // -----------------------------------------------------------------------
    // Variable tracker: which local vars hold repo-sourced Optional values
    // -----------------------------------------------------------------------

    private static boolean isRepositoryFindOptional(PsiExpression expression) {
        if (!(expression instanceof PsiMethodCallExpression call)) {
            return false;
        }
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null) {
            return false;
        }
        PsiType type = qualifier.getType();
        PsiClass qualifierClass = (type != null) ? SpringInspectionUtil.resolvedClass(type) : null;
        if (qualifierClass == null || !SpringInspectionUtil.isSpringDataRepository(qualifierClass)) {
            return false;
        }
        PsiType returnType = call.getType();
        if (returnType == null) {
            return true;
        }
        String canonical = returnType.getCanonicalText();
        return canonical.startsWith("java.util.Optional") || canonical.startsWith("Optional");
    }

    private static final class OptionalSourceTracker {

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
                            && !OptionalSourceTracker.this.tainted.contains(var)
                            && var.getInitializer() != null
                            && isTaintedExpression(var.getInitializer())) {
                        OptionalSourceTracker.this.tainted.add(var);
                        this.didTaint = true;
                    }
                }
            }

            @Override
            public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expr) {
                super.visitAssignmentExpression(expr);
                if (expr.getRExpression() == null || !isTaintedExpression(expr.getRExpression())) {
                    return;
                }
                if (expr.getLExpression() instanceof PsiReferenceExpression lref
                        && lref.resolve() instanceof PsiVariable var
                        && !OptionalSourceTracker.this.tainted.contains(var)) {
                    OptionalSourceTracker.this.tainted.add(var);
                    this.didTaint = true;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Pass 1: seed Optional variables from repo findXxx calls
    // -----------------------------------------------------------------------

    private static final class OptionalSeedingVisitor extends JavaRecursiveElementWalkingVisitor {

        private final OptionalSourceTracker tracker;

        OptionalSeedingVisitor(@NotNull OptionalSourceTracker tracker) {
            this.tracker = tracker;
        }

        @Override
        public void visitDeclarationStatement(@NotNull PsiDeclarationStatement stmt) {
            super.visitDeclarationStatement(stmt);
            for (PsiElement e : stmt.getDeclaredElements()) {
                if (e instanceof PsiLocalVariable var && var.getInitializer() != null) {
                    if (isRepoOptionalCall(var.getInitializer())) {
                        this.tracker.taint(var);
                    }
                }
            }
        }

        @Override
        public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expr) {
            super.visitAssignmentExpression(expr);
            PsiExpression rhs = expr.getRExpression();
            if (rhs != null && isRepoOptionalCall(rhs)
                    && expr.getLExpression() instanceof PsiReferenceExpression lref
                    && lref.resolve() instanceof PsiVariable var) {
                this.tracker.taint(var);
            }
        }

        private static boolean isRepoOptionalCall(@NotNull PsiExpression expr) {
            if (!(expr instanceof PsiMethodCallExpression call)) {
                return false;
            }
            return isRepositoryFindOptional(call);
        }
    }

    // -----------------------------------------------------------------------
    // Pass 2: detect bare .get() / .orElseThrow() on tracked variables
    //         or on immediate repo Optional calls
    // -----------------------------------------------------------------------

    private static final class BareGetSinkVisitor extends JavaRecursiveElementWalkingVisitor {

        private final OptionalSourceTracker tracker;
        private final ProblemsHolder holder;

        BareGetSinkVisitor(@NotNull OptionalSourceTracker tracker,
                            @NotNull ProblemsHolder holder) {
            this.tracker = tracker;
            this.holder = holder;
        }

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            String name = call.getMethodExpression().getReferenceName();
            if (!"get".equals(name) && !"orElseThrow".equals(name)) {
                return;
            }
            if ("orElseThrow".equals(name) && call.getArgumentList().getExpressions().length > 0) {
                return;
            }
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            boolean isTrackedVar = qualifier instanceof PsiReferenceExpression ref
                    && ref.resolve() instanceof PsiVariable var
                    && this.tracker.isTainted(var);
            boolean isImmediateChain = !isTrackedVar && isRepositoryFindOptional(qualifier);
            if (isTrackedVar || isImmediateChain) {
                this.holder.registerProblem(call,
                        "Repository Optional lookup should throw an exception with entity and id context",
                        ProblemHighlightType.WARNING,
                        new AddContextualOrElseThrowFix());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static final class AddContextualOrElseThrowFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Use contextual orElseThrow";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PsiMethodCallExpression terminalCall)) {
                return;
            }
            PsiExpression qualifierExpr = terminalCall.getMethodExpression().getQualifierExpression();
            PsiMethodCallExpression findCall = null;
            if (qualifierExpr instanceof PsiMethodCallExpression mce) {
                findCall = mce;
            } else if (qualifierExpr instanceof PsiReferenceExpression ref
                    && ref.resolve() instanceof PsiLocalVariable local
                    && local.getInitializer() instanceof PsiMethodCallExpression init) {
                findCall = init;
            }
            if (findCall == null) {
                return;
            }
            String entityName = entityName(findCall);
            String idText = idText(findCall.getArgumentList());
            String replacement = findCall.getText() + ".orElseThrow(() -> new IllegalStateException(\""
                    + entityName + " not found: \" + " + idText + "))";
            terminalCall.replace(JavaPsiFacade.getElementFactory(project)
                    .createExpressionFromText(replacement, terminalCall));
        }

        private static String entityName(PsiMethodCallExpression findCall) {
            PsiExpression qualifier = findCall.getMethodExpression().getQualifierExpression();
            if (qualifier == null) {
                return "Entity";
            }
            String text = qualifier.getText();
            String base = text.replaceFirst("(?i)(Repository|Repo)$", "");
            if (base.isEmpty()) {
                return "Entity";
            }
            return Character.toUpperCase(base.charAt(0)) + base.substring(1);
        }

        private static String idText(PsiExpressionList argumentList) {
            PsiExpression[] arguments = argumentList.getExpressions();
            return (arguments.length > 0) ? arguments[0].getText() : "\"<unknown>\"";
        }
    }
}
