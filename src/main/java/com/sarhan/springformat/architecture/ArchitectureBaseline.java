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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

/**
 * Parsed {@code .spring-buddy/architecture-baseline.json}: known, accepted
 * architecture violations. Entries are keyed by a stable fingerprint (see
 * {@link ArchitectureFingerprint}), never by line number. {@code count} is the
 * number of identical findings accepted for that fingerprint; any finding
 * beyond it is new. Pure: no IDE services, never throws.
 *
 * @param entries baseline entries
 * @param error   parse error, or {@code null}
 * @author Akmal Sarhan
 */
public record ArchitectureBaseline(List<Entry> entries, @Nullable String error) {

    public static final int SUPPORTED_VERSION = 1;

    private static final ArchitectureBaseline EMPTY = new ArchitectureBaseline(List.of(), null);

    public ArchitectureBaseline {
        entries = List.copyOf(entries);
    }

    /**
     * One accepted violation group.
     *
     * @param code        stable code, e.g. {@code SB-ARCH-001}
     * @param rule        rule id
     * @param file        project-relative path with {@code /} separators
     * @param fingerprint stable fingerprint
     * @param count       number of accepted identical findings, at least 1
     */
    public record Entry(String code, String rule, String file, String fingerprint, int count) {
    }

    public static ArchitectureBaseline empty() {
        return EMPTY;
    }

    /**
     * Builds a baseline from current findings, merging identical fingerprints.
     */
    public static ArchitectureBaseline of(List<Entry> findings) {
        Map<String, Entry> merged = new LinkedHashMap<>();
        for (Entry finding : findings) {
            merged.merge(finding.fingerprint(), finding, (Entry a, Entry b) -> new Entry(a.code(), a.rule(), a.file(),
                    a.fingerprint(), a.count() + b.count()));
        }
        List<Entry> sorted = new ArrayList<>(merged.values());
        sorted.sort(Comparator.comparing(Entry::file).thenComparing(Entry::code).thenComparing(Entry::rule)
                .thenComparing(Entry::fingerprint));
        return new ArchitectureBaseline(sorted, null);
    }

    /**
     * Number of accepted findings for a fingerprint; 0 when unknown.
     */
    public int count(String fingerprint) {
        for (Entry entry : this.entries) {
            if (entry.fingerprint().equals(fingerprint)) {
                return entry.count();
            }
        }
        return 0;
    }

    /**
     * Entries (or parts of their count) that no longer match a current finding.
     * The returned entries carry the stale count.
     */
    public List<Entry> staleEntries(ArchitectureBaseline current) {
        Map<String, Integer> currentCounts = new HashMap<>();
        for (Entry entry : current.entries()) {
            currentCounts.merge(entry.fingerprint(), entry.count(), Integer::sum);
        }
        List<Entry> stale = new ArrayList<>();
        for (Entry entry : this.entries) {
            int remaining = entry.count() - currentCounts.getOrDefault(entry.fingerprint(), 0);
            if (remaining > 0) {
                stale.add(new Entry(entry.code(), entry.rule(), entry.file(), entry.fingerprint(), remaining));
            }
        }
        return stale;
    }

    /**
     * Number of current findings not covered by this baseline.
     */
    public int newFindingCount(ArchitectureBaseline current) {
        int added = 0;
        for (Entry entry : current.entries()) {
            added += Math.max(0, entry.count() - count(entry.fingerprint()));
        }
        return added;
    }

    /**
     * Serializes deterministically: sorted entries, fixed key order, two-space
     * indent, trailing newline, {@code count} only when greater than 1.
     */
    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", SUPPORTED_VERSION);
        JsonArray violations = new JsonArray();
        for (Entry entry : of(this.entries).entries()) {
            JsonObject item = new JsonObject();
            item.addProperty("code", entry.code());
            item.addProperty("rule", entry.rule());
            item.addProperty("file", entry.file());
            item.addProperty("fingerprint", entry.fingerprint());
            if (entry.count() > 1) {
                item.addProperty("count", entry.count());
            }
            violations.add(item);
        }
        root.add("violations", violations);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n";
    }

    /**
     * Parses baseline JSON. A malformed file yields an empty baseline with an
     * error, so every violation is treated as new (strict, never lenient).
     */
    public static ArchitectureBaseline parse(@Nullable String json) {
        try {
            return parseStrict(json);
        }
        catch (RuntimeException ex) {
            String detail = ex.getMessage();
            return new ArchitectureBaseline(List.of(), (detail != null) ? detail : "invalid baseline");
        }
    }

    private static ArchitectureBaseline parseStrict(@Nullable String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("file is empty");
        }
        JsonElement rootElement = JsonParser.parseString(json);
        if (!rootElement.isJsonObject()) {
            throw new IllegalArgumentException("top-level value must be a JSON object");
        }
        JsonObject root = rootElement.getAsJsonObject();
        JsonElement version = root.get("version");
        if (!(version instanceof JsonPrimitive primitive) || !primitive.isNumber()
                || version.getAsDouble() != SUPPORTED_VERSION) {
            throw new IllegalArgumentException("'version' must be " + SUPPORTED_VERSION);
        }
        JsonElement violations = root.get("violations");
        if (violations == null || violations.isJsonNull()) {
            return EMPTY;
        }
        if (!violations.isJsonArray()) {
            throw new IllegalArgumentException("'violations' must be an array");
        }
        List<Entry> entries = new ArrayList<>();
        int index = 0;
        for (JsonElement element : violations.getAsJsonArray()) {
            String where = "violations[" + index++ + "]";
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(where + " must be an object");
            }
            JsonObject item = element.getAsJsonObject();
            int count = 1;
            JsonElement countElement = item.get("count");
            if (countElement != null && !countElement.isJsonNull()) {
                if (!(countElement instanceof JsonPrimitive countPrimitive) || !countPrimitive.isNumber()
                        || countElement.getAsInt() < 1) {
                    throw new IllegalArgumentException(where + ".count must be a positive number");
                }
                count = countElement.getAsInt();
            }
            entries.add(new Entry(string(item, "code", where), string(item, "rule", where), string(item, "file", where),
                    string(item, "fingerprint", where), count));
        }
        return new ArchitectureBaseline(of(entries).entries(), null);
    }

    private static String string(JsonObject item, String key, String where) {
        JsonElement element = item.get(key);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()
                || primitive.getAsString().isBlank()) {
            throw new IllegalArgumentException(where + "." + key + " is required and must be a string");
        }
        return primitive.getAsString().trim();
    }
}
