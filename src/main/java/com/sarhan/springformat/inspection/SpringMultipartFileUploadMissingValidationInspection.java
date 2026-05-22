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
import org.jetbrains.annotations.NotNull;

/**
 * Detects controller methods that accept MultipartFile without explicit size limits
 * or content-type validation. Unrestricted file uploads can exhaust disk/storage.
 *
 * @author Akmal Sarhan
 */
public class SpringMultipartFileUploadMissingValidationInspection extends AbstractBaseJavaLocalInspectionTool {

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
                for (PsiParameter parameter : method.getParameterList().getParameters()) {
                    if (!isMultipartFile(parameter)) {
                        continue;
                    }
                    if (hasSizeValidation(parameter) || hasContentTypeValidation(method, parameter)) {
                        continue;
                    }
                    holder.registerProblem(
                            (parameter.getNameIdentifier() != null) ? parameter.getNameIdentifier() : parameter,
                            "MultipartFile upload should specify size limits (@Size, @Max) or content-type validation to prevent storage exhaustion",
                            ProblemHighlightType.WARNING
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

    private static boolean isMultipartFile(PsiParameter parameter) {
        PsiType type = parameter.getType();
        String canonical = type.getCanonicalText();
        if (canonical != null && canonical.contains("MultipartFile")) {
            return true;
        }
        if (type instanceof PsiArrayType arrayType) {
            PsiType componentType = arrayType.getComponentType();
            String componentCanonical = componentType.getCanonicalText();
            if (componentCanonical != null && componentCanonical.contains("MultipartFile")) {
                return true;
            }
        }
        // List<MultipartFile>
        if (type instanceof PsiClassType classType) {
            for (PsiType param : classType.getParameters()) {
                String paramCanonical = param.getCanonicalText();
                if (paramCanonical != null && paramCanonical.contains("MultipartFile")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasSizeValidation(PsiParameter parameter) {
        for (PsiAnnotation a : parameter.getAnnotations()) {
            String name = a.getQualifiedName();
            if (name == null && a.getNameReferenceElement() != null) {
                name = a.getNameReferenceElement().getText();
            }
            if (name != null) {
                String shortName = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
                if ("Size".equals(shortName) || "Max".equals(shortName) || "NotEmpty".equals(shortName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasContentTypeValidation(PsiMethod method, PsiParameter parameter) {
        // Check if the method body starts with a content-type check on the parameter
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }
        String paramName = parameter.getName();
        if (paramName == null) {
            return false;
        }
        boolean[] found = {false};
        body.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                String methodName = expression.getMethodExpression().getReferenceName();
                if ("getContentType".equals(methodName) || "contains".equals(methodName)) {
                    found[0] = true;
                }
            }
        });
        return found[0];
    }
}
