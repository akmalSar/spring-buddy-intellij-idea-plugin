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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Akmal Sarhan
 */

@Service(Service.Level.APP)
@State(name = "SpringBuddySettings", storages = @Storage("spring-buddy.xml"))
public final class SpringBuddySettingsState implements PersistentStateComponent<SpringBuddySettingsState.State> {

    private State state = new State();

    public static SpringBuddySettingsState getInstance() {
        return ApplicationManager.getApplication().getService(SpringBuddySettingsState.class);
    }

    @Override
    public @Nullable State getState() {
        this.state.knownGroups = new ArrayList<>(SpringBuddySettingsUtil.defaultGroupIds());
        return this.state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
        if (this.state.enabledGroups == null || this.state.enabledGroups.isEmpty()) {
            this.state.enabledGroups = new ArrayList<>(SpringBuddySettingsUtil.defaultGroupIds());
        }
        if (this.state.inspectionLevel == null || this.state.inspectionLevel.isBlank()) {
            this.state.inspectionLevel = SpringBuddySettingsUtil.DEFAULT_LEVEL;
        }
        migrateGroupIds();
    }

    /**
     * Groups added by newer plugin versions must stay enabled for existing users:
     * a persisted {@code enabledGroups} list that predates a group id would otherwise
     * silently disable the new group. {@code knownGroups} records which group ids the
     * plugin version that wrote the state already knew; any default group missing from
     * that list is newly introduced and gets adopted once. Legacy state written before
     * {@code knownGroups} existed knew every default group except the migration group.
     */
    private void migrateGroupIds() {
        List<String> defaultIds = new ArrayList<>(SpringBuddySettingsUtil.defaultGroupIds());
        List<String> knownGroups = this.state.knownGroups;
        if (knownGroups == null || knownGroups.isEmpty()) {
            knownGroups = new ArrayList<>(defaultIds);
            knownGroups.remove(SpringBuddySettingsUtil.MIGRATION_GROUP_ID);
        }
        for (String groupId : defaultIds) {
            if (!knownGroups.contains(groupId) && !this.state.enabledGroups.contains(groupId)) {
                this.state.enabledGroups.add(groupId);
            }
        }
        this.state.knownGroups = defaultIds;
    }

    public Set<String> getEnabledGroups() {
        return new LinkedHashSet<>(this.state.enabledGroups);
    }

    public void setEnabledGroups(Set<String> enabledGroups) {
        this.state.enabledGroups = new ArrayList<>(enabledGroups);
    }

    public String getInspectionLevel() {
        return this.state.inspectionLevel;
    }

    public void setInspectionLevel(String inspectionLevel) {
        this.state.inspectionLevel = inspectionLevel;
    }

    public boolean isGitCommitCheckEnabled() {
        return this.state.gitCommitCheckEnabled;
    }

    public void setGitCommitCheckEnabled(boolean gitCommitCheckEnabled) {
        this.state.gitCommitCheckEnabled = gitCommitCheckEnabled;
    }

    public static final class State {

        public List<String> enabledGroups = new ArrayList<>(SpringBuddySettingsUtil.defaultGroupIds());

        /**
         * Group ids the plugin version that last wrote this state already knew;
         * defaults missing from this list are newly introduced groups.
         */
        public List<String> knownGroups = new ArrayList<>();

        public String inspectionLevel = SpringBuddySettingsUtil.DEFAULT_LEVEL;

        public boolean gitCommitCheckEnabled = true;
    }
}
