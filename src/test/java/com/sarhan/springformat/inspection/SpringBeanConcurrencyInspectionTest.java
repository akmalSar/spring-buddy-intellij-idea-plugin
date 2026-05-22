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
public class SpringBeanConcurrencyInspectionTest extends BasePlatformTestCase {

    public void testFlagsMutableStateInSingletonBeanAndCanMakeFinal() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.StatefulSingleton());
        this.myFixture.configureByText("Sample.java", """
                @interface Service { }
                @Service
                class SampleService {
                    private int <warning descr="Mutable instance field in singleton Spring bean can be shared across threads; prefer request/local state or a thread-safe type"><caret>counter</warning>;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Make field final");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("private final int counter;"));
    }

    public void testCanMarkMutableStateVolatile() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.StatefulSingleton());
        this.myFixture.configureByText("Sample.java", """
                @interface Service { }
                @Service
                class SampleService {
                    private boolean <warning descr="Mutable instance field in singleton Spring bean can be shared across threads; prefer request/local state or a thread-safe type"><caret>running</warning>;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Mark field volatile");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("private volatile boolean running;"));
    }

    public void testCanConvertMutableReferenceStateToAtomicReference() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.StatefulSingleton());
        this.myFixture.configureByText("Sample.java", """
                @interface Service { }
                @Service
                class SampleService {
                    private Status <warning descr="Mutable instance field in singleton Spring bean can be shared across threads; prefer request/local state or a thread-safe type"><caret>status</warning> = Status.NEW;
                    Status getStatus() { return this.status; }
                    void setStatus(Status status) { this.status = status; }
                }
                enum Status { NEW }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert field to atomic type");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text, text.contains("private java.util.concurrent.atomic.AtomicReference<Status> status = new java.util.concurrent.atomic.AtomicReference<>(Status.NEW);"));
        assertTrue(text, text.contains("return this.status.get();"));
        assertTrue(text, text.contains("this.status.set(status);"));
    }

    public void testCanConvertPrimitiveLongToAtomicLong() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.StatefulSingleton());
        this.myFixture.configureByText("Sample.java", """
                @interface Service { }
                @Service
                class SampleService {
                    private long <warning descr="Mutable instance field in singleton Spring bean can be shared across threads; prefer request/local state or a thread-safe type"><caret>lastCounter</warning> = -1;
                    long getCounter() { return this.lastCounter; }
                    void setCounter(long c) { this.lastCounter = c; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert field to atomic type");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text, text.contains("private java.util.concurrent.atomic.AtomicLong lastCounter = new java.util.concurrent.atomic.AtomicLong(-1);"));
        assertTrue(text, text.contains("return this.lastCounter.get();"));
        assertTrue(text, text.contains("this.lastCounter.set(c);"));
    }

    public void testDoesNotFlagInjectedDependencyOrFinalField() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.StatefulSingleton());
        this.myFixture.configureByText("Sample.java", """
                @interface Service { }
                @interface Autowired { }
                @Service
                class SampleService {
                    @Autowired private Repository repository;
                    private final int counter = 0;
                }
                interface Repository { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagResolvedSpringComponentDependencyField() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.StatefulSingleton());
        this.myFixture.configureByText("Sample.java", """
                @interface Component { }
                @Component
                class AAXServiceDispatcherImpl {
                    WebServiceUtils webServiceUtils;
                    AAXDispatcherBusinessService businessService;
                }

                @Component
                class WebServiceUtils { }

                @Component
                class AAXDispatcherBusinessService { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagLombokAllArgsConstructorDependencyField() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.StatefulSingleton());
        this.myFixture.configureByText("Sample.java", """
                @interface Component { }
                @interface AllArgsConstructor { }
                @Component
                @AllArgsConstructor
                class AAXServiceDispatcherImpl {
                    WebServiceUtils webServiceUtils;
                }

                class WebServiceUtils { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsSharedMutableCacheAndConvertsToConcurrentHashMap() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.SharedMutableCache());
        this.myFixture.configureByText("Sample.java", """
                import java.util.HashMap;
                import java.util.Map;
                @interface Component { }
                @Component
                class SampleComponent {
                    private Map<String, String> <warning descr="Shared mutable cache inside singleton bean should use ConcurrentHashMap or explicit locking"><caret>cache</warning> = new HashMap<>();
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagConcurrentMapCache() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.SharedMutableCache());
        this.myFixture.configureByText("Sample.java", """
                import java.util.concurrent.ConcurrentHashMap;
                import java.util.concurrent.ConcurrentMap;
                @interface Component { }
                @Component
                class SampleComponent {
                    private ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagMapInterfaceBackedByConcurrentHashMap() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.SharedMutableCache());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                import java.util.Map;
                import java.util.concurrent.ConcurrentHashMap;
                @interface Component { }
                @Component
                class AnnotatedClassProvider {
                    private final Map<String, List<String>> annotated = new ConcurrentHashMap<>();
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagConfigurationPropertiesFieldsAsStatefulSingleton() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.StatefulSingleton());
        this.myFixture.configureByText("Sample.java", """
                @interface Configuration { }
                @interface ConfigurationProperties { String value(); }
                @interface Data { }
                @Data
                @Configuration
                @ConfigurationProperties("actuator")
                class ActuatorConfigurationProperties {
                    private boolean enabled;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsAsyncMutationOfSharedBeanState() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.AsyncSharedMutableState());
        this.myFixture.configureByText("Sample.java", """
                import java.util.ArrayList;
                import java.util.List;
                @interface Service { }
                @interface Async { }
                @Service
                class SampleService {
                    private List<String> events = new ArrayList<>();
                    @Async
                    void publish(String value) {
                        <warning descr="@Async method mutates shared bean state without a thread-safe type or explicit locking">this.events.add(value)</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsAsyncAssignmentToSharedBeanState() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.AsyncSharedMutableState());
        this.myFixture.configureByText("Sample.java", """
                @interface Service { }
                @interface Async { }
                @Service
                class SampleService {
                    private String status;
                    @Async
                    void publish(String value) {
                        this.<warning descr="@Async method writes shared mutable bean state without synchronization">status</warning> = value;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagThreadSafeConcurrentTypes() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.StatefulSingleton());
        this.myFixture.configureByText("Sample.java", """
                @interface Component { }
                import java.util.concurrent.CountDownLatch;
                import java.util.concurrent.atomic.AtomicLong;
                @Component
                class Sample {
                    private CountDownLatch latch = new CountDownLatch(1);
                    private final AtomicLong counter = new AtomicLong();
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagIntellijServiceComponent() {
        this.myFixture.enableInspections(new SpringBeanConcurrencyInspection.StatefulSingleton());
        this.myFixture.configureByText("IntellijService.java", """
                package com.intellij.openapi.components;
                public @interface Service { }
                """);
        this.myFixture.configureByText("MyService.java", """
                import com.intellij.openapi.components.Service;
                @Service
                public class MyService {
                    private int counter;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
