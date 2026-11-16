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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Parses {@code .spring-buddy/architecture.json}. Pure: no IDE services, never
 * throws. Any structural problem rejects the whole file and yields
 * {@link ArchitectureConfig#malformed(String)} so a half-understood
 * configuration never produces findings.
 *
 * @author Akmal Sarhan
 */
public final class ArchitectureConfigParser {

    /**
     * Code used for every rule declared in the configuration file.
     */
    public static final String CUSTOM_RULE_CODE = "SB-ARCH-001";

    private static final Pattern SYMBOL = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+(?:#[A-Za-z_$][A-Za-z0-9_$]*)?");

    private static final Pattern RULE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private ArchitectureConfigParser() {
    }

    /**
     * Parses the given JSON text.
     *
     * @param json the file content
     * @return the parsed configuration, or a malformed fallback carrying the error
     */
    public static ArchitectureConfig parse(String json) {
        try {
            return parseStrict(json);
        }
        catch (ConfigException ex) {
            return ArchitectureConfig.malformed(ex.getMessage());
        }
        catch (RuntimeException ex) {
            String detail = ex.getMessage();
            return ArchitectureConfig.malformed("invalid JSON" + ((detail != null) ? ": " + detail : ""));
        }
    }

    private static ArchitectureConfig parseStrict(String json) {
        if (json == null || json.isBlank()) {
            throw new ConfigException("file is empty");
        }
        JsonElement rootElement = JsonParser.parseString(json);
        if (!rootElement.isJsonObject()) {
            throw new ConfigException("top-level value must be a JSON object");
        }
        JsonObject root = rootElement.getAsJsonObject();

        JsonElement version = root.get("version");
        if (version == null || !isNumber(version)) {
            throw new ConfigException("'version' is required and must be a number");
        }
        if (version.getAsDouble() != ArchitectureConfig.SUPPORTED_VERSION) {
            throw new ConfigException("unsupported version " + version.getAsString() + " (supported: "
                    + ArchitectureConfig.SUPPORTED_VERSION + ")");
        }

        boolean includeTests = false;
        JsonElement includeTestsElement = root.get("includeTests");
        if (includeTestsElement != null && !includeTestsElement.isJsonNull()) {
            if (!isBoolean(includeTestsElement)) {
                throw new ConfigException("'includeTests' must be a boolean");
            }
            includeTests = includeTestsElement.getAsBoolean();
        }

        ArchitectureStyle style = ArchitectureStyle.LAYERED;
        JsonElement styleElement = root.get("style");
        if (styleElement != null && !styleElement.isJsonNull()) {
            String value = string(styleElement, "style", true);
            style = ArchitectureStyle.parse(value);
            if (style == null) {
                throw new ConfigException("style: unknown style '" + value + "' (use layered or hexagonal)");
            }
        }

        Map<String, String> presetFixHints = new LinkedHashMap<>();
        Set<String> suppressible = new HashSet<>();
        Map<String, ArchitectureSeverity> presets = parsePresets(root.get("presets"), presetFixHints, suppressible);
        Map<String, List<PackagePattern>> layers = parseLayers(root.get("layers"));
        List<ArchitectureRule> rules = parseRules(root.get("rules"), layers, suppressible);
        List<PackagePattern> allow = parsePatternList(root.get("domainPurityAllow"), "domainPurityAllow", Map.of());
        Set<String> ids = new HashSet<>();
        for (ArchitectureRule rule : rules) {
            ids.add(rule.id());
        }
        List<BannedApi> bannedApis = parseBannedApis(root.get("bannedApis"), layers, ids, suppressible);

        ArchitectureSeverity baselinedSeverity = ArchitectureSeverity.WEAK_WARNING;
        JsonElement baselinedElement = root.get("baselinedSeverity");
        if (baselinedElement != null && !baselinedElement.isJsonNull()) {
            baselinedSeverity = severity(baselinedElement, "baselinedSeverity");
            if (baselinedSeverity != ArchitectureSeverity.WEAK_WARNING && baselinedSeverity != ArchitectureSeverity.OFF) {
                throw new ConfigException("'baselinedSeverity' must be weak_warning or off");
            }
        }

        return new ArchitectureConfig(true, includeTests, presets, layers, rules, allow, bannedApis, presetFixHints,
                suppressible, baselinedSeverity, null, style);
    }

    /**
     * A preset value is a severity string or an object
     * {@code { "severity": ..., "fixHint": ..., "allowSuppression": ... }}.
     */
    private static Map<String, ArchitectureSeverity> parsePresets(JsonElement element, Map<String, String> fixHints,
                                                                  Set<String> suppressible) {
        Map<String, ArchitectureSeverity> presets = new LinkedHashMap<>();
        if (element == null || element.isJsonNull()) {
            return presets;
        }
        if (!element.isJsonObject()) {
            throw new ConfigException("'presets' must be an object");
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String name = entry.getKey();
            if (!ArchitectureConfig.PRESET_NAMES.contains(name)) {
                throw new ConfigException("unknown preset '" + name + "' (known: "
                        + String.join(", ", ArchitectureConfig.PRESET_NAMES) + ")");
            }
            String where = "presets." + name;
            if (!entry.getValue().isJsonObject()) {
                presets.put(name, severity(entry.getValue(), where));
                continue;
            }
            JsonObject preset = entry.getValue().getAsJsonObject();
            JsonElement severityElement = preset.get("severity");
            if (severityElement != null && !severityElement.isJsonNull()) {
                presets.put(name, severity(severityElement, where + ".severity"));
            }
            String fixHint = string(preset.get("fixHint"), where + ".fixHint", false);
            if (fixHint != null && !fixHint.isEmpty()) {
                fixHints.put(name, fixHint);
            }
            if (allowSuppression(preset, where)) {
                suppressible.add(name);
            }
        }
        return presets;
    }

    private static boolean allowSuppression(JsonObject object, String where) {
        JsonElement element = object.get("allowSuppression");
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (!isBoolean(element)) {
            throw new ConfigException(where + ".allowSuppression must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static Map<String, List<PackagePattern>> parseLayers(JsonElement element) {
        Map<String, List<PackagePattern>> layers = new LinkedHashMap<>();
        if (element == null || element.isJsonNull()) {
            return layers;
        }
        if (!element.isJsonObject()) {
            throw new ConfigException("'layers' must be an object");
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String name = entry.getKey();
            if (name.isBlank()) {
                throw new ConfigException("layer names must not be empty");
            }
            List<PackagePattern> patterns = parsePatternList(entry.getValue(), "layers." + name, Map.of());
            if (patterns.isEmpty()) {
                throw new ConfigException("layer '" + name + "' has no package patterns");
            }
            layers.put(name, patterns);
        }
        return layers;
    }

    private static List<ArchitectureRule> parseRules(JsonElement element, Map<String, List<PackagePattern>> layers,
                                                     Set<String> suppressible) {
        List<ArchitectureRule> rules = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return rules;
        }
        if (!element.isJsonArray()) {
            throw new ConfigException("'rules' must be an array");
        }
        Set<String> ids = new HashSet<>();
        int index = 0;
        for (JsonElement ruleElement : element.getAsJsonArray()) {
            String where = "rules[" + index + "]";
            if (!ruleElement.isJsonObject()) {
                throw new ConfigException(where + " must be an object");
            }
            JsonObject rule = ruleElement.getAsJsonObject();
            String id = string(rule.get("id"), where + ".id", true);
            if (!RULE_ID.matcher(id).matches()) {
                throw new ConfigException(where + ".id '" + id + "' may only contain letters, digits, '.', '_' and '-'");
            }
            if (!ids.add(id)) {
                throw new ConfigException("duplicate rule id '" + id + "'");
            }
            where = "rule '" + id + "'";
            List<PackagePattern> from = parsePatternList(rule.get("from"), where + ".from", layers);
            List<PackagePattern> forbidden = parsePatternList(rule.get("mustNotDependOn"), where + ".mustNotDependOn",
                    layers);
            if (from.isEmpty()) {
                throw new ConfigException(where + ".from is required");
            }
            if (forbidden.isEmpty()) {
                throw new ConfigException(where + ".mustNotDependOn is required");
            }
            JsonElement severityElement = rule.get("severity");
            ArchitectureSeverity severity = (severityElement == null || severityElement.isJsonNull())
                    ? ArchitectureSeverity.WARNING : severity(severityElement, where + ".severity");
            String message = string(rule.get("message"), where + ".message", false);
            String fixHint = string(rule.get("fixHint"), where + ".fixHint", false);
            List<PackagePattern> except = parsePatternList(rule.get("except"), where + ".except", layers);
            rules.add(new ArchitectureRule(CUSTOM_RULE_CODE, id, from, forbidden, severity, message, except,
                    fixHint));
            if (allowSuppression(rule, where)) {
                suppressible.add(id);
            }
            index++;
        }
        return rules;
    }

    private static List<BannedApi> parseBannedApis(JsonElement element, Map<String, List<PackagePattern>> layers,
                                                   Set<String> ids, Set<String> suppressible) {
        List<BannedApi> apis = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return apis;
        }
        if (!element.isJsonArray()) {
            throw new ConfigException("'bannedApis' must be an array");
        }
        int index = 0;
        for (JsonElement apiElement : element.getAsJsonArray()) {
            String where = "bannedApis[" + index + "]";
            if (!apiElement.isJsonObject()) {
                throw new ConfigException(where + " must be an object");
            }
            JsonObject api = apiElement.getAsJsonObject();
            String id = string(api.get("id"), where + ".id", true);
            if (!RULE_ID.matcher(id).matches()) {
                throw new ConfigException(where + ".id '" + id + "' may only contain letters, digits, '.', '_' and '-'");
            }
            if (!ids.add(id)) {
                throw new ConfigException("duplicate rule id '" + id + "'");
            }
            where = "banned API '" + id + "'";
            String symbol = string(api.get("symbol"), where + ".symbol", true);
            if (!SYMBOL.matcher(symbol).matches()) {
                throw new ConfigException(where + ".symbol '" + symbol
                        + "' must be a fully-qualified type (a.b.Type) or method (a.b.Type#method)");
            }
            List<PackagePattern> allowedIn = parsePatternList(api.get("allowedIn"), where + ".allowedIn", layers);
            JsonElement severityElement = api.get("severity");
            ArchitectureSeverity severity = (severityElement == null || severityElement.isJsonNull())
                    ? ArchitectureSeverity.WARNING : severity(severityElement, where + ".severity");
            String message = string(api.get("message"), where + ".message", false);
            String fixHint = string(api.get("fixHint"), where + ".fixHint", false);
            apis.add(new BannedApi(id, symbol, allowedIn, severity, message, fixHint));
            if (allowSuppression(api, where)) {
                suppressible.add(id);
            }
            index++;
        }
        return apis;
    }

    /**
     * Accepts a single string or an array of strings. Each entry is a layer
     * name (when {@code layers} defines it) or a package pattern.
     */
    private static List<PackagePattern> parsePatternList(JsonElement element, String where,
                                                         Map<String, List<PackagePattern>> layers) {
        List<PackagePattern> patterns = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return patterns;
        }
        List<JsonElement> entries = new ArrayList<>();
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            array.forEach(entries::add);
        }
        else {
            entries.add(element);
        }
        for (JsonElement entry : entries) {
            String value = string(entry, where, true);
            List<PackagePattern> layer = layers.get(value);
            if (layer != null) {
                patterns.addAll(layer);
                continue;
            }
            try {
                patterns.add(PackagePattern.parse(value));
            }
            catch (IllegalArgumentException ex) {
                throw new ConfigException(where + ": " + ex.getMessage());
            }
        }
        return patterns;
    }

    private static ArchitectureSeverity severity(JsonElement element, String where) {
        String value = string(element, where, true);
        ArchitectureSeverity severity = ArchitectureSeverity.parse(value);
        if (severity == null) {
            throw new ConfigException(where + ": unknown severity '" + value
                    + "' (use error, warning, weak_warning or off)");
        }
        return severity;
    }

    private static String string(JsonElement element, String where, boolean required) {
        if (element == null || element.isJsonNull()) {
            if (required) {
                throw new ConfigException(where + " is required");
            }
            return null;
        }
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            throw new ConfigException(where + " must be a string");
        }
        String value = primitive.getAsString().trim();
        if (required && value.isEmpty()) {
            throw new ConfigException(where + " must not be empty");
        }
        return value;
    }

    private static boolean isNumber(JsonElement element) {
        return element instanceof JsonPrimitive primitive && primitive.isNumber();
    }

    private static boolean isBoolean(JsonElement element) {
        return element instanceof JsonPrimitive primitive && primitive.isBoolean();
    }

    private static final class ConfigException extends RuntimeException {

        private ConfigException(String message) {
            super(message);
        }
    }
}
