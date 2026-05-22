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

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */

final class SpringImportUtil {

    private SpringImportUtil() {
    }

    static void ensureImport(@NotNull Project project, @NotNull PsiJavaFile javaFile, @NotNull String fqn) {
        PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return;
        }
        for (PsiImportStatement statement : importList.getImportStatements()) {
            if (statement.getImportReference() != null && fqn.equals(statement.getImportReference().getQualifiedName())) {
                return;
            }
        }
        importList.add(JavaPsiFacade.getElementFactory(project).createImportStatementOnDemand(
                fqn.substring(0, fqn.lastIndexOf('.'))));
        JavaCodeStyleManager.getInstance(project).optimizeImports(javaFile);
    }
}
