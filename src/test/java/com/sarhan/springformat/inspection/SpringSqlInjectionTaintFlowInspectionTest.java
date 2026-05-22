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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class SpringSqlInjectionTaintFlowInspectionTest extends BasePlatformTestCase {

    public void testReportsJdbcTemplateWithTaint() {
        this.myFixture.enableInspections(new SpringSqlInjectionTaintFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface RequestParam { }
                @interface GetMapping { String value(); }
                @interface RestController { }
                class JdbcTemplate {
                    void query(String sql, Object... args) { }
                }

                @RestController
                class Sample {
                    JdbcTemplate jdbc;

                    @GetMapping("/search")
                    void search(@RequestParam String query) {
                        String sql = "SELECT * FROM users WHERE name = '" + query + "'";
                        jdbc.query(sql);
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Potential SQL injection"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportWithBoundParameter() {
        this.myFixture.enableInspections(new SpringSqlInjectionTaintFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface RequestParam { }
                @interface GetMapping { String value(); }
                @interface RestController { }
                class JdbcTemplate {
                    void query(String sql, Object... args) { }
                }

                @RestController
                class Sample {
                    JdbcTemplate jdbc;

                    @GetMapping("/search")
                    void search(@RequestParam String query) {
                        String sql = "SELECT * FROM users WHERE name = ?";
                        jdbc.query(sql, query);
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Potential SQL injection"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportWithoutTaintSource() {
        this.myFixture.enableInspections(new SpringSqlInjectionTaintFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                class JdbcTemplate {
                    void query(String sql, Object... args) { }
                }
                class Sample {
                    JdbcTemplate jdbc;
                    void search() {
                        String sql = "SELECT * FROM users";
                        jdbc.query(sql);
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Potential SQL injection"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testShowFlowFixRegisteredOnProblem() {
        this.myFixture.enableInspections(new SpringSqlInjectionTaintFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface PathVariable { }
                class JdbcTemplate {
                    void execute(String sql) { }
                }
                class Sample {
                    JdbcTemplate jdbc;
                    void byId(@PathVariable String id) {
                        jdbc.execute("SELECT * FROM t WHERE id = '" + id + "'");
                    }
                }
                """);
        this.myFixture.doHighlighting();
        List<IntentionAction> fixes = this.myFixture.getAllQuickFixes();
        boolean hasDiagramFix = fixes.stream()
                .anyMatch((IntentionAction f) -> f.getText().contains("SQL injection flow diagram"));
        assertTrue("ShowSqlInjectionFlowFix must be offered on flagged call", hasDiagramFix);
    }

    public void testReportsPathVariableDirectConcatenation() {
        this.myFixture.enableInspections(new SpringSqlInjectionTaintFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface PathVariable { }
                class JdbcTemplate {
                    void update(String sql, Object... args) { }
                }
                class Sample {
                    JdbcTemplate jdbc;
                    void delete(@PathVariable String id) {
                        jdbc.update("DELETE FROM t WHERE id = '" + id + "'");
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Potential SQL injection"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportForNonSpringRequestParamFqn() {
        this.myFixture.enableInspections(new SpringSqlInjectionTaintFlowInspection());
        this.myFixture.addFileToProject("com/example/RequestParam.java", """
                package com.example;
                public @interface RequestParam { }
                """);
        this.myFixture.configureByText("Sample.java", """
                class JdbcTemplate {
                    void query(String sql, Object... args) { }
                }
                class Sample {
                    JdbcTemplate jdbc;
                    void search(@com.example.RequestParam String query) {
                        jdbc.query("SELECT * FROM users WHERE name = '" + query + "'");
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Potential SQL injection"))
                .count();
        assertEquals(0, (int) count);
    }
}
