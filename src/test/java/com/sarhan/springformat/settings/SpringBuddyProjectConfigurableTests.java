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
 * Tests for {@link SpringBuddyProjectConfigurable}.
 *
 * @author Akmal Sarhan
 */
public class SpringBuddyProjectConfigurableTests extends BasePlatformTestCase {

    public void testIsModified_afterCreateComponent_withoutChanges_notModified() {
        SpringBuddyProjectConfigurable configurable = new SpringBuddyProjectConfigurable(getProject());

        configurable.createComponent();

        Assertions.assertThat(configurable.isModified())
                .as("fresh component with no selection changes")
                .isFalse();
    }

    public void testApply_selectionChanged_persistsTargetMode() {
        SpringBuddyProjectConfigurable configurable = new SpringBuddyProjectConfigurable(getProject());
        configurable.createComponent();
        configurable.setTargetMode(SpringBootTargetMode.BOOT_4);

        configurable.apply();

        Assertions.assertThat(SpringBuddyProjectSettingsState.getInstance(getProject()).getTargetMode())
                .as("apply persists the selected target mode")
                .isEqualTo(SpringBootTargetMode.BOOT_4);
        Assertions.assertThat(configurable.isModified())
                .as("after apply nothing is modified")
                .isFalse();
    }
}
