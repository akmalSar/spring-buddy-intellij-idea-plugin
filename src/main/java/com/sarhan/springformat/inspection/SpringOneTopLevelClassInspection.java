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
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

/**
 * Detects Java source files that declare more than one top-level class or interface.
 * <p>
 * Each top-level type should reside in its own file named after the type. Multiple top-level types
 * in a single file make navigation and maintenance harder and violate Java naming conventions.
 *
 * @author Akmal Sarhan
 */
public class SpringOneTopLevelClassInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitJavaFile(@NotNull PsiJavaFile file) {
                PsiClass[] classes = file.getClasses();
                if (classes.length < 2) {
                    return;
                }
                boolean publicTypeFound = false;
                PsiClass firstType = null;
                for (PsiClass psiClass : classes) {
                    if (psiClass.hasModifierProperty(com.intellij.psi.PsiModifier.PUBLIC)) {
                        publicTypeFound = true;
                    }
                    if (firstType == null) {
                        firstType = psiClass;
                    }
                    else if (!psiClass.hasModifierProperty(com.intellij.psi.PsiModifier.PUBLIC)) {
                        register(holder, psiClass);
                    }
                }
                if (publicTypeFound && firstType != null
                        && !firstType.hasModifierProperty(com.intellij.psi.PsiModifier.PUBLIC)) {
                    register(holder, firstType);
                }
            }
        };
    }

    private static void register(ProblemsHolder holder, PsiClass psiClass) {
        holder.registerProblem((psiClass.getNameIdentifier() != null) ? psiClass.getNameIdentifier() : psiClass,
                "Top-level type should be declared in its own source file",
                ProblemHighlightType.WARNING,
                new MoveTopLevelTypeToOwnFileFix());
    }

    private static final class MoveTopLevelTypeToOwnFileFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Move top-level type to separate file";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiClass psiClass = element instanceof PsiClass cls ? cls
                    : element.getParent() instanceof PsiClass cls ? cls : null;
            if (psiClass == null || psiClass.getName() == null || !(psiClass.getContainingFile() instanceof PsiJavaFile javaFile)) {
                return;
            }
            PsiClass classToMove = targetClassToMove(psiClass, javaFile);
            if (classToMove == null || classToMove.getName() == null) {
                return;
            }
            PsiDirectory directory = javaFile.getContainingDirectory();
            if (directory == null || directory.findFile(classToMove.getName() + ".java") != null) {
                return;
            }
            PsiFile newFile = PsiFileFactory.getInstance(project).createFileFromText(
                    classToMove.getName() + ".java", javaFile.getFileType(), sourceFor(javaFile, classToMove));
            directory.add(newFile);
            deleteAdjacentWhitespace(classToMove);
            classToMove.delete();
        }

        private static PsiClass targetClassToMove(PsiClass reportedClass, PsiJavaFile javaFile) {
            String fileName = (javaFile.getVirtualFile() != null) ? javaFile.getVirtualFile().getNameWithoutExtension()
                    : javaFile.getName().replaceFirst("\\.java$", "");
            if (!fileName.equals(reportedClass.getName())) {
                return reportedClass;
            }
            for (PsiClass candidate : javaFile.getClasses()) {
                if (candidate != reportedClass) {
                    return candidate;
                }
            }
            return reportedClass;
        }

        private static String sourceFor(PsiJavaFile javaFile, PsiClass psiClass) {
            StringBuilder source = new StringBuilder();
            if (javaFile.getPackageStatement() != null) {
                source.append(javaFile.getPackageStatement().getText()).append("\n\n");
            }
            if (javaFile.getImportList() != null && javaFile.getImportList().getAllImportStatements().length > 0) {
                source.append(javaFile.getImportList().getText()).append("\n\n");
            }
            source.append(psiClass.getText()).append("\n");
            return source.toString();
        }

        private static void deleteAdjacentWhitespace(PsiElement element) {
            PsiElement next = element.getNextSibling();
            if (next instanceof com.intellij.psi.PsiWhiteSpace) {
                next.delete();
            }
            PsiElement previous = element.getPrevSibling();
            if (previous instanceof com.intellij.psi.PsiWhiteSpace) {
                previous.delete();
            }
        }
    }
}
