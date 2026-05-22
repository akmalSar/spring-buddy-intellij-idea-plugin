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
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * Detects incorrect usage of Lombok {@code @NonNull}.
 *
 * <ul>
 *   <li><b>NONNULL-001</b> — {@code @NonNull} on primitive field.</li>
 *   <li><b>NONNULL-002</b> — {@code @NonNull} together with {@code @Builder.Default}.</li>
 * </ul>
 *
 * @author Akmal Sarhan
 */
public class LombokNonNullInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public String getDisplayName() {
        return MyMessageBundle.message("inspection.nonnull.display.name");
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
            public void visitField(@NotNull PsiField field) {
                super.visitField(field);

                if (!field.isPhysical()) {
                    return;
                }
                if (field.getTextLength() <= 0) {
                    return;
                }

                PsiModifierList modifiers = field.getModifierList();
                if (modifiers == null) {
                    return;
                }

                PsiAnnotation nonNullAnn = null;
                for (PsiAnnotation a : modifiers.getAnnotations()) {
                    String fqn = a.getQualifiedName();
                    if ("lombok.NonNull".equals(fqn) || "NonNull".equals(fqn)) {
                        nonNullAnn = a;
                        break;
                    }
                }
                if (nonNullAnn == null) {
                    return;
                }

                // NONNULL-001: @NonNull on primitive
                if (LombokAnnotationUtil.isPrimitiveType(field)) {
                    holder.registerProblem(
                            nonNullAnn,
                            MyMessageBundle.message("inspection.nonnull.001.problem.descriptor"),
                            ProblemHighlightType.ERROR,
                            new RemoveNonNullOnPrimitiveFix()
                    );
                }

                // NONNULL-002: @NonNull + @Builder.Default
                if (LombokAnnotationUtil.hasBuilderDefault(field)) {
                    holder.registerProblem(
                            nonNullAnn,
                            MyMessageBundle.message("inspection.nonnull.002.problem.descriptor"),
                            ProblemHighlightType.ERROR
                    );
                }
            }
        };
    }
}
