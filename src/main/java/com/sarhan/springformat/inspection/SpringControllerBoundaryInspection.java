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
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * Base inspection for controller-layer boundary violations:
 * <ul>
 *   <li>Returning JPA entities directly from REST endpoints</li>
 *   <li>Injecting repositories directly into controllers</li>
 *   <li>Defining {@code @Transactional} boundaries on controllers</li>
 * </ul>
 *
 * @author Akmal Sarhan
 */
public abstract class SpringControllerBoundaryInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                if (!isController(psiClass)) {
                    return;
                }
                inspectClass(psiClass, holder);
            }

            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiClass psiClass = method.getContainingClass();
                if (psiClass == null || !isController(psiClass)) {
                    return;
                }
                inspectMethod(method, holder);
            }
        };
    }

    protected void inspectClass(PsiClass psiClass, ProblemsHolder holder) {
    }

    protected void inspectMethod(PsiMethod method, ProblemsHolder holder) {
    }

    private static boolean isController(PsiClass psiClass) {
        PsiModifierList modifiers = psiClass.getModifierList();
        return hasAnnotation(modifiers, "Controller") || hasAnnotation(modifiers, "RestController");
    }

    private static boolean hasTransactionalAnnotation(PsiClass psiClass) {
        return hasAnnotation(psiClass.getModifierList(), "Transactional");
    }

    private static boolean hasTransactionalAnnotation(PsiMethod method) {
        return hasAnnotation(method.getModifierList(), "Transactional");
    }

    private static boolean hasAnnotation(PsiModifierList modifiers, String shortName) {
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if (shortName.equals(name) || (name != null && name.endsWith("." + shortName))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRepositoryType(PsiType type) {
        PsiClass resolved = SpringInspectionUtil.resolvedClass(type);
        return resolved != null && SpringInspectionUtil.isSpringDataRepository(resolved);
    }

    private static boolean canRegisterProblemOn(PsiElement element) {
        return element != null && element.isPhysical() && element.getContainingFile() != null
                && !element.getTextRange().isEmpty();
    }

    private static boolean returnsLikelyEntity(PsiMethod method) {
        PsiType type = method.getReturnType();
        if (type == null) {
            return false;
        }
        if (isSafeResponseType(type) || isProjectionInterfaceReturn(type)) {
            return false;
        }
        return isJpaEntityType(type);
    }

    private static boolean isJpaEntityType(PsiType type) {
        if (!(type instanceof PsiClassType classType)) {
            return false;
        }
        PsiClass resolved = classType.resolve();
        return resolved != null && (hasAnnotation(resolved.getModifierList(), "Entity")
                || hasAnnotation(resolved.getModifierList(), "MappedSuperclass"));
    }

    private static boolean isSafeResponseType(PsiType type) {
        if (!(type instanceof PsiClassType classType)) {
            return false;
        }
        PsiClass resolved = classType.resolve();
        if (resolved == null) {
            return false;
        }
        String qualifiedName = resolved.getQualifiedName();
        String name = resolved.getName();
        return "org.springframework.http.ResponseEntity".equals(qualifiedName)
                || "org.springframework.http.HttpEntity".equals(qualifiedName)
                || "org.springframework.core.io.Resource".equals(qualifiedName)
                || "org.springframework.data.domain.Page".equals(qualifiedName)
                || "org.springframework.data.domain.Slice".equals(qualifiedName)
                || "org.springframework.data.domain.Window".equals(qualifiedName)
                || "ResponseEntity".equals(name) || "HttpEntity".equals(name) || "Resource".equals(name)
                || "Page".equals(name) || "Slice".equals(name) || "Window".equals(name);
    }

    private static boolean isProjectionInterfaceReturn(PsiType type) {
        if (type instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            if (resolved != null && resolved.isInterface() && !hasAnnotation(resolved.getModifierList(), "Entity")) {
                return true;
            }
            for (PsiType parameter : classType.getParameters()) {
                if (isProjectionInterfaceReturn(parameter)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when ALL return-statement expressions in the method
     * body are safe (new DTO, ResponseEntity wrappers, null, etc.) — meaning
     * the declared entity return type is never actually returned as an entity.
     */
    private static boolean allReturnExpressionsAreSafe(PsiMethod method) {
        if (method.getBody() == null) {
            return false;
        }
        boolean[] hasReturn = {false};
        boolean[] hasUnsafe = {false};
        method.getBody().accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReturnStatement(@NotNull PsiReturnStatement stmt) {
                PsiExpression value = stmt.getReturnValue();
                if (value == null) {
                    return;
                }
                hasReturn[0] = true;
                if (!isReturnExpressionSafe(value)) {
                    hasUnsafe[0] = true;
                }
            }
        });
        return hasReturn[0] && !hasUnsafe[0];
    }

    private static boolean isReturnExpressionSafe(PsiExpression expr) {
        if (expr instanceof PsiNewExpression) {
            return true;
        }
        if (expr instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            if (name != null && (name.equals("ok") || name.equals("of") || name.equals("build")
                    || name.equals("noContent") || name.equals("badRequest") || name.equals("notFound")
                    || name.equals("created") || name.equals("accepted") || name.equals("status"))) {
                return true;
            }
            PsiType returnType = call.getType();
            if (returnType != null && isSafeResponseType(returnType)) {
                return true;
            }
        }
        return false;
    }

    public static class EntityReturn extends SpringControllerBoundaryInspection {
        @Override
        protected void inspectMethod(PsiMethod method, ProblemsHolder holder) {
            if (!returnsLikelyEntity(method)) {
                return;
            }
            if (allReturnExpressionsAreSafe(method)) {
                return;
            }
            if (!hasEntityReturnExpression(method)) {
                return;
            }
            PsiIdentifier nameIdentifier = method.getNameIdentifier();
            holder.registerProblem((nameIdentifier != null) ? nameIdentifier : method,
                    "Controller returns a JPA entity directly; return a DTO or response model instead",
                    ProblemHighlightType.WARNING);
        }
    }

    public static class RepositoryInjection extends SpringControllerBoundaryInspection {
        @Override
        protected void inspectClass(PsiClass psiClass, ProblemsHolder holder) {
            for (PsiField field : psiClass.getFields()) {
                if (isRepositoryType(field.getType())) {
                    holder.registerProblem(field,
                            "Controller should not inject repositories directly; use a service boundary",
                            ProblemHighlightType.WARNING);
                }
            }
            for (PsiMethod constructor : psiClass.getConstructors()) {
                for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
                    if (isRepositoryType(parameter.getType())) {
                        PsiElement anchor = canRegisterProblemOn(parameter) ? parameter
                                : canRegisterProblemOn(constructor) ? constructor : psiClass;
                        holder.registerProblem(anchor,
                                "Controller should not inject repositories directly; use a service boundary",
                                ProblemHighlightType.WARNING);
                    }
                }
            }
        }
    }

    public static class TransactionalController extends SpringControllerBoundaryInspection {
        @Override
        protected void inspectClass(PsiClass psiClass, ProblemsHolder holder) {
            if (hasTransactionalAnnotation(psiClass)) {
                PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
                holder.registerProblem((nameIdentifier != null) ? nameIdentifier : psiClass,
                        "Controllers should not define @Transactional boundaries; use service-layer transactions",
                        ProblemHighlightType.WARNING);
            }
        }

        @Override
        protected void inspectMethod(PsiMethod method, ProblemsHolder holder) {
            if (hasTransactionalAnnotation(method)) {
                PsiIdentifier nameIdentifier = method.getNameIdentifier();
                holder.registerProblem((nameIdentifier != null) ? nameIdentifier : method,
                        "Controller method should not be @Transactional; use service-layer transactions",
                        ProblemHighlightType.WARNING);
            }
        }
    }

    private static boolean hasEntityReturnExpression(PsiMethod method) {
        if (method.getBody() == null) {
            return true;
        }
        boolean[] hasReturn = {false};
        boolean[] hasEntity = {false};
        method.getBody().accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReturnStatement(@NotNull PsiReturnStatement stmt) {
                PsiExpression value = stmt.getReturnValue();
                if (value == null) {
                    return;
                }
                hasReturn[0] = true;
                if (returnsEntityExpression(value)) {
                    hasEntity[0] = true;
                }
            }
        });
        return !hasReturn[0] || hasEntity[0];
    }

    private static boolean returnsEntityExpression(PsiExpression value) {
        PsiType type = value.getType();
        if (type != null && isJpaEntityType(type)) {
            return true;
        }
        if (value instanceof PsiMethodCallExpression call) {
            if (isSpringResponseWrapperFactory(call)) {
                for (PsiExpression arg : call.getArgumentList().getExpressions()) {
                    if (returnsEntityExpression(arg)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isSpringResponseWrapperFactory(PsiMethodCallExpression call) {
        PsiMethod method = call.resolveMethod();
        if (method == null) {
            return false;
        }
        PsiClass owner = method.getContainingClass();
        if (owner == null) {
            return false;
        }
        String qn = owner.getQualifiedName();
        String name = method.getName();
        return "org.springframework.http.ResponseEntity".equals(qn)
                && ("ok".equals(name) || "of".equals(name) || "status".equals(name)
                || "created".equals(name) || "accepted".equals(name));
    }
}
