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

package com.sarhan.springformat.architecture;

import java.util.HashMap;
import java.util.Map;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Single entry point for registering architecture problems. Applies the
 * baseline from {@code .spring-buddy/architecture-baseline.json}: the first
 * {@code count} findings with a baselined fingerprint in a file are reported
 * as WEAK WARNING with the {@value ArchitectureProblem#BASELINED_MARKER} marker
 * (or hidden when {@code baselinedSeverity} is {@code off}); all other
 * findings keep their configured severity, so only new violations block the
 * pre-commit gate.
 * <p>
 * Create one reporter per {@code buildVisitor} call, i.e. per inspected file.
 *
 * @author Akmal Sarhan
 */
public final class ArchitectureReporter {

    private final ProblemsHolder holder;

    private final ArchitectureBaseline baseline;

    private final ArchitectureSeverity baselinedSeverity;

    private final Map<String, Integer> seen = new HashMap<>();

    private ArchitectureReporter(ProblemsHolder holder, ArchitectureBaseline baseline,
                                 ArchitectureSeverity baselinedSeverity) {
        this.holder = holder;
        this.baseline = baseline;
        this.baselinedSeverity = baselinedSeverity;
    }

    public static ArchitectureReporter create(@NotNull ProblemsHolder holder) {
        ArchitectureConfigService service = ArchitectureConfigService.getInstance(holder.getProject());
        ArchitectureBaseline baseline = service.isBaselineSuspended() ? ArchitectureBaseline.empty()
                : service.getBaseline();
        return new ArchitectureReporter(holder, baseline, service.getConfig().baselinedSeverity());
    }

    /**
     * Registers a problem.
     *
     * @param anchor   element to highlight
     * @param range    range within {@code anchor}, or {@code null} for the whole element
     * @param code     stable code
     * @param ruleId   rule id
     * @param severity configured severity; OFF registers nothing
     * @param text     message text, see {@link ArchitectureProblem#text(String, String, String)}
     */
    public void report(@NotNull PsiElement anchor, @Nullable TextRange range, @NotNull String code,
                       @NotNull String ruleId, @NotNull ArchitectureSeverity severity, @NotNull String text) {
        if (severity.isOff()) {
            return;
        }
        ArchitectureSeverity effective = severity;
        String message = ArchitectureProblem.format(code, ruleId, text);
        if (!this.baseline.entries().isEmpty()) {
            String fingerprint = ArchitectureFingerprint.of(anchor, range, code, ruleId).fingerprint();
            int occurrence = this.seen.merge(fingerprint, 1, Integer::sum);
            if (occurrence <= this.baseline.count(fingerprint)) {
                if (this.baselinedSeverity.isOff()) {
                    return;
                }
                effective = ArchitectureSeverity.WEAK_WARNING;
                message += ArchitectureProblem.BASELINED_MARKER;
            }
        }
        ProblemHighlightType type = effective.highlightType();
        if (type == null) {
            return;
        }
        if (range == null) {
            this.holder.registerProblem(anchor, message, type);
        }
        else {
            this.holder.registerProblem(anchor, message, type, range);
        }
    }
}
