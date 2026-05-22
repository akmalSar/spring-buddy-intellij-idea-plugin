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
public class SpringJpaSaveInLoopInspectionTest extends BasePlatformTestCase {

    public void testFlagsSaveInForeachLoopByName() {
        this.myFixture.enableInspections(new SpringJpaSaveInLoopInspection());
        this.myFixture.configureByText("Sample.java", """
                class UserRepository {
                    Object save(Object e) { return e; }
                }
                class Service {
                    UserRepository userRepository;
                    void process(java.util.List<Object> users) {
                        for (Object u : users) {
                            <warning descr="Calling save() in a loop is inefficient — use saveAll(...) instead">userRepository.save(u)</warning>;
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsSaveAndFlushInWhileLoop() {
        this.myFixture.enableInspections(new SpringJpaSaveInLoopInspection());
        this.myFixture.configureByText("Sample.java", """
                class UserRepository {
                    Object saveAndFlush(Object e) { return e; }
                }
                class Service {
                    UserRepository userRepository;
                    void process(java.util.Iterator<Object> it) {
                        while (it.hasNext()) {
                            <warning descr="Calling saveAndFlush() in a loop is inefficient — use saveAll(...) instead">userRepository.saveAndFlush(it.next())</warning>;
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagSingleSave() {
        this.myFixture.enableInspections(new SpringJpaSaveInLoopInspection());
        this.myFixture.configureByText("Sample.java", """
                class UserRepository { Object save(Object e) { return e; } }
                class Service {
                    UserRepository userRepository;
                    void process(Object u) {
                        userRepository.save(u);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagNonRepositoryQualifier() {
        this.myFixture.enableInspections(new SpringJpaSaveInLoopInspection());
        this.myFixture.configureByText("Sample.java", """
                class FileStore { Object save(Object e) { return e; } }
                class Service {
                    FileStore store;
                    void process(java.util.List<Object> items) {
                        for (Object i : items) {
                            store.save(i);
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    /**
     * DataFlow-specific: repository reference propagated through local variable.
     * The old PSI-only implementation only checked the direct qualifier;
     * aliasing the repository to a local variable broke detection.
     */
    public void testFlagsAliasRepositoryReference() {
        this.myFixture.enableInspections(new SpringJpaSaveInLoopInspection());
        this.myFixture.configureByText("Sample.java", """
                class UserRepository {
                    Object save(Object e) { return e; }
                }
                class Service {
                    UserRepository userRepository;
                    void process(java.util.List<Object> users) {
                        UserRepository repo = userRepository;
                        for (Object u : users) {
                            <warning descr="Calling save() in a loop is inefficient — use saveAll(...) instead">repo.save(u)</warning>;
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    /**
     * DataFlow-specific: repository passed as method parameter should still be
     * tracked when aliased to a local variable inside the method.
     */
    public void testFlagsRepositoryParameterAlias() {
        this.myFixture.enableInspections(new SpringJpaSaveInLoopInspection());
        this.myFixture.configureByText("Sample.java", """
                class UserRepository {
                    Object save(Object e) { return e; }
                }
                class Service {
                    void process(UserRepository repository, java.util.List<Object> users) {
                        UserRepository repo = repository;
                        for (Object u : users) {
                            <warning descr="Calling save() in a loop is inefficient — use saveAll(...) instead">repo.save(u)</warning>;
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsSaveAndFlushInDoWhileLoop() {
        this.myFixture.enableInspections(new SpringJpaSaveInLoopInspection());
        this.myFixture.configureByText("Sample.java", """
                class UserRepository {
                    Object saveAndFlush(Object e) { return e; }
                }
                class Service {
                    UserRepository userRepository;
                    void process(java.util.Iterator<Object> it) {
                        do {
                            <warning descr="Calling saveAndFlush() in a loop is inefficient — use saveAll(...) instead">userRepository.saveAndFlush(it.next())</warning>;
                        } while (it.hasNext());
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsSaveInForLoop() {
        this.myFixture.enableInspections(new SpringJpaSaveInLoopInspection());
        this.myFixture.configureByText("Sample.java", """
                class UserRepository {
                    Object save(Object e) { return e; }
                }
                class Service {
                    UserRepository userRepository;
                    void process(java.util.List<Object> users) {
                        for (int i = 0; i < users.size(); i++) {
                            <warning descr="Calling save() in a loop is inefficient — use saveAll(...) instead">userRepository.save(users.get(i))</warning>;
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    /**
     * Direct field access with {@code this.userRepository.save()} must be
     * detected. PsiField extends PsiVariable, so the field check must come
     * before the variable check in isTaintedExpression.
     */
    public void testFlagsDirectFieldAccessWithThis() {
        this.myFixture.enableInspections(new SpringJpaSaveInLoopInspection());
        this.myFixture.configureByText("Sample.java", """
                class UserRepository {
                    Object save(Object e) { return e; }
                }
                class Service {
                    UserRepository userRepository;
                    void process(java.util.List<Object> users) {
                        for (Object u : users) {
                            <warning descr="Calling save() in a loop is inefficient — use saveAll(...) instead">this.userRepository.save(u)</warning>;
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
