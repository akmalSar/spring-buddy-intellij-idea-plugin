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
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NotNull;

/**
 * Detects @RequestMapping used without an explicit HTTP method.
 * When no method is specified, Spring maps the handler to ALL HTTP methods,
 * which can accidentally expose mutation endpoints to GET requests.
 *
 * @author Akmal Sarhan
 */
public class SpringRequestMappingMissingMethodInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiAnnotation annotation = findRequestMapping(method);
                if (annotation == null) {
                    return;
                }
                if (hasMethodAttribute(annotation) || isComposedMapping(annotation)) {
                    return;
                }
                holder.registerProblem(
                        (annotation.getNameReferenceElement() != null) ? annotation.getNameReferenceElement() : annotation,
                        "@RequestMapping without explicit HTTP method maps all methods — prefer @GetMapping/@PostMapping",
                        ProblemHighlightType.WARNING,
                        new ReplaceWithGetMappingFix()
                );
            }
        };
    }

    private static PsiAnnotation findRequestMapping(PsiMethod method) {
        for (PsiAnnotation a : method.getModifierList().getAnnotations()) {
            if (SpringInspectionUtil.isAnnotation(a, "org.springframework.web.bind.annotation.RequestMapping", "RequestMapping")) {
                return a;
            }
        }
        return null;
    }

    private static boolean hasMethodAttribute(PsiAnnotation annotation) {
        for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
            String name = pair.getAttributeName();
            if ("method".equals(name) || name == null || name.isEmpty()) {
                // name == null or empty means value/default, not method
                if ("method".equals(name)) {
                    return true;
                }
            }
        }
        // Also check shorthand: @RequestMapping(method = RequestMethod.GET)
        String text = annotation.getText();
        return text != null && text.contains("method");
    }

    private static boolean isComposedMapping(PsiAnnotation annotation) {
        return SpringInspectionUtil.isAnyAnnotation(annotation,
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping",
                "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PatchMapping");
    }

    private static final class ReplaceWithGetMappingFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Replace with @GetMapping";
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
            if (annotation == null) {
                return;
            }
            String args = annotation.getParameterList().getText();
            String newText = "@GetMapping" + args;
            annotation.replace(JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText(newText, annotation));
        }
    }
}
