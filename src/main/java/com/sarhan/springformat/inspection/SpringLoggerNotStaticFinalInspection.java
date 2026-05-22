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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * SpringLoggerNotStaticFinal: SLF4J Logger fields must be `private static final`.
 * Quick-fix adds the missing `private` / `static` / `final` modifiers.
 *
 * @author Akmal Sarhan
 */
public class SpringLoggerNotStaticFinalInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String SLF4J_LOGGER = "org.slf4j.Logger";

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
                if (!isLoggerType(field.getType())) {
                    return;
                }
                PsiClass containing = field.getContainingClass();
                if (containing != null && containing.isInterface()) {
                    return;
                }
                PsiModifierList modifiers = field.getModifierList();
                if (modifiers == null) {
                    return;
                }
                if (modifiers.hasModifierProperty(PsiModifier.PRIVATE)
                        && modifiers.hasModifierProperty(PsiModifier.STATIC)
                        && modifiers.hasModifierProperty(PsiModifier.FINAL)) {
                    return;
                }
                PsiElement nameId = field.getNameIdentifier();
                holder.registerProblem(nameId,
                        "SLF4J Logger field '" + field.getName() + "' should be private static final",
                        ProblemHighlightType.WARNING, new MakePrivateStaticFinalFix());
            }
        };
    }

    private static boolean isLoggerType(PsiType type) {
        if (type == null) {
            return false;
        }
        String text = type.getCanonicalText();
        return SLF4J_LOGGER.equals(text) || "Logger".equals(text);
    }

    private static final class MakePrivateStaticFinalFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Make logger field 'private static final'";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiField field = (element != null && element.getParent() instanceof PsiField f) ? f : null;
            if (field == null) {
                return;
            }
            PsiModifierList modifiers = field.getModifierList();
            if (modifiers == null) {
                return;
            }
            // Drop conflicting access modifiers before adding `private` to avoid duplicates.
            if (modifiers.hasModifierProperty(PsiModifier.PUBLIC)) {
                modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
            }
            if (modifiers.hasModifierProperty(PsiModifier.PROTECTED)) {
                modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
            }
            modifiers.setModifierProperty(PsiModifier.PRIVATE, true);
            modifiers.setModifierProperty(PsiModifier.STATIC, true);
            modifiers.setModifierProperty(PsiModifier.FINAL, true);
        }
    }
}
