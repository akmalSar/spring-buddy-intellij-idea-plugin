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
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/**
 * Parsed {@code .spring-buddy/architecture.json} (schema version 1), or the
 * built-in fallback when the file is missing or malformed.
 * <p>
 * Preset severities follow one rule: without a valid config file a preset
 * runs at its built-in default, capped at {@link ArchitectureSeverity#WARNING}.
 * ERROR is only reachable through an explicit entry in the config file.
 *
 * @param fromFile          {@code true} when a valid config file was loaded
 * @param includeTests      whether test sources are checked
 * @param presets           preset name to configured severity (explicit entries only)
 * @param layers            layer name to package patterns
 * @param rules             custom rules, layer references already expanded
 * @param domainPurityAllow extra allowed types/packages for the domain purity preset
 * @param bannedApis        banned classes, annotations and methods
 * @param presetFixHints    preset name to configured fix hint (explicit entries only)
 * @param suppressible      rule ids and preset names whose findings may be suppressed
 * @param baselinedSeverity severity of baselined findings: WEAK_WARNING or OFF (hidden)
 * @param error             parse error message when the file was malformed, otherwise {@code null}
 * @param style             declared architecture style, {@link ArchitectureStyle#LAYERED} by default
 * @author Akmal Sarhan
 */
public record ArchitectureConfig(boolean fromFile, boolean includeTests, Map<String, ArchitectureSeverity> presets,
                                 Map<String, List<PackagePattern>> layers, List<ArchitectureRule> rules,
                                 List<PackagePattern> domainPurityAllow, List<BannedApi> bannedApis,
                                 Map<String, String> presetFixHints, Set<String> suppressible,
                                 ArchitectureSeverity baselinedSeverity, @Nullable String error,
                                 ArchitectureStyle style) {

    public static final int SUPPORTED_VERSION = 1;

    public static final String PRESET_CONTROLLER_BOUNDARY = "controllerBoundary";

    public static final String PRESET_DOMAIN_PURITY = "domainPurity";

    public static final String PRESET_CONSTRUCTOR_INJECTION = "constructorInjection";

    public static final String PRESET_SUPPRESSION_POLICY = "suppressionPolicy";

    public static final List<String> PRESET_NAMES = List.of(PRESET_CONTROLLER_BOUNDARY, PRESET_DOMAIN_PURITY,
            PRESET_CONSTRUCTOR_INJECTION, PRESET_SUPPRESSION_POLICY);

    private static final ArchitectureConfig PRESETS_ONLY = new ArchitectureConfig(false, false, Map.of(), Map.of(),
            List.of(), List.of(), List.of(), Map.of(), Set.of(), ArchitectureSeverity.WEAK_WARNING, null,
            ArchitectureStyle.LAYERED);

    public ArchitectureConfig {
        presets = Map.copyOf(presets);
        layers = Map.copyOf(layers);
        rules = List.copyOf(rules);
        domainPurityAllow = List.copyOf(domainPurityAllow);
        bannedApis = List.copyOf(bannedApis);
        presetFixHints = Map.copyOf(presetFixHints);
        suppressible = Set.copyOf(suppressible);
    }

    /**
     * Built-in presets only: used when no config file exists.
     */
    public static ArchitectureConfig presetsOnly() {
        return PRESETS_ONLY;
    }

    /**
     * Built-in presets only, remembering why the config file was rejected.
     */
    public static ArchitectureConfig malformed(String error) {
        return new ArchitectureConfig(false, false, Map.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(), Set.of(),
                ArchitectureSeverity.WEAK_WARNING, error, ArchitectureStyle.LAYERED);
    }

    /**
     * Effective severity of a preset: the configured value when the config
     * file sets it, otherwise the built-in default capped at WARNING.
     */
    public ArchitectureSeverity presetSeverity(String presetName) {
        ArchitectureSeverity configured = this.fromFile ? this.presets.get(presetName) : null;
        if (configured != null) {
            return configured;
        }
        return defaultPresetSeverity(presetName, this.style).capAt(ArchitectureSeverity.WARNING);
    }

    public boolean isHexagonal() {
        return this.style == ArchitectureStyle.HEXAGONAL;
    }

    /**
     * Configured fix hint of a preset, or {@code null} to use the built-in one.
     */
    @Nullable
    public String presetFixHint(String presetName) {
        return this.fromFile ? this.presetFixHints.get(presetName) : null;
    }

    /**
     * Returns {@code true} when findings of the given rule id or preset name
     * may be suppressed with {@code @SuppressWarnings} or {@code //noinspection}.
     */
    public boolean allowsSuppression(String ruleIdOrPreset) {
        return this.suppressible.contains(ruleIdOrPreset);
    }

    /**
     * Built-in default severity of a preset when nothing is configured.
     * {@code domainPurity} only runs by default for the hexagonal style: in a
     * layered project the {@code domain} package usually holds JPA entities.
     */
    public static ArchitectureSeverity defaultPresetSeverity(String presetName, ArchitectureStyle style) {
        if (PRESET_CONSTRUCTOR_INJECTION.equals(presetName)) {
            return ArchitectureSeverity.OFF;
        }
        if (PRESET_DOMAIN_PURITY.equals(presetName) && style != ArchitectureStyle.HEXAGONAL) {
            return ArchitectureSeverity.OFF;
        }
        return ArchitectureSeverity.WARNING;
    }

    /**
     * Package patterns of a layer, or an empty list when the layer is undefined.
     */
    public List<PackagePattern> layer(String name) {
        return this.layers.getOrDefault(name, List.of());
    }
}
