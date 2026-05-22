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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * Detects manually defined methods that shadow Lombok-generated getters.
 *
 * <ul>
 *   <li><b>MANUAL-001</b> — Manual method matching a Lombok-generated getter signature.</li>
 * </ul>
 *
 * @author Akmal Sarhan
 */
public class LombokManualGetterShadowInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public String getDisplayName() {
        return MyMessageBundle.message("inspection.manual.getter.display.name");
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

                // Skip if the class doesn't have @Getter at class level or on any field
                boolean hasClassLevelGetter = LombokAnnotationUtil.findAnnotation(
                        aClass, "lombok.Getter", "Getter") != null;

                for (PsiField field : aClass.getFields()) {
                    if (!field.isPhysical()) {
                        continue;
                    }
                    if (field.getTextLength() <= 0) {
                        continue;
                    }
                    if (field.hasModifierProperty(PsiModifier.STATIC)) {
                        continue;
                    }

                    boolean fieldHasGetter = hasClassLevelGetter
                            || (LombokAnnotationUtil.findAnnotation(
                            field.getModifierList(), "lombok.Getter", "Getter") != null);
                    if (!fieldHasGetter) {
                        continue;
                    }

                    String fieldName = field.getName();
                    PsiType fieldType = field.getType();
                    String expectedGetterName = getExpectedGetterName(fieldName, fieldType);

                    for (PsiMethod method : aClass.getMethods()) {
                        if (!method.isPhysical()) {
                            continue;
                        }
                        if (method.getTextLength() <= 0) {
                            continue;
                        }

                        String methodName = method.getName();
                        if (!expectedGetterName.equals(methodName)) {
                            continue;
                        }

                        // Check parameter count (getters have 0 params)
                        if (method.getParameterList().getParametersCount() != 0) {
                            continue;
                        }

                        // Check return type matches
                        PsiType returnType = method.getReturnType();
                        if ((returnType == null) || !returnType.equals(fieldType)) {
                            continue;
                        }

                        // Check the method is public (like a generated getter)
                        if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
                            continue;
                        }

                        // Check it's not static
                        if (method.hasModifierProperty(PsiModifier.STATIC)) {
                            continue;
                        }

                        holder.registerProblem(
                                (method.getNameIdentifier() != null) ? method.getNameIdentifier() : method,
                                MyMessageBundle.message("inspection.manual.001.problem.descriptor", fieldName),
                                ProblemHighlightType.WARNING
                        );
                    }
                }
            }

            private @NotNull String getExpectedGetterName(@NotNull String fieldName, @NotNull PsiType fieldType) {
                String prefix = "get";
                if (fieldType.equalsToText("boolean")
                        || fieldType.equalsToText("java.lang.Boolean")) {
                    prefix = "is";
                }
                if (fieldName.length() == 1) {
                    return prefix + fieldName.toUpperCase();
                }
                return prefix + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            }
        };
    }
}
