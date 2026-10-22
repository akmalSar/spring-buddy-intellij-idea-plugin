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

/**
 * How the target Spring Boot version is chosen per project.
 *
 * @author Akmal Sarhan
 */
public enum SpringBootTargetMode {

    AUTO("Auto-detect"),

    BOOT_3("Spring Boot 3.x"),

    BOOT_4("Spring Boot 4.x");

    private final String label;

    SpringBootTargetMode(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public static SpringBootTargetMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        for (SpringBootTargetMode mode : values()) {
            if (mode.name().equals(value)) {
                return mode;
            }
        }
        return AUTO;
    }
}
