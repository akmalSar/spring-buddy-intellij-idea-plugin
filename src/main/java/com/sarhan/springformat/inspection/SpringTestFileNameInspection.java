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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

/**
 * SpringTestFileNameCheck: any class that contains at least one {@code @Test} method
 * (JUnit 4 or JUnit 5) must have a name ending with {@code Tests}.
 *
 * <p>This mirrors {@code io.spring.javaformat.checkstyle.check.SpringTestFileNameCheck}.
 *
 * @author Akmal Sarhan
 */
public class SpringTestFileNameInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String SUFFIX = "Tests";
    private static final String SINGLE_SUFFIX = "Test";

    static String expectedName(String currentName) {
        if (currentName.endsWith("TestsIT")) {
            return currentName.substring(0, currentName.length() - "TestsIT".length()) + "IT" + SUFFIX;
        }
        if (currentName.endsWith("TestIT")) {
            return currentName.substring(0, currentName.length() - "TestIT".length()) + "IT" + SUFFIX;
        }
        if (currentName.endsWith(SINGLE_SUFFIX)) {
            return currentName.substring(0, currentName.length() - SINGLE_SUFFIX.length()) + SUFFIX;
        }
        return currentName + SUFFIX;
    }

    private static final java.util.Set<String> TEST_ANNOTATIONS = java.util.Set.of(
            "org.junit.Test",                  // JUnit 4
            "org.junit.jupiter.api.Test",      // JUnit 5
            "Test"                             // unqualified (both)
    );

    private static boolean isStatic(PsiClass psiClass) {
        PsiModifierList mods = psiClass.getModifierList();
        return mods != null && mods.hasModifierProperty(PsiModifier.STATIC);
    }

    private static boolean hasTestMethod(PsiClass psiClass) {
        for (PsiMethod method : psiClass.getMethods()) {
            PsiModifierList mods = method.getModifierList();
            for (PsiAnnotation annotation : mods.getAnnotations()) {
                String fqn = annotation.getQualifiedName();
                if (fqn == null) {
                    continue;
                }
                if (TEST_ANNOTATIONS.contains(fqn) || fqn.endsWith(".Test")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                super.visitClass(psiClass);
                if (!SpringInspectionUtil.isInJavaFile(psiClass)) {
                    return;
                }

                // Only top-level or static nested classes that act as test containers
                if (psiClass.isInterface() || psiClass.isEnum() || psiClass.isAnnotationType()) {
                    return;
                }
                // Inner (non-static) classes are skipped — they are not independent test classes
                if (psiClass.getContainingClass() != null && !isStatic(psiClass)) {
                    return;
                }

                String name = psiClass.getName();
                if (name == null || name.endsWith(SUFFIX)) {
                    return;
                }

                // Only flag if the class actually contains @Test methods
                if (!hasTestMethod(psiClass)) {
                    return;
                }

                PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
                String newName = expectedName(name);
                holder.registerProblem(
                        (nameIdentifier != null) ? nameIdentifier : psiClass,
                        "Test class '" + name + "' should be named '" + newName + "' (Spring convention)",
                        ProblemHighlightType.WARNING,
                        new RenameToTestsFix(newName)
                );
            }
        };
    }

    private static class RenameToTestsFix implements LocalQuickFix {

        private final String newName;

        RenameToTestsFix(String newName) {
            this.newName = newName;
        }

        @Override
        @NotNull
        public String getName() {
            return "Rename to '" + this.newName + "'";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return "Rename test class to end with 'Tests'";
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }

        @Override
        public void applyFix(@NotNull com.intellij.openapi.project.Project project,
                             @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiClass psiClass = element instanceof PsiClass c ? c
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(element, PsiClass.class);
            if (psiClass == null) {
                return;
            }
            // Delegate to IntelliJ's rename refactoring so all references are updated
            com.intellij.refactoring.rename.RenameProcessor processor =
                    new com.intellij.refactoring.rename.RenameProcessor(
                            project, psiClass, this.newName,
                            /* searchInComments */ true,
                            /* searchTextOccurrences */ true);
            processor.run();
        }
    }
}
