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
public class SpringUnexpectedRollbackExceptionInspectionTest extends BasePlatformTestCase {

    private static final String TRANSACTIONAL_STUB = """
            package org.springframework.transaction.annotation;
            import java.lang.annotation.*;
            @Target({ElementType.TYPE, ElementType.METHOD})
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Transactional {}
            """;

    private static final String SERVICE_STUB = """
            package org.springframework.stereotype;
            import java.lang.annotation.*;
            @Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
            public @interface Service {}
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject(
                "org/springframework/transaction/annotation/Transactional.java", TRANSACTIONAL_STUB);
        this.myFixture.addFileToProject(
                "org/springframework/stereotype/Service.java", SERVICE_STUB);
    }

    public void testFlagsTransactionCallCaughtWithoutRethrow() {
        this.myFixture.enableInspections(new SpringUnexpectedRollbackExceptionInspection());
        this.myFixture.configureByText("MyService.java", """
                import org.springframework.transaction.annotation.Transactional;
                import org.springframework.stereotype.Service;

                @Service
                class MyService {
                    @Transactional
                    public void mainMethod(OtherService other) {
                        try {
                            <error descr="Catching a RuntimeException from a @Transactional method call without re-throwing may cause an UnexpectedRollbackException; the transaction was already marked rollback-only">other.doWork()</error>;
                        } catch (RuntimeException e) {
                            // handled, but transaction is already rollback-only
                        }
                    }
                }
                """);
        this.myFixture.configureByText("OtherService.java", """
                import org.springframework.transaction.annotation.Transactional;
                import org.springframework.stereotype.Service;

                @Service
                @Transactional
                class OtherService {
                    public void doWork() {
                        throw new RuntimeException("fail");
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testDoesNotFlagCallWithoutCatch() {
        this.myFixture.enableInspections(new SpringUnexpectedRollbackExceptionInspection());
        this.myFixture.configureByText("MyService.java", """
                import org.springframework.transaction.annotation.Transactional;
                import org.springframework.stereotype.Service;

                @Service
                class MyService {
                    @Transactional
                    public void mainMethod(OtherService other) {
                        other.doWork();
                    }
                }
                """);
        this.myFixture.configureByText("OtherService.java", """
                import org.springframework.transaction.annotation.Transactional;
                import org.springframework.stereotype.Service;

                @Service
                @Transactional
                class OtherService {
                    public void doWork() {}
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testDoesNotFlagNonTransactionalMethod() {
        this.myFixture.enableInspections(new SpringUnexpectedRollbackExceptionInspection());
        this.myFixture.configureByText("MyService.java", """
                import org.springframework.stereotype.Service;

                @Service
                class MyService {
                    public void mainMethod(OtherService other) {
                        try {
                            other.doWork();
                        } catch (RuntimeException e) {
                        }
                    }
                }
                """);
        this.myFixture.configureByText("OtherService.java", """
                import org.springframework.stereotype.Service;

                @Service
                class OtherService {
                    public void doWork() {}
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testFlagsSupportsPropagationBecauseItParticipatesInOuterTransaction() {
        this.myFixture.enableInspections(new SpringUnexpectedRollbackExceptionInspection());
        this.myFixture.configureByText("MyService.java", """
                import org.springframework.transaction.annotation.Transactional;
                import org.springframework.stereotype.Service;

                @Service
                class MyService {
                    @Transactional
                    public void mainMethod(OtherService other) {
                        try {
                            <error descr="Catching a RuntimeException from a @Transactional method call without re-throwing may cause an UnexpectedRollbackException; the transaction was already marked rollback-only">other.doWork()</error>;
                        } catch (RuntimeException e) {
                        }
                    }
                }
                """);
        this.myFixture.configureByText("OtherService.java", """
                import org.springframework.transaction.annotation.Transactional;
                import org.springframework.stereotype.Service;

                @Service
                class OtherService {
                    @Transactional(propagation = Propagation.SUPPORTS)
                    public void doWork() { }
                }
                enum Propagation { SUPPORTS }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }
}
