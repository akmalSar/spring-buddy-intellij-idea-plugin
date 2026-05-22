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
public class SpringDataProjectionFullLoadInspectionTest extends BasePlatformTestCase {

    public void testFlagsProjectionMethodReturningEntity() {
        this.myFixture.enableInspections(new SpringDataProjectionFullLoadInspection());
        this.myFixture.configureByText("UserSummary.java", """
                interface UserSummary {
                    String getName();
                    User <warning descr="Projection method returns an entity-like type; this can defeat projection optimization and load full entities">getRelatedUser</warning>();
                }
                interface UserRepository extends Repository<User, Long> {
                    UserSummary findProjectedById(Long id);
                }
                interface Repository<T, ID> { }
                @Entity
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsProjectionSpelValue() {
        this.myFixture.enableInspections(new SpringDataProjectionFullLoadInspection());
        this.myFixture.configureByText("UserSummary.java", """
                @interface Value { String value(); }
                interface UserSummary {
                    @Value("#{target.name + ' ' + target.email}")
                    String <warning descr="SpEL in Spring Data projection can force full entity loading; prefer closed projections">getDisplayName</warning>();
                }
                interface UserRepository extends Repository<User, Long> {
                    UserSummary findProjectedById(Long id);
                }
                interface Repository<T, ID> { }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagClosedScalarProjection() {
        this.myFixture.enableInspections(new SpringDataProjectionFullLoadInspection());
        this.myFixture.configureByText("UserSummary.java", """
                interface UserSummary {
                    String getName();
                    String getEmail();
                }
                interface UserRepository extends Repository<User, Long> {
                    UserSummary findProjectedById(Long id);
                }
                interface Repository<T, ID> { }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagCommonScalarValueProjectionTypes() {
        this.myFixture.enableInspections(new SpringDataProjectionFullLoadInspection());
        this.myFixture.configureByText("BookingAmountProjection.java", """
                import java.math.BigDecimal;
                import java.util.UUID;
                public interface BookingAmountProjection {
                    UUID getAspId();
                    BigDecimal getTotal();
                }
                interface BookingRepository extends Repository<Booking, Long> {
                    BookingAmountProjection findProjectedById(Long id);
                }
                interface Repository<T, ID> { }
                class Booking { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagProjectionNamedInterfaceNotUsedByRepository() {
        this.myFixture.enableInspections(new SpringDataProjectionFullLoadInspection());
        this.myFixture.configureByText("UserSummary.java", """
                interface UserSummary {
                    User getRelatedUser();
                }
                @Entity
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagUnusedSpelProjectionMethod() {
        this.myFixture.enableInspections(new SpringDataProjectionFullLoadInspection());
        this.myFixture.configureByText("UserSummary.java", """
                @interface Value { String value(); }
                interface UserSummary {
                    String getName();
                    @Value("#{target.name + ' ' + target.email}")
                    String getDisplayName();
                }
                interface UserRepository extends Repository<User, Long> {
                    UserSummary findProjectedById(Long id);
                }
                interface Repository<T, ID> { }
                class UserService {
                    UserRepository repo;
                    void read(Long id) {
                        repo.findProjectedById(id).getName();
                    }
                }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
