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
import java.util.List;
import java.util.Set;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.assertj.core.api.Assertions;

/**
 * Tests for {@link SpringBuddySettingsState}.
 *
 * @author Akmal Sarhan
 */
public class SpringBuddySettingsStateTests extends BasePlatformTestCase {

    public void testLoadState_existingSettings_preservesDisabledGroups() {
        SpringBuddySettingsState settings = new SpringBuddySettingsState();
        SpringBuddySettingsState.State state = new SpringBuddySettingsState.State();
        state.enabledGroups = new ArrayList<>(List.of("spring-javaformat"));
        state.knownGroups = new ArrayList<>(SpringBuddySettingsUtil.defaultGroupIds());

        settings.loadState(state);

        Assertions.assertThat(settings.getEnabledGroups())
                .as("enabled groups after restart")
                .containsExactly("spring-javaformat");
    }

    public void testLoadState_legacyStateWithoutKnownGroups_addsOnlyNewGroup() {
        SpringBuddySettingsState settings = new SpringBuddySettingsState();
        SpringBuddySettingsState.State state = new SpringBuddySettingsState.State();
        state.enabledGroups = new ArrayList<>(List.of("spring-javaformat"));

        settings.loadState(state);

        Assertions.assertThat(settings.getEnabledGroups())
                .as("legacy state adopts new group and keeps user-disabled groups disabled")
                .containsExactly("spring-javaformat", SpringBuddySettingsUtil.MIGRATION_GROUP_ID);
    }

    public void testLoadState_legacyStateWithNullKnownGroups_addsOnlyNewGroup() {
        SpringBuddySettingsState settings = new SpringBuddySettingsState();
        SpringBuddySettingsState.State state = new SpringBuddySettingsState.State();
        state.enabledGroups = new ArrayList<>(List.of("pitfalls"));
        state.knownGroups = null;

        settings.loadState(state);

        Assertions.assertThat(settings.getEnabledGroups())
                .as("null knownGroups treated as legacy state")
                .containsExactly("pitfalls", SpringBuddySettingsUtil.MIGRATION_GROUP_ID);
    }

    public void testLoadState_knownGroupsPresent_disabledGroupStaysDisabled() {
        SpringBuddySettingsState settings = new SpringBuddySettingsState();
        SpringBuddySettingsState.State state = new SpringBuddySettingsState.State();
        state.enabledGroups = new ArrayList<>(List.of("pitfalls"));
        state.knownGroups = new ArrayList<>(SpringBuddySettingsUtil.defaultGroupIds());

        settings.loadState(state);

        Assertions.assertThat(settings.getEnabledGroups())
                .as("user-disabled group must stay disabled")
                .containsExactly("pitfalls");
    }

    public void testLoadState_knownGroupsMissingNewGroup_addsOnlyNewGroup() {
        SpringBuddySettingsState settings = new SpringBuddySettingsState();
        SpringBuddySettingsState.State state = new SpringBuddySettingsState.State();
        state.enabledGroups = new ArrayList<>(List.of("pitfalls"));
        state.knownGroups = new ArrayList<>(SpringBuddySettingsUtil.defaultGroupIds());
        state.knownGroups.remove(SpringBuddySettingsUtil.MIGRATION_GROUP_ID);

        settings.loadState(state);

        Assertions.assertThat(settings.getEnabledGroups())
                .as("only newly introduced group is adopted")
                .containsExactly("pitfalls", SpringBuddySettingsUtil.MIGRATION_GROUP_ID);
    }

    public void testGetState_disabledGroupThenReload_disabledGroupStaysDisabled() {
        SpringBuddySettingsState settings = new SpringBuddySettingsState();
        settings.setEnabledGroups(Set.of("pitfalls"));
        SpringBuddySettingsState reloaded = new SpringBuddySettingsState();

        reloaded.loadState(settings.getState());

        Assertions.assertThat(reloaded.getEnabledGroups())
                .as("saved state keeps user-disabled groups disabled")
                .containsExactly("pitfalls");
    }
}
