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
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects @Valid annotations on primitive types or String parameters.
 * @Valid only works on complex objects that contain constraint annotations.
 * On primitives and String it has no effect and is misleading.
 *
 * @author Akmal Sarhan
 */
public class SpringValidOnPrimitiveInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                for (PsiParameter parameter : method.getParameterList().getParameters()) {
                    PsiAnnotation validAnnotation = findValidAnnotation(parameter);
                    if (validAnnotation == null) {
                        continue;
                    }
                    if (!isPrimitiveOrString(parameter.getType())) {
                        continue;
                    }
                    holder.registerProblem(validAnnotation,
                            "@Valid has no effect on primitive or String parameters; use @NotNull, @NotBlank, or @Size directly",
                            ProblemHighlightType.WARNING,
                            new RemoveValidAnnotationFix());
                }
            }
        };
    }

    private static @Nullable PsiAnnotation findValidAnnotation(PsiParameter parameter) {
        PsiModifierList modifiers = parameter.getModifierList();
        return SpringInspectionUtil.findAnyAnnotation(modifiers,
                "jakarta.validation.Valid",
                "javax.validation.Valid");
    }

    private static boolean isPrimitiveOrString(PsiType type) {
        if (type instanceof PsiPrimitiveType) {
            return true;
        }
        String canonical = type.getCanonicalText();
        if (canonical == null) {
            return false;
        }
        return canonical.equals("java.lang.String")
                || canonical.equals("java.lang.Integer") || canonical.equals("int")
                || canonical.equals("java.lang.Long") || canonical.equals("long")
                || canonical.equals("java.lang.Double") || canonical.equals("double")
                || canonical.equals("java.lang.Boolean") || canonical.equals("boolean")
                || canonical.equals("java.lang.Short") || canonical.equals("short")
                || canonical.equals("java.lang.Float") || canonical.equals("float")
                || canonical.equals("java.lang.Byte") || canonical.equals("byte")
                || canonical.equals("java.lang.Character") || canonical.equals("char");
    }

    private static final class RemoveValidAnnotationFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Remove @Valid annotation";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiAnnotation annotation = descriptor.getPsiElement() instanceof PsiAnnotation a ? a
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiAnnotation.class);
            if (annotation != null) {
                annotation.delete();
            }
        }
    }
}
