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
public class SpringRemainingPitfallInspectionTest extends BasePlatformTestCase {

    public void testReportsServiceMultipleRepositoriesWithoutTransaction() {
        this.myFixture.enableInspections(new SpringRemainingPitfallInspection.ServiceMultipleRepositoriesWithoutTransaction());
        this.myFixture.configureByText("Sample.java", """
                @Service
                class SampleService {
                    UserRepository userRepository;
                    OrderRepository orderRepository;
                    void <warning descr="Service method uses multiple repositories without an explicit @Transactional boundary">save</warning>() { userRepository.save(new Object()); orderRepository.save(new Object()); }
                }
                interface UserRepository { void save(Object value); }
                interface OrderRepository { void save(Object value); }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportMultipleRepositoriesReadOnly() {
        this.myFixture.enableInspections(new SpringRemainingPitfallInspection.ServiceMultipleRepositoriesWithoutTransaction());
        this.myFixture.configureByText("Sample.java", """
                @Service
                class SampleService {
                    UserRepository userRepository;
                    OrderRepository orderRepository;
                    void load() { userRepository.findAll(); orderRepository.findAll(); }
                }
                interface UserRepository { java.util.List findAll(); }
                interface OrderRepository { java.util.List findAll(); }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportMultipleRepositoriesWithTransactionalOnClass() {
        this.myFixture.enableInspections(new SpringRemainingPitfallInspection.ServiceMultipleRepositoriesWithoutTransaction());
        this.myFixture.configureByText("Sample.java", """
                @interface Transactional { }
                @Transactional
                @Service
                class SampleService {
                    UserRepository userRepository;
                    OrderRepository orderRepository;
                    void save() { userRepository.save(new Object()); orderRepository.save(new Object()); }
                }
                interface UserRepository { void save(Object value); }
                interface OrderRepository { void save(Object value); }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsSpringBootTestSliceCandidate() {
        this.myFixture.enableInspections(new SpringRemainingPitfallInspection.SpringBootTestSlice());
        this.myFixture.configureByText("SampleTest.java", """
                @SpringBootTest
                class <warning descr="@SpringBootTest may be broader than needed; consider a focused test slice">SampleTest</warning> { MockMvc mvc; }
                class MockMvc { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsMockBeanContextCacheFragmentation() {
        this.myFixture.enableInspections(new SpringRemainingPitfallInspection.MockBeanContextCache());
        this.myFixture.configureByText("SampleTest.java", """
                @SpringBootTest
                class SampleTest {
                    <warning descr="@MockBean changes the Spring test context cache key; centralize common mocks to avoid extra context loads">@MockBean</warning>
                    EmailService emailService;
                }
                class EmailService { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportMockBeanOutsideSpringTestContext() {
        this.myFixture.enableInspections(new SpringRemainingPitfallInspection.MockBeanContextCache());
        this.myFixture.configureByText("SampleTest.java", """
                class SampleTest {
                    @MockBean
                    EmailService emailService;
                }
                class EmailService { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsConfigurationPropertiesMissingValidated() {
        this.myFixture.enableInspections(new SpringRemainingPitfallInspection.ConfigurationPropertiesMissingValidated());
        this.myFixture.configureByText("SampleProperties.java", """
                @ConfigurationProperties
                class <warning descr="@ConfigurationProperties uses constraints but is missing @Validated">SampleProperties</warning> { @NotNull String url; }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsScheduledWithoutLock() {
        this.myFixture.enableInspections(new SpringRemainingPitfallInspection.ScheduledWithoutLock());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    @Scheduled(cron = "*")
                    void <warning descr="@Scheduled method may run on every cluster node; consider a distributed lock such as @SchedulerLock">run</warning>() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsServiceSwallowedException() {
        this.myFixture.enableInspections(new SpringRemainingPitfallInspection.ServiceSwallowedException());
        this.myFixture.configureByText("SampleService.java", """
                class SampleService {
                    void run() { try { work(); } catch (<warning descr="Service method catches a broad exception without rethrowing it">Exception</warning> ex) { log(ex); } }
                    void work() throws Exception { }
                    void log(Exception ex) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
