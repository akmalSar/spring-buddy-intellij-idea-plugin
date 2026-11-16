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
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiNameValuePair;
import com.sarhan.springformat.architecture.ArchitectureConfig;
import com.sarhan.springformat.architecture.ArchitectureConfigService;
import com.sarhan.springformat.architecture.ArchitectureDependencyScanner;
import com.sarhan.springformat.architecture.ArchitecturePresets;
import com.sarhan.springformat.architecture.ArchitectureProblem;
import com.sarhan.springformat.architecture.ArchitectureReporter;
import com.sarhan.springformat.architecture.ArchitectureSeverity;
import org.jetbrains.annotations.NotNull;

/**
 * Suppression policy ({@code SB-ARCH-007}): reports
 * {@code @SuppressWarnings("...")} values and {@code //noinspection} comments
 * that switch off an architecture check, i.e. name an architecture inspection
 * short name (for example {@code DomainPurity}) or a code {@code SB-ARCH-NNN}.
 * A suppression is accepted only when every rule or preset the token hides is
 * marked {@code "allowSuppression": true} in
 * {@code .spring-buddy/architecture.json}. This inspection itself cannot be
 * suppressed.
 *
 * @author Akmal Sarhan
 */
public class SuppressionPolicyInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String SUPPRESS_WARNINGS = "java.lang.SuppressWarnings";

    private static final Pattern NOINSPECTION = Pattern.compile(
            "^//\\s*noinspection\\s+([\\w.\\-]+(?:\\s*,\\s*[\\w.\\-]+)*)");

    private static final Pattern TOKEN = Pattern.compile("[\\w.\\-]+");

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element) {
        return false;
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        ArchitectureConfig config = ArchitectureConfigService.getInstance(holder.getProject()).getConfig();
        ArchitectureSeverity severity = config.presetSeverity(ArchitectureConfig.PRESET_SUPPRESSION_POLICY);
        if (severity.isOff()
                || ArchitectureDependencyScanner.isExcludedTestSource(holder.getFile(), config.includeTests())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        ArchitectureReporter reporter = ArchitectureReporter.create(holder);
        return new JavaElementVisitor() {
            @Override
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                if (!isSuppressWarnings(annotation)) {
                    return;
                }
                for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
                    String name = pair.getAttributeName();
                    if (name != null && !"value".equals(name)) {
                        continue;
                    }
                    for (PsiLiteralExpression literal : literals(pair.getValue())) {
                        if (literal.getValue() instanceof String token) {
                            check(literal, null, token.trim(), config, severity, reporter);
                        }
                    }
                }
            }

            @Override
            public void visitComment(@NotNull PsiComment comment) {
                String text = comment.getText();
                Matcher matcher = NOINSPECTION.matcher(text);
                if (!matcher.find()) {
                    return;
                }
                Matcher token = TOKEN.matcher(matcher.group(1));
                int offset = matcher.start(1);
                while (token.find()) {
                    check(comment, new TextRange(offset + token.start(), offset + token.end()), token.group(),
                            config, severity, reporter);
                }
            }
        };
    }

    private static void check(PsiElement anchor, TextRange range, String token, ArchitectureConfig config,
                              ArchitectureSeverity severity, ArchitectureReporter reporter) {
        Set<String> keys = ArchitecturePresets.suppressedKeys(config, token);
        if (keys == null || keys.isEmpty() || allAllowed(config, keys)) {
            return;
        }
        String fix = ArchitecturePresets.fixHint(config, ArchitectureConfig.PRESET_SUPPRESSION_POLICY,
                "Remove the suppression and fix the violation. For an intended exception add it to the rule's "
                        + "except/allowedIn in .spring-buddy/architecture.json, or record it with the architecture "
                        + "baseline action");
        String text = ArchitectureProblem.text("Suppression of architecture check " + token + " (hides "
                        + String.join(", ", keys) + ")",
                "suppressed findings vanish from the IDE, the pre-commit gate and the export while the "
                        + "violation stays in the code",
                fix);
        reporter.report(anchor, range, ArchitecturePresets.CODE_SUPPRESSION,
                ArchitecturePresets.RULE_SUPPRESSION_POLICY, severity, text);
    }

    private static boolean allAllowed(ArchitectureConfig config, Set<String> keys) {
        for (String key : keys) {
            if (!config.allowsSuppression(key)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSuppressWarnings(PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null) {
            PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
            name = (reference != null) ? reference.getText() : null;
        }
        return SUPPRESS_WARNINGS.equals(name) || "SuppressWarnings".equals(name);
    }

    private static List<PsiLiteralExpression> literals(PsiAnnotationMemberValue value) {
        List<PsiLiteralExpression> literals = new ArrayList<>();
        if (value instanceof PsiLiteralExpression literal) {
            literals.add(literal);
        }
        else if (value instanceof PsiArrayInitializerMemberValue array) {
            for (PsiAnnotationMemberValue initializer : array.getInitializers()) {
                if (initializer instanceof PsiLiteralExpression literal) {
                    literals.add(literal);
                }
            }
        }
        return literals;
    }
}
