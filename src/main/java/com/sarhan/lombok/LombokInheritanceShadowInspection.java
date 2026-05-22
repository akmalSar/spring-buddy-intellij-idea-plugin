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

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * Detects field shadowing and related issues in Lombok inheritance hierarchies.
 *
 * <ul>
 *   <li><b>INHERIT-001</b> — Field in subclass shadows parent field.</li>
 *   <li><b>INHERIT-002</b> — Shadowing field with {@code @Getter} / {@code @Setter}.</li>
 *   <li><b>INHERIT-003</b> — {@code @AllArgsConstructor} on subclass with shadowed fields.</li>
 *   <li><b>INHERIT-004</b> — Validation annotation on shadowing field.</li>
 * </ul>
 *
 * @author Akmal Sarhan
 */
public class LombokInheritanceShadowInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public String getDisplayName() {
        return MyMessageBundle.message("inspection.inheritance.shadow.display.name");
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
        return "Lombok builder";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(
            @NotNull ProblemsHolder holder,
            boolean isOnTheFly) {

                if (!LombokInspectionUtil.isInJavaFile(holder.getFile())) {
                    return PsiElementVisitor.EMPTY_VISITOR;
                }

        return new JavaElementVisitor() {

            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                super.visitClass(aClass);

                // INHERIT-003: @AllArgsConstructor on subclass with shadowed fields
                checkAllArgsConstructorOnSubclass(aClass, holder);

                // Check fields for shadowing — skip non-physical/synthetic fields
                for (PsiField field : aClass.getFields()) {
                    if (!field.isPhysical()) {
                        continue;
                    }
                    if (field.getTextLength() <= 0) {
                        continue;
                    }
                    String fieldName = field.getName();
                    if (fieldName == null) {
                        continue;
                    }

                    PsiClass parent = findParentFieldOwner(aClass, fieldName);
                    if ((parent == null) || (parent == aClass)) {
                        continue;
                    }

                    // INHERIT-001: Field shadows parent field
                    holder.registerProblem(
                            field,
                            MyMessageBundle.message("inspection.inherit.001.problem.descriptor", fieldName),
                            ProblemHighlightType.WARNING
                    );

                    // INHERIT-002: Shadowing field with @Getter / @Setter
                    if (LombokAnnotationUtil.hasGetter(field) || LombokAnnotationUtil.hasSetter(field)) {
                        holder.registerProblem(
                                field,
                                MyMessageBundle.message("inspection.inherit.002.problem.descriptor", fieldName),
                                ProblemHighlightType.WARNING
                        );
                    }

                    // INHERIT-004: Validation annotation on shadowing field
                    checkValidationAnnotations(field, holder);
                }
            }

            private PsiClass findParentFieldOwner(@NotNull PsiClass clazz, @NotNull String fieldName) {
                for (PsiClass sup : clazz.getSupers()) {
                    if ((sup.getQualifiedName() != null) && sup.getQualifiedName().equals("java.lang.Object")) {
                        continue;
                    }
                    for (PsiField f : sup.getAllFields()) {
                        if (fieldName.equals(f.getName()) && (f.getContainingClass() != clazz)) {
                            return f.getContainingClass();
                        }
                    }
                }
                return null;
            }

            private void checkAllArgsConstructorOnSubclass(@NotNull PsiClass aClass,
                                                           @NotNull ProblemsHolder holder) {
                PsiAnnotation allArgsAnn = LombokAnnotationUtil.findAnnotation(
                        aClass, "lombok.AllArgsConstructor", "AllArgsConstructor");
                if (allArgsAnn == null) {
                    return;
                }

                boolean hasShadowed = false;
                for (PsiField field : aClass.getFields()) {
                    if (!field.isPhysical()) {
                        continue;
                    }
                    String fieldName = field.getName();
                    if (fieldName == null) {
                        continue;
                    }
                    for (PsiClass sup : aClass.getSupers()) {
                        if ((sup.getQualifiedName() != null) && sup.getQualifiedName().equals("java.lang.Object")) {
                            continue;
                        }
                        for (PsiField parentField : sup.getAllFields()) {
                            if (fieldName.equals(parentField.getName())) {
                                hasShadowed = true;
                                break;
                            }
                        }
                        if (hasShadowed) {
                            break;
                        }
                    }
                    if (hasShadowed) {
                        break;
                    }
                }

                if (hasShadowed) {
                    holder.registerProblem(
                            allArgsAnn,
                            MyMessageBundle.message("inspection.inherit.003.problem.descriptor"),
                            ProblemHighlightType.WARNING
                    );
                }
            }

            private void checkValidationAnnotations(@NotNull PsiField field,
                                                    @NotNull ProblemsHolder holder) {
                PsiModifierList modifiers = field.getModifierList();
                if (modifiers == null) {
                    return;
                }

                for (PsiAnnotation ann : modifiers.getAnnotations()) {
                    String qName = ann.getQualifiedName();
                    if (qName == null) {
                        continue;
                    }
                    for (String validation : LombokAnnotationUtil.VALIDATION_ANNOTATIONS) {
                        if (validation.equals(qName) || qName.endsWith("." + validation.substring(validation.lastIndexOf('.') + 1))) {
                            holder.registerProblem(
                                    ann,
                                    MyMessageBundle.message("inspection.inherit.004.problem.descriptor", field.getName()),
                                    ProblemHighlightType.WARNING
                            );
                            return; // Flag once per field is enough
                        }
                    }
                }
            }
        };
    }
}
