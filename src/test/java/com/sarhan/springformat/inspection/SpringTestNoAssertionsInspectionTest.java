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
public class SpringTestNoAssertionsInspectionTest extends BasePlatformTestCase {

    private static final String JUPITER_TEST_STUB = """
            package org.junit.jupiter.api;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            public @interface Test { }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("org/junit/jupiter/api/Test.java", JUPITER_TEST_STUB);
    }

    public void testFlagsTestMethodWithNoAssertion() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    @Test
                    void <warning descr="Test method 'doesNothing' has no assertion or verify() call">doesNothing</warning>() {
                        int x = 1 + 1;
                        System.out.println(x);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagTestMethodWithAssertThat() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static <T> Object assertThat(T t) { return null; }
                    @Test
                    void asserts() {
                        assertThat(1).isEqualTo(1);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagTestMethodWithVerify() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static Object verify(Object o) { return null; }
                    @Test
                    void verifies() {
                        verify(new Object());
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagArchUnitCheck() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
                class SampleTests {
                    @Test
                    void givenClasses_whenRestControllerHasCorrectProduces_thenSuccess() {
                        ArchRuleDefinition.classes()
                            .that()
                            .areAnnotatedWith(RestController.class)
                            .should(PRODUCES_CLASS_CONDITION)
                            .check(CLASSES);
                    }

                    static class RestController { }
                    static Object PRODUCES_CLASS_CONDITION;
                    static Object CLASSES;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagArchUnitCheckWithOrShould() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    @Test
                    void givenClasses_whenMethodHasCorrectProduces_thenSuccess() {
                        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                            .that()
                            .areAnnotatedWith(RestController.class)
                            .or()
                            .areAnnotatedWith(Controller.class)
                            .should(PRODUCES_METHOD_CONDITION)
                            .orShould(PRODUCES_CLASS_CONDITION)
                            .check(CLASSES);
                    }

                    static class RestController { }
                    static class Controller { }
                    static Object PRODUCES_METHOD_CONDITION;
                    static Object PRODUCES_CLASS_CONDITION;
                    static Object CLASSES;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagArchUnitCheckWithShouldFluentCondition() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    @Test
                    void givenClasses_whenNameEndingWithController_thenSuccess() {
                        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                            .that()
                            .areAnnotatedWith(RestController.class)
                            .or()
                            .areAnnotatedWith(Controller.class)
                            .should()
                            .haveSimpleNameEndingWith("Controller")
                            .check(CLASSES);
                    }

                    static class RestController { }
                    static class Controller { }
                    static Object CLASSES;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagArchUnitMethodsCheckWithOrShould() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    @Test
                    void givenMethods_whenAnnotatedWithAnyRequestMappingShouldOnlyBeInController_thenSuccess() {
                        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods()
                            .that()
                            .areAnnotatedWith(GetMapping.class)
                            .or()
                            .areAnnotatedWith(PostMapping.class)
                            .should()
                            .beDeclaredInClassesThat()
                            .areAnnotatedWith(Controller.class)
                            .orShould()
                            .beDeclaredInClassesThat()
                            .areAnnotatedWith(RestController.class)
                            .check(CLASSES);
                    }

                    static class GetMapping { }
                    static class PostMapping { }
                    static class Controller { }
                    static class RestController { }
                    static Object CLASSES;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagArchUnitMethodsCheckWithAndShould() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    @Test
                    void givenMethods_whenReturningPageShouldHavePageableWithAnnotationPageableDefault_thenSuccess() {
                        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods()
                            .that()
                            .areAnnotatedWith(GetMapping.class)
                            .or()
                            .areAnnotatedWith(PostMapping.class)
                            .should()
                            .beDeclaredInClassesThat()
                            .areAnnotatedWith(Controller.class)
                            .orShould()
                            .beDeclaredInClassesThat()
                            .areAnnotatedWith(RestController.class)
                            .andShould(METHOD_WITH_RETURN_TYPE_PAGE_HAS_PAGEABLE_ANNOTATION_CONDITION)
                            .check(CLASSES);
                    }

                    static class GetMapping { }
                    static class PostMapping { }
                    static class Controller { }
                    static class RestController { }
                    static Object METHOD_WITH_RETURN_TYPE_PAGE_HAS_PAGEABLE_ANNOTATION_CONDITION;
                    static Object CLASSES;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagArchUnitFieldsCheck() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    @Test
                    void givenClasses_whenCheckingNotNullShouldNotBeAnnotatedWithNotBlank_thenSuccess() {
                        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields()
                            .that()
                            .areAnnotatedWith(NotNull.class)
                            .should()
                            .notBeAnnotatedWith(NotBlank.class)
                            .check(EUROPACE_ENTITY_CLASSES);
                    }

                    static class NotNull { }
                    static class NotBlank { }
                    static Object EUROPACE_ENTITY_CLASSES;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagWebTestClientExpectations() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    WebTestClient webTestClient;
                    @Test
                    void given_classWithEntityDropdownField_when_callApi_Then_getDropDownValues() {
                        this.webTestClient.get()
                            .uri("/api/dropDown")
                            .accept(MediaType.APPLICATION_JSON)
                            .exchange()
                            .expectStatus()
                            .isOk()
                            .expectBody()
                            .jsonPath("$[0].id")
                            .isEqualTo("1")
                            .jsonPath("$[1].id")
                            .isEqualTo("2");
                    }

                    static class MediaType { static Object APPLICATION_JSON; }
                    interface WebTestClient { Request get(); }
                    interface Request { Request uri(String uri); Request accept(Object mediaType); Response exchange(); }
                    interface Response { Status expectStatus(); Body expectBody(); }
                    interface Status { Response isOk(); }
                    interface Body { Body jsonPath(String path); Body isEqualTo(Object value); }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsUnrelatedCheckCall() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    @Test
                    void <warning descr="Test method 'onlyValidationCheck' has no assertion or verify() call">onlyValidationCheck</warning>() {
                        check(CLASSES);
                    }

                    static Object CLASSES;
                    static void check(Object value) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagTestCallingHelperWithAssertion() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                import java.util.List;
                import java.util.stream.Collectors;

                class SampleTests {
                    @Test
                    void givenEntityList_whenUsingSortedEntityList_thenSortedCorrectly() throws Exception {
                        List<EntityDependency> sortedDependencies = createOrderedEntityHierarchyForDeletion();
                        validateSortOrder(sortedDependencies);
                    }

                    private static void validateSortOrder(List<EntityDependency> entityDependencies) {
                        List<Class<?>> entityClasses = entityDependencies.stream()
                            .map(EntityDependency::getEntityClass)
                            .collect(Collectors.toList());
                        Assertions.assertThat(entityClasses.indexOf(Contact2ContactInformation.class))
                            .isLessThan(entityClasses.indexOf(ContactInformation.class));
                    }

                    static List<EntityDependency> createOrderedEntityHierarchyForDeletion() { return null; }
                    interface EntityDependency { Class<?> getEntityClass(); }
                    static class Contact2ContactInformation { }
                    static class ContactInformation { }
                    static class Assertions { static Assert assertThat(int value) { return null; } }
                    interface Assert { void isLessThan(int value); }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagTestCallingInheritedHelperWithAssertion() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests extends BaseTests {
                    @Test
                    void givenSimplestValidRequest_whenPost_thenSuccess() throws Exception {
                        performErstelleProvisionsForderungenRequestToCreateBusinessCase();
                    }
                }

                class BaseTests {
                    protected void performErstelleProvisionsForderungenRequestToCreateBusinessCase() throws Exception {
                        performErstelleProvisionsForderungenRequestToCreateBusinessCase("path.json");
                    }

                    protected void performErstelleProvisionsForderungenRequestToCreateBusinessCase(String path) throws Exception {
                        performErstelleProvisionsForderungenRequest(path);
                        Assertions.assertThat(getCreatedTestEntitiesForType(BusinessCase.class)).hasSize(1);
                    }

                    void performErstelleProvisionsForderungenRequest(String path) { }
                    Object getCreatedTestEntitiesForType(Class<?> type) { return null; }
                }

                class BusinessCase { }
                class Assertions { static Assert assertThat(Object value) { return null; } }
                interface Assert { void hasSize(int size); }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagNonTestMethod() {
        this.myFixture.enableInspections(new SpringTestNoAssertionsInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void plainMethod() {
                        int x = 1;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
