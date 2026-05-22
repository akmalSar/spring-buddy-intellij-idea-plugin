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

package com.sarhan.dataFlow.base;

import com.intellij.psi.*;
import com.sarhan.springformat.inspection.SpringInspectionUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Detects whether a {@link PsiMethod} is a lazy-loading trigger when invoked on
 * a detached JPA entity.
 *
 * A method is considered a lazy-loading sink if all of the following hold:
 * <ul>
 *   <li>Name starts with {@code get}, {@code is}, or {@code fetch}</li>
 *   <li>Takes zero arguments</li>
 *   <li>Is not {@code static}</li>
 *   <li>Return type or annotations prove a lazy association:
 *     <ul>
 *       <li>Collection type ({@code List}, {@code Set}, {@code Map}, {@code Collection})</li>
 *       <li>Annotated with {@code @OneToMany}, {@code @ManyToMany}, or {@code @ElementCollection}</li>
 *       <li>Returns another JPA entity from a backing {@code @ManyToOne(fetch = LAZY)}
 *           or {@code @OneToOne(fetch = LAZY)} field</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>Collection associations are lazy by default. To-one associations are eager
 * by default, so a backing field must explicitly declare {@code fetch = LAZY}
 * before an entity-return getter is treated as a lazy-loading trigger.
 *
 * @author Akmal Sarhan
 */
public final class LazyLoadingSinkDetector {

    private LazyLoadingSinkDetector() { }

    /**
     * Returns true if calling this method on a detached entity may trigger
     * Hibernate lazy loading and therefore a {@code LazyInitializationException}.
     */
    public static boolean isLazyLoadingTrigger(@Nullable PsiMethod method) {
        if (method == null) {
            return false;
        }
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }
        if (method.getParameterList().getParametersCount() != 0) {
            return false;
        }

        String name = method.getName();
        if (name == null) {
            return false;
        }
        if (!name.startsWith("get") && !name.startsWith("is") && !name.startsWith("fetch")) {
            return false;
        }

        PsiType returnType = method.getReturnType();
        if (returnType == null) {
            return false;
        }

        // 1. Collection return types are the most common lazy-loading triggers
        String canonical = returnType.getCanonicalText();
        if (canonical != null) {
            if (canonical.startsWith("java.util.List")
                    || canonical.startsWith("java.util.Set")
                    || canonical.startsWith("java.util.Map")
                    || canonical.startsWith("java.util.Collection")) {
                return true;
            }
        }

        // 2. Annotation-based detection (works on stubs without resolved types)
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers != null) {
            for (PsiAnnotation a : modifiers.getAnnotations()) {
                String annName = a.getQualifiedName();
                if (annName == null && a.getNameReferenceElement() != null) {
                    annName = a.getNameReferenceElement().getText();
                }
                if (annName != null && ("OneToMany".equals(annName) || annName.endsWith(".OneToMany")
                        || "ManyToMany".equals(annName) || annName.endsWith(".ManyToMany")
                        || "ElementCollection".equals(annName) || annName.endsWith(".ElementCollection"))) {
                    return true;
                }
            }
        }

        // 3. Entity-returning to-one getters are only lazy when the backing
        // field explicitly declares fetch = LAZY. @ManyToOne/@OneToOne are
        // EAGER by default, so treating every entity return as lazy is noisy.
        PsiClass resolved = resolvedClass(returnType);
        return resolved != null && SpringInspectionUtil.isJpaEntity(resolved) && hasLazyToOneBackingField(method);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static PsiClass resolvedClass(PsiType type) {
        return type instanceof PsiClassType classType ? classType.resolve() : null;
    }

    private static boolean hasLazyToOneBackingField(PsiMethod method) {
        String propertyName = getterPropertyName(method.getName());
        if (propertyName == null) {
            return false;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        PsiField field = containingClass.findFieldByName(propertyName, true);
        if (field == null) {
            return false;
        }
        PsiModifierList modifiers = field.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if ((SpringInspectionUtil.isAnnotation(annotation, "javax.persistence.ManyToOne", "ManyToOne")
                    || SpringInspectionUtil.isAnnotation(annotation, "jakarta.persistence.ManyToOne", "ManyToOne")
                    || SpringInspectionUtil.isAnnotation(annotation, "javax.persistence.OneToOne", "OneToOne")
                    || SpringInspectionUtil.isAnnotation(annotation, "jakarta.persistence.OneToOne", "OneToOne"))
                    && isLazy(annotation)) {
                return true;
            }
        }
        return false;
    }

    private static String getterPropertyName(String methodName) {
        int prefixLength = 0;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            prefixLength = 3;
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            prefixLength = 2;
        }
        if (prefixLength == 0) {
            return null;
        }
        return Character.toLowerCase(methodName.charAt(prefixLength)) + methodName.substring(prefixLength + 1);
    }

    private static boolean isLazy(PsiAnnotation annotation) {
        PsiAnnotationMemberValue fetch = annotation.findDeclaredAttributeValue("fetch");
        return fetch != null && fetch.getText().contains("LAZY");
    }
}
