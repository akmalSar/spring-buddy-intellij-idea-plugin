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

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * Reports JPA entities that do not declare an identifier in their class hierarchy.
 *
 * @author Akmal Sarhan
 */
public class SpringJpaEntityMissingIdInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                if (!hasOwnEntityAnnotation(psiClass) || hasIdentifier(psiClass)) {
                    return;
                }
                PsiIdentifier identifier = psiClass.getNameIdentifier();
                holder.registerProblem((identifier != null) ? identifier : psiClass,
                        "JPA @Entity has no @Id or @EmbeddedId in its class hierarchy; Hibernate fails startup",
                        ProblemHighlightType.ERROR);
            }
        };
    }

    private static boolean hasOwnEntityAnnotation(PsiClass psiClass) {
        return SpringInspectionUtil.hasAnnotation(psiClass.getModifierList(), "Entity");
    }

    private static boolean hasIdentifier(PsiClass psiClass) {
        for (PsiClass current = psiClass; current != null; current = current.getSuperClass()) {
            for (PsiField field : current.getFields()) {
                PsiModifierList modifiers = field.getModifierList();
                if (SpringInspectionUtil.hasAnnotation(modifiers, "Id")
                        || SpringInspectionUtil.hasAnnotation(modifiers, "EmbeddedId")) {
                    return true;
                }
            }
            for (PsiMethod method : current.getMethods()) {
                PsiModifierList modifiers = method.getModifierList();
                if (SpringInspectionUtil.hasAnnotation(modifiers, "Id")
                        || SpringInspectionUtil.hasAnnotation(modifiers, "EmbeddedId")) {
                    return true;
                }
            }
        }
        return false;
    }
}
