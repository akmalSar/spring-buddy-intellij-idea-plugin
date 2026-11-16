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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

/**
 * Built-in architecture presets: stable codes, rule ids and the rule sets of
 * presets that run on {@link ArchitectureDependencyScanner}.
 *
 * <table>
 *   <caption>Codes</caption>
 *   <tr><td>{@code SB-ARCH-001}</td><td>custom rule from the config file</td></tr>
 *   <tr><td>{@code SB-ARCH-002}</td><td>controller injects a repository</td></tr>
 *   <tr><td>{@code SB-ARCH-003}</td><td>JPA entity in a controller endpoint signature</td></tr>
 *   <tr><td>{@code SB-ARCH-004}</td><td>domain purity</td></tr>
 *   <tr><td>{@code SB-ARCH-005}</td><td>field injection instead of constructor injection</td></tr>
 *   <tr><td>{@code SB-ARCH-006}</td><td>banned API used outside its allowed packages</td></tr>
 *   <tr><td>{@code SB-ARCH-007}</td><td>suppression of an architecture check</td></tr>
 * </table>
 *
 * @author Akmal Sarhan
 */
public final class ArchitecturePresets {

    public static final String CODE_CUSTOM_RULE = ArchitectureConfigParser.CUSTOM_RULE_CODE;

    public static final String CODE_CONTROLLER_REPOSITORY = "SB-ARCH-002";

    public static final String CODE_CONTROLLER_ENTITY = "SB-ARCH-003";

    public static final String CODE_DOMAIN_PURITY = "SB-ARCH-004";

    public static final String CODE_CONSTRUCTOR_INJECTION = "SB-ARCH-005";

    public static final String CODE_BANNED_API = "SB-ARCH-006";

    public static final String CODE_SUPPRESSION = "SB-ARCH-007";

    public static final String RULE_SUPPRESSION_POLICY = "suppression-policy";

    /**
     * Inspection short name to the stable code it reports.
     */
    public static final Map<String, String> TOOL_CODES = Map.of(
            "ForbiddenPackageDependency", ArchitectureConfigParser.CUSTOM_RULE_CODE,
            "SpringControllerRepositoryInjection", "SB-ARCH-002",
            "SpringControllerEntityReturn", "SB-ARCH-003",
            "SpringControllerEntityParameter", "SB-ARCH-003",
            "DomainPurity", "SB-ARCH-004",
            "SpringConstructorInjection", "SB-ARCH-005",
            "BannedApi", "SB-ARCH-006",
            "SuppressionPolicy", "SB-ARCH-007");

    public static final String RULE_CONTROLLER_REPOSITORY = "controller-repository-injection";

    public static final String RULE_CONTROLLER_ENTITY_RETURN = "controller-entity-return";

    public static final String RULE_CONTROLLER_ENTITY_PARAMETER = "controller-entity-parameter";

    public static final String RULE_DOMAIN_NO_WEB = "domain-no-web";

    public static final String RULE_DOMAIN_NO_PERSISTENCE = "domain-no-persistence";

    public static final String RULE_DOMAIN_NO_INFRASTRUCTURE = "domain-no-infrastructure";

    public static final String RULE_CONSTRUCTOR_INJECTION = "constructor-injection";

    /**
     * Layer name used by the domain purity preset; falls back to {@link #DEFAULT_DOMAIN_PATTERN}.
     */
    public static final String LAYER_DOMAIN = "domain";

    /**
     * Optional layer name whose packages the domain must not reference.
     */
    public static final String LAYER_INFRASTRUCTURE = "infrastructure";

    public static final String DEFAULT_DOMAIN_PATTERN = "..domain..";

    static final List<String> WEB_PACKAGES = List.of(
            "org.springframework.web..",
            "org.springframework.http..",
            "jakarta.servlet..",
            "javax.servlet..");

    static final List<String> PERSISTENCE_PACKAGES = List.of(
            "jakarta.persistence..",
            "javax.persistence..",
            "org.hibernate..",
            "org.springframework.data..",
            "org.springframework.jdbc..",
            "org.springframework.orm..");

    private static final String LAYERED_HINT_SUFFIX = ", or set presets.domainPurity to off in "
            + ArchitectureConfigService.CONFIG_PATH;

    private ArchitecturePresets() {
    }

    /**
     * Rules of the {@code domainPurity} preset. Classes in the {@code domain}
     * layer (default {@code ..domain..}) must not reference Spring Web /
     * Servlet types or types of the {@code infrastructure} layer when the
     * config defines one. JPA / persistence framework types are forbidden only
     * for {@link ArchitectureStyle#HEXAGONAL}: in a layered project the
     * {@code domain} package holds the JPA entities. Entries in
     * {@code domainPurityAllow} are exempt.
     *
     * @return the rules; empty when the preset is off
     */
    public static List<ArchitectureRule> domainPurityRules(ArchitectureConfig config) {
        ArchitectureSeverity severity = config.presetSeverity(ArchitectureConfig.PRESET_DOMAIN_PURITY);
        if (severity.isOff()) {
            return List.of();
        }
        List<PackagePattern> from = config.layer(LAYER_DOMAIN);
        if (from.isEmpty()) {
            from = List.of(PackagePattern.parse(DEFAULT_DOMAIN_PATTERN));
        }
        List<PackagePattern> allow = config.domainPurityAllow();
        String fixHint = config.presetFixHint(ArchitectureConfig.PRESET_DOMAIN_PURITY);
        boolean hexagonal = config.isHexagonal();
        List<ArchitectureRule> rules = new ArrayList<>();
        rules.add(new ArchitectureRule(CODE_DOMAIN_PURITY, RULE_DOMAIN_NO_WEB, from, patterns(WEB_PACKAGES), severity,
                "domain code must stay independent of HTTP and the Servlet API", allow,
                (fixHint != null) ? fixHint : hexagonal
                        ? "Move request/response handling into the web adapter and pass plain domain values into the "
                                + "domain"
                        : "Move request/response handling into the controller and pass plain values into domain "
                                + "classes" + LAYERED_HINT_SUFFIX));
        if (hexagonal) {
            rules.add(new ArchitectureRule(CODE_DOMAIN_PURITY, RULE_DOMAIN_NO_PERSISTENCE, from,
                    patterns(PERSISTENCE_PACKAGES), severity,
                    "domain code must stay independent of JPA and persistence frameworks", allow,
                    (fixHint != null) ? fixHint : "Keep persistence mapping in the persistence adapter (separate "
                            + "entity or mapping class) and expose a repository port owned by the domain. If this "
                            + "project keeps JPA entities in the domain package, set style to layered in "
                            + ArchitectureConfigService.CONFIG_PATH));
        }
        List<PackagePattern> infrastructure = config.layer(LAYER_INFRASTRUCTURE);
        if (!infrastructure.isEmpty()) {
            rules.add(new ArchitectureRule(CODE_DOMAIN_PURITY, RULE_DOMAIN_NO_INFRASTRUCTURE, from, infrastructure,
                    severity, "the domain must not know infrastructure adapters", allow,
                    (fixHint != null) ? fixHint : hexagonal
                            ? "Define a port interface in the domain and implement it in the infrastructure layer"
                            : "Call the infrastructure type from a service instead of the domain class"
                                    + LAYERED_HINT_SUFFIX));
        }
        return rules;
    }

    /**
     * Built-in fix hints of presets that do not run on the dependency engine,
     * overridable through {@code presets.<name>.fixHint}.
     */
    public static String fixHint(ArchitectureConfig config, String presetName, String builtIn) {
        String configured = config.presetFixHint(presetName);
        return (configured != null) ? configured : builtIn;
    }

    /**
     * Config keys (rule ids or preset names) whose findings a suppression
     * token hides. The token is an inspection short name as used in
     * {@code @SuppressWarnings} / {@code //noinspection}, or a code
     * {@code SB-ARCH-NNN}.
     *
     * @return the keys, or {@code null} when the token does not target an architecture check
     */
    @Nullable
    public static Set<String> suppressedKeys(ArchitectureConfig config, String token) {
        String code = TOOL_CODES.getOrDefault(token, token);
        Set<String> keys = new LinkedHashSet<>();
        switch (code) {
            case CODE_CUSTOM_RULE -> config.rules().forEach((ArchitectureRule rule) -> keys.add(rule.id()));
            case CODE_CONTROLLER_REPOSITORY, CODE_CONTROLLER_ENTITY ->
                    keys.add(ArchitectureConfig.PRESET_CONTROLLER_BOUNDARY);
            case CODE_DOMAIN_PURITY -> keys.add(ArchitectureConfig.PRESET_DOMAIN_PURITY);
            case CODE_CONSTRUCTOR_INJECTION -> keys.add(ArchitectureConfig.PRESET_CONSTRUCTOR_INJECTION);
            case CODE_BANNED_API -> config.bannedApis().forEach((BannedApi api) -> keys.add(api.id()));
            case CODE_SUPPRESSION -> keys.add(ArchitectureConfig.PRESET_SUPPRESSION_POLICY);
            default -> {
                return null;
            }
        }
        return keys;
    }

    private static List<PackagePattern> patterns(List<String> raw) {
        List<PackagePattern> patterns = new ArrayList<>(raw.size());
        for (String pattern : raw) {
            patterns.add(PackagePattern.parse(pattern));
        }
        return patterns;
    }
}
