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
public class SpringJacksonLazyEntitySerializationInspectionTest extends BasePlatformTestCase {

    public void testReportsLazyOneToManyWithoutJsonIgnore() {
        this.myFixture.enableInspections(new SpringJacksonLazyEntitySerializationInspection());
        this.myFixture.configureByText("User.java", """
                import javax.persistence.Entity;
                import javax.persistence.Id;
                import javax.persistence.OneToMany;
                import java.util.List;

                @Entity
                class User {
                    @Id Long id;
                    @OneToMany
                    List<Order> orders;
                }
                """);
        this.myFixture.configureByText("Order.java", """
                import javax.persistence.Entity;
                import javax.persistence.Id;

                @Entity
                class Order {
                    @Id Long id;
                }
                """);
        this.myFixture.configureByText("UserController.java", """
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class UserController {
                    @GetMapping("/users")
                    User <error descr="Jackson serialization of entity with lazy-loaded associations may cause LazyInitializationException or N+1 queries — use DTO projection or add @JsonIgnore">getUser</error>() {
                        return new User();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithJsonIgnore() {
        this.myFixture.enableInspections(new SpringJacksonLazyEntitySerializationInspection());
        this.myFixture.configureByText("User.java", """
                import javax.persistence.Entity;
                import javax.persistence.Id;
                import javax.persistence.OneToMany;
                import com.fasterxml.jackson.annotation.JsonIgnore;
                import java.util.List;

                @Entity
                class User {
                    @Id Long id;
                    @OneToMany
                    @JsonIgnore
                    List<Order> orders;
                }
                """);
        this.myFixture.configureByText("Order.java", """
                import javax.persistence.Entity;
                import javax.persistence.Id;

                @Entity
                class Order {
                    @Id Long id;
                }
                """);
        this.myFixture.configureByText("UserController.java", """
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class UserController {
                    @GetMapping("/users")
                    User getUser() {
                        return new User();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithNonController() {
        this.myFixture.enableInspections(new SpringJacksonLazyEntitySerializationInspection());
        this.myFixture.configureByText("User.java", """
                import javax.persistence.Entity;
                import javax.persistence.Id;
                import javax.persistence.OneToMany;
                import java.util.List;

                @Entity
                class User {
                    @Id Long id;
                    @OneToMany
                    List<Order> orders;
                }
                """);
        this.myFixture.configureByText("Order.java", """
                import javax.persistence.Entity;
                import javax.persistence.Id;

                @Entity
                class Order {
                    @Id Long id;
                }
                """);
        this.myFixture.configureByText("UserService.java", """
                import org.springframework.stereotype.Service;

                @Service
                class UserService {
                    User getUser() {
                        return new User();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportForNonSpringControllerFqn() {
        this.myFixture.enableInspections(new SpringJacksonLazyEntitySerializationInspection());
        this.myFixture.addFileToProject("com/example/RestController.java", """
                package com.example;
                public @interface RestController { }
                """);
        this.myFixture.addFileToProject("com/example/GetMapping.java", """
                package com.example;
                public @interface GetMapping { String value() default ""; }
                """);
        this.myFixture.configureByText("User.java", """
                @interface Entity { }
                @interface Id { }
                @interface OneToMany { }
                class List<T> { }

                @Entity
                class User {
                    @Id Long id;
                    @OneToMany List<Order> orders;
                }
                @Entity
                class Order {
                    @Id Long id;
                }
                """);
        this.myFixture.configureByText("UserController.java", """
                @com.example.RestController
                class UserController {
                    @com.example.GetMapping("/users")
                    User getUser() {
                        return new User();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotTreatUnrelatedOkFactoryAsResponseWrapper() {
        this.myFixture.enableInspections(new SpringJacksonLazyEntitySerializationInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                @interface RestController { }
                @interface GetMapping { String value(); }
                @interface Entity { }
                @interface OneToMany { }

                @Entity
                class User {
                    @OneToMany List<Order> orders;
                }
                class Order { }
                class UserDto { }
                class MyWrapper<T> {
                    static <T> MyWrapper<T> ok(T value) { return null; }
                }
                @RestController
                class UserController {
                    @GetMapping("/users")
                    User getUser() {
                        return MyWrapper.ok(new UserDto());
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
