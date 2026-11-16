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

import java.util.Locale;

import com.intellij.codeInspection.ProblemHighlightType;
import org.jetbrains.annotations.Nullable;

/**
 * Severity of an architecture rule or preset, as written in
 * {@code .spring-buddy/architecture.json}. {@link #OFF} disables the rule.
 *
 * @author Akmal Sarhan
 */
public enum ArchitectureSeverity {

    OFF(0, "OFF", null),
    WEAK_WARNING(1, "WEAK WARNING", ProblemHighlightType.WEAK_WARNING),
    WARNING(2, "WARNING", ProblemHighlightType.WARNING),
    ERROR(3, "ERROR", ProblemHighlightType.ERROR);

    private final int rank;

    private final String label;

    private final ProblemHighlightType highlightType;

    ArchitectureSeverity(int rank, String label, ProblemHighlightType highlightType) {
        this.rank = rank;
        this.label = label;
        this.highlightType = highlightType;
    }

    /**
     * Parses a severity value case-insensitively: {@code error}, {@code warning},
     * {@code weak_warning} (or {@code weak warning}) and {@code off}.
     *
     * @param value the raw value
     * @return the severity, or {@code null} when the value is unknown
     */
    @Nullable
    public static ArchitectureSeverity parse(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (ArchitectureSeverity severity : values()) {
            if (severity.name().equals(normalized)) {
                return severity;
            }
        }
        return null;
    }

    public boolean isOff() {
        return this == OFF;
    }

    /**
     * Returns this severity, lowered to {@code max} when it is stronger.
     */
    public ArchitectureSeverity capAt(ArchitectureSeverity max) {
        return (this.rank > max.rank) ? max : this;
    }

    public String label() {
        return this.label;
    }

    /**
     * Highlight type used when registering a problem; {@code null} for {@link #OFF}.
     */
    @Nullable
    public ProblemHighlightType highlightType() {
        return this.highlightType;
    }
}
