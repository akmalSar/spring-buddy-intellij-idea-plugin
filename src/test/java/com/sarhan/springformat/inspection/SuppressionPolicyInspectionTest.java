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

package com.sarhan.springformat.inspection;

import java.util.List;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class SuppressionPolicyInspectionTest extends BasePlatformTestCase {

    private static final String CONFIG = """
            {
              "version": 1,
              "presets": {
                "suppressionPolicy": "error",
                "constructorInjection": { "severity": "warning", "allowSuppression": true }
              },
              "rules": [
                { "id": "domain-no-web", "from": "a..", "mustNotDependOn": "b.." },
                { "id": "legacy", "from": "c..", "mustNotDependOn": "d..", "allowSuppression": true }
              ],
              "bannedApis": [ { "id": "no-date", "symbol": "java.util.Date" } ]
            }
            """;

    private static final String NOT_A_FIX = " Suppressing or working around this check is not a fix.";

    private static final String WHY = ". Why: suppressed findings vanish from the IDE, the pre-commit gate and the "
            + "export while the violation stays in the code. Fix: Remove the suppression and fix the violation. For an "
            + "intended exception add it to the rule's except/allowedIn in .spring-buddy/architecture.json, or record "
            + "it with the architecture baseline action." + NOT_A_FIX;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new SuppressionPolicyInspection());
    }

    public void testReportsSuppressWarningsOfArchitectureChecks() {
        addConfig(CONFIG);
        check("""
                class Sample {
                    @SuppressWarnings(<error descr="[SB-ARCH-007 suppression-policy] Suppression of architecture check DomainPurity (hides domainPurity)%W">"DomainPurity"</error>)
                    void a() { }
                    @SuppressWarnings({"unchecked", <error descr="[SB-ARCH-007 suppression-policy] Suppression of architecture check SB-ARCH-006 (hides no-date)%W">"SB-ARCH-006"</error>})
                    void b() { }
                    @java.lang.SuppressWarnings(value = <error descr="[SB-ARCH-007 suppression-policy] Suppression of architecture check ForbiddenPackageDependency (hides domain-no-web, legacy)%W">"ForbiddenPackageDependency"</error>)
                    void c() { }
                }
                """);
    }

    public void testReportsNoinspectionComments() {
        addConfig(CONFIG);
        check("""
                class Sample {
                    void a() {
                        //noinspection unused, <error descr="[SB-ARCH-007 suppression-policy] Suppression of architecture check SpringControllerRepositoryInjection (hides controllerBoundary)%W">SpringControllerRepositoryInjection</error>
                        int x = 1;
                    }
                }
                """);
    }

    public void testAllowedSuppressionsAndUnrelatedTokensAreAccepted() {
        addConfig(CONFIG);
        check("""
                class Sample {
                    @SuppressWarnings({"unchecked", "SpringConstructorInjection", "SB-ARCH-005", "SB-ARCH-999"})
                    void a() {
                        //noinspection SpringConstructorInjection,unused
                        int x = 1;
                    }
                    @Deprecated
                    void b() { }
                }
                class SuppressWarningsHolder { }
                """);
    }

    public void testAllowedOnlyWhenEveryHiddenRuleAllowsIt() {
        addConfig(CONFIG.replace("\"from\": \"a..\", \"mustNotDependOn\": \"b..\"",
                "\"from\": \"a..\", \"mustNotDependOn\": \"b..\", \"allowSuppression\": true"));
        check("""
                class Sample {
                    @SuppressWarnings("ForbiddenPackageDependency")
                    void a() { }
                }
                """);
    }

    public void testConfiguredFixHintReplacesBuiltIn() {
        addConfig(CONFIG.replace("\"suppressionPolicy\": \"error\"",
                "\"suppressionPolicy\": { \"severity\": \"warning\", \"fixHint\": \"ask the architecture owner\" }"));
        check("""
                class Sample {
                    @SuppressWarnings(<warning descr="[SB-ARCH-007 suppression-policy] Suppression of architecture check DomainPurity (hides domainPurity). Why: suppressed findings vanish from the IDE, the pre-commit gate and the export while the violation stays in the code. Fix: ask the architecture owner. Suppressing or working around this check is not a fix.">"DomainPurity"</warning>)
                    void a() { }
                }
                """);
    }

    public void testCannotBeSuppressedItself() {
        addConfig(CONFIG);
        check("""
                class Sample {
                    @SuppressWarnings({"SuppressionPolicy", <error descr="[SB-ARCH-007 suppression-policy] Suppression of architecture check DomainPurity (hides domainPurity)%W">"DomainPurity"</error>})
                    void a() { }
                }
                """.replace("{\"SuppressionPolicy\"", "{<error descr=\"[SB-ARCH-007 suppression-policy] Suppression of "
                + "architecture check SuppressionPolicy (hides suppressionPolicy)%W\">\"SuppressionPolicy\"</error>"));
    }

    public void testOffAndMissingConfig() {
        check("""
                class Sample {
                    @SuppressWarnings(<warning descr="[SB-ARCH-007 suppression-policy] Suppression of architecture check DomainPurity (hides domainPurity)%W">"DomainPurity"</warning>)
                    void a() { }
                }
                """);
        addConfig("{ \"version\": 1, \"presets\": { \"suppressionPolicy\": \"off\" } }");
        check("""
                class Sample {
                    @SuppressWarnings("DomainPurity")
                    void a() { }
                }
                """);
    }

    private void addConfig(String json) {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", json);
    }

    private void check(String text) {
        String content = text.replace("%W", WHY);
        this.myFixture.configureByText("Sample.java", content);
        this.myFixture.checkHighlighting(true, false, true, true);
        long expected = content.split("descr=\"\\[SB-ARCH-", -1).length - 1;
        List<HighlightInfo> infos = this.myFixture.doHighlighting();
        long actual = infos.stream()
                .filter((HighlightInfo info) -> info.getDescription() != null
                        && info.getDescription().startsWith("[SB-ARCH-"))
                .count();
        assertEquals("architecture problems: " + infos, expected, actual);
    }
}
