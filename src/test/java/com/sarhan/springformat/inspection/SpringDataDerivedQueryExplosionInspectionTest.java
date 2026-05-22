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
public class SpringDataDerivedQueryExplosionInspectionTest extends BasePlatformTestCase {

    public void testFlagsDerivedQueryWithMoreThanTwoConditions() {
        this.myFixture.enableInspections(new SpringDataDerivedQueryExplosionInspection());
        this.myFixture.configureByText("UserRepository.java", """
                import java.util.List;
                interface UserRepository {
                    List<User> <warning descr="Spring Data derived query is complex; use @Query to make operator precedence and joins explicit">findByAddressCityAndAddressCountryAndProfileActiveTrue</warning>(String city, String country);
                }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsDerivedQueryMixingAndOr() {
        this.myFixture.enableInspections(new SpringDataDerivedQueryExplosionInspection());
        this.myFixture.configureByText("UserRepository.java", """
                import java.util.List;
                interface UserRepository {
                    List<User> <warning descr="Spring Data derived query is complex; use @Query to make operator precedence and joins explicit">findByNameOrAgeAndActive</warning>(String name, Integer age, boolean active);
                }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagSimpleDerivedQuery() {
        this.myFixture.enableInspections(new SpringDataDerivedQueryExplosionInspection());
        this.myFixture.configureByText("UserRepository.java", """
                import java.util.List;
                interface UserRepository {
                    List<User> findByNameAndActive(String name, boolean active);
                }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagExplicitQuery() {
        this.myFixture.enableInspections(new SpringDataDerivedQueryExplosionInspection());
        this.myFixture.configureByText("UserRepository.java", """
                import java.util.List;
                @interface Query { String value(); }
                interface UserRepository {
                    @Query("select u from User u where u.name = :name or (u.age = :age and u.active = :active)")
                    List<User> findByNameOrAgeAndActive(String name, Integer age, boolean active);
                }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagNonRepositoryType() {
        this.myFixture.enableInspections(new SpringDataDerivedQueryExplosionInspection());
        this.myFixture.configureByText("UserService.java", """
                import java.util.List;
                class UserService {
                    List<User> findByNameOrAgeAndActive(String name, Integer age, boolean active) { return null; }
                }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
