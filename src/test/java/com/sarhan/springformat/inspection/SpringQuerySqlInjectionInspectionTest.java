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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class SpringQuerySqlInjectionInspectionTest extends BasePlatformTestCase {

    public void testReportsVariableConcatenation() {
        this.myFixture.enableInspections(new SpringQuerySqlInjectionInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                @interface Repository { }

                @Repository
                interface SampleRepository {
                    @Query(<error descr="@Query built with string concatenation is vulnerable to SQL injection — use parameter placeholders (?1, :name) instead">"select u from User u where u.name = '" + input + "'"</error>)
                    void findByName(String input);
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportLiteralOnlyConcatenation() {
        this.myFixture.enableInspections(new SpringQuerySqlInjectionInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                @interface Repository { }

                @Repository
                interface SampleRepository {
                    @Query("SELECT COUNT(*) FROM AccountStatementPosition asp JOIN AccountStatementPositionComment aspc "
                            + "on aspc.accountStatementPosition = asp where asp.id = :accountStatementPositionId")
                    void countComments();
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithSafePlaceholder() {
        this.myFixture.enableInspections(new SpringQuerySqlInjectionInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                @interface Repository { }

                @Repository
                interface SampleRepository {
                    @Query("select u from User u where u.name = :name")
                    void findByName();
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithSafeOrdinal() {
        this.myFixture.enableInspections(new SpringQuerySqlInjectionInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                @interface Repository { }

                @Repository
                interface SampleRepository {
                    @Query("select u from User u where u.name = ?1")
                    void findByName();
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
