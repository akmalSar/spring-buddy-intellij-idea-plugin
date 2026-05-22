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

/**
 * Tests for {@link SpringJacksonDeserializedFieldAccessInspection}.
 *
 * @author Akmal Sarhan
 */
public class SpringJacksonDeserializedFieldAccessInspectionTest extends BasePlatformTestCase {

    public void testReportsJsonIgnoredFieldAccess() {
        this.myFixture.enableInspections(new SpringJacksonDeserializedFieldAccessInspection());
        this.myFixture.configureByText("User.java", """
                class User {
                    String name;
                    @com.fasterxml.jackson.annotation.JsonIgnore
                    String password;
                    public String getName() { return name; }
                    public String getPassword() { return password; }
                }
                """);
        this.myFixture.configureByText("UserController.java", """
                @org.springframework.web.bind.annotation.RestController
                class UserController {
                    @org.springframework.web.bind.annotation.PostMapping("/users")
                    void create(@org.springframework.web.bind.annotation.RequestBody User user) {
                        String n = user.getName();
                        String p = user.getPassword();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("not available after Jackson deserialization"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsChainedGetterAccess() {
        this.myFixture.enableInspections(new SpringJacksonDeserializedFieldAccessInspection());
        this.myFixture.configureByText("Address.java", """
                class Address {
                    String street;
                    @com.fasterxml.jackson.annotation.JsonIgnore
                    String secretCode;
                    public String getStreet() { return street; }
                    public String getSecretCode() { return secretCode; }
                }
                """);
        this.myFixture.configureByText("User.java", """
                class User {
                    Address address;
                    public Address getAddress() { return address; }
                }
                """);
        this.myFixture.configureByText("UserController.java", """
                @org.springframework.web.bind.annotation.RestController
                class UserController {
                    @org.springframework.web.bind.annotation.PostMapping("/users")
                    void create(@org.springframework.web.bind.annotation.RequestBody User user) {
                        String s = user.getAddress().getStreet();
                        String c = user.getAddress().getSecretCode();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("not available after Jackson deserialization"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsViewInterfacePropertyAccess() {
        this.myFixture.enableInspections(new SpringJacksonDeserializedFieldAccessInspection());
        this.myFixture.configureByText("UserView.java", """
                interface UserView {
                    String getName();
                }
                """);
        this.myFixture.configureByText("User.java", """
                @com.fasterxml.jackson.databind.annotation.JsonSerialize(as = UserView.class)
                class User implements UserView {
                    String name;
                    String email;
                    public String getName() { return name; }
                    public String getEmail() { return email; }
                }
                """);
        this.myFixture.configureByText("UserController.java", """
                @org.springframework.web.bind.annotation.RestController
                class UserController {
                    @org.springframework.web.bind.annotation.PostMapping("/users")
                    void create(@org.springframework.web.bind.annotation.RequestBody User user) {
                        String n = user.getName();
                        String e = user.getEmail();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("not available after Jackson deserialization"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsJsonIgnorePropertiesFieldAccess() {
        this.myFixture.enableInspections(new SpringJacksonDeserializedFieldAccessInspection());
        this.myFixture.configureByText("User.java", """
                @com.fasterxml.jackson.annotation.JsonIgnoreProperties("secret")
                class User {
                    String name;
                    String secret;
                    public String getName() { return name; }
                    public String getSecret() { return secret; }
                }
                """);
        this.myFixture.configureByText("UserController.java", """
                @org.springframework.web.bind.annotation.RestController
                class UserController {
                    @org.springframework.web.bind.annotation.PostMapping("/users")
                    void create(@org.springframework.web.bind.annotation.RequestBody User user) {
                        String n = user.getName();
                        String s = user.getSecret();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("not available after Jackson deserialization"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportForAccessibleField() {
        this.myFixture.enableInspections(new SpringJacksonDeserializedFieldAccessInspection());
        this.myFixture.configureByText("User.java", """
                class User {
                    String name;
                    public String getName() { return name; }
                }
                """);
        this.myFixture.configureByText("UserController.java", """
                @org.springframework.web.bind.annotation.RestController
                class UserController {
                    @org.springframework.web.bind.annotation.PostMapping("/users")
                    void create(@org.springframework.web.bind.annotation.RequestBody User user) {
                        String n = user.getName();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("not available after Jackson deserialization"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportForNonController() {
        this.myFixture.enableInspections(new SpringJacksonDeserializedFieldAccessInspection());
        this.myFixture.configureByText("User.java", """
                class User {
                    String name;
                    @com.fasterxml.jackson.annotation.JsonIgnore
                    String password;
                    public String getName() { return name; }
                    public String getPassword() { return password; }
                }
                """);
        this.myFixture.configureByText("UserService.java", """
                class UserService {
                    void process(User user) {
                        String p = user.getPassword();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("not available after Jackson deserialization"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportForNonRequestBodyParameter() {
        this.myFixture.enableInspections(new SpringJacksonDeserializedFieldAccessInspection());
        this.myFixture.configureByText("User.java", """
                class User {
                    String name;
                    @com.fasterxml.jackson.annotation.JsonIgnore
                    String password;
                    public String getName() { return name; }
                    public String getPassword() { return password; }
                }
                """);
        this.myFixture.configureByText("UserController.java", """
                @org.springframework.web.bind.annotation.RestController
                class UserController {
                    @org.springframework.web.bind.annotation.PostMapping("/users")
                    void create(String name) {
                        // no @RequestBody parameter
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("not available after Jackson deserialization"))
                .count();
        assertEquals(0, (int) count);
    }

    /**
     * Field-level {@code @JsonSerialize(as = SomeView.class)} restricts what properties
     * are available on the deserialized value of that field. The inspection must trace
     * through the getter chain (dto → getContract → getPartner → getCustomerNumber) and
     * flag the final getter when the property is outside the view.
     */
    /**
     * Field-level {@code @JsonSerialize(as = SomeView.class)} restricts what properties
     * are available on the deserialized value of that field. The inspection must trace
     * through the getter chain (dto → getPartner → getCustomerNumber) and flag the
     * final getter when the property is outside the view.
     */
    public void testReportsFieldLevelJsonSerializeViewRestriction() {
        this.myFixture.enableInspections(new SpringJacksonDeserializedFieldAccessInspection());
        this.myFixture.configureByText("BdfView.java", """
                interface BdfView {
                    String getId();
                }
                """);
        this.myFixture.configureByText("Partner.java", """
                class Partner {
                    String customerNumber;
                    public String getCustomerNumber() { return customerNumber; }
                }
                """);
        this.myFixture.configureByText("Contract.java", """
                class Contract {
                    @com.fasterxml.jackson.databind.annotation.JsonSerialize(as = BdfView.class)
                    Partner partner;
                    public Partner getPartner() { return partner; }
                }
                """);
        this.myFixture.configureByText("Controller.java", """
                @org.springframework.web.bind.annotation.RestController
                class ContractController {
                    @org.springframework.web.bind.annotation.PostMapping("/validate")
                    void validate(@org.springframework.web.bind.annotation.RequestBody Contract dto) {
                        String num = dto.getPartner().getCustomerNumber();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("not available after Jackson deserialization"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportForPropertyInFieldLevelView() {
        this.myFixture.enableInspections(new SpringJacksonDeserializedFieldAccessInspection());
        this.myFixture.configureByText("BdfView.java", """
                interface BdfView {
                    String getCustomerNumber();
                }
                """);
        this.myFixture.configureByText("Partner.java", """
                class Partner {
                    String customerNumber;
                    public String getCustomerNumber() { return customerNumber; }
                }
                """);
        this.myFixture.configureByText("Contract.java", """
                class Contract {
                    @com.fasterxml.jackson.databind.annotation.JsonSerialize(as = BdfView.class)
                    Partner partner;
                    public Partner getPartner() { return partner; }
                }
                """);
        this.myFixture.configureByText("Controller.java", """
                @org.springframework.web.bind.annotation.RestController
                class ContractController {
                    @org.springframework.web.bind.annotation.PostMapping("/validate")
                    void validate(@org.springframework.web.bind.annotation.RequestBody Contract dto) {
                        String num = dto.getPartner().getCustomerNumber();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("not available after Jackson deserialization"))
                .count();
        assertEquals(0, (int) count);
    }
}
