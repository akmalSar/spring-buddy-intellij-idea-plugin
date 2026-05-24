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
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.assertj.core.api.Assertions;

/**
 * Tests for {@link SpringHibernateQueryTextBlockInspection}.
 *
 * @author Akmal Sarhan
 */
public class SpringHibernateQueryTextBlockInspectionTests extends BasePlatformTestCase {

    public void testQueryAnnotation_longString_reportsWarning() {
        this.myFixture.enableInspections(new SpringHibernateQueryTextBlockInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                interface Repo {
                    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :role ORDER BY u.name")
                    Object users();
                }
                """);

        List<HighlightInfo> highlights = warningHighlights();

        Assertions.assertThat(highlights)
                .as("query text-block warning")
                .hasSize(1);
    }

    public void testQueryAnnotation_textBlock_doesNotReportWarning() {
        this.myFixture.enableInspections(new SpringHibernateQueryTextBlockInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                interface Repo {
                    @Query(\"\"\"
                            SELECT u
                            FROM User u
                                     JOIN u.roles r
                            WHERE r.name = :role
                            ORDER BY u.name
                            \"\"\")
                    Object users();
                }
                """);

        List<HighlightInfo> highlights = warningHighlights();

        Assertions.assertThat(highlights)
                .as("text-block query warnings")
                .isEmpty();
    }

    public void testQueryAnnotation_quickFixFormatsTextBlock() {
        this.myFixture.enableInspections(new SpringHibernateQueryTextBlockInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                interface Repo {
                    @Query(<caret>"SELECT u FROM User u JOIN u.roles r WHERE r.name = :role ORDER BY u.name")
                    Object users();
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Format query as text block"));

        this.myFixture.checkResult("""
                @interface Query { String value(); }
                interface Repo {
                    @Query(\"\"\"
                            SELECT u
                            FROM User u
                                     JOIN u.roles r
                            WHERE r.name = :role
                            ORDER BY u.name
                            \"\"\")
                    Object users();
                }
                """);
    }

    public void testQueryAnnotation_concatenatedString_quickFixFormatsTextBlock() {
        this.myFixture.enableInspections(new SpringHibernateQueryTextBlockInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                interface Repo {
                    @Query(<caret>"SELECT u FROM User u " + "WHERE u.name = :name")
                    Object users();
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Format query as text block"));

        this.myFixture.checkResult("""
                @interface Query { String value(); }
                interface Repo {
                    @Query(\"\"\"
                            SELECT u
                            FROM User u
                            WHERE u.name = :name
                            \"\"\")
                    Object users();
                }
                """);
    }

    public void testQueryAnnotation_singleLineQuery_quickFixKeepsCleanIndentation() {
        this.myFixture.enableInspections(new SpringHibernateQueryTextBlockInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.Optional;
                import java.util.UUID;
                @interface Query { String value(); }
                @interface Param { String value(); }
                interface Repo {
                    @Query(<caret>"SELECT bsr FROM BillrunSummaryReporting bsr WHERE bsr.tenantId = :tenantId AND bsr.billrunId = :billrunId")
                    Optional<BillrunSummaryReporting> findByBillrunIdAndTenantIdSkipLocked(@Param("billrunId") String billrunId, @Param("tenantId")UUID tenantId);
                }
                class BillrunSummaryReporting { }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Format query as text block"));

        this.myFixture.checkResult("""
                import java.util.Optional;
                import java.util.UUID;
                @interface Query { String value(); }
                @interface Param { String value(); }
                interface Repo {
                    @Query(\"\"\"
                            SELECT bsr
                            FROM BillrunSummaryReporting bsr
                            WHERE bsr.tenantId = :tenantId
                              AND bsr.billrunId = :billrunId
                            \"\"\")
                    Optional<BillrunSummaryReporting> findByBillrunIdAndTenantIdSkipLocked(@Param("billrunId") String billrunId, @Param("tenantId")UUID tenantId);
                }
                class BillrunSummaryReporting { }
                """);
    }

    public void testEntityManagerCreateQuery_quickFixFormatsTextBlock() {
        this.myFixture.addFileToProject("jakarta/persistence/EntityManager.java", """
                package jakarta.persistence;
                public interface EntityManager {
                    Object createQuery(String query);
                }
                """);
        this.myFixture.enableInspections(new SpringHibernateQueryTextBlockInspection());
        this.myFixture.configureByText("Sample.java", """
                class Repo {
                    private jakarta.persistence.EntityManager entityManager;
                    Object users() {
                        return this.entityManager.createQuery(<caret>"SELECT u FROM User u JOIN u.roles r WHERE r.name = :role ORDER BY u.name");
                    }
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Format query as text block"));

        this.myFixture.checkResult("""
                class Repo {
                    private jakarta.persistence.EntityManager entityManager;
                    Object users() {
                        return this.entityManager.createQuery(\"\"\"
                                SELECT u
                                FROM User u
                                         JOIN u.roles r
                                WHERE r.name = :role
                                ORDER BY u.name
                                \"\"\");
                    }
                }
                """);
    }

    private List<HighlightInfo> warningHighlights() {
        return this.myFixture.doHighlighting().stream()
                .filter((HighlightInfo highlight) -> HighlightSeverity.WARNING.equals(highlight.getSeverity()))
                .toList();
    }
}
