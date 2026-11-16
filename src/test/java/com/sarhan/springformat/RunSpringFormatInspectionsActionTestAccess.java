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

import java.util.List;
import java.util.Map;

import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Exposes the package-private pre-commit gate entry point to tests in other packages.
 *
 * @author Akmal Sarhan
 */
public final class RunSpringFormatInspectionsActionTestAccess {

    private RunSpringFormatInspectionsActionTestAccess() {
    }

    public static Map<String, List<String>> collect(Project project, VirtualFile file, ScopeToolState state) {
        return RunSpringFormatInspectionsAction.collectErrorInspections(project, new VirtualFile[] { file },
                List.of(state));
    }
}
