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

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jetbrains.annotations.Nullable;

/**
 * A package pattern used by architecture rules.
 * <p>
 * Glob syntax (matched against the package name):
 * <ul>
 *   <li>{@code com.acme.domain} &mdash; exactly that package (or exactly that
 *   fully-qualified class name)</li>
 *   <li>{@code com.acme.domain..} &mdash; the package and all subpackages</li>
 *   <li>{@code ..domain..} &mdash; any package containing a {@code domain} segment</li>
 *   <li>{@code com..web} &mdash; {@code com.web}, {@code com.x.web}, {@code com.x.y.web}</li>
 *   <li>{@code com.acme.*} &mdash; {@code *} matches within one package segment</li>
 * </ul>
 * A {@code regex:} prefix switches to a Java regular expression that must
 * match the whole package name.
 *
 * @author Akmal Sarhan
 */
public final class PackagePattern {

    public static final String REGEX_PREFIX = "regex:";

    private static final Pattern GLOB_CHARS = Pattern.compile("[A-Za-z0-9_$.*]+");

    private final String raw;

    private final Pattern pattern;

    private final boolean literal;

    private PackagePattern(String raw, Pattern pattern, boolean literal) {
        this.raw = raw;
        this.pattern = pattern;
        this.literal = literal;
    }

    /**
     * Parses a glob or {@code regex:} pattern.
     *
     * @param raw the pattern text
     * @return the parsed pattern
     * @throws IllegalArgumentException when the pattern is empty or invalid
     */
    public static PackagePattern parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty package pattern");
        }
        String text = raw.trim();
        if (text.startsWith(REGEX_PREFIX)) {
            String regex = text.substring(REGEX_PREFIX.length());
            if (regex.isEmpty()) {
                throw new IllegalArgumentException("empty regex in pattern '" + text + "'");
            }
            try {
                return new PackagePattern(text, Pattern.compile(regex), false);
            }
            catch (PatternSyntaxException ex) {
                throw new IllegalArgumentException("invalid regex in pattern '" + text + "': " + ex.getDescription());
            }
        }
        if (!GLOB_CHARS.matcher(text).matches() || text.contains("...")) {
            throw new IllegalArgumentException("invalid package pattern '" + text + "'");
        }
        boolean literal = !text.contains("*") && !text.contains("..");
        return new PackagePattern(text, Pattern.compile(globToRegex(text)), literal);
    }

    private static String globToRegex(String glob) {
        if ("..".equals(glob)) {
            return ".*";
        }
        StringBuilder regex = new StringBuilder();
        int length = glob.length();
        int i = 0;
        while (i < length) {
            char c = glob.charAt(i);
            if (c == '.' && i + 1 < length && glob.charAt(i + 1) == '.') {
                boolean start = i == 0;
                boolean end = i + 2 == length;
                if (start) {
                    regex.append("(?:.*\\.)?");
                }
                else if (end) {
                    regex.append("(?:\\..*)?");
                }
                else {
                    regex.append("(?:\\..*)?\\.");
                }
                i += 2;
                continue;
            }
            if (c == '.') {
                regex.append("\\.");
            }
            else if (c == '*') {
                regex.append("[^.]*");
            }
            else if (c == '$') {
                regex.append("\\$");
            }
            else {
                regex.append(c);
            }
            i++;
        }
        return regex.toString();
    }

    /**
     * Returns {@code true} when the package matches, or when this is a literal
     * pattern equal to the fully-qualified class name.
     *
     * @param packageName        package of the class (empty for the default package)
     * @param qualifiedClassName fully-qualified class name, may be {@code null}
     */
    public boolean matches(@Nullable String packageName, @Nullable String qualifiedClassName) {
        if (packageName != null && this.pattern.matcher(packageName).matches()) {
            return true;
        }
        return this.literal && this.raw.equals(qualifiedClassName);
    }

    public static boolean anyMatches(List<PackagePattern> patterns, @Nullable String packageName,
                                     @Nullable String qualifiedClassName) {
        for (PackagePattern pattern : patterns) {
            if (pattern.matches(packageName, qualifiedClassName)) {
                return true;
            }
        }
        return false;
    }

    public String raw() {
        return this.raw;
    }

    @Override
    public String toString() {
        return this.raw;
    }
}
