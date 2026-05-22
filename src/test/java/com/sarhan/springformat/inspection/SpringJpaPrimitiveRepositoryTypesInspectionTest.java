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
public class SpringJpaPrimitiveRepositoryTypesInspectionTest extends BasePlatformTestCase {

    public void testReportsAndFixesPrimitiveEntityId() {
        this.myFixture.enableInspections(new SpringJpaPrimitiveRepositoryTypesInspection());
        this.myFixture.configureByText("User.java", """
                @Entity
                class User {
                    @Id
                    <warning descr="JPA entity ID uses primitive 'long'; use wrapper 'Long'"><caret>long</warning> id;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Use wrapper type");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                @Entity
                class User {
                    @Id
                    Long id;
                }
                """);
    }

    public void testReportsAndFixesPrimitiveRepositoryParameter() {
        this.myFixture.enableInspections(new SpringJpaPrimitiveRepositoryTypesInspection());
        this.myFixture.configureByText("UserRepository.java", """
                interface UserRepository extends JpaRepository<User, Long> {
                    User findByAge(<warning descr="Spring Data repository parameter uses primitive 'int'; use wrapper 'Integer'"><caret>int</warning> age);
                }
                class User { }
                interface JpaRepository<T, ID> { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Use wrapper type");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                interface UserRepository extends JpaRepository<User, Long> {
                    User findByAge(Integer age);
                }
                class User { }
                interface JpaRepository<T, ID> { }
                """);
    }

    public void testReportsAndFixesPrimitiveQueryReturn() {
        this.myFixture.enableInspections(new SpringJpaPrimitiveRepositoryTypesInspection());
        this.myFixture.configureByText("UserRepository.java", """
                interface UserRepository extends JpaRepository<User, Long> {
                    @Query("select max(u.id) from User u")
                    <warning descr="Spring Data repository query returns primitive 'long'; use wrapper 'Long' for nullable results"><caret>long</warning> findMaxId();
                }
                class User { }
                interface JpaRepository<T, ID> { }
                @interface Query { String value(); }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Use wrapper type");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                interface UserRepository extends JpaRepository<User, Long> {
                    @Query("select max(u.id) from User u")
                    Long findMaxId();
                }
                class User { }
                interface JpaRepository<T, ID> { }
                @interface Query { String value(); }
                """);
    }

    public void testDoesNotReportPrimitiveNonIdFieldOrWrapperRepositoryParameter() {
        this.myFixture.enableInspections(new SpringJpaPrimitiveRepositoryTypesInspection());
        this.myFixture.configureByText("Sample.java", """
                @Entity
                class User {
                    @Id Long id;
                    int cachedScore;
                }
                interface UserRepository extends JpaRepository<User, Long> {
                    User findByAge(Integer age);
                    long countByActive(boolean active);
                }
                interface JpaRepository<T, ID> { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
