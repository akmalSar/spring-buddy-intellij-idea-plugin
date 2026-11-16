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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifierList;
import com.sarhan.springformat.architecture.ArchitectureConfig;
import com.sarhan.springformat.architecture.ArchitectureConfigService;
import com.sarhan.springformat.architecture.ArchitectureDependencyScanner;
import com.sarhan.springformat.architecture.ArchitecturePresets;
import com.sarhan.springformat.architecture.ArchitectureProblem;
import com.sarhan.springformat.architecture.ArchitectureReporter;
import com.sarhan.springformat.architecture.ArchitectureSeverity;
import org.jetbrains.annotations.NotNull;

/**
 * Constructor injection preset ({@code SB-ARCH-005}, default off): reports
 * fields annotated with {@code @Autowired} or {@code @Inject} in Spring
 * stereotype classes. Field injection hides dependencies, prevents
 * {@code final} fields and makes the class unusable without a container.
 *
 * @author Akmal Sarhan
 */
public class SpringConstructorInjectionInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final List<String> STEREOTYPES = List.of(
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.context.annotation.Configuration");

    private static final String[] INJECTION_ANNOTATIONS = {
            "org.springframework.beans.factory.annotation.Autowired",
            "jakarta.inject.Inject",
            "javax.inject.Inject" };

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        ArchitectureConfig config = ArchitectureConfigService.getInstance(holder.getProject()).getConfig();
        ArchitectureSeverity severity = config.presetSeverity(ArchitectureConfig.PRESET_CONSTRUCTOR_INJECTION);
        if (severity.isOff() || ArchitectureDependencyScanner.isExcludedTestSource(holder.getFile(), config.includeTests())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        String text = ArchitectureProblem.text("Field injection in a Spring component",
                "hidden dependencies cannot be final and the class cannot be created without the container",
                ArchitecturePresets.fixHint(config, ArchitectureConfig.PRESET_CONSTRUCTOR_INJECTION,
                        "Declare the field final and inject it through the constructor"));
        ArchitectureReporter reporter = ArchitectureReporter.create(holder);
        return new JavaElementVisitor() {
            @Override
            public void visitField(@NotNull PsiField field) {
                PsiClass containingClass = field.getContainingClass();
                if (containingClass == null || !isSpringComponent(containingClass)) {
                    return;
                }
                PsiAnnotation injection = SpringInspectionUtil.findAnyAnnotation(field.getModifierList(),
                        INJECTION_ANNOTATIONS);
                if (injection == null) {
                    return;
                }
                PsiElement anchor = field.getNameIdentifier();
                reporter.report(anchor, null, ArchitecturePresets.CODE_CONSTRUCTOR_INJECTION,
                        ArchitecturePresets.RULE_CONSTRUCTOR_INJECTION, severity, text);
            }
        };
    }

    private static boolean isSpringComponent(PsiClass psiClass) {
        PsiModifierList modifiers = psiClass.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (String stereotype : STEREOTYPES) {
            if (SpringInspectionUtil.hasAnnotation(modifiers, stereotype, SpringInspectionUtil.shortName(stereotype))) {
                return true;
            }
        }
        return false;
    }
}
