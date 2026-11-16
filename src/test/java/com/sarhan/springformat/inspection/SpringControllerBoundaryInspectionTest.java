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
public class SpringControllerBoundaryInspectionTest extends BasePlatformTestCase {

    public void testReportsEntityReturnFromController() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.EntityReturn());
        this.myFixture.configureByText("Sample.java", """
                @RestController
                @RestController
                class SampleController {
                    User <warning descr="[SB-ARCH-003 controller-entity-return] Controller returns a JPA entity directly. Why: serializing entities leaks the persistence model and can trigger lazy loading outside the transaction. Fix: Map the entity to a response DTO in the service or a mapper and return the DTO. Suppressing or working around this check is not a fix.">get</warning>() { return new User(); }
                }
                @Entity
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportDtoReturnFromController() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.EntityReturn());
        this.myFixture.configureByText("Sample.java", """
                @RestController
                class SampleController {
                    UserDto get() { return new UserDto(); }
                }
                class UserDto { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportResponseEntityMapOrResourceReturnFromController() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.EntityReturn());
        this.myFixture.configureByText("Sample.java", """
                import java.util.Map;
                
                @RestController
                class SampleController {
                    ResponseEntity<Map<String, String>> upload() { return null; }
                    ResponseEntity<Resource> download() { return null; }
                }
                class ResponseEntity<T> { }
                interface Resource { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportHttpEntityCustomResponseWrapperFromController() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.EntityReturn());
        this.myFixture.configureByText("Sample.java", """
                @RestController
                class SampleController {
                    HttpEntity<Base64File> getClientLogo() { return null; }
                }
                class HttpEntity<T> { }
                @Getter
                @RequiredArgsConstructor
                class Base64File {
                    private final String mimeType;
                    private final String filename;
                    private final String base64Data;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportPageOfProjectionInterfaceReturnFromController() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.EntityReturn());
        this.myFixture.configureByText("Sample.java", """
                @RestController
                class SampleController {
                    Page<ContractPartnerInfo> contractPartnerInfoByApartment() { return null; }
                }
                interface Page<T> { }
                @JsonDeserialize(as = ContractPartnerInfoImpl.class)
                interface ContractPartnerInfo { String getCustomerLastName(); }
                class ContractPartnerInfoImpl implements ContractPartnerInfo { public String getCustomerLastName() { return ""; } }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportDateReturnFromController() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.EntityReturn());
        this.myFixture.configureByText("Sample.java", """
                class SampleController {
                    Date nextDate(String processInstanceId) { return null; }
                }
                class Date { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsRepositoryInjectionInController() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.RepositoryInjection());
        this.myFixture.configureByText("Sample.java", """
                @RestController
                class SampleController {
                    UserRepository <warning descr="[SB-ARCH-002 controller-repository-injection] Controller injects a repository directly. Why: business rules and transaction boundaries belong in the service layer. Fix: Move the repository access into a service method and inject that service. Suppressing or working around this check is not a fix.">repository</warning>;
                }
                interface UserRepository { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsConstructorRepositoryInjectionOnParameterNameOnly() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.RepositoryInjection());
        this.myFixture.configureByText("Sample.java", """
                @RestController
                class SampleController {
                    SampleController(UserRepository <warning descr="[SB-ARCH-002 controller-repository-injection] Controller injects a repository directly. Why: business rules and transaction boundaries belong in the service layer. Fix: Move the repository access into a service method and inject that service. Suppressing or working around this check is not a fix.">repository</warning>) { }
                }
                interface UserRepository { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsTransactionalControllerMethod() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.TransactionalController());
        this.myFixture.configureByText("Sample.java", """
                @RestController
                class SampleController {
                    @Transactional
                    void <warning descr="Controller method should not be @Transactional; use service-layer transactions">save</warning>() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsEntityRequestBodyAndCollectionParameter() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.EntityParameter());
        this.myFixture.configureByText("Sample.java", """
                @interface RestController { }
                @interface PostMapping { }
                @interface RequestBody { }
                @interface Entity { }
                interface Box<T> { }
                @RestController
                class SampleController {
                    @PostMapping
                    void create(@RequestBody User <warning descr="[SB-ARCH-003 controller-entity-parameter] Controller endpoint accepts a JPA entity. Why: binding request data onto entities allows mass assignment of persistent fields. Fix: Bind a request DTO and let the service map it onto the entity. Suppressing or working around this check is not a fix.">user</warning>) { }
                    @PostMapping
                    void createAll(@RequestBody Box<User> <warning descr="[SB-ARCH-003 controller-entity-parameter] Controller endpoint accepts a JPA entity. Why: binding request data onto entities allows mass assignment of persistent fields. Fix: Bind a request DTO and let the service map it onto the entity. Suppressing or working around this check is not a fix.">users</warning>) { }
                }
                @Entity
                class User { }
                """);
        this.myFixture.checkHighlighting(true, false, true, true);
    }

    public void testDoesNotReportDtoParameterOrNonMappedMethod() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.EntityParameter());
        this.myFixture.configureByText("Sample.java", """
                @interface RestController { }
                @interface PostMapping { }
                @interface Entity { }
                @RestController
                class SampleController {
                    @PostMapping
                    void create(UserDto dto) { }
                    void helper(User user) { }
                }
                @Entity
                class User { }
                class UserDto { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        assertEmpty(this.myFixture.doHighlighting().stream()
                .filter(info -> info.getDescription() != null && info.getDescription().startsWith("[SB-ARCH-"))
                .toList());
    }

    public void testDoesNotReportEntityParameterOutsideController() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.EntityParameter());
        this.myFixture.configureByText("Sample.java", """
                @interface PostMapping { }
                @interface Entity { }
                class SampleService {
                    @PostMapping
                    void create(User user) { }
                }
                @Entity
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        assertEmpty(this.myFixture.doHighlighting().stream()
                .filter(info -> info.getDescription() != null && info.getDescription().startsWith("[SB-ARCH-"))
                .toList());
    }

    public void testControllerBoundaryPresetCanBeRaisedToError() {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", """
                { "version": 1, "presets": { "controllerBoundary": "error" } }
                """);
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.RepositoryInjection());
        this.myFixture.configureByText("Sample.java", """
                @RestController
                class SampleController {
                    UserRepository <error descr="[SB-ARCH-002 controller-repository-injection] Controller injects a repository directly. Why: business rules and transaction boundaries belong in the service layer. Fix: Move the repository access into a service method and inject that service. Suppressing or working around this check is not a fix.">repository</error>;
                }
                interface UserRepository { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testControllerBoundaryPresetCanBeTurnedOff() {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", """
                { "version": 1, "presets": { "controllerBoundary": "off" } }
                """);
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.RepositoryInjection(),
                new SpringControllerBoundaryInspection.EntityReturn());
        this.myFixture.configureByText("Sample.java", """
                @RestController
                class SampleController {
                    UserRepository repository;
                    User get() { return new User(); }
                }
                @Entity
                class User { }
                interface UserRepository { }
                """);
        assertEmpty(this.myFixture.doHighlighting().stream()
                .filter(info -> info.getDescription() != null && info.getDescription().startsWith("[SB-ARCH-"))
                .toList());
    }

    public void testDoesNotTreatUnrelatedOkFactoryAsResponseWrapper() {
        this.myFixture.enableInspections(new SpringControllerBoundaryInspection.EntityReturn());
        this.myFixture.configureByText("Sample.java", """
                @RestController
                class SampleController {
                    User get() { return MyWrapper.ok(new UserDto()); }
                }
                @Entity
                class User { }
                class UserDto { }
                class MyWrapper<T> {
                    static <T> MyWrapper<T> ok(T value) { return null; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
