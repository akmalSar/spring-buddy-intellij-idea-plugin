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
import com.sarhan.dataFlow.SpringGhostSaveDataFlowInspection;

/**
 * Tests for {@link SpringGhostSaveDataFlowInspection}.
 *
 * <p>Ghost Save detects mutations of detached JPA entities loaded from
 * Spring Data repositories or EntityManager outside an active
 * {@code @Transactional} context.
 *
 * @author Akmal Sarhan
 */
public class SpringGhostSaveDataFlowInspectionTest extends BasePlatformTestCase {

    private static final String USER_STUB = """
            class User {
                void setName(Name n) { }
                User withName(Name n) { return this; }
                void addItem(Item i) { }
                void removeItem(Item i) { }
                void clear() { }
                Name getName() { return null; }
            }
            class Name { }
            class Item { }
            """;

    public void testReportsBasicSetter() {
        this.myFixture.enableInspections(new SpringGhostSaveDataFlowInspection());
        this.myFixture.configureByText("User.java", USER_STUB);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface UserRepository {
                    User findById(int id);
                }

                @Service
                class UserService {
                    UserRepository repo;

                    public void updateName(int id, Name name) {
                        User user = repo.findById(id);
                        user.setName(name);
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Ghost Save"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsBuilderStyleMutator() {
        this.myFixture.enableInspections(new SpringGhostSaveDataFlowInspection());
        this.myFixture.configureByText("User.java", USER_STUB);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface UserRepository {
                    User findById(int id);
                }

                @Service
                class UserService {
                    UserRepository repo;

                    public void updateName(int id, Name name) {
                        User user = repo.findById(id);
                        user.withName(name);
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Ghost Save"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsKnownMutator() {
        this.myFixture.enableInspections(new SpringGhostSaveDataFlowInspection());
        this.myFixture.configureByText("User.java", USER_STUB);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface UserRepository {
                    User findById(int id);
                }

                @Service
                class UserService {
                    UserRepository repo;

                    public void modifyUser(int id) {
                        User user = repo.findById(id);
                        user.addItem(new Item());
                        user.removeItem(new Item());
                        user.clear();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Ghost Save"))
                .count();
        assertEquals(3, (int) count);
    }

    public void testReportsInterProcedural() {
        this.myFixture.enableInspections(new SpringGhostSaveDataFlowInspection());
        this.myFixture.configureByText("User.java", USER_STUB);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface UserRepository {
                    User findById(int id);
                }

                @Service
                class UserService {
                    UserRepository repo;

                    public void process(int id) {
                        User user = repo.findById(id);
                        applyDefaults(user);
                    }

                    private void applyDefaults(User user) {
                        user.setName(new Name());
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Ghost Save"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsAliasMutation() {
        this.myFixture.enableInspections(new SpringGhostSaveDataFlowInspection());
        this.myFixture.configureByText("User.java", USER_STUB);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface UserRepository {
                    User findById(int id);
                }

                @Service
                class UserService {
                    UserRepository repo;

                    public void updateName(int id, Name name) {
                        User original = repo.findById(id);
                        User alias = original;
                        alias.setName(name);
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Ghost Save"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportInsideTransactional() {
        this.myFixture.enableInspections(new SpringGhostSaveDataFlowInspection());
        this.myFixture.configureByText("User.java", USER_STUB);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface UserRepository {
                    User findById(int id);
                }

                @Service
                class UserService {
                    UserRepository repo;

                    @Transactional
                    public void updateName(int id, Name name) {
                        User user = repo.findById(id);
                        user.setName(name);
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Ghost Save"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportClassLevelTransactional() {
        this.myFixture.enableInspections(new SpringGhostSaveDataFlowInspection());
        this.myFixture.configureByText("User.java", USER_STUB);
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { }
                @interface Service { }
                @interface Repository { }

                @Repository
                interface UserRepository {
                    User findById(int id);
                }

                @Service
                @Transactional
                class UserService {
                    UserRepository repo;

                    public void updateName(int id, Name name) {
                        User user = repo.findById(id);
                        user.setName(name);
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Ghost Save"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportOnPlainObject() {
        this.myFixture.enableInspections(new SpringGhostSaveDataFlowInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Service { }

                class User {
                    void setName(Name n) { }
                }
                class Name { }

                @Service
                class UserService {
                    public void updateName(Name name) {
                        User user = new User();
                        user.setName(name);
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Ghost Save"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportGetterCall() {
        this.myFixture.enableInspections(new SpringGhostSaveDataFlowInspection());
        this.myFixture.configureByText("User.java", USER_STUB);
        this.myFixture.configureByText("Sample.java", """
                @interface Service { }
                @interface Repository { }

                @Repository
                interface UserRepository {
                    User findById(int id);
                }

                @Service
                class UserService {
                    UserRepository repo;

                    public Name fetchName(int id) {
                        User user = repo.findById(id);
                        return user.getName();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null && h.getDescription().contains("Ghost Save"))
                .count();
        assertEquals(0, (int) count);
    }
}
