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

import java.util.List;
import java.util.Set;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */

public final class SpringBuddySettingsSynchronizer {

    private SpringBuddySettingsSynchronizer() {
    }

    public static void syncOpenProjects() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (!project.isDisposed()) {
                    syncProject(project);
                }
            }
        });
    }

    public static void syncProject(@NotNull Project project) {
        InspectionProfileImpl profile = ProjectInspectionProfileManager.getInstance(project).getCurrentProfile();
        syncProfile(project, profile);
        ProjectInspectionProfileManager.getInstance(project).fireProfileChanged(profile);
    }

    public static void syncProfile(@NotNull Project project, @NotNull InspectionProfileImpl profile) {
        SpringBuddySettingsState settings = SpringBuddySettingsState.getInstance();
        HighlightDisplayLevel minimumLevel = SpringBuddySettingsUtil.level(settings.getInspectionLevel());
        Set<String> enabledGroups = settings.getEnabledGroups();
        for (Element tool : SpringBuddySettingsUtil.loadBundledProfileTools()) {
            String toolName = tool.getAttributeValue("class");
            if (toolName == null) {
                continue;
            }
            ScopeToolState state = profile.getToolDefaultState(toolName, project);
            if (state == null) {
                continue;
            }
            HighlightDisplayLevel toolLevel = SpringBuddySettingsUtil.toolLevel(tool);
            boolean enabled = SpringBuddySettingsUtil.isEnabled(toolGroups(tool), enabledGroups)
                    && toolLevel.getSeverity().compareTo(minimumLevel.getSeverity()) >= 0;
            state.setEnabled(enabled);
            state.setLevel(toolLevel);
            ToolsImpl tools = profile.getToolsOrNull(toolName, project);
            if (tools != null) {
                tools.setDefaultState(state.getTool(), enabled, toolLevel);
            }
            HighlightDisplayKey key = state.getTool().getDisplayKey();
            if (key != null) {
                profile.setErrorLevel(key, toolLevel, project);
            }
        }
        profile.profileChanged();
    }

    private static List<String> toolGroups(Element tool) {
        List<String> groups = SpringBuddySettingsUtil.splitGroups(tool.getAttributeValue("groups"));
        return groups.isEmpty() ? List.of("misc") : groups;
    }
}
