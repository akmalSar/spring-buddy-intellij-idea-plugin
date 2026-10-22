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

package com.sarhan.lombok;

import java.util.Arrays;
import java.util.List;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.sarhan.springformat.inspection.SpringInspectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared utilities for Lombok annotation detection across all inspections.
 *
 * @author Akmal Sarhan
 */
public final class LombokAnnotationUtil {

    // ── Builder annotations ───────────────────────────────────────────────
    public static final List<String> BUILDER_ANNOTATIONS = Arrays.asList(
            "lombok.Builder", "Builder",
            "lombok.SuperBuilder", "lombok.experimental.SuperBuilder", "SuperBuilder"
    );
    // ── Spring / JPA / Jackson ───────────────────────────────────────────
    public static final List<String> SPRING_STEREOTYPES = Arrays.asList(
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.context.annotation.Configuration",
            "org.springframework.boot.autoconfigure.SpringBootApplication",
            "org.springframework.boot.SpringBootConfiguration"
    );
    public static final List<String> JPA_ENTITY_ANNOTATIONS = Arrays.asList(
            "javax.persistence.Entity",
            "jakarta.persistence.Entity",
            "javax.persistence.MappedSuperclass",
            "jakarta.persistence.MappedSuperclass",
            "javax.persistence.Embeddable",
            "jakarta.persistence.Embeddable"
    );
    public static final List<String> VALIDATION_ANNOTATIONS = Arrays.asList(
            "javax.validation.constraints.NotNull",
            "jakarta.validation.constraints.NotNull",
            "javax.validation.constraints.NotBlank",
            "jakarta.validation.constraints.NotBlank",
            "javax.validation.constraints.NotEmpty",
            "jakarta.validation.constraints.NotEmpty",
            "javax.validation.constraints.Min",
            "jakarta.validation.constraints.Min",
            "javax.validation.constraints.Max",
            "jakarta.validation.constraints.Max",
            "javax.validation.constraints.Size",
            "jakarta.validation.constraints.Size",
            "javax.validation.constraints.Pattern",
            "jakarta.validation.constraints.Pattern",
            "javax.validation.Valid",
            "jakarta.validation.Valid"
    );

    private LombokAnnotationUtil() {
    }

    public static boolean hasBuilderAnnotation(@Nullable PsiClass aClass) {
        return findBuilderAnnotation(aClass) != null;
    }

    public static boolean hasSuperBuilderAnnotation(@Nullable PsiClass aClass) {
        if (aClass == null) {
            return false;
        }
        PsiModifierList modifiers = aClass.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation a : modifiers.getAnnotations()) {
            String fqn = a.getQualifiedName();
            if (isSuperBuilderAnnotation(fqn, a)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSuperBuilderAnnotation(@Nullable String fqn, @NotNull PsiAnnotation annotation) {
        String text = annotation.getText();
        return "lombok.SuperBuilder".equals(fqn)
                || "lombok.experimental.SuperBuilder".equals(fqn)
                || "SuperBuilder".equals(fqn)
                || text.startsWith("@SuperBuilder")
                || text.startsWith("@lombok.SuperBuilder")
                || text.startsWith("@lombok.experimental.SuperBuilder");
    }

    public static @Nullable PsiAnnotation findBuilderAnnotation(@Nullable PsiClass aClass) {
        if (aClass == null) {
            return null;
        }
        PsiModifierList modifiers = aClass.getModifierList();
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation a : modifiers.getAnnotations()) {
            String fqn = a.getQualifiedName();
            if (isBuilderAnnotation(fqn)) {
                return a;
            }
        }
        return null;
    }

    public static boolean isBuilderAnnotation(@Nullable String fqn) {
        return "lombok.Builder".equals(fqn) || "Builder".equals(fqn)
                || "lombok.SuperBuilder".equals(fqn)
                || "lombok.experimental.SuperBuilder".equals(fqn)
                || "SuperBuilder".equals(fqn);
    }

    // ── Constructor annotations ────────────────────────────────────────────
    public static boolean hasAllArgsConstructor(@Nullable PsiClass aClass) {
        return findAnnotation(aClass, "lombok.AllArgsConstructor", "AllArgsConstructor") != null;
    }

    public static boolean hasNoArgsConstructor(@Nullable PsiClass aClass) {
        return findAnnotation(aClass, "lombok.NoArgsConstructor", "NoArgsConstructor") != null;
    }

    public static boolean hasRequiredArgsConstructor(@Nullable PsiClass aClass) {
        return findAnnotation(aClass, "lombok.RequiredArgsConstructor", "RequiredArgsConstructor") != null;
    }

    // ── Other Lombok annotations ──────────────────────────────────────────
    public static boolean hasEqualsAndHashCode(@Nullable PsiClass aClass) {
        return findAnnotation(aClass, "lombok.EqualsAndHashCode", "EqualsAndHashCode") != null;
    }

    public static boolean hasData(@Nullable PsiClass aClass) {
        return findAnnotation(aClass, "lombok.Data", "Data") != null;
    }

    public static boolean hasToString(@Nullable PsiClass aClass) {
        return findAnnotation(aClass, "lombok.ToString", "ToString") != null;
    }

    public static boolean hasGetter(@Nullable PsiElement element) {
        if (element instanceof PsiField field) {
            PsiModifierList modifiers = field.getModifierList();
            if (modifiers != null) {
                for (PsiAnnotation a : modifiers.getAnnotations()) {
                    String fqn = a.getQualifiedName();
                    if ("lombok.Getter".equals(fqn) || "Getter".equals(fqn)) {
                        return true;
                    }
                }
            }
            PsiClass containingClass = field.getContainingClass();
            return (containingClass != null) && hasAnnotation(containingClass, "lombok.Getter", "Getter");
        }
        return false;
    }

    public static boolean hasSetter(@Nullable PsiElement element) {
        if (element instanceof PsiField field) {
            PsiModifierList modifiers = field.getModifierList();
            if (modifiers != null) {
                for (PsiAnnotation a : modifiers.getAnnotations()) {
                    String fqn = a.getQualifiedName();
                    if ("lombok.Setter".equals(fqn) || "Setter".equals(fqn)) {
                        return true;
                    }
                }
            }
            PsiClass containingClass = field.getContainingClass();
            return (containingClass != null) && hasAnnotation(containingClass, "lombok.Setter", "Setter");
        }
        return false;
    }

    public static boolean hasNonNull(@Nullable PsiField field) {
        if (field == null) {
            return false;
        }
        PsiModifierList modifiers = field.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation a : modifiers.getAnnotations()) {
            String fqn = a.getQualifiedName();
            if ("lombok.NonNull".equals(fqn) || "NonNull".equals(fqn)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasBuilderDefault(@Nullable PsiField field) {
        if (field == null) {
            return false;
        }
        PsiModifierList modifiers = field.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation a : modifiers.getAnnotations()) {
            String fqn = a.getQualifiedName();
            if ("lombok.Builder.Default".equals(fqn) || "Builder.Default".equals(fqn)) {
                return true;
            }
        }
        return false;
    }

    // ── Generic annotation helpers ────────────────────────────────────────
    public static @Nullable PsiAnnotation findAnnotation(
            @Nullable PsiClass aClass,
            @NotNull String fqn,
            @NotNull String shortName) {
        if (aClass == null) {
            return null;
        }
        PsiModifierList modifiers = aClass.getModifierList();
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation a : modifiers.getAnnotations()) {
            String qName = a.getQualifiedName();
            if (fqn.equals(qName) || shortName.equals(qName)) {
                return a;
            }
        }
        return null;
    }

    public static boolean hasAnnotation(@Nullable PsiClass aClass,
                                        @NotNull String fqn,
                                        @NotNull String shortName) {
        return findAnnotation(aClass, fqn, shortName) != null;
    }

    public static @Nullable PsiAnnotation findAnnotation(
            @Nullable PsiModifierList modifierList,
            @NotNull String fqn,
            @NotNull String shortName) {
        if (modifierList == null) {
            return null;
        }
        for (PsiAnnotation a : modifierList.getAnnotations()) {
            String qName = a.getQualifiedName();
            if (fqn.equals(qName) || shortName.equals(qName)) {
                return a;
            }
        }
        return null;
    }

    public static boolean isSpringManaged(@Nullable PsiClass aClass) {
        if (aClass == null) {
            return false;
        }
        PsiModifierList modifiers = aClass.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation a : modifiers.getAnnotations()) {
            String qName = a.getQualifiedName();
            if (qName == null) {
                continue;
            }
            for (String stereotype : SPRING_STEREOTYPES) {
                if (stereotype.equals(qName)
                        || qName.endsWith("." + stereotype.substring(stereotype.lastIndexOf('.') + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isJpaEntity(@Nullable PsiClass aClass) {
        return aClass != null && SpringInspectionUtil.isJpaEntity(aClass);
    }

    public static boolean hasJsonDeserialize(@Nullable PsiClass aClass) {
        if (aClass == null) {
            return false;
        }
        for (String fqn : SpringInspectionUtil.JSON_DESERIALIZE_FQNS) {
            if (findAnnotation(aClass, fqn, "JsonDeserialize") != null) {
                return true;
            }
        }
        return false;
    }

    // ── Field helpers ────────────────────────────────────────────────────
    public static int countInstanceFields(@Nullable PsiClass aClass) {
        if (aClass == null) {
            return 0;
        }
        int count = 0;
        for (PsiField f : aClass.getFields()) {
            if (!f.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC)) {
                count++;
            }
        }
        return count;
    }

    public static boolean isPrimitiveType(@Nullable PsiField field) {
        if (field == null) {
            return false;
        }
        return field.getType() instanceof com.intellij.psi.PsiPrimitiveType;
    }

    public static void ensureImport(@NotNull Project project,
                                    @NotNull PsiClass psiClass,
                                    @NotNull String fqn) {
        if (psiClass.getContainingFile() instanceof PsiJavaFile javaFile) {
            flushPostponedPsiChanges(project, psiClass);
            PsiClass importedClass = JavaPsiFacade.getInstance(project)
                    .findClass(fqn, GlobalSearchScope.allScope(project));
            if (importedClass != null) {
                JavaCodeStyleManager.getInstance(project).addImport(javaFile, importedClass);
                flushPostponedPsiChanges(project, psiClass);
            }
        }
    }

    public static void reformatSafely(@NotNull Project project,
                                      @NotNull PsiElement element) {
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        PsiFile containingFile = element.getContainingFile();
        if (containingFile != null) {
            Document document = documentManager.getDocument(containingFile);
            if (document != null) {
                documentManager.doPostponedOperationsAndUnblockDocument(document);
                documentManager.commitDocument(document);
            }
        } else {
            documentManager.commitAllDocuments();
        }
        com.intellij.psi.codeStyle.CodeStyleManager.getInstance(project).reformat(element);
    }

    public static void flushPostponedPsiChanges(@NotNull Project project,
                                                @NotNull PsiClass psiClass) {
        if (psiClass.getContainingFile() instanceof PsiJavaFile javaFile) {
            PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
            Document document = documentManager.getDocument(javaFile);
            if (document != null) {
                documentManager.doPostponedOperationsAndUnblockDocument(document);
                documentManager.commitDocument(document);
            }
        }
    }
}
