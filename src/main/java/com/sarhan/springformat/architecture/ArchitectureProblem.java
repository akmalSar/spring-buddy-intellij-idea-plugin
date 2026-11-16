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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

/**
 * Stable message format shared by all architecture inspections:
 * <pre>
 * [SB-ARCH-NNN rule-id] What is wrong (detail). Why: reason. Fix: root-cause fix. Suppressing or working around this check is not a fix.
 * </pre>
 * {@code Why:} is omitted when no reason is known. Baselined findings end with
 * {@value #BASELINED_MARKER}. The pre-commit gate shows the message verbatim
 * and the Markdown export parses it back into code, rule id, text and fix hint.
 *
 * @param code   stable code, e.g. {@code SB-ARCH-001}
 * @param ruleId rule id from the configuration or preset
 * @param text   human-readable message
 * @author Akmal Sarhan
 */
public record ArchitectureProblem(String code, String ruleId, String text) {

    /**
     * Closing sentence of every architecture message.
     */
    public static final String NOT_A_FIX = "Suppressing or working around this check is not a fix.";

    public static final String BASELINED_MARKER = " [baselined]";

    private static final String WHY = ". Why: ";

    private static final String FIX = ". Fix: ";

    private static final Pattern MESSAGE = Pattern.compile("^\\[(SB-ARCH-\\d{3}) ([^\\]\\s]+)] (.+)$", Pattern.DOTALL);

    /**
     * Formats a problem message from an already built text.
     */
    public static String format(String code, String ruleId, String text) {
        return "[" + code + " " + ruleId + "] " + text;
    }

    /**
     * Builds the agent-oriented message text.
     *
     * @param what what is wrong, including the offending target
     * @param why  why it matters, may be {@code null}
     * @param fix  root-cause fix
     */
    public static String text(String what, @Nullable String why, String fix) {
        StringBuilder text = new StringBuilder(stripPeriod(what));
        if (why != null && !why.isBlank()) {
            text.append(WHY).append(stripPeriod(why.trim()));
        }
        text.append(FIX).append(stripPeriod(fix.trim())).append(". ").append(NOT_A_FIX);
        return text.toString();
    }

    /**
     * Parses a message produced by {@link #format(String, String, String)}.
     *
     * @return the parsed problem, or {@code null} when the message is not an architecture problem
     */
    @Nullable
    public static ArchitectureProblem parse(@Nullable String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = MESSAGE.matcher(message.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new ArchitectureProblem(matcher.group(1), matcher.group(2), matcher.group(3).trim());
    }

    public String message() {
        return format(this.code, this.ruleId, this.text);
    }

    public boolean baselined() {
        return this.text.endsWith(BASELINED_MARKER.trim());
    }

    /**
     * Text before the fix hint: what is wrong and why.
     */
    public String summary() {
        String body = withoutMarker();
        int fix = body.indexOf(FIX);
        if (fix >= 0) {
            return body.substring(0, fix);
        }
        return stripSuffix(body, " " + NOT_A_FIX);
    }

    /**
     * The root-cause fix, or {@code null} when the message has none.
     */
    @Nullable
    public String fixHint() {
        String body = withoutMarker();
        int fix = body.indexOf(FIX);
        if (fix < 0) {
            return null;
        }
        String hint = stripSuffix(body.substring(fix + FIX.length()), " " + NOT_A_FIX);
        return stripPeriod(hint);
    }

    private String withoutMarker() {
        return stripSuffix(this.text, BASELINED_MARKER.trim()).trim();
    }

    private static String stripSuffix(String value, String suffix) {
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }

    private static String stripPeriod(String value) {
        String trimmed = value.trim();
        boolean sentenceEnd = trimmed.endsWith(".") && !trimmed.endsWith("..");
        return sentenceEnd ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
