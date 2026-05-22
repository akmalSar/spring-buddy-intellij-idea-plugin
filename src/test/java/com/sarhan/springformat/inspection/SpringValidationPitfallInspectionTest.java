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
public class SpringValidationPitfallInspectionTest extends BasePlatformTestCase {

    public void testReportsAndFixesNestedDtoMissingValid() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.NestedDtoValid());
        this.myFixture.configureByText("Sample.java", """
                class CreateUserRequest {
                    AddressRequest <warning descr="Nested DTO field has validation constraints but is missing @Valid"><caret>address</warning>;
                }
                class AddressRequest { @NotBlank String street; }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Add @Valid annotation");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("import javax.validation.Valid;"));
        assertTrue(text.contains("@Valid"));
    }

    public void testAddValidUsesJavaxWhenProjectUsesJavaxValidation() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.NestedDtoValid());
        this.myFixture.configureByText("Sample.java", """
                import javax.validation.constraints.NotBlank;
                
                class CreateUserRequest {
                    AddressRequest <warning descr="Nested DTO field has validation constraints but is missing @Valid"><caret>address</warning>;
                }
                class AddressRequest { @NotBlank String street; }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Add @Valid annotation");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("import javax.validation.Valid;"));
        assertTrue(text.contains("@Valid"));
    }

    public void testReportsNestedObjectWithConstraintsEvenWithoutDtoName() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.NestedDtoValid());
        this.myFixture.configureByText("Sample.java", """
                class CreateUserRequest {
                    PostalAddress <warning descr="Nested DTO field has validation constraints but is missing @Valid">address</warning>;
                }
                class PostalAddress { @NotBlank String street; }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsBindingResultWrongOrder() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.BindingResultOrder());
        this.myFixture.configureByText("Sample.java", """
                class Controller {
                    void save(@Valid Request request, String other, <error descr="BindingResult must immediately follow the @Valid/@Validated parameter it belongs to"><caret>BindingResult result</error>) { }
                }
                class Request { }
                class BindingResult { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Move BindingResult after validated parameter");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("void save(@Valid Request request, BindingResult result, String other)"));
    }

    public void testMovesBindingResultWhenUsingSpringType() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.BindingResultOrder());
        this.myFixture.configureByText("Sample.java", """
                class Controller {
                    void save(@Valid Request request, String other, <error descr="BindingResult must immediately follow the @Valid/@Validated parameter it belongs to"><caret>org.springframework.validation.BindingResult result</error>) { }
                }
                class Request { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Move BindingResult after validated parameter");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText()
                .contains("void save(@Valid Request request, org.springframework.validation.BindingResult result, String other)"));
    }

    public void testReportsMissingValidatedForMethodConstraints() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.MissingValidated());
        this.myFixture.configureByText("Sample.java", """
                @Service
                class UserService {
                    void find(@NotNull <warning descr="Constraint annotations on method parameters require @Validated on the containing class"><caret>String id</warning>) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Add @Validated annotation");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("import org.springframework.validation.annotation.Validated;"));
        assertTrue(text.contains("@Validated"));
    }

    public void testDoesNotOfferValidatedFixWhenClassAlreadyValidated() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.MissingValidated());
        this.myFixture.configureByText("Sample.java", """
                @Validated
                class Service {
                    void first(@NotNull String id) { }
                    void second(@NotNull String id) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotOfferValidatedFixWhenSuperClassAlreadyValidated() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.MissingValidated());
        this.myFixture.configureByText("Sample.java", """
                @Validated
                class BaseService {
                }
                @Service
                class UserService extends BaseService {
                    void find(@NotNull String id) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportBindingResultWhenNoValidatedParam() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.BindingResultOrder());
        this.myFixture.configureByText("Sample.java", """
                class ValidatorImpl implements SomeValidator {
                    public void validate(Object target, Errors errors) { }
                }
                interface SomeValidator {
                    void validate(Object target, Errors errors);
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportMissingValidatedForNonSpringComponentWithNotNullParameters() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.MissingValidated());
        this.myFixture.configureByText("SpringAssertJChainableInspection.java", """
                package com.sarhan.springformat.inspection;

                import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
                import com.intellij.codeInspection.ProblemsHolder;
                import com.intellij.psi.JavaElementVisitor;
                import com.intellij.psi.PsiCodeBlock;
                import com.intellij.psi.PsiElementVisitor;
                import org.jetbrains.annotations.NotNull;

                public class SpringAssertJChainableInspection extends AbstractBaseJavaLocalInspectionTool {
                    @Override
                    @NotNull
                    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
                        return new JavaElementVisitor() {
                            @Override
                            public void visitCodeBlock(@NotNull PsiCodeBlock block) {
                                super.visitCodeBlock(block);
                            }
                        };
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagStaticFieldAsNestedDtoMissingValid() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.NestedDtoValid());
        this.myFixture.configureByText("Sample.java", """
                class Container {
                    static AddressRequest address;
                }
                class AddressRequest { @NotBlank String street; }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagIntellijServiceAsSpringComponent() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.MissingValidated());
        this.myFixture.configureByText("IntellijService.java", """
                package com.intellij.openapi.components;
                public @interface Service {
                    Level value() default Level.APP;
                    enum Level { APP, PROJECT }
                }
                """);
        this.myFixture.configureByText("MyService.java", """
                import com.intellij.openapi.components.Service;
                import org.jetbrains.annotations.NotNull;
                @Service(Service.Level.APP)
                public class MyService {
                    public void load(@NotNull String state) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagJetbrainsNotNullAsConstraint() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.MissingValidated());
        this.myFixture.configureByText("Sample.java", """
                import org.jetbrains.annotations.NotNull;
                @Service
                class UserService {
                    void load(@NotNull String state) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportMissingValidatedForOverrideMethod() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.MissingValidated());
        this.myFixture.configureByText("Sample.java", """
                @Service
                class UserService extends BaseService {
                    @Override
                    void find(@NotNull String id) { }
                }
                class BaseService {
                    void find(String id) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportMissingValidatedForRestController() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.MissingValidated());
        this.myFixture.configureByText("Sample.java", """
                @RestController
                class AddressController {
                    void find(@NotNull String id) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportMissingValidatedForRepository() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.MissingValidated());
        this.myFixture.configureByText("Sample.java", """
                @Repository
                interface InvoiceRepository {
                    void deleteById(@NotNull UUID id);
                }
                class UUID { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    /**
     * JPA entities are not DTOs — {@code @Valid} cascading on entity fields is a
     * different concern. The nested-DTO inspection must not flag fields inside
     * {@code @Entity}, {@code @MappedSuperclass}, or {@code @Embeddable} classes.
     */
    public void testDoesNotFlagNestedDtoInsideJpaEntity() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.NestedDtoValid());
        this.myFixture.configureByText("Sample.java", """
                @interface Entity { }
                @interface NotBlank { }
                @Entity
                class Customer {
                    Address address; // should NOT be flagged — this is an entity, not a DTO
                }
                class Address {
                    @NotBlank String street;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    /**
     * A non-DTO class that references a JPA entity field should NOT be flagged.
     * The entity is a domain object, not a nested DTO, so {@code @Valid} cascading
     * is not the correct pattern.
     */
    public void testDoesNotFlagFieldWhoseTypeIsJpaEntity() {
        this.myFixture.enableInspections(new SpringValidationPitfallInspection.NestedDtoValid());
        this.myFixture.configureByText("Sample.java", """
                @interface Entity { }
                @interface NotBlank { }
                @Entity
                class Booking {
                    @NotBlank String reference;
                }
                class BookingExportContext {
                    Booking booking; // should NOT be flagged — Booking is an entity, not a DTO
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
