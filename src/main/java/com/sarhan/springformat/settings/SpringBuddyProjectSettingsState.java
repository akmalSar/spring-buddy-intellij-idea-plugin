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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Per-project Spring Buddy settings, persisted in {@code .idea/spring-buddy.xml}
 * so the whole team shares them via version control.
 *
 * @author Akmal Sarhan
 */
@Service(Service.Level.PROJECT)
@State(name = "SpringBuddyProjectSettings", storages = @Storage("spring-buddy.xml"))
public final class SpringBuddyProjectSettingsState
        implements PersistentStateComponent<SpringBuddyProjectSettingsState.State> {

    private State state = new State();

    private long modificationCount;

    public static SpringBuddyProjectSettingsState getInstance(@NotNull Project project) {
        return project.getService(SpringBuddyProjectSettingsState.class);
    }

    @Override
    public @Nullable State getState() {
        return this.state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
        this.modificationCount++;
    }

    public SpringBootTargetMode getTargetMode() {
        return SpringBootTargetMode.fromValue(this.state.targetMode);
    }

    public void setTargetMode(SpringBootTargetMode mode) {
        SpringBootTargetMode value = (mode == null) ? SpringBootTargetMode.AUTO : mode;
        if (value != SpringBootTargetMode.fromValue(this.state.targetMode)) {
            this.state.targetMode = value.name();
            this.modificationCount++;
        }
    }

    public long getModificationCount() {
        return this.modificationCount;
    }

    public static final class State {

        public String targetMode = SpringBootTargetMode.AUTO.name();
    }
}
