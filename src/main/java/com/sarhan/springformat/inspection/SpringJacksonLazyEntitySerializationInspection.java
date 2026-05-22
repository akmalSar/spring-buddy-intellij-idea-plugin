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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects REST controller methods that return JPA entities or collections of entities
 * without @JsonIgnore on lazy-loaded associations (@OneToMany, @ManyToOne, @OneToOne, @ManyToMany).
 * This can trigger LazyInitializationException or N+1 queries during Jackson serialization.
 *
 * @author Akmal Sarhan
 */
public class SpringJacksonLazyEntitySerializationInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                if (!isRestControllerMethod(method)) {
                    return;
                }
                PsiType returnType = method.getReturnType();
                if (returnType == null) {
                    return;
                }
                PsiClass entityClass = resolveEntityClass(returnType);
                if (entityClass == null) {
                    return;
                }
                if (allReturnExpressionsAreNonEntity(method)) {
                    return;
                }
                if (hasLazyAssociationWithoutJsonIgnore(entityClass)) {
                    PsiElement element = (method.getNameIdentifier() != null) ? method.getNameIdentifier() : method;
                    holder.registerProblem(
                            element,
                            "Jackson serialization of entity with lazy-loaded associations may cause LazyInitializationException or N+1 queries — use DTO projection or add @JsonIgnore",
                            ProblemHighlightType.ERROR
                    );
                }
            }
        };
    }

    private static boolean isRestControllerMethod(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        // Check class-level @RestController or @Controller
        PsiModifierList classModifiers = containingClass.getModifierList();
        if (classModifiers == null) {
            return false;
        }
        boolean isController = false;
        for (PsiAnnotation a : classModifiers.getAnnotations()) {
            if (isControllerAnnotation(a)) {
                isController = true;
                break;
            }
        }
        if (!isController) {
            return false;
        }
        // Method must have a mapping annotation
        PsiModifierList methodModifiers = method.getModifierList();
        for (PsiAnnotation a : methodModifiers.getAnnotations()) {
            if (isMappingAnnotation(a)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isControllerAnnotation(PsiAnnotation annotation) {
        return SpringInspectionUtil.isAnyAnnotation(annotation,
                "org.springframework.web.bind.annotation.RestController",
                "org.springframework.stereotype.Controller");
    }

    private static boolean isMappingAnnotation(PsiAnnotation annotation) {
        return SpringInspectionUtil.isAnyAnnotation(annotation,
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping",
                "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PatchMapping");
    }

    private static PsiClass resolveEntityClass(PsiType type) {
        PsiClass resolved = SpringInspectionUtil.resolvedClass(type);
        if (resolved != null && SpringInspectionUtil.isJpaEntity(resolved)) {
            return resolved;
        }
        // Check generic arguments for collection wrappers (List<Entity>, Page<Entity>)
        if (type instanceof PsiClassType classType) {
            for (PsiType param : classType.getParameters()) {
                PsiClass paramClass = SpringInspectionUtil.resolvedClass(param);
                if (paramClass != null && SpringInspectionUtil.isJpaEntity(paramClass)) {
                    return paramClass;
                }
            }
        }
        // Check Optional<Entity>
        String canonical = type.getCanonicalText();
        if (canonical != null && canonical.startsWith("java.util.Optional<")) {
            PsiType optionalType = PsiUtil.substituteTypeParameter(type, "java.util.Optional", 0, false);
            if (optionalType != null) {
                PsiClass optClass = SpringInspectionUtil.resolvedClass(optionalType);
                if (optClass != null && SpringInspectionUtil.isJpaEntity(optClass)) {
                    return optClass;
                }
            }
        }
        return null;
    }

    private static boolean hasLazyAssociationWithoutJsonIgnore(PsiClass entityClass) {
        for (PsiField field : entityClass.getAllFields()) {
            boolean isAssociation = false;
            boolean hasJsonIgnore = false;
            boolean isLazy = false;
            for (PsiAnnotation a : field.getAnnotations()) {
                String name = a.getQualifiedName();
                if (name == null && a.getNameReferenceElement() != null) {
                    name = a.getNameReferenceElement().getText();
                }
                if (name != null) {
                    String shortName = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
                    if ("OneToMany".equals(shortName) || "ManyToOne".equals(shortName)
                            || "OneToOne".equals(shortName) || "ManyToMany".equals(shortName)) {
                        isAssociation = true;
                        String text = a.getText();
                        // Default for to-one is EAGER, to-many is LAZY
                        if ("OneToMany".equals(shortName) || "ManyToMany".equals(shortName)) {
                            isLazy = true;
                        } else if (text != null && text.contains("LAZY")) {
                            isLazy = true;
                        }
                    }
                    if (SpringInspectionUtil.isAnnotation(a, "com.fasterxml.jackson.annotation.JsonIgnore", "JsonIgnore")) {
                        hasJsonIgnore = true;
                    }
                }
            }
            if (isAssociation && isLazy && !hasJsonIgnore) {
                return true;
            }
        }
        return false;
    }

    private static boolean allReturnExpressionsAreNonEntity(PsiMethod method) {
        if (method.getBody() == null) {
            return false;
        }
        boolean[] hasReturn = {false};
        boolean[] hasEntityReturn = {false};
        method.getBody().accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReturnStatement(@NotNull PsiReturnStatement stmt) {
                PsiExpression value = stmt.getReturnValue();
                if (value == null) {
                    return;
                }
                hasReturn[0] = true;
                if (returnsEntityValue(value)) {
                    hasEntityReturn[0] = true;
                }
            }
        });
        return hasReturn[0] && !hasEntityReturn[0];
    }

    private static boolean returnsEntityValue(@NotNull PsiExpression value) {
        PsiType type = value.getType();
        if (type != null && resolveEntityClass(type) != null) {
            return true;
        }
        if (value instanceof PsiMethodCallExpression call) {
            if (isSpringResponseWrapperFactory(call)) {
                for (PsiExpression arg : call.getArgumentList().getExpressions()) {
                    if (returnsEntityValue(arg)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isSpringResponseWrapperFactory(@NotNull PsiMethodCallExpression call) {
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
