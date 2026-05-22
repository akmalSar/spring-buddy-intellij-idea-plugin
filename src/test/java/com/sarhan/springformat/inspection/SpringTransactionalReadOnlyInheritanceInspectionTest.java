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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class SpringTransactionalReadOnlyInheritanceInspectionTest extends BasePlatformTestCase {

    public void testFlagsWriteMethodInReadOnlyTransactionalClassAndFixes() {
        this.myFixture.enableInspections(new SpringTransactionalReadOnlyInheritanceInspection());
        this.myFixture.configureByText("UserService.java", """
                @Transactional(readOnly = true)
                class UserService {
                    Repository repo;
                    @Transactional
                    void <error descr="Method writes inside @Transactional(readOnly = true); explicitly set readOnly = false"><caret>save</error>(User user) {
                        repo.save(user);
                    }
                }
                interface Repository { void save(User user); }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Set readOnly = false");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                @Transactional(readOnly = true)
                class UserService {
                    Repository repo;
                    @Transactional(readOnly = false)
                    void save(User user) {
                        repo.save(user);
                    }
                }
                interface Repository { void save(User user); }
                class User { }
                """);
    }

    public void testFixReplacesExistingReadOnlyTrue() {
        this.myFixture.enableInspections(new SpringTransactionalReadOnlyInheritanceInspection());
        this.myFixture.configureByText("UserService.java", """
                @Transactional(readOnly = true)
                class UserService {
                    Repository repo;
                    @Transactional(timeout = 5, readOnly = true)
                    void <error descr="Method writes inside @Transactional(readOnly = true); explicitly set readOnly = false"><caret>save</error>(User user) {
                        repo.save(user);
                    }
                }
                interface Repository { void save(User user); }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Set readOnly = false");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                @Transactional(readOnly = true)
                class UserService {
                    Repository repo;
                    @Transactional(timeout = 5, readOnly = false)
                    void save(User user) {
                        repo.save(user);
                    }
                }
                interface Repository { void save(User user); }
                class User { }
                """);
    }

    public void testDoesNotFlagExplicitReadOnlyFalse() {
        this.myFixture.enableInspections(new SpringTransactionalReadOnlyInheritanceInspection());
        this.myFixture.configureByText("UserService.java", """
                @Transactional(readOnly = true)
                class UserService {
                    Repository repo;
                    @Transactional(readOnly = false)
                    void save(User user) { repo.save(user); }
                }
                interface Repository { void save(User user); }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagTransactionalServiceMethodWithoutWriteSignal() {
        this.myFixture.enableInspections(new SpringTransactionalReadOnlyInheritanceInspection());
        this.myFixture.configureByText("UserService.java", """
                @Transactional(readOnly = true)
                class UserService {
                    SubledgerService subledgerService;

                    @Transactional
                    void process(SubledgerDto dto) {
                        subledgerService.handleSubledger(dto);
                    }
                }

                class SubledgerService {
                    @org.springframework.transaction.annotation.Transactional
                    public void handleSubledger(SubledgerDto dto) { }
                }

                class SubledgerDto { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagTransactionalServiceMethodWithRequiresNewWithoutWriteSignal() {
        this.myFixture.enableInspections(new SpringTransactionalReadOnlyInheritanceInspection());
        this.myFixture.configureByText("UserService.java", """
                @Transactional(readOnly = true)
                class UserService {
                    SubledgerService subledgerService;

                    @Transactional
                    void process(SubledgerDto dto) {
                        subledgerService.handleSubledger(dto);
                    }
                }

                class SubledgerService {
                    @org.springframework.transaction.annotation.Transactional(
                        propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
                    public void handleSubledger(SubledgerDto dto) { }
                }

                class SubledgerDto { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagReadOnlyServiceMethod() {
        this.myFixture.enableInspections(new SpringTransactionalReadOnlyInheritanceInspection());
        this.myFixture.configureByText("UserService.java", """
                @Transactional(readOnly = true)
                class UserService {
                    SubledgerService subledgerService;

                    @Transactional
                    void process(SubledgerDto dto) {
                        subledgerService.readSubledger(dto);
                    }
                }

                class SubledgerService {
                    @org.springframework.transaction.annotation.Transactional(readOnly = true)
                    public void readSubledger(SubledgerDto dto) { }
                }

                class SubledgerDto { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagMethodInClassWithReadOnlyFalse() {
        this.myFixture.enableInspections(new SpringTransactionalReadOnlyInheritanceInspection());
        this.myFixture.configureByText("UserService.java", """
                @Transactional(readOnly = true)
                class UserService {
                    SubledgerService subledgerService;

                    @Transactional
                    void process(SubledgerDto dto) {
                        subledgerService.handleSubledger(dto);
                    }
                }

                @org.springframework.transaction.annotation.Transactional(readOnly = true)
                class SubledgerService {
                    public void handleSubledger(SubledgerDto dto) { }
                }

                class SubledgerDto { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagReadOnlyTrueCallingAmbiguousTransactionalMethod() {
        this.myFixture.enableInspections(new SpringTransactionalReadOnlyInheritanceInspection());
        this.myFixture.configureByText("SubledgerScheduler.java", """
                @org.springframework.stereotype.Service
                class SubledgerScheduler {
                    SubledgerService subledgerService;

                    @org.springframework.transaction.annotation.Transactional(readOnly = true)
                    void executeScheduler() {
                        subledgerService.handleSubledger(new SubledgerDto());
                    }
                }

                @org.springframework.stereotype.Service
                class SubledgerService {
                    @org.springframework.transaction.annotation.Transactional(
                        propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
                    public void handleSubledger(SubledgerDto dto) { }
                }

                class SubledgerDto { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
