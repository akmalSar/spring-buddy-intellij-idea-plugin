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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NotNull;

/**
 * SpringEntityEqualsHashCodeOnId: flags an {@code @Entity} class whose hand-written
 * {@code equals(Object)} or {@code hashCode()} references a field annotated with {@code @Id}
 * or {@code @EmbeddedId}. Generated identifiers are {@code null} before persist, which breaks
 * equality contracts when entities are added to {@code Set}/{@code Map} prior to flush.
 *
 * @author Akmal Sarhan
 */
public class SpringEntityEqualsHashCodeOnIdInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                super.visitClass(psiClass);
                if (!isEntity(psiClass)) {
                    return;
                }
                Set<String> idFieldNames = collectIdFieldNames(psiClass);
                if (idFieldNames.isEmpty()) {
                    return;
                }
                for (PsiMethod method : psiClass.getMethods()) {
                    if (!isEqualsOrHashCode(method)) {
                        continue;
                    }
                    PsiCodeBlock body = method.getBody();
                    if (body == null) {
                        continue;
                    }
                    if (referencesAny(body, idFieldNames)) {
                        PsiElement reportTarget = method.getNameIdentifier();
                        if (reportTarget == null || !reportTarget.isPhysical()) {
                            reportTarget = method;
                        }
                        // Skip Lombok-generated synthetic methods that are not physically present in source
                        if (!reportTarget.isPhysical()) {
                            continue;
                        }
                        holder.registerProblem(reportTarget,
                                "@Entity " + method.getName()
                                        + "() references the @Id field; generated identifiers are null before persist",
                                ProblemHighlightType.ERROR);
                    }
                }
            }
        };
    }

    private static boolean isEntity(PsiClass psiClass) {
        for (PsiAnnotation annotation : psiClass.getAnnotations()) {
            String fqn = annotation.getQualifiedName();
            if (fqn == null) {
                continue;
            }
            if ("javax.persistence.Entity".equals(fqn)
                    || "jakarta.persistence.Entity".equals(fqn)
                    || "Entity".equals(fqn)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> collectIdFieldNames(PsiClass psiClass) {
        Set<String> names = new HashSet<>();
        for (PsiField field : psiClass.getAllFields()) {
            for (PsiAnnotation annotation : field.getAnnotations()) {
                String fqn = annotation.getQualifiedName();
                if (fqn == null) {
                    continue;
                }
                if ("javax.persistence.Id".equals(fqn)
                        || "jakarta.persistence.Id".equals(fqn)
                        || "javax.persistence.EmbeddedId".equals(fqn)
                        || "jakarta.persistence.EmbeddedId".equals(fqn)
                        || "Id".equals(fqn)
                        || "EmbeddedId".equals(fqn)) {
                    names.add(field.getName());
                }
            }
        }
        return names;
    }

    private static boolean isEqualsOrHashCode(PsiMethod method) {
        if ("hashCode".equals(method.getName()) && method.getParameterList().getParametersCount() == 0) {
            return true;
        }
        if ("equals".equals(method.getName()) && method.getParameterList().getParametersCount() == 1) {
            return true;
        }
        return false;
    }

    private static boolean referencesAny(PsiCodeBlock body, Set<String> fieldNames) {
        boolean[] found = {false};
        body.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (found[0]) {
                    return;
                }
                if (element instanceof PsiReferenceExpression ref) {
                    String name = ref.getReferenceName();
                    if (name != null && fieldNames.contains(name)) {
                        found[0] = true;
                        return;
                    }
                }
                super.visitElement(element);
            }
        });
        return found[0];
    }
}
