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
 * Tests for {@link SpringJsonIgnoreFieldAccessInspection}.
 *
 * @author Akmal Sarhan
 */
public class SpringJsonIgnoreFieldAccessInspectionTest extends BasePlatformTestCase {

    public void testReportsJsonIgnoreFieldAccessInService() {
        this.myFixture.enableInspections(new SpringJsonIgnoreFieldAccessInspection());
        this.myFixture.configureByText("UserService.java", """
                @interface Service { }
                @interface JsonIgnore { }
                class User {
                    String name;
                    @JsonIgnore
                    String password;
                    public String getName() { return name; }
                    public String getPassword() { return password; }
                }
                @Service
                class UserService {
                    void validate(User user) {
                        String n = user.getName();
                        String p = user.getPassword();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@JsonIgnore'd"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsChainedGetterAccessInService() {
        this.myFixture.enableInspections(new SpringJsonIgnoreFieldAccessInspection());
        this.myFixture.configureByText("UserService.java", """
                @interface Service { }
                @interface JsonIgnore { }
                class Address {
                    String street;
                    @JsonIgnore
                    String secretCode;
                    public String getStreet() { return street; }
                    public String getSecretCode() { return secretCode; }
                }
                class User {
                    Address address;
                    public Address getAddress() { return address; }
                }
                @Service
                class UserService {
                    void process(User user) {
                        String s = user.getAddress().getStreet();
                        String c = user.getAddress().getSecretCode();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@JsonIgnore'd"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsJsonIgnorePropertiesFieldAccess() {
        this.myFixture.enableInspections(new SpringJsonIgnoreFieldAccessInspection());
        this.myFixture.configureByText("UserService.java", """
                @interface Service { }
                @interface JsonIgnoreProperties {
                    String[] value();
                }
                @JsonIgnoreProperties("secret")
                class User {
                    String name;
                    String secret;
                    public String getName() { return name; }
                    public String getSecret() { return secret; }
                }
                @Service
                class UserService {
                    void validate(User user) {
                        String n = user.getName();
                        String s = user.getSecret();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@JsonIgnore'd"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testNoReportForNonComponent() {
        this.myFixture.enableInspections(new SpringJsonIgnoreFieldAccessInspection());
        this.myFixture.configureByText("PlainClass.java", """
                @interface JsonIgnore { }
                class User {
                    String name;
                    @JsonIgnore
                    String password;
                    public String getName() { return name; }
                    public String getPassword() { return password; }
                }
                class PlainClass {
                    void process(User user) {
                        String p = user.getPassword();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@JsonIgnore'd"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportForAccessibleField() {
        this.myFixture.enableInspections(new SpringJsonIgnoreFieldAccessInspection());
        this.myFixture.configureByText("UserService.java", """
                @interface Service { }
                class User {
                    String name;
                    public String getName() { return name; }
                }
                @Service
                class UserService {
                    void validate(User user) {
                        String n = user.getName();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@JsonIgnore'd"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testNoReportForNoParameterAccess() {
        this.myFixture.enableInspections(new SpringJsonIgnoreFieldAccessInspection());
        this.myFixture.configureByText("UserService.java", """
                @interface Service { }
                @interface JsonIgnore { }
                class User {
                    String name;
                    @JsonIgnore
                    String password;
                    public String getName() { return name; }
                    public String getPassword() { return password; }
                }
                @Service
                class UserService {
                    void doWork() {
                        User user = new User();
                        String p = user.getPassword();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@JsonIgnore'd"))
                .count();
        assertEquals(0, (int) count);
    }

    /**
     * JPA entities loaded from a repository have all fields populated, including those
     * annotated with {@code @JsonIgnore} (which only suppresses serialization). Accessing
     * such fields in a service method must NOT be flagged, otherwise the inspection
     * produces false positives on every entity with a lazy collection.
     */
    public void testNoReportForJpaEntityParameter() {
        this.myFixture.enableInspections(new SpringJsonIgnoreFieldAccessInspection());
        this.myFixture.configureByText("UserService.java", """
                @interface Service { }
                @interface Entity { }
                @interface JsonIgnore { }
                @interface OneToMany { }
                class Order { }
                @Entity
                class Customer {
                    String name;
                    @JsonIgnore
                    @OneToMany
                    java.util.List<Order> orders;
                    public String getName() { return name; }
                    public java.util.List<Order> getOrders() { return orders; }
                }
                @Service
                class CustomerService {
                    void process(Customer customer) {
                        String n = customer.getName();
                        java.util.List<Order> o = customer.getOrders(); // NOT flagged — entity from repo
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@JsonIgnore'd"))
                .count();
        assertEquals(0, (int) count);
    }

    /**
     * A JPA entity annotated with {@code @Valid} (or {@code @RequestBody}) is treated
     * as arriving from Jackson deserialization — the controller passed it in. In that
     * case {@code @JsonIgnore} fields ARE null and the inspection must flag them.
     */
    public void testReportsJpaEntityWithValidAnnotation() {
        this.myFixture.enableInspections(new SpringJsonIgnoreFieldAccessInspection());
        this.myFixture.configureByText("UserService.java", """
                @interface Service { }
                @interface Entity { }
                @interface JsonIgnore { }
                @interface Valid { }
                @Entity
                class Customer {
                    String name;
                    @JsonIgnore
                    String password;
                    public String getName() { return name; }
                    public String getPassword() { return password; }
                }
                @Service
                class CustomerService {
                    void process(@Valid Customer customer) {
                        String n = customer.getName();
                        String p = customer.getPassword(); // flagged — @Valid means Jackson source
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@JsonIgnore'd"))
                .count();
        assertEquals(1, (int) count);
    }

    /**
     * A plain DTO (no JPA annotations) passed into a service should still trigger the
     * inspection when a {@code @JsonIgnore} property is accessed, because such a DTO
     * most likely arrived from a controller via Jackson deserialization.
     */
    public void testReportsJsonIgnoreOnPlainDtoInService() {
        this.myFixture.enableInspections(new SpringJsonIgnoreFieldAccessInspection());
        this.myFixture.configureByText("UserService.java", """
                @interface Service { }
                @interface JsonIgnore { }
                class UserDto {
                    String name;
                    @JsonIgnore
                    String password;
                    public String getName() { return name; }
                    public String getPassword() { return password; }
                }
                @Service
                class UserService {
                    void validate(UserDto dto) {
                        String n = dto.getName();
                        String p = dto.getPassword(); // should flag — DTO from Jackson
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@JsonIgnore'd"))
                .count();
        assertEquals(1, (int) count);
    }

    /**
     * Private helper methods frequently receive DTOs that were created and enriched by
     * application code after Jackson deserialization. Without an explicit Jackson input
     * marker on the parameter, the inspection should not assume helper parameters came
     * directly from the HTTP body.
     */
    public void testNoReportForPrivateHelperParameter() {
        this.myFixture.enableInspections(new SpringJsonIgnoreFieldAccessInspection());
        this.myFixture.configureByText("ImportService.java", """
                @interface Service { }
                @interface JsonIgnore { }
                class ImportLineDto {
                    @JsonIgnore
                    Integer rowNumber;
                    public Integer getRowNumber() { return rowNumber; }
                    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }
                }
                @Service
                class ImportService {
                    void process(java.util.List<ImportLineDto> lines) {
                        for (ImportLineDto currentLine : lines) {
                            validateUniqueLine(currentLine);
                        }
                    }
                    private void validateUniqueLine(ImportLineDto currentLine) {
                        Integer row = currentLine.getRowNumber();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@JsonIgnore'd"))
                .count();
        assertEquals(0, (int) count);
    }

    public void testReportsPrivateHelperParameterWithRequestBodyMarker() {
        this.myFixture.enableInspections(new SpringJsonIgnoreFieldAccessInspection());
        this.myFixture.configureByText("ImportService.java", """
                @interface Service { }
                @interface RequestBody { }
                @interface JsonIgnore { }
                class ImportLineDto {
                    @JsonIgnore
                    Integer rowNumber;
                    public Integer getRowNumber() { return rowNumber; }
                }
                @Service
                class ImportService {
                    private void validateUniqueLine(@RequestBody ImportLineDto currentLine) {
                        Integer row = currentLine.getRowNumber();
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("@JsonIgnore'd"))
                .count();
        assertEquals(1, (int) count);
    }
}
