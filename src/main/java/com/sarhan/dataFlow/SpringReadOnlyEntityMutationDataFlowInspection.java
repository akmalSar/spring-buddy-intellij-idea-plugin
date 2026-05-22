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

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.sarhan.dataFlow.base.SpringEntitySourceDetector;
import com.sarhan.render.flow.mutation.MutationFlowMode;
import com.sarhan.render.flow.mutation.ShowJpaEntityMutationFlowFix;
import com.sarhan.springformat.inspection.SpringInspectionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects JPA entity mutations inside @Transactional(readOnly = true) methods.
 * Calling setters or collection mutators on <strong>managed</strong> entities
 * (those loaded from a repository or EntityManager) inside a read-only
 * transaction still triggers dirty checking and may attempt a flush, causing
 * TransactionSystemException or UnexpectedRollbackException at commit time.
 *
 * <p>Uses the DataFlow taint engine to distinguish managed entities from
 * transient ones ({@code new Entity()}), eliminating false positives.
 *
 * @author Akmal Sarhan
 */
public class SpringReadOnlyEntityMutationDataFlowInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        SpringEntitySourceDetector sourceDetector =
                new SpringEntitySourceDetector(holder.getProject());
        Set<PsiElement> reportedElements = new HashSet<>();

        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                if (!isEffectiveReadOnly(method)) {
                    return;
                }
                if (method.getBody() == null) {
                    return;
                }
                analyse(method, sourceDetector, holder, reportedElements);
            }
        };
    }

    private static void analyse(@NotNull PsiMethod method,
                                 @NotNull SpringEntitySourceDetector sourceDetector,
                                 @NotNull ProblemsHolder holder,
                                 @NotNull Set<PsiElement> reportedElements) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return;
        }

        TaintedEntityTracker tracker = new TaintedEntityTracker();
        PsiAnnotation txAnnotation = findTransactionalAnnotation(method);

        // Pass 1: seed taint from repository / EntityManager read sources
        body.accept(new EntitySourceSeedingVisitor(tracker, sourceDetector));

        // Pass 2: propagate aliases (assignments, Optional unwrap, cast, ternary)
        tracker.propagateAliases(body);

        // Pass 3: detect mutations on tainted entity variables
        body.accept(new MutationSinkVisitor(tracker, holder, reportedElements, txAnnotation));
    }

    // -----------------------------------------------------------------------
    // Pass 1: seed entity sources
    // -----------------------------------------------------------------------

    private static boolean isLikelyMutation(String methodName) {
        return methodName.startsWith("set") || methodName.startsWith("add")
                || methodName.startsWith("remove") || methodName.startsWith("clear")
                || methodName.equals("put") || methodName.equals("merge")
                || methodName.equals("persist");
    }

    private static PsiAnnotation findTransactionalAnnotation(PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers != null) {
            PsiAnnotation methodTx = findTransactionalOnModifiers(modifiers);
            if (methodTx != null) {
                return methodTx;
            }
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return null;
        }
        PsiModifierList classModifiers = containingClass.getModifierList();
        return (classModifiers != null) ? findTransactionalOnModifiers(classModifiers) : null;
    }

    private static PsiAnnotation findTransactionalOnModifiers(PsiModifierList modifiers) {
        return SpringInspectionUtil.findAnyAnnotation(modifiers,
                "org.springframework.transaction.annotation.Transactional",
                "jakarta.transaction.Transactional",
                "javax.transaction.Transactional");
    }

    private static boolean isEffectiveReadOnly(PsiMethod method) {
        PsiModifierList methodMods = method.getModifierList();
        PsiAnnotation methodTx = (methodMods != null) ? findTransactionalOnModifiers(methodMods) : null;

        if (methodTx != null) {
            Boolean methodReadOnly = explicitReadOnly(methodTx);
            if (methodReadOnly != null) {
                return methodReadOnly;
            }
            return false;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        PsiModifierList classModifiers = containingClass.getModifierList();
        if (classModifiers == null) {
            return false;
        }
        PsiAnnotation classTx = findTransactionalOnModifiers(classModifiers);
        return classTx != null && Boolean.TRUE.equals(explicitReadOnly(classTx));
    }

    private static Boolean explicitReadOnly(PsiAnnotation annotation) {
        for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
            if ("readOnly".equals(pair.getAttributeName()) && pair.getValue() != null) {
                return "true".equals(pair.getValue().getText());
            }
        }
        return null;
    }

    private static boolean isReadOnly(PsiAnnotation annotation) {
        for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
            if ("readOnly".equals(pair.getAttributeName()) && pair.getValue() != null) {
                return "true".equals(pair.getValue().getText());
            }
        }
        return false;
    }

    private static final class EntitySourceSeedingVisitor
            extends JavaRecursiveElementWalkingVisitor {

        private final TaintedEntityTracker tracker;
        private final SpringEntitySourceDetector sourceDetector;

        EntitySourceSeedingVisitor(@NotNull TaintedEntityTracker tracker,
                                    @NotNull SpringEntitySourceDetector sourceDetector) {
            this.tracker = tracker;
            this.sourceDetector = sourceDetector;
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
            if (rhs != null && isEntitySource(rhs)) {
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
            if (initializer != null && isEntitySource(initializer)) {
                this.tracker.taint(variable);
            }
        }

        private boolean isEntitySource(@NotNull PsiExpression expr) {
            if (expr instanceof PsiMethodCallExpression call) {
                PsiMethod method = call.resolveMethod();
                if (method != null && this.sourceDetector.isDetachedEntitySource(method)) {
                    return true;
                }
                // Optional unwrap chain: repo.findById(id).get() / .orElse(null) / .orElseThrow()
                String name = call.getMethodExpression().getReferenceName();
                if (name != null && (name.equals("get") || name.startsWith("orElse"))) {
                    return isEntitySource(call.getMethodExpression().getQualifierExpression());
                }
            }
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Pass 3: detect mutation sinks
    // -----------------------------------------------------------------------

    private static final class MutationSinkVisitor extends JavaRecursiveElementWalkingVisitor {

        private final TaintedEntityTracker tracker;
        private final ProblemsHolder holder;
        private final Set<PsiElement> reportedElements;
        private final PsiAnnotation txAnnotation;

        MutationSinkVisitor(@NotNull TaintedEntityTracker tracker,
                              @NotNull ProblemsHolder holder,
                              @NotNull Set<PsiElement> reportedElements,
                              PsiAnnotation txAnnotation) {
            this.tracker = tracker;
            this.holder = holder;
            this.reportedElements = reportedElements;
            this.txAnnotation = txAnnotation;
        }

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            PsiMethod target = call.resolveMethod();
            if (target == null) {
                return;
            }
            String name = target.getName();
            if (name == null) {
                return;
            }
            if (!isLikelyMutation(name)) {
                return;
            }
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (!this.tracker.isTaintedExpression(qualifier)) {
                return;
            }
            registerIfNew(call,
                    "Entity mutation inside @Transactional(readOnly = true) triggers dirty checking and may cause rollback",
                    this.txAnnotation);
        }

        private void registerIfNew(@NotNull PsiElement element, @NotNull String message, PsiAnnotation txAnnotation) {
            if (this.reportedElements.add(element)) {
                if (txAnnotation != null) {
                    this.holder.registerProblem(element, message, ProblemHighlightType.ERROR,
                            new ShowJpaEntityMutationFlowFix(MutationFlowMode.READ_ONLY),
                            new RemoveReadOnlyFix(txAnnotation));
                } else {
                    this.holder.registerProblem(element, message, ProblemHighlightType.ERROR,
                            new ShowJpaEntityMutationFlowFix(MutationFlowMode.READ_ONLY));
                }
            }
        }
    }

    private static final class RemoveReadOnlyFix implements LocalQuickFix {

        private final PsiAnnotation txAnnotation;

        RemoveReadOnlyFix(PsiAnnotation txAnnotation) {
            this.txAnnotation = txAnnotation;
        }

        @Override
        @NotNull
        public String getName() {
            return "Remove readOnly = true from @Transactional";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            if (this.txAnnotation == null) {
                return;
            }
            String name = this.txAnnotation.getQualifiedName();
            if (name == null) {
                name = (this.txAnnotation.getNameReferenceElement() != null)
                        ? this.txAnnotation.getNameReferenceElement().getText() : "Transactional";
            }
            String text = this.txAnnotation.getText();
            int paren = text.indexOf('(');
            if (paren < 0) {
                return; // @Transactional with no args — nothing to remove
            }
            String inner = text.substring(paren + 1, text.length() - 1);
            // Remove readOnly = true/false and trailing comma if present
            String cleaned = inner.replaceAll("\\s*readOnly\\s*=\\s*\\w+\\s*,?\\s*", "").trim();
            String newText;
            if (cleaned.isEmpty() || cleaned.equals("")) {
                newText = "@" + name;
            } else {
                newText = "@" + name + "(" + cleaned + ")";
            }
            this.txAnnotation.replace(com.intellij.psi.JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText(newText, this.txAnnotation));
        }
    }
}
