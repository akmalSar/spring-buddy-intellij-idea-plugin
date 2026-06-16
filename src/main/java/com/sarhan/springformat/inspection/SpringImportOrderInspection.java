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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.NotNull;

/**
 * Detects import statements that are not ordered or grouped according to
 * spring-javaformat conventions (java, javax, other, org.springframework, static).
 *
 * @author Akmal Sarhan
 */
public class SpringImportOrderInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String PROJECT_ROOT_PACKAGE = "org.springframework";

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitFile(@NotNull PsiFile file) {
                if (!(file instanceof PsiJavaFile javaFile)) {
                    return;
                }
                PsiImportList importList = javaFile.getImportList();
                if (importList == null || importList.getAllImportStatements().length < 2) {
                    return;
                }
                List<ImportEntry> actual = ImportEntry.from(importList.getAllImportStatements());
                if (!actual.equals(ordered(actual))) {
                    holder.registerProblem(importList,
                            "Imports are not ordered according to spring-javaformat conventions",
                            ProblemHighlightType.WARNING, new OptimizeSpringImportsFix());
                    return;
                }
                if (!blankLinesCorrect(importList, actual)) {
                    holder.registerProblem(importList,
                            "Imports are not grouped correctly (blank lines between groups are required)",
                            ProblemHighlightType.WARNING, new OptimizeSpringImportsFix());
                }
            }
        };
    }

    private static List<ImportEntry> ordered(List<ImportEntry> imports) {
        List<ImportEntry> result = new ArrayList<>(imports);
        result.sort(Comparator.comparingInt(ImportEntry::group).thenComparing(ImportEntry::name));
        return result;
    }

    private static boolean blankLinesCorrect(PsiImportList importList, List<ImportEntry> entries) {
        PsiImportStatementBase[] statements = importList.getAllImportStatements();
        for (int i = 1; i < statements.length; i++) {
            boolean needsBlank = entries.get(i - 1).group() != entries.get(i).group();
            boolean hasBlank = hasBlankLine(statements[i - 1], statements[i]);
            if (needsBlank != hasBlank) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasBlankLine(com.intellij.psi.PsiElement before, com.intellij.psi.PsiElement after) {
        com.intellij.psi.PsiElement sibling = before.getNextSibling();
        while (sibling != null && sibling != after) {
            if (sibling instanceof PsiWhiteSpace ws) {
                String text = ws.getText();
                if (text.contains("\n\n") || text.contains("\n \n") || text.contains("\n\t\n")) {
                    return true;
                }
            }
            sibling = sibling.getNextSibling();
        }
        return false;
    }

    private static final class OptimizeSpringImportsFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Reorder imports according to spring-javaformat";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            com.intellij.psi.PsiElement element = descriptor.getPsiElement();
            PsiImportList importList = element instanceof PsiImportList list ? list
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(element, PsiImportList.class);
            if (importList == null) {
                return;
            }
            List<ImportEntry> imports = ordered(ImportEntry.from(importList.getAllImportStatements()));
            if (imports.isEmpty()) {
                return;
            }

            PsiImportList sortedImportList = this.createImportList(project, imports);
            if (sortedImportList != null) {
                importList.replace(sortedImportList);
            }
        }

        private PsiImportList createImportList(Project project, List<ImportEntry> imports) {
            StringBuilder source = new StringBuilder("class Dummy {\n");
            int previousGroup = -1;
            for (int i = imports.size() - 1; i >= 0; i--) {
                ImportEntry entry = imports.get(i);
                if (previousGroup != -1 && entry.group() != previousGroup) {
                    source.insert(0, "\n");
                }
                source.insert(0, entry.source.getText() + "\n");
                previousGroup = entry.group();
            }
            source.append("}\n");
            PsiFile file = PsiFileFactory.getInstance(project)
                    .createFileFromText("Dummy.java", JavaLanguage.INSTANCE, source.toString());
            return file instanceof PsiJavaFile javaFile ? javaFile.getImportList() : null;
        }
    }

    private record ImportEntry(String name, boolean isStatic, PsiImportStatementBase source) {
        private static List<ImportEntry> from(PsiImportStatementBase[] statements) {
            List<ImportEntry> result = new ArrayList<>();
            for (PsiImportStatementBase statement : statements) {
                ImportEntry entry = from(statement);
                if (entry.name() != null) {
                    result.add(entry);
                }
            }
            return result;
        }

        private static ImportEntry from(PsiImportStatementBase statement) {
            if (statement instanceof PsiImportStatement importStatement) {
                return new ImportEntry(importStatement.getQualifiedName(), false, statement);
            }
            if (statement instanceof PsiImportStaticStatement staticStatement) {
                PsiJavaCodeReferenceElement reference = staticStatement.getImportReference();
                return new ImportEntry((reference != null) ? reference.getQualifiedName() : null, true, statement);
            }
            return new ImportEntry(null, false, statement);
        }

        private int group() {
            if (this.isStatic) {
                return 4;
            }
            if (this.name.startsWith("java.")) {
                return 0;
            }
            if (this.name.startsWith("javax.")) {
                return 1;
            }
            if (this.name.equals(PROJECT_ROOT_PACKAGE) || this.name.startsWith(PROJECT_ROOT_PACKAGE + ".")) {
                return 3;
            }
            return 2;
        }

    }
}
