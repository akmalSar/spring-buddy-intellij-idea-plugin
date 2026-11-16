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

package com.sarhan.springformat.export;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.sarhan.springformat.architecture.ArchitectureProblem;

/**
 * Builds a Markdown report from inspection results, formatted for machine
 * consumption by an AI agent: stable inspection IDs, a metadata header, and a
 * delimited table of occurrences (file, line, message, Done checkbox) per
 * inspection. Each occurrence row is a task the agent marks done by flipping
 * its checkbox from {@code - [ ]} to {@code - [x]}. Purely textual: all data
 * is passed in by the caller, so this class is trivially unit-testable.
 *
 * @author Akmal Sarhan
 */
public final class InspectionMarkdownReport {

    private static final String UNKNOWN_CELL = "unknown";

    static final String PLUGIN_REPOSITORY_URL = "https://github.com/akmalSar/spring-buddy-intellij-idea-plugin";

    static final String FALSE_POSITIVE_NOTE = "False positives: if a finding does not hold because the inspection "
            + "itself is wrong (not because the code is an exception), fix the inspection, not the project code. "
            + "First check whether the workspace contains the Spring Buddy plugin source, usually a directory named "
            + "'spring-buddy-intellij-idea-plugin'. If it does, fix the false positive there: adjust the inspection, "
            + "add a negative test that reproduces the finding, and run the plugin test suite. Otherwise, tell the "
            + "user to fork " + PLUGIN_REPOSITORY_URL + ", fix the inspection with a reproducing test, and submit a "
            + "pull request. In both cases leave the task unchecked and note 'false positive' and the reason in the "
            + "Message cell.\n";

    private InspectionMarkdownReport() {
    }

    /**
     * A single problem occurrence: the file it belongs to (path relative to
     * the project root), the one-based line number ({@code -1} when unknown),
     * the problem message, and the severity the problem was reported with
     * ({@code null} when it is the inspection's level).
     */
    public record Problem(String filePath, int line, String message, String severity) {

        public Problem(String filePath, int line, String message) {
            this(filePath, line, message, null);
        }
    }

    /**
     * All problems of one inspection tool: its stable short name, display
     * name, configured severity level, bundled group path, tool description,
     * and the individual problem occurrences.
     */
    public record InspectionSection(String inspectionId, String inspectionName, String level, String groupPath,
                                    String description, List<Problem> problems) {
    }

    /**
     * Renders the report. Sections without problems are skipped.
     *
     * @param scopeTitle  title of the analysis scope, may be {@code null}
     * @param projectRoot absolute path of the project root used to resolve
     *                    relative file paths, may be {@code null}
     * @param generatedAt formatted generation timestamp, may be {@code null}
     * @param sections    inspection sections in display order
     * @return the Markdown document, never {@code null}
     */
    public static String build(String scopeTitle, String projectRoot, String generatedAt,
                               List<InspectionSection> sections) {
        int totalProblems = 0;
        Set<String> files = new LinkedHashSet<>();
        int sectionCount = 0;
        for (InspectionSection section : sections) {
            if (section.problems().isEmpty()) {
                continue;
            }
            sectionCount++;
            for (Problem problem : section.problems()) {
                totalProblems++;
                if (problem.filePath() != null && !problem.filePath().isBlank()) {
                    files.add(problem.filePath());
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Spring Buddy Inspection Report\n\n");
        sb.append("<!--\n");
        sb.append("Machine-readable export of Spring Buddy inspection results. ");
        sb.append("Each problem is listed under its inspection with a stable inspection ID. ");
        sb.append("Verify a finding by opening the listed file at the listed 1-based line number. ");
        sb.append("Line 'unknown' means the platform could not resolve a line for the descriptor.\n");
        sb.append("\n");
        sb.append("Architecture rule findings are additionally listed in the 'Architecture Violations' section, ");
        sb.append("one per line: '" + ARCHITECTURE_FORMAT + "'. STATUS is 'new' (blocks the commit when ");
        sb.append("SEVERITY is ERROR) or 'baselined' (accepted in .spring-buddy/architecture-baseline.json). ");
        sb.append("Apply the root-cause fix from the fix field; suppressing or working around a check is not a fix.\n");
        sb.append("\n");
        sb.append("Every row in an Occurrences table is a task with a Done checkbox ('- [ ]'). ");
        sb.append("Verify the finding first, fix it second: a task is only marked done after the fix ");
        sb.append("was applied and verified. Mark a task finished by changing its Done cell from ");
        sb.append("'- [ ]' to '- [x]'. Never touch rows already marked '- [x]'.\n");
        sb.append("\n");
        sb.append("Example prompt for an AI agent:\n");
        sb.append("\"Read the report file and work through every task whose Done cell is still '- [ ]', ");
        sb.append("one section at a time, ERROR sections first. For each task: first verify that the ");
        sb.append("finding actually holds — open the file at the given line and confirm the problem the ");
        sb.append("inspection description describes; if it does not hold, leave the box unchecked and ");
        sb.append("state the reason by appending to the row's Message cell, then move on. If the finding ");
        sb.append("holds, apply the fix the description asks for, make the code compile and the related ");
        sb.append("tests pass, then flip that row's Done cell from '- [ ]' to '- [x]'. If you cannot ");
        sb.append("safely fix a task, leave the box unchecked and state the reason in the Message cell ");
        sb.append("instead.\" Substitute the actual report file name before sending.\n");
        sb.append("\n");
        sb.append(FALSE_POSITIVE_NOTE);
        sb.append("-->\n\n");

        sb.append("## Metadata\n");
        sb.append("- Generated: ").append(generatedAt == null || generatedAt.isBlank() ? UNKNOWN_CELL : generatedAt).append('\n');
        sb.append("- Project root: ").append(projectRoot == null || projectRoot.isBlank() ? UNKNOWN_CELL : projectRoot).append('\n');
        sb.append("- Scope: ").append(scopeTitle == null || scopeTitle.isBlank() ? UNKNOWN_CELL : scopeTitle).append('\n');
        sb.append("- Inspections: ").append(sectionCount).append('\n');
        sb.append("- Problems: ").append(totalProblems).append('\n');
        sb.append("- Files affected: ").append(files.size()).append('\n');

        if (totalProblems == 0) {
            sb.append("\nNo problems were reported.\n");
            return sb.toString();
        }

        appendArchitectureViolations(sb, sections);

        for (InspectionSection section : sections) {
            if (section.problems().isEmpty()) {
                continue;
            }
            sb.append("\n---\n\n");
            sb.append("## ");
            sb.append(section.inspectionId() == null || section.inspectionId().isBlank() ? UNKNOWN_CELL : section.inspectionId());
            if (section.inspectionName() != null && !section.inspectionName().isBlank()) {
                sb.append(" — ").append(section.inspectionName());
            }
            sb.append(" [").append(section.level() == null || section.level().isBlank() ? "unspecified" : section.level()).append(']');
            sb.append('\n');

            if (section.groupPath() != null && !section.groupPath().isBlank()) {
                sb.append("\n- Group: ").append(section.groupPath()).append('\n');
            }

            if (section.description() != null && !section.description().isBlank()) {
                sb.append("\n### Description\n\n");
                sb.append(section.description().trim()).append('\n');
            }

            sb.append("\n### Occurrences (").append(section.problems().size()).append(")\n\n");
            sb.append("| File | Line | Message | Done |\n");
            sb.append("| --- | ---: | --- | --- |\n");
            List<Problem> sorted = new ArrayList<>(section.problems());
            sorted.sort(Comparator.comparing((Problem p) -> p.filePath() == null ? "" : p.filePath())
                    .thenComparingInt(Problem::line));
            for (Problem problem : sorted) {
                sb.append("| ").append(cell(problem.filePath()));
                sb.append(" | ").append(problem.line() <= 0 ? UNKNOWN_CELL : String.valueOf(problem.line()));
                sb.append(" | ").append(cell(problem.message()));
                sb.append(" | - [ ] |\n");
            }
        }
        return sb.toString();
    }

    /**
     * Renders the {@code ## Architecture Violations} section: one line per
     * problem whose message carries an architecture code, in the stable format
     * {@code SB-ARCH-NNN | SEVERITY | path/to/File.java:LINE | rule-id | message | fix | STATUS},
     * sorted by code, path and line. A {@code |} inside a field is written
     * as {@code \|}, so splitting a line on {@code " | "} always yields seven
     * fields. {@code fix} is {@code -} when the finding has no fix hint. Nothing is written when there are no
     * architecture problems. The problems also stay in their inspection
     * sections, where they are tracked as tasks.
     */
    static final String ARCHITECTURE_FORMAT =
            "SB-ARCH-NNN | SEVERITY | path/to/File.java:LINE | rule-id | message | fix | STATUS";

    static void appendArchitectureViolations(StringBuilder sb, List<InspectionSection> sections) {
        List<String[]> rows = new ArrayList<>();
        for (InspectionSection section : sections) {
            for (Problem problem : section.problems()) {
                ArchitectureProblem parsed = ArchitectureProblem.parse(problem.message());
                if (parsed == null) {
                    continue;
                }
                String severity = (problem.severity() != null && !problem.severity().isBlank()) ? problem.severity()
                        : section.level();
                String path = (problem.filePath() == null || problem.filePath().isBlank()) ? UNKNOWN_CELL
                        : problem.filePath();
                String line = (problem.line() <= 0) ? UNKNOWN_CELL : String.valueOf(problem.line());
                String fix = parsed.fixHint();
                rows.add(new String[] { parsed.code(), oneLine(severity, "unspecified"), oneLine(path, UNKNOWN_CELL),
                        line, parsed.ruleId(), oneLine(parsed.summary(), UNKNOWN_CELL).replace("|", "\\|"),
                        oneLine(fix, "-").replace("|", "\\|"), parsed.baselined() ? "baselined" : "new" });
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        rows.sort(Comparator.comparing((String[] row) -> row[0]).thenComparing((String[] row) -> row[2])
                .thenComparingInt((String[] row) -> UNKNOWN_CELL.equals(row[3]) ? -1 : Integer.parseInt(row[3]))
                .thenComparing((String[] row) -> row[4]));
        sb.append("\n---\n\n");
        sb.append("## Architecture Violations\n\n");
        sb.append("<!-- Format: ").append(ARCHITECTURE_FORMAT).append(" -->\n\n");
        for (String[] row : rows) {
            sb.append(row[0]).append(" | ").append(row[1]).append(" | ").append(row[2]).append(':').append(row[3])
                    .append(" | ").append(row[4]).append(" | ").append(row[5]).append(" | ").append(row[6])
                    .append(" | ").append(row[7]).append('\n');
        }
    }

    private static String oneLine(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    /**
     * Prepares a value for a Markdown table cell: escapes pipes, collapses
     * line breaks, and falls back to "unknown" for blank values.
     */
    public static String cell(String value) {
        if (value == null) {
            return UNKNOWN_CELL;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return UNKNOWN_CELL;
        }
        return text.replace("|", "\\|").replaceAll("\\s+", " ");
    }

    /**
     * Converts simple HTML fragments (inspection descriptions and problem
     * messages) to readable Markdown-ish text: {@code <code>} becomes
     * backticks, {@code <p>} becomes paragraph breaks, remaining tags are
     * stripped and common entities are decoded.
     */
    public static String toText(String html) {
        if (html == null) {
            return "";
        }
        String text = html
                .replace("\r\n", "\n")
                .replace("<p>\n", "<p>")
                .replace("</p>", "")
                .replace("<p>", "\n\n")
                .replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("</li>", "\n")
                .replace("<li>", "- ")
                .replace("<code>", "`")
                .replace("</code>", "`")
                .replace("<b>", "**")
                .replace("</b>", "**")
                .replace("<strong>", "**")
                .replace("</strong>", "**")
                .replace("<i>", "*")
                .replace("</i>", "*")
                .replace("<em>", "*")
                .replace("</em>", "*")
                .replace("<tt>", "`")
                .replace("</tt>", "`")
                .replaceAll("<[^>]*>", "");
        text = text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        return text.replaceAll("\n{3,}", "\n\n").trim();
    }

    /**
     * Same as {@link #toText(String)} but collapses all line breaks and
     * repeated whitespace into single spaces, for use in one-line messages.
     */
    public static String toSingleLine(String html) {
        return toText(html).replaceAll("\\s+", " ").trim();
    }

    /**
     * Formats the current time for the report header.
     */
    public static String defaultTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
