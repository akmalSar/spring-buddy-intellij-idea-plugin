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
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDoWhileStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWhileStatement;
import org.jetbrains.annotations.NotNull;

/**
 * Detects access to lazy JPA associations inside loops or stream operations
 * within transactional methods. Each access triggers a separate query, causing
 * the classic N+1 query performance problem.
 *
 * @author Akmal Sarhan
 */
public class SpringNPlusOneQueryInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                if (!hasTransactionalAnnotation(method) || method.getBody() == null) {
                    return;
                }
                method.getBody().accept(new LazyAssociationAccessVisitor(holder));
            }
        };
    }

    private static boolean hasTransactionalAnnotation(PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (isAnnotation(annotation, "Transactional")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStreamOperationLambda(PsiLambdaExpression expression) {
        PsiElement parent = expression.getParent();
        while (parent != null && !(parent instanceof PsiMethodCallExpression)) {
            parent = parent.getParent();
        }
        if (!(parent instanceof PsiMethodCallExpression call)) {
            return false;
        }
        return isStreamLikeOperation(call);
    }

    private static boolean isStreamLikeOperation(PsiMethodCallExpression call) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null) {
            return false;
        }
        PsiType type = qualifier.getType();
        String canonicalText = (type != null) ? type.getCanonicalText() : "";
        if (canonicalText.startsWith("java.util.stream.")) {
            return true;
        }
        if (qualifier instanceof PsiMethodCallExpression qualifierCall) {
            String qualifierMethodName = qualifierCall.getMethodExpression().getReferenceName();
            return "stream".equals(qualifierMethodName) || "parallelStream".equals(qualifierMethodName);
        }
        return false;
    }

    private static PsiField fieldFromGetter(PsiMethodCallExpression expression) {
        PsiMethod method = expression.resolveMethod();
        if (method == null || method.getParameterList().getParametersCount() != 0) {
            return null;
        }
        String name = method.getName();
        String fieldName = null;
        if (name.startsWith("get") && name.length() > 3) {
            fieldName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
        } else if (name.startsWith("is") && name.length() > 2) {
            fieldName = Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        PsiClass containingClass = method.getContainingClass();
        if (fieldName == null || containingClass == null) {
            return null;
        }
        PsiField field = containingClass.findFieldByName(fieldName, false);
        if (field == null) {
            return null;
        }
        PsiType methodReturnType = method.getReturnType();
        PsiType fieldType = field.getType();
        if (methodReturnType != null && fieldType != null
                && !methodReturnType.isAssignableFrom(fieldType) && !fieldType.isAssignableFrom(methodReturnType)) {
            return null;
        }
        if (!SpringInspectionUtil.isJpaEntity(containingClass)) {
            return null;
        }
        return field;
    }

    private static boolean isLazyAssociation(PsiField field) {
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

    private static boolean isAnnotation(PsiAnnotation annotation, String shortName) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return shortName.equals(name) || (name != null && name.endsWith("." + shortName));
    }

    private static boolean isLazy(PsiAnnotation annotation) {
        PsiAnnotationMemberValue fetch = annotation.findDeclaredAttributeValue("fetch");
        return isEnumConstant(fetch, "LAZY");
    }

    private static boolean isEager(PsiAnnotation annotation) {
        PsiAnnotationMemberValue fetch = annotation.findDeclaredAttributeValue("fetch");
        return isEnumConstant(fetch, "EAGER");
    }

    private static boolean isEnumConstant(PsiAnnotationMemberValue value, String constantName) {
        if (value instanceof PsiReferenceExpression reference) {
            PsiElement resolved = reference.resolve();
            return resolved instanceof com.intellij.psi.PsiEnumConstant enumConstant
                    && constantName.equals(enumConstant.getName());
        }
        return value != null && (constantName.equals(value.getText())
                || value.getText().endsWith("." + constantName));
    }

    private static class LazyAssociationAccessVisitor extends JavaRecursiveElementVisitor {

        private final ProblemsHolder holder;

        private final Set<Integer> reportedOffsets = new HashSet<>();

        private int loopOrStreamDepth;

        LazyAssociationAccessVisitor(ProblemsHolder holder) {
            this.holder = holder;
        }

        @Override
        public void visitForStatement(@NotNull PsiForStatement statement) {
            this.loopOrStreamDepth++;
            super.visitForStatement(statement);
            this.loopOrStreamDepth--;
        }

        @Override
        public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
            this.loopOrStreamDepth++;
            super.visitForeachStatement(statement);
            this.loopOrStreamDepth--;
        }

        @Override
        public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
            this.loopOrStreamDepth++;
            super.visitWhileStatement(statement);
            this.loopOrStreamDepth--;
        }

        @Override
        public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
            this.loopOrStreamDepth++;
            super.visitDoWhileStatement(statement);
            this.loopOrStreamDepth--;
        }

        @Override
        public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
            if (isStreamOperationLambda(expression)) {
                this.loopOrStreamDepth++;
                super.visitLambdaExpression(expression);
                this.loopOrStreamDepth--;
                return;
            }
            super.visitLambdaExpression(expression);
        }

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (this.loopOrStreamDepth == 0) {
                return;
            }
            PsiField field = fieldFromGetter(expression);
            if (field != null && isLazyAssociation(field)) {
                this.register(expression);
            }
        }

        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            if (this.loopOrStreamDepth == 0) {
                return;
            }
            PsiElement resolved = expression.resolve();
            if (resolved instanceof PsiField field && isLazyAssociation(field)) {
                this.register(expression);
            }
        }

        private void register(PsiElement element) {
            if (this.reportedOffsets.add(element.getTextOffset())) {
                this.holder.registerProblem(element,
                        "Potential N+1 query: lazy association accessed inside a loop or stream operation; use fetch join, EntityGraph, or DTO projection",
                        ProblemHighlightType.WARNING);
            }
        }
    }
}
