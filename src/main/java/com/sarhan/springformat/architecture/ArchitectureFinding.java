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

package com.sarhan.springformat.architecture;

import org.jetbrains.annotations.Nullable;

/**
 * Common view of anything the architecture engine can report: a dependency
 * rule or a banned API. Provides the stable code, rule id, severity and the
 * texts that make up the problem message.
 *
 * @author Akmal Sarhan
 */
public interface ArchitectureFinding {

    /**
     * Stable code, e.g. {@code SB-ARCH-001}.
     */
    String code();

    /**
     * Rule id from the configuration or preset.
     */
    String id();

    ArchitectureSeverity severity();

    /**
     * Configured reason (the "why" of the message), may be {@code null}.
     */
    @Nullable
    String message();

    /**
     * Configured root-cause fix, may be {@code null}.
     */
    @Nullable
    default String fixHint() {
        return null;
    }

    /**
     * What is wrong, naming the offending target.
     */
    default String what(String target) {
        return "Forbidden dependency on " + target;
    }

    /**
     * Fix used when no {@link #fixHint()} is configured.
     */
    default String defaultFix(String target) {
        return "Move this code into a layer that may use the dependency, or depend on an interface owned by this "
                + "layer and implement it in the layer that may";
    }

    /**
     * Full message text: what, why and the root-cause fix.
     */
    default String text(String target) {
        String fix = fixHint();
        return ArchitectureProblem.text(what(target), message(),
                (fix != null && !fix.isBlank()) ? fix : defaultFix(target));
    }
}
