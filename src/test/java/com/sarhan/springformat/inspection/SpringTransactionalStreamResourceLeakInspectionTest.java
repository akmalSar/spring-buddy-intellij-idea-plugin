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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class SpringTransactionalStreamResourceLeakInspectionTest extends BasePlatformTestCase {

    private static final String REPOSITORY_STUBS = """
            package org.springframework.data.repository;
            public interface Repository<T, ID> {}
            """;

    private static final String CRUD_REPOSITORY_STUBS = """
            package org.springframework.data.repository;
            public interface CrudRepository<T, ID> extends Repository<T, ID> {}
            """;

    private static final String STREAM_STUB = """
            package java.util.stream;
            import java.util.function.Consumer;
            public interface Stream<T> {
                void forEach(Consumer<? super T> action);
                Stream<T> filter(java.util.function.Predicate<? super T> predicate);
            }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject(
                "org/springframework/data/repository/Repository.java", REPOSITORY_STUBS);
        this.myFixture.addFileToProject(
                "org/springframework/data/repository/CrudRepository.java", CRUD_REPOSITORY_STUBS);
        this.myFixture.addFileToProject("java/util/stream/Stream.java", STREAM_STUB);
    }

    public void testFlagsStreamReturnFromRepository() {
        this.myFixture.addFileToProject("org/springframework/data/repository/UserRepo.java", """
                package org.springframework.data.repository;
                import java.util.stream.Stream;
                public interface UserRepo extends CrudRepository<String, Integer> {
                    Stream<String> findAllAsStream();
                }
                """);
        this.myFixture.enableInspections(new SpringTransactionalStreamResourceLeakInspection());
        this.myFixture.configureByText("Service.java", """
                import java.util.stream.Stream;
                import org.springframework.data.repository.UserRepo;

                class Service {
                    void process(UserRepo repo) {
                        Stream<String> stream =
                            <error descr="Repository Stream should be used with try-with-resources or explicitly closed to prevent database connection leaks">repo.findAllAsStream()</error>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testDoesNotFlagNonRepositoryStream() {
        this.myFixture.enableInspections(new SpringTransactionalStreamResourceLeakInspection());
        this.myFixture.configureByText("Helper.java", """
                import java.util.stream.Stream;

                class Helper {
                    Stream<String> getStream() { return null; }

                    void process() {
                        Stream<String> stream = getStream();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testDoesNotFlagListReturn() {
        this.myFixture.addFileToProject("org/springframework/data/repository/UserRepo.java", """
                package org.springframework.data.repository;
                import java.util.List;
                public interface UserRepo extends CrudRepository<String, Integer> {
                    List<String> findAll();
                }
                """);
        this.myFixture.enableInspections(new SpringTransactionalStreamResourceLeakInspection());
        this.myFixture.configureByText("Service.java", """
                import java.util.List;
                import org.springframework.data.repository.UserRepo;

                class Service {
                    void process(UserRepo repo) {
                        List<String> list = repo.findAll();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testDoesNotFlagStreamInTryWithResources() {
        this.myFixture.addFileToProject("org/springframework/data/repository/UserRepo.java", """
                package org.springframework.data.repository;
                import java.util.stream.Stream;
                public interface UserRepo extends CrudRepository<String, Integer> {
                    Stream<String> findAllAsStream();
                }
                """);
        this.myFixture.enableInspections(new SpringTransactionalStreamResourceLeakInspection());
        this.myFixture.configureByText("Service.java", """
                import java.util.stream.Stream;
                import org.springframework.data.repository.UserRepo;

                class Service {
                    void process(UserRepo repo) {
                        try (Stream<String> stream = repo.findAllAsStream()) {
                            stream.forEach(System.out::println);
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testFixWrapsChainedStreamCallInTryWithResources() {
        this.myFixture.addFileToProject("org/springframework/data/repository/UserRepo.java", """
                package org.springframework.data.repository;
                import java.util.stream.Stream;
                public interface UserRepo extends CrudRepository<String, Integer> {
                    Stream<String> findAllAsStream();
                }
                """);
        this.myFixture.enableInspections(new SpringTransactionalStreamResourceLeakInspection());
        this.myFixture.configureByText("Service.java", """
                import java.util.stream.Stream;
                import org.springframework.data.repository.UserRepo;

                class Service {
                    void process(UserRepo repo) {
                        <caret>repo.findAllAsStream().forEach(System.out::println);
                    }
                }
                """);
        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Wrap Stream in try-with-resources"));
        String text = this.myFixture.getFile().getText();
        assertTrue("Should wrap in try-with-resources: " + text, text.contains("try ("));
        assertTrue("Should declare stream variable: " + text, text.contains("Stream<String> stream"));
        assertTrue("Should preserve forEach call: " + text, text.contains("stream.forEach(System.out::println)"));
    }

    public void testDoesNotOfferFixForDeclarationStatement() {
        this.myFixture.addFileToProject("org/springframework/data/repository/UserRepo.java", """
                package org.springframework.data.repository;
                import java.util.stream.Stream;
                public interface UserRepo extends CrudRepository<String, Integer> {
                    Stream<String> findAllAsStream();
                }
                """);
        this.myFixture.enableInspections(new SpringTransactionalStreamResourceLeakInspection());
        this.myFixture.configureByText("Service.java", """
                import java.util.stream.Stream;
                import org.springframework.data.repository.UserRepo;

                class Service {
                    void process(UserRepo repo) {
                        Stream<String> s = <error descr="Repository Stream should be used with try-with-resources or explicitly closed to prevent database connection leaks">repo.findAllAsStream()</error>;
                        s.forEach(System.out::println);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
        // Quick fix should NOT be available because wrapping a declaration
        // would leave the variable out of scope for later usages
        List<IntentionAction> intentions = this.myFixture.getAvailableIntentions();
        boolean hasFix = intentions.stream()
                .anyMatch((IntentionAction i) -> i.getText().contains("Wrap Stream in try-with-resources"));
        assertFalse("Should not offer quick fix for declaration statement", hasFix);
    }
}
