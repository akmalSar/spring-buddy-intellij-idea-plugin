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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.sarhan.lombok.LombokAnnotationUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects {@code @Value} or {@code @Autowired} fields in classes that are not managed by the Spring
 * container (i.e. classes lacking a stereotype annotation such as {@code @Component}).
 * Field injection will not be performed for non-Spring-managed beans, so the dependencies
 * remain uninitialized at runtime.
 *
 * @author Akmal Sarhan
 */
public class SpringLeakyValueInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitField(@NotNull PsiField field) {
                PsiAnnotation target = findInjectionAnnotation(field.getModifierList());
                if (target == null) {
                    return;
                }
                PsiClass containingClass = field.getContainingClass();
                if (containingClass == null || LombokAnnotationUtil.isSpringManaged(containingClass)) {
                    return;
                }
                if (isTestClass(containingClass)) {
                    return;
                }
                holder.registerProblem(target,
                        "@Value field in non-Spring-managed class will not be injected; annotate the class with @Component",
                        ProblemHighlightType.ERROR,
                        new AddComponentAnnotationFix());
            }
        };
    }

    private static boolean isTestClass(PsiClass psiClass) {
        String name = psiClass.getName();
        return name != null && (name.endsWith("Test") || name.endsWith("Tests")
                || name.endsWith("IT") || name.endsWith("TestIT")
                || name.endsWith("TestsIT") || name.endsWith("ITTests"));
    }

    private static PsiAnnotation findInjectionAnnotation(PsiModifierList modifiers) {
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String qName = annotation.getQualifiedName();
            if (qName == null && annotation.getNameReferenceElement() != null) {
                qName = annotation.getNameReferenceElement().getText();
            }
            if ("Value".equals(qName) || "org.springframework.beans.factory.annotation.Value".equals(qName)
                    || "Autowired".equals(qName) || "org.springframework.beans.factory.annotation.Autowired".equals(qName)) {
                return annotation;
            }
        }
        return null;
    }

    private static final class AddComponentAnnotationFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Add @Component";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiField field = descriptor.getPsiElement() instanceof PsiField f ? f
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
            if (field == null) {
                return;
            }
            PsiClass psiClass = field.getContainingClass();
            if (psiClass == null || psiClass.getModifierList() == null) {
                return;
            }
            psiClass.getModifierList().addAnnotation("Component");
            if (psiClass.getContainingFile() instanceof PsiJavaFile javaFile) {
                PsiClass importedClass = JavaPsiFacade.getInstance(project)
                        .findClass("org.springframework.stereotype.Component", GlobalSearchScope.allScope(project));
                if (importedClass != null) {
                    JavaCodeStyleManager.getInstance(project).addImport(javaFile, importedClass);
                }
            }
        }
    }
}
