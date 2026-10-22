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
 * Target Spring Boot major version a project wants Spring Buddy to analyse for.
 *
 * @author Akmal Sarhan
 */
public enum SpringBootTarget {

    BOOT_3(3, "Spring Boot 3.x"),

    BOOT_4(4, "Spring Boot 4.x");

    private final int major;

    private final String label;

    SpringBootTarget(int major, String label) {
        this.major = major;
        this.label = label;
    }

    public int major() {
        return this.major;
    }

    public String label() {
        return this.label;
    }

    public boolean isAtLeast(SpringBootTarget other) {
        return this.major >= other.major;
    }

    /**
     * Maps a detected Boot major version to the nearest supported target.
     * Unknown or pre-3 versions fall back to Boot 3, anything from 4 up to Boot 4
     * so that a future Boot 5 still keeps the Boot 4 inspections active.
     */
    public static SpringBootTarget fromMajor(int major) {
        if (major >= BOOT_4.major) {
            return BOOT_4;
        }
        return BOOT_3;
    }

    public static SpringBootTarget fromVersionString(String version) {
        int major = majorOf(version);
        return (major > 0) ? fromMajor(major) : BOOT_3;
    }

    static int majorOf(String version) {
        if (version == null || version.isBlank()) {
            return 0;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            if (Character.isDigit(c)) {
                digits.append(c);
            }
            else if (!digits.isEmpty() || !Character.isWhitespace(c)) {
                break;
            }
        }
        try {
            return Integer.parseInt(digits.toString());
        }
        catch (NumberFormatException ex) {
            return 0;
        }
    }
}
