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

package com.sarhan.springformat;

import java.util.Collection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * An {@link AnalysisScope} with a custom display name, so the inspection
 * results tab shows a meaningful title (e.g. "Spring Buddy") instead of the
 * generic "Inspections on Selected directories".
 *
 * @author Akmal Sarhan
 */
class NamedAnalysisScope extends AnalysisScope {

    private final @NotNull String displayName;

    NamedAnalysisScope(@NotNull Project project, @NotNull Collection<? extends VirtualFile> files,
            @NotNull String displayName) {
        super(project, files);
        this.displayName = displayName;
    }

    NamedAnalysisScope(@NotNull Project project, @NotNull String displayName) {
        super(project);
        this.displayName = displayName;
    }

    @Override
    public @NotNull String getDisplayName() {
        return this.displayName;
    }

    @Override
    public @NotNull String getShortenName() {
        return this.displayName;
    }

}
