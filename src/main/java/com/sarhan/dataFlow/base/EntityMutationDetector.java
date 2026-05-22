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

import java.util.Set;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Detects whether a PsiMethod is a mutation on an entity object.
 *
 * Deliberately does NOT use PropertyUtil.isSimplePropertySetter because that
 * utility requires a backing field to exist on the class — which fails on
 * stub classes, interfaces, and demo files that only declare setter methods.
 *
 * Covers:
 *   Standard setters:       setName(String)    → void
 *   Builder-style mutators: withName(String)   → this / SameType
 *   Known JPA lifecycle:    add/remove/clear on collection fields
 *
 * @author Akmal Sarhan
 */
public final class EntityMutationDetector {

    // Common collection-mutation and domain-event method names on entities
    private static final Set<String> KNOWN_MUTATOR_NAMES = Set.of(
            "add", "remove", "clear",
            "addItem", "removeItem",
            "addElement", "removeElement",
            "addChild", "removeChild",
            "markDeleted", "markDirty",
            "activate", "deactivate",
            "archive", "restore",
            "enable", "disable",
            "approve", "reject",
            "publish", "unpublish"
    );

    private EntityMutationDetector() { }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns true if this method mutates the state of the qualifier object.
     * Works on stub classes without backing fields.
     */
    public static boolean isMutationMethod(@NotNull PsiMethod method) {
        // Static methods don't mutate an instance
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }

        String name = method.getName();

        if (isStandardSetter(method, name)) {
            return true;
        }
        if (isBuilderStyleMutator(method, name)) {
            return true;
        }
        if (KNOWN_MUTATOR_NAMES.contains(name)) {
            return true;
        }

        return false;
    }

    // -----------------------------------------------------------------------
    // Checks
    // -----------------------------------------------------------------------

    /**
     * setXxx(T value) → void
     * No backing field required — name + signature is sufficient.
     */
    private static boolean isStandardSetter(@NotNull PsiMethod method,
                                            @NotNull String name) {
        if (!name.startsWith("set") || name.length() <= 3) {
            return false;
        }
        if (method.getParameterList().getParametersCount() != 1) {
            return false;
        }
        PsiType returnType = method.getReturnType();
        return returnType != null && returnType.equals(PsiTypes.voidType());
    }

    /**
     * withXxx(T value) → SameType   (fluent/builder pattern)
     * Return type must be the declaring class itself.
     */
    private static boolean isBuilderStyleMutator(@NotNull PsiMethod method,
                                                 @NotNull String name) {
        if (!name.startsWith("with") || name.length() <= 4) {
            return false;
        }
        if (method.getParameterList().getParametersCount() != 1) {
            return false;
        }

        PsiType returnType = method.getReturnType();
        if (returnType == null) {
            return false;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        // Compare by simple name (works on stubs) and qualified name (indexed classes)
        String simpleName = containingClass.getName();
        String qualName   = containingClass.getQualifiedName();

        return (simpleName != null && returnType.equalsToText(simpleName))
                || (qualName != null && returnType.equalsToText(qualName));
    }
}
