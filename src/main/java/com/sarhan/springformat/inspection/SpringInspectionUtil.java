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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */

public final class SpringInspectionUtil {

    private static final Pattern QUERY_FORMAT_PATTERN = Pattern.compile(
            " (?i)((?:JOIN FETCH|LEFT JOIN|RIGHT JOIN|INNER JOIN|OUTER JOIN|JOIN|ON|AND|OR|ORDER BY|GROUP BY|HAVING|UNION|SET|VALUES|FROM|WHERE|UPDATE|DELETE|INSERT INTO) )");

    private SpringInspectionUtil() {
    }

    public static boolean isInJavaFile(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (!(file instanceof PsiJavaFile)) {
            return false;
        }
        VirtualFile virtualFile = file.getVirtualFile();
        return virtualFile != null && "java".equalsIgnoreCase(virtualFile.getExtension());
    }

    public static boolean hasAnnotation(PsiModifierList modifiers, String shortName) {
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

    public static boolean hasAnnotation(PsiModifierList modifiers, String fqn, String shortName) {
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (isAnnotation(annotation, fqn, shortName)) {
                return true;
            }
        }
        return false;
    }

    public static PsiAnnotation findAnnotation(PsiModifierList modifiers, String fqn, String shortName) {
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (isAnnotation(annotation, fqn, shortName)) {
                return annotation;
            }
        }
        return null;
    }

    public static boolean isAnnotation(PsiAnnotation annotation, String fqn, String shortName) {
        String name = annotationName(annotation);
        return fqn.equals(name) || isUnqualifiedName(name, shortName);
    }

    public static boolean isAnyAnnotation(PsiAnnotation annotation, String... fqns) {
        String name = annotationName(annotation);
        if (name == null) {
            return false;
        }
        for (String fqn : fqns) {
            if (fqn.equals(name) || isUnqualifiedName(name, shortName(fqn))) {
                return true;
            }
        }
        return false;
    }

    public static PsiAnnotation findAnyAnnotation(PsiModifierList modifiers, String... fqns) {
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (isAnyAnnotation(annotation, fqns)) {
                return annotation;
            }
        }
        return null;
    }

    public static String annotationName(PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return name;
    }

    public static String shortName(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return (dot >= 0) ? fqn.substring(dot + 1) : fqn;
    }

    private static boolean isUnqualifiedName(String name, String shortName) {
        return shortName.equals(name) && !name.contains(".");
    }

    public static boolean isSpringDataRepository(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        if (hasAnnotation(psiClass.getModifierList(), "Repository")) {
            return true;
        }
        String className = psiClass.getName();
        if (className != null && className.endsWith("Repository")) {
            return true;
        }
        for (PsiClassType type : psiClass.getExtendsListTypes()) {
            PsiClass resolved = type.resolve();
            if (resolved != null && isSpringDataRepositoryType(resolved)) {
                return true;
            }
        }
        for (PsiClass superClass : psiClass.getSupers()) {
            if (isSpringDataRepositoryType(superClass)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSpringDataRepositoryType(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName != null && qualifiedName.startsWith("org.springframework.data.")) {
            String name = psiClass.getName();
            if ("Repository".equals(name) || "CrudRepository".equals(name) || "PagingAndSortingRepository".equals(name)
                    || "JpaRepository".equals(name) || "ListCrudRepository".equals(name) || "ListPagingAndSortingRepository".equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static PsiClass resolvedClass(PsiType type) {
        return type instanceof PsiClassType classType ? classType.resolve() : null;
    }

    @NotNull
    public static String formatQueryText(@NotNull String query) {
        return formatQueryText(null, query);
    }

    @NotNull
    public static String formatQueryText(Project project, @NotNull String query) {
        if (project != null) {
            String formatted = formatWithSqlFormatter(project, query);
            if (formatted != null) {
                return formatted;
            }
        }
        return formatQueryTextFallback(query);
    }

    private static String formatWithSqlFormatter(@NotNull Project project, @NotNull String query) {
        Language sql = Language.findLanguageByID("SQL");
        if (sql == null) {
            return null;
        }
        PsiFile file = PsiFileFactory.getInstance(project).createFileFromText("query.sql", sql, query.trim());
        PsiElement reformatted = CodeStyleManager.getInstance(project).reformat(file);
        String formatted = reformatted.getText().trim();
        return formatted.isEmpty() ? null : formatted;
    }

    @NotNull
    private static String formatQueryTextFallback(@NotNull String query) {
        String normalized = query.trim().replaceAll("\\s+", " ");
        Matcher matcher = QUERY_FORMAT_PATTERN.matcher(normalized);
        StringBuilder formatted = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            formatted.append(normalized, lastEnd, matcher.start());
            formatted.append('\n');
            formatted.append(matcher.group(1));
            lastEnd = matcher.end();
        }
        formatted.append(normalized, lastEnd, normalized.length());
        return formatted.toString();
    }

    @NotNull
    public static String textBlockLiteral(@NotNull String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"\"\"", "\\\"\"\"");
        return "\"\"\"\n" + escaped + "\n\"\"\"";
    }

    /**
     * Returns {@code true} when the class (or any superclass) carries a JPA
     * entity annotation ({@code @Entity}, {@code @MappedSuperclass}, or
     * {@code @Embeddable}).
     */
    public static boolean isJpaEntity(@NotNull PsiClass psiClass) {
        PsiClass current = psiClass;
        while (current != null) {
            PsiModifierList mods = current.getModifierList();
            if (mods != null) {
                if (hasAnnotation(mods, "Entity")
                        || hasAnnotation(mods, "MappedSuperclass")
                        || hasAnnotation(mods, "Embeddable")) {
                    return true;
                }
            }
            current = current.getSuperClass();
        }
        return false;
    }

    /**
     * Returns {@code true} when the class (or any superclass) carries a JPA
     * entity annotation, or when any field of the class carries a field-level
     * JPA annotation ({@code @Id}, {@code @OneToMany}, {@code @Column}, etc.).
     * <p>
     * This is a broader heuristic for cases where the class itself is not
     * explicitly annotated but is clearly part of the JPA domain model.
     */
    public static boolean isJpaEntityOrModel(@NotNull PsiClass psiClass) {
        if (isJpaEntity(psiClass)) {
            return true;
        }
        PsiClass current = psiClass;
        while (current != null) {
            for (com.intellij.psi.PsiField field : current.getAllFields()) {
                PsiModifierList mods = field.getModifierList();
                if (mods == null) {
                    continue;
                }
                if (hasAnnotation(mods, "Id")
                        || hasAnnotation(mods, "OneToMany")
                        || hasAnnotation(mods, "ManyToOne")
                        || hasAnnotation(mods, "OneToOne")
                        || hasAnnotation(mods, "ManyToMany")
                        || hasAnnotation(mods, "Column")
                        || hasAnnotation(mods, "ElementCollection")
                        || hasAnnotation(mods, "Temporal")
                        || hasAnnotation(mods, "Lob")
                        || hasAnnotation(mods, "Enumerated")) {
                    return true;
                }
            }
            current = current.getSuperClass();
        }
        return false;
    }
}
