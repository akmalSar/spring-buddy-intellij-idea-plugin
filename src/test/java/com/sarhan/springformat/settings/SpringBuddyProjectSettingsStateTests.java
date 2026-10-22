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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.assertj.core.api.Assertions;

/**
 * Tests for {@link SpringBuddyProjectSettingsState}.
 *
 * @author Akmal Sarhan
 */
public class SpringBuddyProjectSettingsStateTests extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        SpringBuddyProjectSettingsState.getInstance(getProject()).setTargetMode(SpringBootTargetMode.AUTO);
        super.tearDown();
    }

    public void testGetTargetMode_noPersistedState_defaultsToAuto() {
        SpringBootTargetMode mode = SpringBuddyProjectSettingsState.getInstance(getProject()).getTargetMode();

        Assertions.assertThat(mode)
                .as("default target mode")
                .isEqualTo(SpringBootTargetMode.AUTO);
    }

    public void testLoadState_invalidPersistedValue_fallsBackToAuto() {
        SpringBuddyProjectSettingsState settings = SpringBuddyProjectSettingsState.getInstance(getProject());
        SpringBuddyProjectSettingsState.State state = new SpringBuddyProjectSettingsState.State();
        state.targetMode = "GARBAGE";

        settings.loadState(state);

        Assertions.assertThat(settings.getTargetMode())
                .as("unknown persisted value normalised to AUTO")
                .isEqualTo(SpringBootTargetMode.AUTO);
    }

    public void testSetTargetMode_roundTrip_persistsValue() {
        SpringBuddyProjectSettingsState settings = SpringBuddyProjectSettingsState.getInstance(getProject());

        settings.setTargetMode(SpringBootTargetMode.BOOT_4);

        Assertions.assertThat(settings.getTargetMode())
                .as("target mode after set")
                .isEqualTo(SpringBootTargetMode.BOOT_4);
        Assertions.assertThat(SpringBootTargetMode.fromValue(settings.getState().targetMode))
                .as("persisted raw value parses back")
                .isEqualTo(SpringBootTargetMode.BOOT_4);
    }

    public void testSetTargetMode_sameValue_doesNotIncrementModificationCount() {
        SpringBuddyProjectSettingsState settings = SpringBuddyProjectSettingsState.getInstance(getProject());
        settings.setTargetMode(SpringBootTargetMode.BOOT_4);
        long before = settings.getModificationCount();

        settings.setTargetMode(SpringBootTargetMode.BOOT_4);

        Assertions.assertThat(settings.getModificationCount())
                .as("unchanged value must not bump modification count")
                .isEqualTo(before);

        settings.setTargetMode(SpringBootTargetMode.AUTO);

        Assertions.assertThat(settings.getModificationCount())
                .as("changed value must bump modification count once")
                .isEqualTo(before + 1);
    }
}
