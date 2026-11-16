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

package com.sarhan.springformat.settings;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

/**
 * @author Akmal Sarhan
 */

public final class SpringBuddySettingsUtil {

    public static final String DEFAULT_LEVEL = "WARNING";

    /**
     * Inspections gated on a Spring Boot 4 target; toggled per project via
     * {@link SpringBootVersionResolver}.
     */
    public static final String MIGRATION_GROUP_ID = "spring-boot 3 -> 4 migration";

    /**
     * Architecture rules ({@code SB-ARCH-*}). Tools in this group are registered
     * at WARNING but report per-problem severities from
     * {@code .spring-buddy/architecture.json}, so they are exempt from the
     * minimum-level filter and the commit gate inspects their problems
     * individually.
     */
    public static final String ARCHITECTURE_GROUP_ID = "architecture";

    private static final String PROFILE_RESOURCE = "/inspection/spring-format-builtin.xml";

    public static final List<String> LEVELS = List.of("ERROR", "WARNING", "WEAK WARNING", "INFO", "INFORMATION");

    private static final List<InspectionGroup> GROUPS = List.of(
            new InspectionGroup("spring-javaformat", "1. spring-javaformat", "Spring Java Format"),
            new InspectionGroup("pitfalls", "2. pitfalls", "Pitfalls"),
            new InspectionGroup("lombok", "3. Lombok", "Lombok"),
            new InspectionGroup("misc", "4. Misc Java/Spring quality", "Misc Java/Spring quality"),
            new InspectionGroup("mockito", "Mockito", "Mockito"),
            new InspectionGroup("hibernate migration", "hibernate migration", "hibernate migration"),
            new InspectionGroup("spring-boot 2 -> 3 migration", "spring-boot 2 -> 3 migration", "spring-boot 2 -> 3 migration"),
            new InspectionGroup(MIGRATION_GROUP_ID, MIGRATION_GROUP_ID, MIGRATION_GROUP_ID),
            new InspectionGroup("async-concurrency", "Async / concurrency", "Async / concurrency"),
            new InspectionGroup(ARCHITECTURE_GROUP_ID, "Architecture", "Architecture"));

    private SpringBuddySettingsUtil() {
    }

    public static List<InspectionGroup> groups() {
        return GROUPS;
    }

    public static Set<String> defaultGroupIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (InspectionGroup group : GROUPS) {
            ids.add(group.id());
        }
        return ids;
    }

    public static List<String> splitGroups(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter((String group) -> !group.isEmpty())
                .toList();
    }

    public static boolean isEnabled(List<String> groupIds, Set<String> enabledGroups) {
        for (String groupId : groupIds) {
            if (enabledGroups.contains(groupId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when the bundled profile entry belongs to the architecture group.
     */
    public static boolean isArchitectureTool(Element tool) {
        return splitGroups(tool.getAttributeValue("groups")).contains(ARCHITECTURE_GROUP_ID);
    }

    /**
     * Returns {@code true} when the inspection with the given short name is an
     * architecture tool in the bundled profile.
     */
    public static boolean isArchitectureTool(String shortName) {
        return ArchitectureTools.NAMES.contains(shortName);
    }

    /**
     * Lazily computed short names of the bundled architecture tools; the
     * bundled profile is a read-only resource, so it is parsed only once.
     */
    private static final class ArchitectureTools {

        private static final Set<String> NAMES = load();

        private static Set<String> load() {
            Set<String> names = new LinkedHashSet<>();
            for (Element tool : loadBundledProfileTools()) {
                String name = tool.getAttributeValue("class");
                if (name != null && isArchitectureTool(tool)) {
                    names.add(name);
                }
            }
            return Set.copyOf(names);
        }
    }

    public static HighlightDisplayLevel level(String levelName) {
        if ("INFORMATION".equals(levelName)) {
            return HighlightDisplayLevel.find(HighlightSeverity.INFORMATION);
        }
        HighlightDisplayLevel level = HighlightDisplayLevel.find(levelName);
        return (level != null) ? level : HighlightDisplayLevel.WARNING;
    }

    public static HighlightDisplayLevel toolLevel(Element tool) {
        HighlightDisplayLevel level = level(tool.getAttributeValue("level"));
        return (level != null) ? level : HighlightDisplayLevel.WARNING;
    }

    public static boolean isAtLeastLevel(Element tool, HighlightDisplayLevel minimumLevel) {
        return toolLevel(tool).getSeverity().compareTo(minimumLevel.getSeverity()) >= 0;
    }

    public static List<Element> loadBundledProfileTools() {
        try (InputStream stream = SpringBuddySettingsUtil.class.getResourceAsStream(PROFILE_RESOURCE)) {
            if (stream == null) {
                return List.of();
            }
            return JDOMUtil.load(stream).getChildren("inspection_tool");
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static Map<String, Boolean> defaultSelections() {
        Map<String, Boolean> selections = new LinkedHashMap<>();
        for (InspectionGroup group : GROUPS) {
            selections.put(group.id(), true);
        }
        return selections;
    }

    public record InspectionGroup(String id, String xmlName, String label) {
    }
}
