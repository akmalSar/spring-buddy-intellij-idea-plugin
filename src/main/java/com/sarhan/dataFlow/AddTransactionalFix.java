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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Quick fix for Ghost Save inspection.
 *
 * Adds @Transactional to the nearest enclosing non-private method that is
 * an appropriate Spring transaction boundary:
 *
 *   1. The method that directly contains the flagged call — preferred.
 *   2. If that method is private (not a Spring proxy boundary), walks up
 *      to the nearest public/protected method.
 *
 * After inserting the annotation the import is added and the file is
 * reformatted via JavaCodeStyleManager to keep the style consistent.
 *
 * Placement:
 *   - If the method already has other annotations, @Transactional is inserted
 *     before them (topmost position).
 *   - Otherwise it is added directly before the method signature.
 *
 * @author Akmal Sarhan
 */
public class AddTransactionalFix implements LocalQuickFix {

    private static final String TRANSACTIONAL_FQN =
            "org.springframework.transaction.annotation.Transactional";

    // -----------------------------------------------------------------------
    // LocalQuickFix API
    // -----------------------------------------------------------------------

    @Override
    public @NotNull String getFamilyName() {
        return "Add @Transactional";
    }

    @Override
    public @NotNull String getName() {
        return "Add @Transactional to enclosing method";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement flaggedElement = descriptor.getPsiElement();

        // Find the nearest enclosing method that is a valid Spring proxy boundary
        PsiMethod targetMethod = findTransactionBoundary(flaggedElement);
        if (targetMethod == null) {
            return;
        }

        // Already annotated — nothing to do
        if (targetMethod.hasAnnotation(TRANSACTIONAL_FQN)) {
            return;
        }
        if (hasAnnotationBySimpleName(targetMethod, "Transactional")) {
            return;
        }

        addTransactionalAnnotation(project, targetMethod);
    }

    // -----------------------------------------------------------------------
    // Finding the right method to annotate
    // -----------------------------------------------------------------------

    /**
     * Walks up the PSI tree from the flagged element to find the correct
     * method to annotate.
     *
     * Spring AOP proxies only intercept public and protected methods.
     * Private methods cannot be a @Transactional boundary, so we skip them
     * and annotate the nearest non-private enclosing method instead.
     */
    private static PsiMethod findTransactionBoundary(@NotNull PsiElement element) {
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);

        while (method != null) {
            PsiModifierList mods = method.getModifierList();
            boolean isPrivate = mods.hasModifierProperty(PsiModifier.PRIVATE);

            if (!isPrivate) {
                return method; // public or protected — valid proxy boundary
            }

            // Private — step up to the next enclosing method
            method = PsiTreeUtil.getParentOfType(method, PsiMethod.class);
        }

        return null; // no suitable boundary found (e.g. static initializer)
    }

    // -----------------------------------------------------------------------
    // Annotation insertion
    // -----------------------------------------------------------------------

    private static void addTransactionalAnnotation(@NotNull Project project,
                                                   @NotNull PsiMethod method) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

        // Create the annotation element
        PsiAnnotation annotation =
                factory.createAnnotationFromText("@" + TRANSACTIONAL_FQN, method);

        PsiModifierList modifierList = method.getModifierList();

        // Insert before any existing annotations, or before the modifier list itself
        PsiAnnotation[] existingAnnotations = modifierList.getAnnotations();
        if (existingAnnotations.length > 0) {
            // Add before the first existing annotation
            modifierList.addBefore(annotation, existingAnnotations[0]);
        } else {
            // No existing annotations — add at the start of the modifier list
            // If the modifier list is empty, add before the method entirely
            PsiElement firstModifier = modifierList.getFirstChild();
            if (firstModifier != null) {
                modifierList.addBefore(annotation, firstModifier);
            } else {
                modifierList.add(annotation);
            }
        }

        // Shorten the FQN to @Transactional and add the import
        JavaCodeStyleManager.getInstance(project)
                .shortenClassReferences(method.getModifierList());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Checks for @Transactional by simple name — handles the case where the
     * annotation is already present but unresolved (no Spring on classpath).
     */
    private static boolean hasAnnotationBySimpleName(@NotNull PsiModifierListOwner element,
                                                     @NotNull String simpleName) {
        PsiModifierList modList = element.getModifierList();
        if (modList == null) {
            return false;
        }
        for (PsiAnnotation ann : modList.getAnnotations()) {
            PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
            if (ref != null && simpleName.equals(ref.getReferenceName())) {
                return true;
            }
        }
        return false;
    }
}
