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
                    User <warning descr="Controller returns a JPA entity directly; return a DTO or response model instead">get</warning>() { return new User(); }
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
                    <warning descr="Controller should not inject repositories directly; use a service boundary">UserRepository repository</warning>;
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
