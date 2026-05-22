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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * Detects cache key collisions within a single class: two or more methods
 * annotated with {@code @Cacheable} or {@code @CachePut} that share the same
 * cache name(s).
 * <p>
 * Spring Cache stores entries under a single namespace per cache name. When
 * multiple methods share a cache name they all write into the same underlying
 * cache. If the methods have different parameter signatures (or return types),
 * a key collision can cause one method to read a value written by another,
 * leading to {@code ClassCastException}, stale data, or silent corruption.
 * Even when keys differ by default (Spring's {@code SimpleKeyGenerator} uses
 * all method arguments), custom key expressions or overlapping argument values
 * make collisions likely in practice.
 *
 * @author Spring Buddy
 * @author Akmal Sarhan
 */
public class SpringCacheableKeyCollisionInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                // Map: cacheName -> first method that used it
                Map<String, PsiMethod> cacheNameToMethod = new HashMap<>();
                // Set of methods already reported (to avoid duplicate reports on the same method)
                Set<PsiMethod> reportedMethods = new HashSet<>();

                for (PsiMethod method : psiClass.getMethods()) {
                    Set<String> cacheNames = extractCacheNames(method);
                    if (cacheNames.isEmpty()) {
                        continue;
                    }
                    for (String cacheName : cacheNames) {
                        PsiMethod firstMethod = cacheNameToMethod.get(cacheName);
                        if (firstMethod == null) {
                            cacheNameToMethod.put(cacheName, method);
                        } else if (firstMethod != method) {
                            // Report on the current method
                            if (!reportedMethods.contains(method)) {
                                PsiIdentifier nameIdentifier = method.getNameIdentifier();
                                holder.registerProblem(
                                        (nameIdentifier != null) ? nameIdentifier : method,
                                        "Method '" + method.getName() + "' shares cache name \""
                                                + cacheName + "\" with method '"
                                                + firstMethod.getName()
                                                + "'. This can cause cache key collisions and data corruption.",
                                        ProblemHighlightType.WARNING);
                                reportedMethods.add(method);
                            }
                            // Also report on the first method if not already reported
                            if (!reportedMethods.contains(firstMethod)) {
                                PsiIdentifier firstIdentifier = firstMethod.getNameIdentifier();
                                holder.registerProblem(
                                        (firstIdentifier != null) ? firstIdentifier : firstMethod,
                                        "Method '" + firstMethod.getName() + "' shares cache name \""
                                                + cacheName + "\" with method '"
                                                + method.getName()
                                                + "'. This can cause cache key collisions and data corruption.",
                                        ProblemHighlightType.WARNING);
                                reportedMethods.add(firstMethod);
                            }
                        }
                    }
                }
            }
        };
    }

    /**
     * Extracts all cache names from {@code @Cacheable} and {@code @CachePut}
     * annotations on the method. Handles both {@code value="name"} and
     * {@code cacheNames={"a","b"}}.
     */
    private static Set<String> extractCacheNames(@NotNull PsiMethod method) {
        Set<String> names = new HashSet<>();
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers == null) {
            return names;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (isCacheableOrCachePutAnnotation(annotation)) {
                // Try 'value' attribute first (default)
                PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
                if (value == null) {
                    value = annotation.findAttributeValue("cacheNames");
                }
                if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String s) {
                    names.add(s);
                } else if (value instanceof PsiArrayInitializerMemberValue arr) {
                    for (PsiAnnotationMemberValue v : arr.getInitializers()) {
                        if (v instanceof PsiLiteralExpression lit && lit.getValue() instanceof String s) {
                            names.add(s);
                        }
                    }
                }
            }
        }
        return names;
    }

    private static boolean isCacheableOrCachePutAnnotation(@NotNull PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return "org.springframework.cache.annotation.Cacheable".equals(name)
                || "org.springframework.cache.annotation.CachePut".equals(name)
                || "Cacheable".equals(name)
                || "CachePut".equals(name);
    }
}
