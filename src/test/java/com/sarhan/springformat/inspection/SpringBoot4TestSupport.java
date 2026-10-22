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

package com.sarhan.springformat.inspection;

import com.intellij.openapi.project.Project;
import com.sarhan.springformat.settings.SpringBootTargetMode;
import com.sarhan.springformat.settings.SpringBuddyProjectSettingsState;

/**
 * Test support: switches the per-project Boot target. Call {@link #reset}
 * from {@code tearDown} because the light test project is reused between tests.
 *
 * @author Akmal Sarhan
 */
public final class SpringBoot4TestSupport {

    private SpringBoot4TestSupport() {
    }

    public static void setBoot4(Project project) {
        SpringBuddyProjectSettingsState.getInstance(project).setTargetMode(SpringBootTargetMode.BOOT_4);
    }

    public static void setBoot3(Project project) {
        SpringBuddyProjectSettingsState.getInstance(project).setTargetMode(SpringBootTargetMode.BOOT_3);
    }

    public static void reset(Project project) {
        SpringBuddyProjectSettingsState.getInstance(project).setTargetMode(SpringBootTargetMode.AUTO);
    }
}
