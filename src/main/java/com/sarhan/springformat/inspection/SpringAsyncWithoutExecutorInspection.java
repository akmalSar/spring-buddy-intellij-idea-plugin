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
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Detects @Async methods that do not specify a custom Executor.
 * Without an explicit executor Spring falls back to SimpleAsyncTaskExecutor,
 * which creates a new thread for every task and can exhaust memory under load.
 *
 * @author Akmal Sarhan
 */
public class SpringAsyncWithoutExecutorInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        Project project = holder.getProject();
        boolean hasGlobalAsyncConfigurer = hasAsyncConfigurerInProject(project);
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                PsiAnnotation async = findAsync(method);
                if (async == null) {
                    return;
                }
                // Class-level @Async with an executor attribute is acceptable
                // Method-level @Async without executor attribute is flagged
                if (hasExecutorSpecified(async)) {
                    return;
                }
                // Skip if a global AsyncConfigurer is present in the project
                if (hasGlobalAsyncConfigurer) {
                    return;
                }
                holder.registerProblem(
                        (async.getNameReferenceElement() != null) ? async.getNameReferenceElement() : async,
                        "@Async without explicit executor falls back to SimpleAsyncTaskExecutor — specify executor='...' or implement AsyncConfigurer",
                        ProblemHighlightType.ERROR,
                        new AddExecutorAttributeFix()
                );
            }
        };
    }

    private static PsiAnnotation findAsync(PsiMethod method) {
        // Also check class-level @Async (if present on the class it applies to every method)
        PsiAnnotation classAsync = null;
        if (method.getContainingClass() != null) {
            classAsync = findAsyncOnClass(method.getContainingClass());
        }
        PsiAnnotation methodAsync = null;
        for (PsiAnnotation a : method.getModifierList().getAnnotations()) {
            if (SpringInspectionUtil.isAnnotation(a, "org.springframework.scheduling.annotation.Async", "Async")) {
                methodAsync = a;
                break;
            }
        }
        // Prefer method-level annotation if present
        if (methodAsync != null) {
            return methodAsync;
        }
        return classAsync;
    }

    private static PsiAnnotation findAsyncOnClass(com.intellij.psi.PsiClass psiClass) {
        com.intellij.psi.PsiModifierList modifiers = psiClass.getModifierList();
        if (modifiers == null) {
            return null;
        }
        return SpringInspectionUtil.findAnnotation(modifiers, "org.springframework.scheduling.annotation.Async", "Async");
    }

    private static boolean hasExecutorSpecified(PsiAnnotation annotation) {
        // Spring's @Async uses the 'value' attribute as the executor name.
        // Check for any explicit attribute (value=... or executor=...).
        com.intellij.psi.PsiAnnotationParameterList paramList = annotation.getParameterList();
        if (paramList == null) {
            return false;
        }
        com.intellij.psi.PsiNameValuePair[] attributes = paramList.getAttributes();
        if (attributes.length == 0) {
            return false;
        }
        // If there is a single unnamed attribute or a named value/executor attribute,
        // an executor is considered specified.
        for (com.intellij.psi.PsiNameValuePair pair : attributes) {
            String attrName = pair.getAttributeName();
            if (attrName == null || "value".equals(attrName) || "executor".equals(attrName)) {
                com.intellij.psi.PsiAnnotationMemberValue value = pair.getValue();
                if (value != null) {
                    String text = value.getText();
                    if (text != null && !text.isEmpty() && !"\"\"".equals(text) && !"''".equals(text)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasAsyncConfigurerInProject(@NotNull Project project) {
        PsiClass asyncConfigurer = JavaPsiFacade.getInstance(project)
                .findClass("org.springframework.scheduling.annotation.AsyncConfigurer",
                        GlobalSearchScope.allScope(project));
        if (asyncConfigurer == null) {
            return false;
        }
        return com.intellij.psi.search.searches.ClassInheritorsSearch
                .search(asyncConfigurer, GlobalSearchScope.projectScope(project), true)
                .findFirst() != null;
    }

    private static final class AddExecutorAttributeFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Add executor = \"taskExecutor\"";
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
            PsiAnnotationMemberValue value = JavaPsiFacade.getElementFactory(project)
                    .createExpressionFromText("\"taskExecutor\"", annotation);
            annotation.setDeclaredAttributeValue("executor", value);
        }
    }
}
