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

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.sarhan.dataFlow.base.InterproceduralTaintEngine;
import com.sarhan.dataFlow.base.SpringEntitySourceDetector;
import org.jetbrains.annotations.NotNull;

/**
 * Detects "Ghost Saves" — mutations of JPA detached entities that are silently
 * ignored because there is no active Hibernate session to flush the changes.
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │  SOURCE: any call to a Spring Data repository finder or EntityManager   │
 * │          method outside an active @Transactional context                │
 * │                                                                         │
 * │  SINK:   any setter / mutator call on a variable that traces back to    │
 * │          such a source, including through aliases and helper methods     │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Capabilities:
 *   ✅ Zero false positives inside @Transactional contexts (method or class)
 *   ✅ Full pointer-alias propagation (fixed-point loop)
 *   ✅ Optional<T> unwrap tracking: .get(), .orElseThrow(), .orElse()
 *   ✅ Cast passthrough:  (User) rawRef
 *   ✅ Ternary propagation: condition ? tainted : other
 *   ✅ Spring Data interface hierarchy (JpaRepository, CrudRepository, …)
 *   ✅ EntityManager.find() / getReference() / merge() as sources
 *   ✅ @Query / @NamedQuery annotated methods as sources
 *   ✅ Inter-procedural taint: follows entities passed into helper methods
 *   ✅ Works on stub classes without backing fields (no PropertyUtil dependency)
 *   ✅ Works without full PSI index (stub/demo file fallback via simple names)
 *   ✅ Deduplicates problems when multiple entry paths reach the same sink
 *
 * @author Akmal Sarhan
 */
public class SpringGhostSaveDataFlowInspection extends LocalInspectionTool {

    @Override
    public @NotNull PsiElementVisitor buildVisitor(
            @NotNull ProblemsHolder holder, boolean isOnTheFly) {

        SpringEntitySourceDetector detector =
                new SpringEntitySourceDetector(holder.getProject());

        // Shared across all visitMethod calls in this inspection run.
        // Prevents duplicate problem registration when two public entry-point
        // methods both reach the same sink via different call paths.
        Set<PsiElement> reportedElements = new HashSet<>();

        return new JavaElementVisitor() {

            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                if (method.getBody() == null) {
                    return; // abstract / native
                }
                if (method.isConstructor()) {
                    return; // constructors don't ghost-save
                }
                if (isPrivateOrPackage(method)) {
                    return; // reached via worklist, not directly
                }
                if (isGetter(method)) {
                    return; // read-only, not a mutation entry
                }
                if (isContextTransactional(method)) {
                    return; // safe context
                }

                new InterproceduralTaintEngine(detector, holder, reportedElements)
                        .analyse(method, false);
            }
        };
    }

    // -----------------------------------------------------------------------
    // Entry-point filters
    // -----------------------------------------------------------------------

    /**
     * Private and package-private methods are NOT analysed as independent
     * entry points — they are reached inter-procedurally from their callers,
     * which supply the correct taint context via the worklist.
     *
     * Analysing them independently would give them an empty taint set,
     * causing false negatives in the inter-procedural path.
     */
    static boolean isPrivateOrPackage(@NotNull PsiMethod method) {
        PsiModifierList mods = method.getModifierList();
        return mods.hasModifierProperty(PsiModifier.PRIVATE)
                || (!mods.hasModifierProperty(PsiModifier.PUBLIC)
                && !mods.hasModifierProperty(PsiModifier.PROTECTED));
    }

    /**
     * Getters are read-only by convention and are never ghost-save entry points.
     * Skipping them avoids false positives on patterns like:
     *   user.getAddress().setStreet("x")  — where getAddress() is not a mutation
     */
    static boolean isGetter(@NotNull PsiMethod method) {
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

    /**
     * Methods annotated with @Transactional (or whose class is) operate inside
     * an active Hibernate session — entities are managed, not detached.
     *
     * <p>Uses both FQN and simple-name checks so the inspection works on
     * stub classes and demo files that declare {@code @Transactional} without
     * a package qualifier.
     */
    static boolean isContextTransactional(@NotNull PsiMethod method) {
        final String TX_FQN = "org.springframework.transaction.annotation.Transactional";
        final String TX_SIMPLE = "Transactional";

        // 1. Method itself
        if (hasAnnotationByAnyName(method, TX_FQN, TX_SIMPLE)) {
            return true;
        }

        // 2. Superclass hierarchy of the containing class
        PsiClass cls = method.getContainingClass();
        while (cls != null) {
            if (hasAnnotationByAnyName(cls, TX_FQN, TX_SIMPLE)) {
                return true;
            }

            // 3. Superclass declaration of this same method
            for (PsiMethod superMethod : method.findSuperMethods(cls)) {
                if (hasAnnotationByAnyName(superMethod, TX_FQN, TX_SIMPLE)) {
                    return true;
                }
            }

            cls = cls.getSuperClass();
        }

        // 4. Interface default methods with @Transactional
        for (PsiMethod superMethod : method.findSuperMethods()) {
            if (hasAnnotationByAnyName(superMethod, TX_FQN, TX_SIMPLE)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks for an annotation by FQN, then by simple name from the annotation
     * reference element. Essential when annotation classes are not on the
     * classpath (demo files, stub-only projects).
     */
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
}
