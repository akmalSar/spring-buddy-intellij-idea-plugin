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

import java.util.List;
import java.util.Set;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Determines whether a PsiMethod is a source of detached JPA entities.
 *
 * Detection strategies (applied in order, fastest first):
 *
 *  A) Direct @Repository annotation on the containing class
 *  B) Simple class name ends with "Repository"   — works on stubs
 *  C) Qualified class name ends with "Repository" — works when indexed
 *  D) Super-interface simple names match Spring Data types — works on stubs
 *  E) Full InheritanceUtil check via PSI index    — most accurate, requires index
 *  F) EntityManager method name + class name match — javax and jakarta
 *  G) @Query / @NamedQuery annotation on the method — direct annotation check
 *
 * @author Akmal Sarhan
 */
public class SpringEntitySourceDetector {

    private static final List<String> SPRING_REPO_FQNS = List.of(
            "org.springframework.data.repository.Repository",
            "org.springframework.data.repository.CrudRepository",
            "org.springframework.data.repository.PagingAndSortingRepository",
            "org.springframework.data.jpa.repository.JpaRepository"
    );

    private static final Set<String> SPRING_REPO_SIMPLE_NAMES = Set.of(
            "Repository",
            "CrudRepository",
            "PagingAndSortingRepository",
            "JpaRepository"
    );

    private static final Set<String> ENTITY_MANAGER_FQNS = Set.of(
            "javax.persistence.EntityManager",
            "jakarta.persistence.EntityManager"
    );

    // Method name prefixes that Spring Data derives queries from
    private static final List<String> REPO_METHOD_PREFIXES = List.of(
            "find", "get", "read", "query", "search", "stream"
    );

    // EntityManager methods that return detached-capable entity references
    private static final Set<String> ENTITY_MANAGER_SOURCE_METHODS = Set.of(
            "find", "getReference", "merge", "createQuery", "createNamedQuery"
    );

    // Query / TypedQuery methods that return entity results
    private static final Set<String> QUERY_SOURCE_METHODS = Set.of(
            "getResultList", "getResultStream", "getSingleResult"
    );

    private final Project project;

    public SpringEntitySourceDetector(@NotNull Project project) {
        this.project = project;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public boolean isDetachedEntitySource(@NotNull PsiMethod method) {
        return isSpringRepositoryMethod(method)
                || isEntityManagerSource(method)
                || isQuerySource(method)
                || hasQueryAnnotation(method);
    }

    // -----------------------------------------------------------------------
    // Strategy implementations
    // -----------------------------------------------------------------------

    private boolean isSpringRepositoryMethod(@NotNull PsiMethod method) {
        // Gate: method name must look like a finder
        if (!hasRepoMethodPrefix(method.getName())) {
            return false;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        return isRepositoryClass(containingClass);
    }

    private boolean isRepositoryClass(@NotNull PsiClass clazz) {
        // Strategy A: direct @Repository annotation
        if (hasAnnotationByAnyName(clazz,
                "org.springframework.stereotype.Repository", "Repository")) {
            return true;
        }

        // Strategy B: simple name heuristic
        String simpleName = clazz.getName();
        if (simpleName != null && simpleName.endsWith("Repository")) {
            return true;
        }

        // Strategy C: qualified name heuristic
        String qualifiedName = clazz.getQualifiedName();
        if (qualifiedName != null && qualifiedName.endsWith("Repository")) {
            return true;
        }

        // Strategy D: check super-interface simple names (no index needed)
        for (PsiClassType superType : clazz.getExtendsListTypes()) {
            String superSimple = superType.getClassName();
            if (superSimple != null && (
                    SPRING_REPO_SIMPLE_NAMES.contains(superSimple)
                            || superSimple.endsWith("Repository"))) {
                return true;
            }
        }
        for (PsiClassType superType : clazz.getImplementsListTypes()) {
            String superSimple = superType.getClassName();
            if (superSimple != null && (
                    SPRING_REPO_SIMPLE_NAMES.contains(superSimple)
                            || superSimple.endsWith("Repository"))) {
                return true;
            }
        }

        // Strategy E: full PSI index inheritance (most accurate, last resort)
        if (isIndexed(clazz)) {
            for (String fqn : SPRING_REPO_FQNS) {
                PsiClass repoInterface = JavaPsiFacade.getInstance(this.project)
                        .findClass(fqn, GlobalSearchScope.allScope(this.project));
                if (repoInterface != null
                        && InheritanceUtil.isInheritorOrSelf(clazz, repoInterface, true)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isEntityManagerSource(@NotNull PsiMethod method) {
        if (!ENTITY_MANAGER_SOURCE_METHODS.contains(method.getName())) {
            return false;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        // Check FQN (indexed classes)
        String fqn = containingClass.getQualifiedName();
        if (fqn != null && ENTITY_MANAGER_FQNS.contains(fqn)) {
            return true;
        }

        // Fallback: simple name (stubs / unresolved types)
        String simpleName = containingClass.getName();
        return "EntityManager".equals(simpleName);
    }

    private boolean isQuerySource(@NotNull PsiMethod method) {
        if (!QUERY_SOURCE_METHODS.contains(method.getName())) {
            return false;
        }

        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        String fqn = containingClass.getQualifiedName();
        if (fqn != null && (fqn.equals("javax.persistence.Query") || fqn.equals("jakarta.persistence.Query")
                || fqn.equals("javax.persistence.TypedQuery") || fqn.equals("jakarta.persistence.TypedQuery"))) {
            return true;
        }

        String simpleName = containingClass.getName();
        return "Query".equals(simpleName) || "TypedQuery".equals(simpleName);
    }

    private boolean hasQueryAnnotation(@NotNull PsiMethod method) {
        return hasAnnotationByAnyName(method,
                "org.springframework.data.jpa.repository.Query", "Query")
                || hasAnnotationByAnyName(method,
                "javax.persistence.NamedQuery", "NamedQuery")
                || hasAnnotationByAnyName(method,
                "jakarta.persistence.NamedQuery", "NamedQuery");
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    /**
     * Checks for an annotation by FQN, then by simple name from the annotation
     * reference element. The simple-name fallback is essential when annotation
     * classes are not on the project classpath (demo files, stub-only projects).
     */
    private static boolean hasAnnotationByAnyName(
            @NotNull PsiModifierListOwner element,
            @NotNull String fqn,
            @NotNull String simpleName) {

        if (element.hasAnnotation(fqn)) {
            return true;
        }

        PsiModifierList modList = element.getModifierList();
        if (modList == null) {
            return false;
        }

        for (PsiAnnotation ann : modList.getAnnotations()) {
            String qualName = ann.getQualifiedName();
            if (qualName != null) {
                if (qualName.equals(fqn) || qualName.equals(simpleName)) {
                    return true;
                }
            } else {
                // Unresolved annotation — compare via reference element text
                PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
                if (ref != null && simpleName.equals(ref.getReferenceName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasRepoMethodPrefix(@Nullable String name) {
        if (name == null) {
            return false;
        }
        for (String prefix : REPO_METHOD_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A class is "indexed" if it has a qualified name different from its
     * simple name, meaning it lives in a real package and is likely in the index.
     */
    private static boolean isIndexed(@NotNull PsiClass clazz) {
        String fqn = clazz.getQualifiedName();
        String simple = clazz.getName();
        return fqn != null && simple != null && !fqn.equals(simple);
    }
}
