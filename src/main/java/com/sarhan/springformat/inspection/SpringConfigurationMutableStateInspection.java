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

/**
 * Detects mutable (non-final, non-@Value, non-@Autowired) instance fields
 * in @Configuration classes. @Configuration beans are singletons by default;
 * mutable state can cause race conditions during bean creation or refresh.
 *
 * @author Akmal Sarhan
 */
public class SpringConfigurationMutableStateInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                if (!isConfigurationClass(aClass)) {
                    return;
                }
                for (PsiField field : aClass.getFields()) {
                    if (field.hasModifierProperty(PsiModifier.STATIC)) {
                        continue;
                    }
                    if (field.hasModifierProperty(PsiModifier.FINAL)) {
                        continue;
                    }
                    if (hasSpringInjectAnnotation(field)) {
                        continue;
                    }
                    PsiElement element = (field.getNameIdentifier() != null) ? field.getNameIdentifier() : field;
                    if (field.getInitializer() != null) {
                        holder.registerProblem(
                                element,
                                "Mutable instance field in @Configuration class is not thread-safe during context refresh — make it final or externalize state",
                                ProblemHighlightType.WARNING,
                                new MakeFieldFinalFix()
                        );
                    } else {
                        holder.registerProblem(
                                element,
                                "Mutable instance field in @Configuration class is not thread-safe during context refresh — make it final or externalize state",
                                ProblemHighlightType.WARNING
                        );
                    }
                }
            }
        };
    }

    private static boolean isConfigurationClass(PsiClass psiClass) {
        PsiModifierList modifiers = psiClass.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation a : modifiers.getAnnotations()) {
            String name = a.getQualifiedName();
            if (name == null && a.getNameReferenceElement() != null) {
                name = a.getNameReferenceElement().getText();
            }
            if (name != null && (name.endsWith(".Configuration") || name.endsWith(".SpringBootConfiguration"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSpringInjectAnnotation(PsiField field) {
        for (PsiAnnotation a : field.getAnnotations()) {
            String name = a.getQualifiedName();
            if (name == null && a.getNameReferenceElement() != null) {
                name = a.getNameReferenceElement().getText();
            }
            if (name != null) {
                String shortName = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
                if ("Value".equals(shortName) || "Autowired".equals(shortName)
                        || "Inject".equals(shortName) || "Resource".equals(shortName)
                        || "Qualifier".equals(shortName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class MakeFieldFinalFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Make field final";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiField field = descriptor.getPsiElement() instanceof PsiField f ? f
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
            if (field == null || field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            field.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        }
    }
}
