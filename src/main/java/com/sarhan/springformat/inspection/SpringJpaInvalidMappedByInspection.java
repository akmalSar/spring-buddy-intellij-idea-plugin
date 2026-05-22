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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * Reports invalid JPA {@code mappedBy} references.
 *
 * @author Akmal Sarhan
 */
public class SpringJpaInvalidMappedByInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitField(@NotNull PsiField field) {
                PsiAnnotation relation = relationAnnotation(field);
                if (relation == null) {
                    return;
                }
                String mappedBy = mappedBy(relation);
                if (mappedBy == null || mappedBy.isBlank()) {
                    return;
                }
                PsiClass target = targetEntity(field);
                PsiClass owner = field.getContainingClass();
                if (target == null || owner == null) {
                    return;
                }
                PsiField backReference = target.findFieldByName(mappedBy, true);
                if (backReference == null || !pointsTo(backReference.getType(), owner)) {
                    holder.registerProblem(relation,
                            "mappedBy references '" + mappedBy + "', but the target entity has no matching owning-side field",
                            ProblemHighlightType.ERROR);
                }
            }
        };
    }

    private static PsiAnnotation relationAnnotation(PsiField field) {
        PsiModifierList modifiers = field.getModifierList();
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (isAnnotation(annotation, "OneToMany") || isAnnotation(annotation, "OneToOne")) {
                return annotation;
            }
        }
        return null;
    }

    private static String mappedBy(PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("mappedBy");
        if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String s) {
            return s;
        }
        return null;
    }

    private static PsiClass targetEntity(PsiField field) {
        PsiType type = field.getType();
        if (type instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            PsiType[] parameters = classType.getParameters();
            if (isCollectionType(resolved) && parameters.length == 1 && parameters[0] instanceof PsiClassType elementType) {
                return elementType.resolve();
            }
            String text = type.getCanonicalText();
            int genericStart = text.indexOf('<');
            int genericEnd = text.lastIndexOf('>');
            if (genericStart >= 0 && genericEnd > genericStart) {
                PsiClass containingClass = field.getContainingClass();
                if (containingClass != null) {
                    String elementName = text.substring(genericStart + 1, genericEnd);
                    return findClassByName(containingClass, elementName);
                }
            }
            return resolved;
        }
        return null;
    }

    private static PsiClass findClassByName(PsiClass context, String className) {
        PsiClass topLevel = context;
        while (topLevel.getContainingClass() != null) {
            topLevel = topLevel.getContainingClass();
        }
        if (topLevel.getContainingFile() instanceof com.intellij.psi.PsiJavaFile javaFile) {
            for (PsiClass psiClass : javaFile.getClasses()) {
                if (className.equals(psiClass.getName())) {
                    return psiClass;
                }
            }
        }
        return null;
    }

    private static boolean isCollectionType(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        String qualifiedName = psiClass.getQualifiedName();
        if ("java.util.Collection".equals(qualifiedName) || "java.util.List".equals(qualifiedName)
                || "java.util.Set".equals(qualifiedName)) {
            return true;
        }
        for (PsiClass superClass : psiClass.getSupers()) {
            if (isCollectionType(superClass)) {
                return true;
            }
        }
        return false;
    }

    private static boolean pointsTo(PsiType type, PsiClass expectedClass) {
        if (type instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            if (resolved == expectedClass) {
                return true;
            }
            PsiType[] parameters = classType.getParameters();
            return parameters.length == 1 && pointsTo(parameters[0], expectedClass);
        }
        return false;
    }

    private static boolean isAnnotation(PsiAnnotation annotation, String shortName) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return shortName.equals(name) || (name != null && name.endsWith("." + shortName));
    }
}
