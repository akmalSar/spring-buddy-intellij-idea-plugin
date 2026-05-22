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
import java.util.Locale;
import java.util.Set;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.sarhan.dataFlow.base.LazyLoadingSinkDetector;
import com.sarhan.dataFlow.base.SpringEntitySourceDetector;
import com.sarhan.render.flow.nplusone.ShowNPlusOneFlowFix;
import com.sarhan.springformat.inspection.SpringInspectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Predicts N+1 query problems by detecting lazy getter calls on JPA entities
 * inside loops or stream operations, where the entities came from a collection
 * returned by a Spring Data repository or EntityManager query.
 *
 * <p>Uses the DataFlow taint engine to track detached entity collections:
 * <pre>
 *   SOURCE: List&lt;Order&gt; orders = orderRepo.findAll();
 *   ALIAS:  for (Order order : orders)   — order is tainted
 *   SINK:   order.getItems().size()       — triggers N+1 select per iteration
 * </pre>
 *
 * <p>Unlike the PSI-only N+1 inspection, this only flags when the collection
 * variable traces back to a repository/EntityManager source, eliminating false
 * positives on DTO projections or already-fetched join results passed as
 * method parameters.
 *
 * @author Akmal Sarhan
 */
public class SpringJpaNPlusOnePredictionDataFlowInspection extends AbstractBaseJavaLocalInspectionTool {

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
                if (method.getBody() == null || method.isConstructor()) {
                    return;
                }
                if (!isLazyLoadingPossible(method)) {
                    return;
                }
                if (isGetter(method)) {
                    return; // read-only, not an N+1 entry point
                }
                analyse(method, sourceDetector, holder, reportedElements);
            }
        };
    }

    // -----------------------------------------------------------------------
    // Analysis
    // -----------------------------------------------------------------------

    private static void analyse(@NotNull PsiMethod method,
                                 @NotNull SpringEntitySourceDetector sourceDetector,
                                 @NotNull ProblemsHolder holder,
                                 @NotNull Set<PsiElement> reportedElements) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return;
        }

        TaintedEntityTracker tracker = new TaintedEntityTracker();

        // Seed taint from method parameters (inter-service calls, DTO mappers, etc.)
        for (PsiParameter param : method.getParameterList().getParameters()) {
            PsiType paramType = param.getType();
            if (isCollectionOrArrayType(paramType) || isEntityType(paramType)) {
                tracker.taint(param);
            }
        }

        // Pass 1: seed taint from collection-returning repository / EM sources
        body.accept(new CollectionSourceSeedingVisitor(tracker, sourceDetector));

        // Alias propagation (assignments, for-each, forEach/map lambdas, stream)
        tracker.propagateAliases(body);

        // Pass 2: detect lazy getter / direct-field sinks on tainted variables inside loops
        body.accept(new NPlusOneSinkVisitor(tracker, holder, reportedElements));
    }

    // -----------------------------------------------------------------------
    // Pass 1: seed collection sources
    // -----------------------------------------------------------------------

    /**
     * Returns true if the type is a collection, iterable, stream, page, slice, or array type.
     */
    private static boolean isCollectionOrArrayType(@Nullable PsiType type) {
        if (type instanceof PsiArrayType) {
            return true;
        }
        if (!(type instanceof PsiClassType classType)) {
            return false;
        }
        String name = classType.getClassName();
        if (name == null) {
            return false;
        }
        return name.equals("List") || name.equals("Set")
                || name.equals("Collection") || name.equals("Iterable")
                || name.equals("Stream")
                || name.equals("Page") || name.equals("Slice");
    }

    /**
     * Returns true if the type is a JPA entity or mapped superclass.
     */
    private static boolean isEntityType(@Nullable PsiType type) {
        if (!(type instanceof PsiClassType classType)) {
            return false;
        }
        PsiClass resolved = classType.resolve();
        return resolved != null && SpringInspectionUtil.isJpaEntity(resolved);
    }

    /**
     * Returns true if the field is annotated with a lazy JPA association.
     */
    private static boolean isLazyAssociation(@NotNull PsiField field) {
        PsiModifierList modifiers = field.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if ((isAnnotation(annotation, "OneToMany") || isAnnotation(annotation, "ManyToMany")
                    || isAnnotation(annotation, "ElementCollection")) && !isEager(annotation)) {
                return true;
            }
            if (isAnnotation(annotation, "Basic") && isLazy(annotation)) {
                return true;
            }
            if (isAnnotation(annotation, "LazyCollection") && !isEager(annotation)) {
                return true;
            }
            if ((isAnnotation(annotation, "ManyToOne") || isAnnotation(annotation, "OneToOne")) && isLazy(annotation)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnnotation(@NotNull PsiAnnotation annotation, @NotNull String shortName) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return shortName.equals(name) || (name != null && name.endsWith("." + shortName))
                || annotation.getText().startsWith("@" + shortName);
    }

    private static boolean isLazy(@NotNull PsiAnnotation annotation) {
        return annotation.getText().contains("LAZY");
    }

    private static boolean isEager(@NotNull PsiAnnotation annotation) {
        return annotation.getText().contains("EAGER");
    }

    /**
     * Returns true if the element is inside a loop statement or lambda expression.
     * Stops at method/class boundaries.
     */
    private static boolean isInsideLoopOrStream(@NotNull PsiElement element) {
        PsiElement parent = element.getParent();
        while (parent != null
                && !(parent instanceof PsiMethod)
                && !(parent instanceof PsiClass)) {
            if (parent instanceof PsiLoopStatement) {
                return true;
            }
            if (parent instanceof PsiLambdaExpression) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    /**
     * N+1 queries only happen when lazy loading is possible (inside a transaction
     * or with Open Session in View). Outside a transaction you get
     * LazyInitializationException instead — handled by the ERROR-level inspection.
     */
    private static boolean isLazyLoadingPossible(@NotNull PsiMethod method) {
        if (isContextTransactional(method)) {
            return true;
        }
        if (isOpenSessionInViewEnabled(method)) {
            return true;
        }
        PsiClass containingClass = method.getContainingClass();
        return containingClass != null && isTestClass(containingClass);
    }

    private static boolean isContextTransactional(@NotNull PsiMethod method) {
        final String TX_FQN = "org.springframework.transaction.annotation.Transactional";
        final String TX_SIMPLE = "Transactional";

        if (hasAnnotationByAnyName(method, TX_FQN, TX_SIMPLE)) {
            return true;
        }

        PsiClass cls = method.getContainingClass();
        while (cls != null) {
            if (hasAnnotationByAnyName(cls, TX_FQN, TX_SIMPLE)) {
                return true;
            }
            for (PsiMethod superMethod : method.findSuperMethods(cls)) {
                if (hasAnnotationByAnyName(superMethod, TX_FQN, TX_SIMPLE)) {
                    return true;
                }
            }
            cls = cls.getSuperClass();
        }

        for (PsiMethod superMethod : method.findSuperMethods()) {
            if (hasAnnotationByAnyName(superMethod, TX_FQN, TX_SIMPLE)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasAnnotationByAnyName(
            @NotNull PsiModifierListOwner element,
            @NotNull String fqn,
            @NotNull String simpleName) {

        if (element.hasAnnotation(fqn)) {
            return true;
        }

        PsiModifierList modList = element.getModifierList();
        if (modList == null) {
            return false;
        }

        for (PsiAnnotation ann : modList.getAnnotations()) {
            String qualName = ann.getQualifiedName();
            if (qualName != null) {
                if (qualName.equals(fqn) || qualName.equals(simpleName)) {
                    return true;
                }
            } else {
                PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
                if (ref != null && simpleName.equals(ref.getReferenceName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOpenSessionInViewEnabled(@NotNull PsiMethod method) {
        String text = method.getContainingFile().getText().toLowerCase(Locale.ROOT);
        return text.contains("spring.jpa.open-in-view=true")
                || text.contains("open-in-view: true");
    }

    private static boolean isGetter(@NotNull PsiMethod method) {
        String name = method.getName();
        if (!name.startsWith("get") && !name.startsWith("is")) {
            return false;
        }
        if (method.getParameterList().getParametersCount() != 0) {
            return false;
        }
        PsiType ret = method.getReturnType();
        return ret != null && !ret.equals(PsiTypes.voidType());
    }

    private static boolean isTestClass(PsiClass psiClass) {
        String name = psiClass.getName();
        if (name != null && (name.endsWith("Test") || name.endsWith("Tests")
                || name.endsWith("IT") || name.endsWith("TestIT")
                || name.endsWith("TestsIT") || name.endsWith("ITTests"))) {
            return true;
        }
        PsiModifierList modifiers = psiClass.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation a : modifiers.getAnnotations()) {
            String annName = a.getQualifiedName();
            if (annName == null && a.getNameReferenceElement() != null) {
                annName = a.getNameReferenceElement().getText();
            }
            if (annName != null && ("SpringBootTest".equals(annName) || annName.endsWith(".SpringBootTest")
                    || "WebMvcTest".equals(annName) || annName.endsWith(".WebMvcTest")
                    || "DataJpaTest".equals(annName) || annName.endsWith(".DataJpaTest")
                    || "ContextConfiguration".equals(annName) || annName.endsWith(".ContextConfiguration")
                    || "Test".equals(annName) || annName.endsWith(".Test"))) {
                return true;
            }
        }
        return false;
    }

    private static final class CollectionSourceSeedingVisitor
            extends JavaRecursiveElementWalkingVisitor {

        private final TaintedEntityTracker tracker;
        private final SpringEntitySourceDetector sourceDetector;

        CollectionSourceSeedingVisitor(@NotNull TaintedEntityTracker tracker,
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
            if (rhs != null && isCollectionSource(rhs)) {
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
            if (initializer != null && isCollectionSource(initializer)) {
                this.tracker.taint(variable);
            }
        }

        private boolean isCollectionSource(@NotNull PsiExpression expr) {
            if (expr instanceof PsiMethodCallExpression call) {
                PsiMethod method = call.resolveMethod();
                if (method != null) {
                    if (this.sourceDetector.isDetachedEntitySource(method)) {
                        // Must return a collection or array of entities
                        if (isCollectionOrArrayType(method.getReturnType())) {
                            return true;
                        }
                    }
                }
                // Optional unwrap chain: repo.findAllOpt().get() / .orElse(null)
                String name = call.getMethodExpression().getReferenceName();
                if (name != null && (name.equals("get") || name.startsWith("orElse"))) {
                    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
                    if (qualifier != null) {
                        return isCollectionSource(qualifier);
                    }
                }
            }
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Pass 2: detect lazy getter sinks inside loops / streams
    // -----------------------------------------------------------------------

    private static final class NPlusOneSinkVisitor extends JavaRecursiveElementWalkingVisitor {

        private final TaintedEntityTracker tracker;
        private final ProblemsHolder holder;
        private final Set<PsiElement> reportedElements;

        NPlusOneSinkVisitor(@NotNull TaintedEntityTracker tracker,
                            @NotNull ProblemsHolder holder,
                            @NotNull Set<PsiElement> reportedElements) {
            this.tracker = tracker;
            this.holder = holder;
            this.reportedElements = reportedElements;
        }

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            PsiMethod resolved = call.resolveMethod();
            if (resolved == null) {
                return;
            }
            if (!LazyLoadingSinkDetector.isLazyLoadingTrigger(resolved)) {
                return;
            }
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (!this.tracker.isTaintedExpression(qualifier)) {
                return;
            }
            if (!isInsideLoopOrStream(call)) {
                return;
            }
            registerIfNew(call,
                    "N+1 query predicted: calling " + resolved.getName()
                            + "() on a detached entity inside a loop or stream operation triggers lazy loading");
        }

        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            PsiElement resolved = expression.resolve();
            if (!(resolved instanceof PsiField field)) {
                return;
            }
            if (!isLazyAssociation(field)) {
                return;
            }
            PsiExpression qualifier = expression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (!this.tracker.isTaintedExpression(qualifier)) {
                return;
            }
            if (!isInsideLoopOrStream(expression)) {
                return;
            }
            registerIfNew(expression,
                    "N+1 query predicted: lazy association loaded inside a loop or stream "
                            + "for each element of a repository result — use fetch join, EntityGraph, or batch loading");
        }

        private void registerIfNew(@NotNull PsiElement element, @NotNull String message) {
            if (this.reportedElements.add(element)) {
                this.holder.registerProblem(element, message, ProblemHighlightType.WARNING,
                        new ShowNPlusOneFlowFix());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
}
