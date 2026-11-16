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
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaFile;
import com.sarhan.springformat.architecture.ArchitectureConfig;
import com.sarhan.springformat.architecture.ArchitectureConfigService;
import com.sarhan.springformat.architecture.ArchitectureDependencyScanner;
import com.sarhan.springformat.architecture.ArchitectureReporter;
import org.jetbrains.annotations.NotNull;

/**
 * Reports uses of the classes, annotations and methods listed in the
 * {@code bannedApis} section of {@code .spring-buddy/architecture.json}
 * ({@code SB-ARCH-006}) outside the packages named in {@code allowedIn}.
 * All entries share this one inspection; each entry carries its own id and
 * severity. Without a config file this inspection reports nothing.
 *
 * @author Akmal Sarhan
 */
public class BannedApiInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        ArchitectureConfig config = ArchitectureConfigService.getInstance(holder.getProject()).getConfig();
        if (config.bannedApis().isEmpty()
                || ArchitectureDependencyScanner.isExcludedTestSource(holder.getFile(), config.includeTests())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        ArchitectureReporter reporter = ArchitectureReporter.create(holder);
        return new JavaElementVisitor() {
            @Override
            public void visitJavaFile(@NotNull PsiJavaFile file) {
                for (ArchitectureDependencyScanner.Violation violation
                        : ArchitectureDependencyScanner.scanBannedApis(file, config.bannedApis())) {
                    ArchitectureDependencyScanner.register(reporter, violation);
                }
            }
        };
    }
}
