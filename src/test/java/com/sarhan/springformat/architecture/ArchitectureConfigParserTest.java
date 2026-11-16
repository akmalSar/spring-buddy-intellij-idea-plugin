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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ArchitectureConfigParser} and preset severity rules
 * of {@link ArchitectureConfig}.
 *
 * @author Akmal Sarhan
 */
public class ArchitectureConfigParserTest {

    @Test
    public void parsesValidConfigAndExpandsLayers() {
        ArchitectureConfig config = ArchitectureConfigParser.parse("""
                {
                  "version": 1,
                  "includeTests": true,
                  "presets": { "controllerBoundary": "error", "domainPurity": "off" },
                  "layers": {
                    "domain": ["com.acme.domain.."],
                    "web": "com.acme.web.."
                  },
                  "rules": [
                    {
                      "id": "domain-no-web",
                      "from": "domain",
                      "mustNotDependOn": ["web", "regex:org\\\\.springframework\\\\.web(\\\\..*)?"],
                      "severity": "ERROR",
                      "message": "Domain must not know the web layer",
                      "except": ["com.acme.web.shared.."]
                    }
                  ]
                }
                """);

        assertThat(config.error()).isNull();
        assertThat(config.fromFile()).isTrue();
        assertThat(config.includeTests()).isTrue();
        assertThat(config.layer("web")).extracting(PackagePattern::raw).containsExactly("com.acme.web..");
        assertThat(config.presetSeverity(ArchitectureConfig.PRESET_CONTROLLER_BOUNDARY))
                .isEqualTo(ArchitectureSeverity.ERROR);
        assertThat(config.presetSeverity(ArchitectureConfig.PRESET_DOMAIN_PURITY)).isEqualTo(ArchitectureSeverity.OFF);
        assertThat(config.rules()).hasSize(1);
        ArchitectureRule rule = config.rules().get(0);
        assertThat(rule.code()).isEqualTo("SB-ARCH-001");
        assertThat(rule.id()).isEqualTo("domain-no-web");
        assertThat(rule.severity()).isEqualTo(ArchitectureSeverity.ERROR);
        assertThat(rule.from()).extracting(PackagePattern::raw).containsExactly("com.acme.domain..");
        assertThat(rule.mustNotDependOn()).hasSize(2);
        assertThat(rule.forbids("com.acme.web.api", "com.acme.web.api.Controller")).isTrue();
        assertThat(rule.forbids("org.springframework.web.bind", null)).isTrue();
        assertThat(rule.forbids("com.acme.web.shared", "com.acme.web.shared.Dto")).isFalse();
    }

    @Test
    public void ruleSeverityDefaultsToWarningAndIncludeTestsToFalse() {
        ArchitectureConfig config = ArchitectureConfigParser.parse("""
                { "version": 1, "rules": [ { "id": "r", "from": "a..", "mustNotDependOn": "b.." } ] }
                """);
        assertThat(config.error()).isNull();
        assertThat(config.includeTests()).isFalse();
        assertThat(config.rules().get(0).severity()).isEqualTo(ArchitectureSeverity.WARNING);
    }

    @Test
    public void presetsWithoutConfigFileAreCappedAtWarning() {
        ArchitectureConfig config = ArchitectureConfig.presetsOnly();
        assertThat(config.style()).isEqualTo(ArchitectureStyle.LAYERED);
        assertThat(config.presetSeverity(ArchitectureConfig.PRESET_CONTROLLER_BOUNDARY))
                .isEqualTo(ArchitectureSeverity.WARNING);
        assertThat(config.presetSeverity(ArchitectureConfig.PRESET_DOMAIN_PURITY))
                .isEqualTo(ArchitectureSeverity.OFF);
        assertThat(config.presetSeverity(ArchitectureConfig.PRESET_CONSTRUCTOR_INJECTION))
                .isEqualTo(ArchitectureSeverity.OFF);
    }

    @Test
    public void styleDefaultsToLayeredAndControlsDomainPurity() {
        ArchitectureConfig layered = ArchitectureConfigParser.parse("{ \"version\": 1 }");
        assertThat(layered.style()).isEqualTo(ArchitectureStyle.LAYERED);
        assertThat(layered.presetSeverity(ArchitectureConfig.PRESET_DOMAIN_PURITY)).isEqualTo(ArchitectureSeverity.OFF);

        ArchitectureConfig layeredOn = ArchitectureConfigParser.parse(
                "{ \"version\": 1, \"style\": \"layered\", \"presets\": { \"domainPurity\": \"error\" } }");
        assertThat(ArchitecturePresets.domainPurityRules(layeredOn)).extracting(ArchitectureRule::id)
                .containsExactly(ArchitecturePresets.RULE_DOMAIN_NO_WEB);
        assertThat(ArchitecturePresets.domainPurityRules(layeredOn)).extracting(ArchitectureRule::fixHint)
                .noneMatch((String hint) -> hint.contains("port") || hint.contains("adapter"));

        ArchitectureConfig hexagonal = ArchitectureConfigParser.parse("{ \"version\": 1, \"style\": \"Hexagonal\" }");
        assertThat(hexagonal.error()).isNull();
        assertThat(hexagonal.style()).isEqualTo(ArchitectureStyle.HEXAGONAL);
        assertThat(hexagonal.presetSeverity(ArchitectureConfig.PRESET_DOMAIN_PURITY))
                .isEqualTo(ArchitectureSeverity.WARNING);
        assertThat(ArchitecturePresets.domainPurityRules(hexagonal)).extracting(ArchitectureRule::id)
                .containsExactly(ArchitecturePresets.RULE_DOMAIN_NO_WEB, ArchitecturePresets.RULE_DOMAIN_NO_PERSISTENCE);

        assertMalformed("{ \"version\": 1, \"style\": \"onion\" }", "unknown style 'onion'");
        assertMalformed("{ \"version\": 1, \"style\": 3 }", "style must be a string");
    }

    @Test
    public void malformedConfigFallsBackToPresetsWithError() {
        assertMalformed("{ not json", "invalid JSON");
        assertMalformed("[]", "top-level value must be a JSON object");
        assertMalformed("{}", "'version' is required");
        assertMalformed("{ \"version\": 2 }", "unsupported version");
        assertMalformed("{ \"version\": 1, \"includeTests\": \"yes\" }", "'includeTests' must be a boolean");
        assertMalformed("{ \"version\": 1, \"presets\": { \"unknown\": \"error\" } }", "unknown preset 'unknown'");
        assertMalformed("{ \"version\": 1, \"presets\": { \"domainPurity\": \"fatal\" } }", "unknown severity 'fatal'");
        assertMalformed("{ \"version\": 1, \"rules\": [ { \"from\": \"a..\", \"mustNotDependOn\": \"b..\" } ] }",
                "rules[0].id is required");
        assertMalformed("{ \"version\": 1, \"rules\": [ { \"id\": \"r\", \"from\": \"a..\" } ] }",
                "mustNotDependOn is required");
        assertMalformed("{ \"version\": 1, \"rules\": [ { \"id\": \"r\", \"from\": \"a/b\", \"mustNotDependOn\": \"b..\" } ] }",
                "invalid package pattern 'a/b'");
        assertMalformed("""
                { "version": 1, "rules": [
                  { "id": "r", "from": "a..", "mustNotDependOn": "b.." },
                  { "id": "r", "from": "c..", "mustNotDependOn": "d.." } ] }
                """, "duplicate rule id 'r'");
    }

    @Test
    public void parsesBannedApisAndFixHints() {
        ArchitectureConfig config = ArchitectureConfigParser.parse("""
                {
                  "version": 1,
                  "layers": { "time": ["com.acme.time.."] },
                  "rules": [ { "id": "r", "from": "a..", "mustNotDependOn": "b..", "fixHint": "use a port" } ],
                  "bannedApis": [
                    { "id": "no-date", "symbol": "java.util.Date", "allowedIn": "time", "severity": "error",
                      "message": "Use java.time", "fixHint": "use Instant" },
                    { "id": "no-exit", "symbol": "java.lang.System#exit" }
                  ]
                }
                """);
        assertThat(config.error()).isNull();
        assertThat(config.rules().get(0).fixHint()).isEqualTo("use a port");
        assertThat(config.bannedApis()).hasSize(2);
        BannedApi date = config.bannedApis().get(0);
        assertThat(date.code()).isEqualTo("SB-ARCH-006");
        assertThat(date.severity()).isEqualTo(ArchitectureSeverity.ERROR);
        assertThat(date.fixHint()).isEqualTo("use Instant");
        assertThat(date.isAllowedIn("com.acme.time.impl", null)).isTrue();
        assertThat(date.isAllowedIn("com.acme.order", null)).isFalse();
        assertThat(date.matchesClass("java.util.Date")).isTrue();
        assertThat(date.matchesClass("java.sql.Date")).isFalse();
        BannedApi exit = config.bannedApis().get(1);
        assertThat(exit.severity()).isEqualTo(ArchitectureSeverity.WARNING);
        assertThat(exit.allowedIn()).isEmpty();
        assertThat(exit.matchesMethod("java.lang.System", "exit")).isTrue();
        assertThat(exit.matchesMethod("java.lang.System", "exitNow")).isFalse();
        assertThat(exit.matchesMethod("java.lang.Runtime", "exit")).isFalse();
    }

    @Test
    public void malformedBannedApisRejectWholeFile() {
        assertMalformed("{ \"version\": 1, \"bannedApis\": {} }", "'bannedApis' must be an array");
        assertMalformed("{ \"version\": 1, \"bannedApis\": [ { \"id\": \"x\" } ] }", "symbol is required");
        assertMalformed("{ \"version\": 1, \"bannedApis\": [ { \"id\": \"x\", \"symbol\": \"Date\" } ] }",
                "must be a fully-qualified type");
        assertMalformed("{ \"version\": 1, \"bannedApis\": [ { \"id\": \"x\", \"symbol\": \"a.B#\" } ] }",
                "must be a fully-qualified type");
        assertMalformed("""
                { "version": 1, "rules": [ { "id": "x", "from": "a..", "mustNotDependOn": "b.." } ],
                  "bannedApis": [ { "id": "x", "symbol": "a.B" } ] }
                """, "duplicate rule id 'x'");
    }

    @Test
    public void parsesPresetObjectsSuppressionAndBaselinedSeverity() {
        ArchitectureConfig config = ArchitectureConfigParser.parse("""
                {
                  "version": 1,
                  "baselinedSeverity": "off",
                  "presets": {
                    "domainPurity": { "severity": "error", "fixHint": "use ports", "allowSuppression": true },
                    "controllerBoundary": { "fixHint": "use a service" }
                  },
                  "rules": [ { "id": "r", "from": "a..", "mustNotDependOn": "b..", "allowSuppression": true } ],
                  "bannedApis": [ { "id": "b", "symbol": "a.B", "allowSuppression": false } ]
                }
                """);
        assertThat(config.error()).isNull();
        assertThat(config.baselinedSeverity()).isEqualTo(ArchitectureSeverity.OFF);
        assertThat(config.presetSeverity(ArchitectureConfig.PRESET_DOMAIN_PURITY)).isEqualTo(ArchitectureSeverity.ERROR);
        assertThat(config.presetSeverity(ArchitectureConfig.PRESET_CONTROLLER_BOUNDARY))
                .isEqualTo(ArchitectureSeverity.WARNING);
        assertThat(config.presetFixHint(ArchitectureConfig.PRESET_DOMAIN_PURITY)).isEqualTo("use ports");
        assertThat(config.presetFixHint(ArchitectureConfig.PRESET_CONTROLLER_BOUNDARY)).isEqualTo("use a service");
        assertThat(config.allowsSuppression(ArchitectureConfig.PRESET_DOMAIN_PURITY)).isTrue();
        assertThat(config.allowsSuppression("r")).isTrue();
        assertThat(config.allowsSuppression("b")).isFalse();
        assertThat(config.allowsSuppression(ArchitectureConfig.PRESET_CONTROLLER_BOUNDARY)).isFalse();
        assertThat(ArchitecturePresets.domainPurityRules(config)).extracting(ArchitectureRule::fixHint)
                .containsOnly("use ports");

        ArchitectureConfig defaults = ArchitectureConfigParser.parse("{ \"version\": 1 }");
        assertThat(defaults.baselinedSeverity()).isEqualTo(ArchitectureSeverity.WEAK_WARNING);
        assertThat(defaults.presetSeverity(ArchitectureConfig.PRESET_SUPPRESSION_POLICY))
                .isEqualTo(ArchitectureSeverity.WARNING);

        assertMalformed("{ \"version\": 1, \"baselinedSeverity\": \"error\" }", "must be weak_warning or off");
        assertMalformed("{ \"version\": 1, \"presets\": { \"domainPurity\": { \"allowSuppression\": \"yes\" } } }",
                "allowSuppression must be a boolean");
    }

    @Test
    public void parsesAgentOrientedMessageParts() {
        String text = ArchitectureProblem.text("Banned API a.B is used outside 'x..'", "reason.", "use C.");
        assertThat(text).isEqualTo("Banned API a.B is used outside 'x..'. Why: reason. Fix: use C. "
                + "Suppressing or working around this check is not a fix.");
        ArchitectureProblem problem = ArchitectureProblem.parse(ArchitectureProblem.format("SB-ARCH-006", "b", text)
                + ArchitectureProblem.BASELINED_MARKER);
        assertThat(problem).isNotNull();
        assertThat(problem.baselined()).isTrue();
        assertThat(problem.summary()).isEqualTo("Banned API a.B is used outside 'x..'. Why: reason");
        assertThat(problem.fixHint()).isEqualTo("use C");
        ArchitectureProblem legacy = ArchitectureProblem.parse("[SB-ARCH-002 r] Old message");
        assertThat(legacy.fixHint()).isNull();
        assertThat(legacy.baselined()).isFalse();
        assertThat(legacy.summary()).isEqualTo("Old message");
    }

    @Test
    public void malformedConfigNeverEnablesErrorPresets() {
        ArchitectureConfig config = ArchitectureConfigParser.parse("{ \"version\": 1, \"presets\": "
                + "{ \"controllerBoundary\": \"error\" }, \"rules\": 5 }");
        assertThat(config.error()).isNotNull();
        assertThat(config.presetSeverity(ArchitectureConfig.PRESET_CONTROLLER_BOUNDARY))
                .isEqualTo(ArchitectureSeverity.WARNING);
        assertThat(config.rules()).isEmpty();
    }

    @Test
    public void formatsAndParsesProblemMessages() {
        String message = ArchitectureProblem.format("SB-ARCH-001", "domain-no-web", "Domain must not use web");
        assertThat(message).isEqualTo("[SB-ARCH-001 domain-no-web] Domain must not use web");
        ArchitectureProblem problem = ArchitectureProblem.parse(message);
        assertThat(problem).isNotNull();
        assertThat(problem.code()).isEqualTo("SB-ARCH-001");
        assertThat(problem.ruleId()).isEqualTo("domain-no-web");
        assertThat(problem.text()).isEqualTo("Domain must not use web");
        assertThat(ArchitectureProblem.parse("Controller returns a JPA entity")).isNull();
    }

    @Test
    public void shippedSampleConfigIsValid() throws java.io.IOException {
        String json = java.nio.file.Files.readString(java.nio.file.Path.of("docs/samples/architecture.json"));
        ArchitectureConfig config = ArchitectureConfigParser.parse(json);
        assertThat(config.error()).isNull();
        assertThat(config.rules()).extracting(ArchitectureRule::id).contains("domain-independent", "web-no-persistence");
        assertThat(config.presetSeverity(ArchitectureConfig.PRESET_DOMAIN_PURITY)).isEqualTo(ArchitectureSeverity.ERROR);
    }

    private static void assertMalformed(String json, String expectedError) {
        ArchitectureConfig config = ArchitectureConfigParser.parse(json);
        assertThat(config.fromFile()).isFalse();
        assertThat(config.error()).contains(expectedError);
        assertThat(config.rules()).isEmpty();
    }
}
