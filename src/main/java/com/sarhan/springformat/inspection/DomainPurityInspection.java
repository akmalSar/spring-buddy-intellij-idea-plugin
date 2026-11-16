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

import java.util.List;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.sarhan.springformat.architecture.ArchitectureConfig;
import com.sarhan.springformat.architecture.ArchitectureConfigService;
import com.sarhan.springformat.architecture.ArchitectureDependencyScanner;
import com.sarhan.springformat.architecture.ArchitectureReporter;
import com.sarhan.springformat.architecture.ArchitecturePresets;
import com.sarhan.springformat.architecture.ArchitectureRule;
import org.jetbrains.annotations.NotNull;

/**
 * Domain purity preset ({@code SB-ARCH-004}) on top of the architecture
 * dependency engine: classes in the {@code domain} layer must not reference
 * Spring Web / Servlet API types, JPA and persistence framework types
 * ({@code "style": "hexagonal"} only), or the configured {@code infrastructure}
 * layer. In the default layered style the preset is off unless configured and
 * never reports on JPA entities. See
 * {@link ArchitecturePresets#domainPurityRules(ArchitectureConfig)}.
 *
 * @author Akmal Sarhan
 */
public class DomainPurityInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        ArchitectureConfig config = ArchitectureConfigService.getInstance(holder.getProject()).getConfig();
        List<ArchitectureRule> rules = ArchitecturePresets.domainPurityRules(config);
        if (rules.isEmpty()
                || ArchitectureDependencyScanner.isExcludedTestSource(holder.getFile(), config.includeTests())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        ArchitectureReporter reporter = ArchitectureReporter.create(holder);
        boolean exemptEntities = !config.isHexagonal();
        return new JavaElementVisitor() {
            @Override
            public void visitJavaFile(@NotNull PsiJavaFile file) {
                for (ArchitectureDependencyScanner.Violation violation : ArchitectureDependencyScanner.scan(file, rules)) {
                    if (exemptEntities && isInJpaEntity(file, violation.anchor())) {
                        continue;
                    }
                    ArchitectureDependencyScanner.register(reporter, violation);
                }
            }
        };
    }

    /**
     * Whether the violation belongs to a JPA entity: the top-level class around
     * the anchor, or for imports any top-level class of the file.
     */
    private static boolean isInJpaEntity(PsiJavaFile file, PsiElement anchor) {
        PsiClass owner = PsiTreeUtil.getTopmostParentOfType(anchor, PsiClass.class);
        if (owner != null) {
            return SpringInspectionUtil.isJpaEntity(owner);
        }
        for (PsiClass topLevel : file.getClasses()) {
            if (SpringInspectionUtil.isJpaEntity(topLevel)) {
                return true;
            }
        }
        return false;
    }
}
