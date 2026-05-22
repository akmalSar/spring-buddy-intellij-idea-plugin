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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import org.jetbrains.annotations.NotNull;

/**
 * SpringOptionalAsFieldOrParameter: flags {@code java.util.Optional} (and primitive variants)
 * used as a field type or method parameter type. Per Brian Goetz, {@code Optional} is intended
 * as a return type only.
 *
 * @author Akmal Sarhan
 */
public class SpringOptionalAsFieldOrParameterInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitField(@NotNull PsiField field) {
                super.visitField(field);
                if (isOptional(field.getType())) {
                    PsiTypeElement te = field.getTypeElement();
                    PsiElement target = (te != null) ? te : field.getNameIdentifier();
                    holder.registerProblem(target,
                            "Optional should not be used as a field type; use a nullable field or a real value",
                            ProblemHighlightType.WARNING);
                }
            }

            @Override
            public void visitParameter(@NotNull PsiParameter parameter) {
                super.visitParameter(parameter);
                // skip catch parameters and lambda inferred params with no explicit type
                if (parameter.getDeclarationScope() instanceof com.intellij.psi.PsiMethod
                        && isOptional(parameter.getType())) {
                    PsiTypeElement te = parameter.getTypeElement();
                    if (te == null) {
                        return;
                    }
                    holder.registerProblem(te,
                            "Optional should not be used as a method parameter type; accept the value (possibly nullable) or overload",
                            ProblemHighlightType.WARNING);
                }
            }
        };
    }

    private static boolean isOptional(PsiType type) {
        if (!(type instanceof PsiClassType ct)) {
            return false;
        }
        String fqn = ct.rawType().getCanonicalText();
        return "java.util.Optional".equals(fqn)
                || "java.util.OptionalInt".equals(fqn)
                || "java.util.OptionalLong".equals(fqn)
                || "java.util.OptionalDouble".equals(fqn);
    }
}
