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

import java.util.HashMap;
import java.util.Map;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * Reports duplicate controller request mappings within the same controller class.
 *
 * @author Akmal Sarhan
 */
public class SpringDuplicateRequestMappingInspection extends AbstractBaseJavaLocalInspectionTool {

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
                Map<String, PsiMethod> mappings = new HashMap<>();
                for (PsiMethod method : psiClass.getMethods()) {
                    Mapping mapping = mapping(method);
                    if (mapping == null) {
                        continue;
                    }
                    PsiMethod existing = mappings.putIfAbsent(mapping.key(), method);
                    if (existing != null) {
                        register(method, existing, mapping.key(), holder);
                        register(existing, method, mapping.key(), holder);
                    }
                }
            }
        };
    }

    private static void register(PsiMethod method, PsiMethod other, String key, ProblemsHolder holder) {
        PsiIdentifier identifier = method.getNameIdentifier();
        holder.registerProblem((identifier != null) ? identifier : method,
                "Duplicate Spring MVC mapping '" + key + "' also declared by method '" + other.getName()
                        + "'; application startup fails with ambiguous mapping",
                ProblemHighlightType.ERROR);
    }

    private static boolean isController(PsiClass psiClass) {
        PsiModifierList modifiers = psiClass.getModifierList();
        return findAnnotation(modifiers, "Controller") != null || findAnnotation(modifiers, "RestController") != null;
    }

    private static Mapping mapping(PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String shortName = annotationShortName(annotation);
            String httpMethod = switch (shortName) {
                case "GetMapping" -> "GET";
                case "PostMapping" -> "POST";
                case "PutMapping" -> "PUT";
                case "DeleteMapping" -> "DELETE";
                case "PatchMapping" -> "PATCH";
                case "RequestMapping" -> requestMethod(annotation);
                default -> null;
            };
            if (httpMethod != null) {
                return new Mapping(httpMethod, path(annotation), condition(annotation, "params"),
                        condition(annotation, "headers"), condition(annotation, "consumes"), condition(annotation, "produces"));
            }
        }
        return null;
    }

    private static String requestMethod(PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("method");
        return (value != null) ? value.getText() : "ALL";
    }

    private static String path(PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("value");
        if (value == null) {
            value = annotation.findDeclaredAttributeValue("path");
        }
        if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String s) {
            return normalizePath(s);
        }
        return (value != null) ? value.getText() : "";
    }

    private static String condition(PsiAnnotation annotation, String attribute) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue(attribute);
        return (value != null) ? value.getText() : "";
    }

    private static String normalizePath(String path) {
        if (path.isEmpty()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static PsiAnnotation findAnnotation(PsiModifierList modifiers, String shortName) {
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (shortName.equals(annotationShortName(annotation))) {
                return annotation;
            }
        }
        return null;
    }

    private static String annotationShortName(PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(dot + 1) : name;
    }

    private record Mapping(String method, String path, String params, String headers, String consumes, String produces) {
        String key() {
            return this.method + " " + this.path + " params=" + this.params + " headers=" + this.headers
                    + " consumes=" + this.consumes + " produces=" + this.produces;
        }
    }
}
