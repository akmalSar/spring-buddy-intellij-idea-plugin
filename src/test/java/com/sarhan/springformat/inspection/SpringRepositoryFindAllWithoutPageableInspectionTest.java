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
public class SpringRepositoryFindAllWithoutPageableInspectionTest extends BasePlatformTestCase {

    public void testReportsFindAllWithoutPageable() {
        this.myFixture.enableInspections(new SpringRepositoryFindAllWithoutPageableInspection());
        this.myFixture.configureByText("UserRepository.java", """
                import org.springframework.data.repository.CrudRepository;
                import java.util.List;

                interface UserRepository extends CrudRepository<User, Long> {
                    List<User> findAll();
                }
                """);
        this.myFixture.configureByText("User.java", """
                class User {
                    Long id;
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void work(UserRepository repo) {
                        repo.<warning descr="findAll() without Pageable can exhaust heap on large tables — use findAll(Pageable) or Stream with pagination">findAll</warning>();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportFindAllWithPageable() {
        this.myFixture.enableInspections(new SpringRepositoryFindAllWithoutPageableInspection());
        this.myFixture.configureByText("UserRepository.java", """
                import org.springframework.data.domain.Pageable;
                import org.springframework.data.repository.CrudRepository;
                import java.util.List;

                interface UserRepository extends CrudRepository<User, Long> {
                    List<User> findAll(Pageable pageable);
                }
                """);
        this.myFixture.configureByText("User.java", """
                class User {
                    Long id;
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.data.domain.Pageable;

                class Sample {
                    void work(UserRepository repo, Pageable pageable) {
                        repo.findAll(pageable);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportOnNonRepository() {
        this.myFixture.enableInspections(new SpringRepositoryFindAllWithoutPageableInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void work() {
                        java.util.List.of(1, 2, 3).findAll(x -> true);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
