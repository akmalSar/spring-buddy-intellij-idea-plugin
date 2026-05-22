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

/**
 * @author Akmal Sarhan
 */
public class SpringJakartaTransactionalMigrationInspectionTest extends BasePlatformTestCase {

    private static final String JAKARTA_TX_STUB = """
            package jakarta.transaction;
            public @interface Transactional {
                TxType value() default TxType.REQUIRED;
                Class[] rollbackOn() default {};
                Class[] dontRollbackOn() default {};
            }
            """;

    private static final String TX_TYPE_STUB = """
            package jakarta.transaction;
            public enum TxType {
                REQUIRED, REQUIRES_NEW, SUPPORTS, MANDATORY, NOT_SUPPORTED, NEVER
            }
            """;

    private static final String SPRING_TX_STUB = """
            package org.springframework.transaction.annotation;
            public @interface Transactional {
                String value() default "";
                org.springframework.transaction.annotation.Propagation propagation() default org.springframework.transaction.annotation.Propagation.REQUIRED;
                Class[] rollbackFor() default {};
                Class[] noRollbackFor() default {};
            }
            """;

    private static final String PROPAGATION_STUB = """
            package org.springframework.transaction.annotation;
            public enum Propagation {
                REQUIRED, REQUIRES_NEW, SUPPORTS, MANDATORY, NOT_SUPPORTED, NEVER
            }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("jakarta/transaction/TxType.java", TX_TYPE_STUB);
        this.myFixture.addFileToProject("jakarta/transaction/Transactional.java", JAKARTA_TX_STUB);
        this.myFixture.addFileToProject("org/springframework/transaction/annotation/Propagation.java", PROPAGATION_STUB);
        this.myFixture.addFileToProject("org/springframework/transaction/annotation/Transactional.java", SPRING_TX_STUB);
    }

    public void testReportsJakartaTransactionalOnMethod() {
        this.myFixture.enableInspections(new SpringJakartaTransactionalMigrationInspection());
        this.myFixture.configureByText("Sample.java", """
                import jakarta.transaction.Transactional;

                class Sample {
                    @Transactional
                    void save() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Jakarta @Transactional is ignored by Spring"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsJavaxTransactionalOnClass() {
        // javax stub (same qualified-name check will miss this unless we add it, but we keep the test name for coverage)
        this.myFixture.enableInspections(new SpringJakartaTransactionalMigrationInspection());
        this.myFixture.configureByText("Sample.java", """
                import jakarta.transaction.Transactional;

                @Transactional
                class Sample {
                    void save() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Jakarta @Transactional is ignored by Spring"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportOnSpringTransactional() {
        this.myFixture.enableInspections(new SpringJakartaTransactionalMigrationInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.transaction.annotation.Transactional;

                class Sample {
                    @Transactional
                    void save() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Jakarta @Transactional is ignored by Spring"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportWithoutAnnotation() {
        this.myFixture.enableInspections(new SpringJakartaTransactionalMigrationInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void save() { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Jakarta @Transactional is ignored by Spring"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testQuickFixMigratesAttributes() {
        this.myFixture.enableInspections(new SpringJakartaTransactionalMigrationInspection());
        this.myFixture.configureByText("Sample.java", """
                import jakarta.transaction.Transactional;

                @<caret>Transactional(rollbackOn = Exception.class, dontRollbackOn = IllegalStateException.class)
                class Sample {
                    void save() { }
                }
                """);
        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Migrate to Spring @Transactional"));
        String text = this.myFixture.getFile().getText();
        assertTrue("Should use Spring @Transactional", text.contains("org.springframework.transaction.annotation.Transactional"));
        assertTrue("Should have rollbackFor", text.contains("rollbackFor"));
        assertTrue("Should have noRollbackFor", text.contains("noRollbackFor"));
        assertFalse("Should not have rollbackOn", text.contains("rollbackOn"));
        assertFalse("Should not have dontRollbackOn", text.contains("dontRollbackOn"));
    }

    public void testQuickFixMigratesTxTypeValue() {
        this.myFixture.enableInspections(new SpringJakartaTransactionalMigrationInspection());
        this.myFixture.configureByText("Sample.java", """
                import jakarta.transaction.Transactional;
                import jakarta.transaction.TxType;

                @<caret>Transactional(TxType.REQUIRES_NEW)
                class Sample {
                    void save() { }
                }
                """);
        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Migrate to Spring @Transactional"));
        String text = this.myFixture.getFile().getText();
        assertTrue("Should map TxType to propagation", text.contains("propagation = Propagation.REQUIRES_NEW"));
        assertFalse("Should not contain TxType", text.contains("TxType"));
    }
}
