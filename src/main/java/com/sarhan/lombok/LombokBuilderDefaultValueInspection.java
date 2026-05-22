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
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;

/**
 * Detects field initializers that are silently erased by {@code @Builder}.
 *
 * <p>Lombok's {@code @Builder} ignores field initializers, so a field like
 * {@code String role = "GUEST"} in a {@code @Builder}-annotated class will
 * result in {@code role == null} when the object is built via
 * {@code builder().build()}.
 *
 * <ul>
 *   <li><b>BUILDERDEFAULT-VALUE-001</b> — field initializer without
 *       {@code @Builder.Default} in a {@code @Builder} class.</li>
 * </ul>
 *
 * @author Akmal Sarhan
 */
public class LombokBuilderDefaultValueInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public String getDisplayName() {
        return MyMessageBundle.message("inspection.builderdefault.value.display.name");
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

                if (!LombokAnnotationUtil.hasBuilderAnnotation(aClass)) {
                    return;
                }

                for (PsiField field : aClass.getFields()) {
                    if (!field.isPhysical()) {
                        continue;
                    }
                    if (field.hasModifierProperty(PsiModifier.STATIC)) {
                        continue;
                    }
                    if (field.hasModifierProperty(PsiModifier.FINAL)) {
                        continue;
                    }
                    if (LombokAnnotationUtil.hasBuilderDefault(field)) {
                        continue;
                    }
                    if (field.getInitializer() == null) {
                        continue;
                    }

                    holder.registerProblem(
                            field,
                            MyMessageBundle.message("inspection.builderdefault.value.problem.descriptor"),
                            ProblemHighlightType.WARNING,
                            new AddBuilderDefaultFix()
                    );
                }
            }
        };
    }
}
