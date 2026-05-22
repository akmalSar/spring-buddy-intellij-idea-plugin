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
 * Detects {@code @Setter} on fields in a {@code @Builder} class.
 *
 * <ul>
 *   <li><b>SETTER-001</b> — {@code @Setter} on a field in a {@code @Builder} class.</li>
 * </ul>
 *
 * @author Akmal Sarhan
 */
public class LombokSetterOnBuilderFieldInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public String getDisplayName() {
        return MyMessageBundle.message("inspection.setter.builder.display.name");
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
                    if (field.getTextLength() <= 0) {
                        continue;
                    }
                    PsiModifierList modifiers = field.getModifierList();
                    if (modifiers == null) {
                        continue;
                    }

                    for (PsiAnnotation ann : modifiers.getAnnotations()) {
                        String fqn = ann.getQualifiedName();
                        if ("lombok.Setter".equals(fqn) || "Setter".equals(fqn)) {
                            holder.registerProblem(
                                    ann,
                                    MyMessageBundle.message("inspection.setter.001.problem.descriptor"),
                                    ProblemHighlightType.WEAK_WARNING,
                                    new RemoveSetterFix()
                            );
                        }
                    }
                }
            }
        };
    }
}
