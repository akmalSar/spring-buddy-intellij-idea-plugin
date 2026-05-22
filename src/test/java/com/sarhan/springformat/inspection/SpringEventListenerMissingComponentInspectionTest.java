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
public class SpringEventListenerMissingComponentInspectionTest extends BasePlatformTestCase {

    private static final String EVENT_LISTENER_STUB = """
            package org.springframework.context.event;
            public @interface EventListener { }
            """;

    private static final String TRANSACTIONAL_EVENT_LISTENER_STUB = """
            package org.springframework.transaction.event;
            public @interface TransactionalEventListener { }
            """;

    private static final String COMPONENT_STUB = """
            package org.springframework.stereotype;
            public @interface Component { }
            """;

    private static final String SERVICE_STUB = """
            package org.springframework.stereotype;
            public @interface Service { }
            """;

    private static final String SPRING_BOOT_APPLICATION_STUB = """
            package org.springframework.boot.autoconfigure;
            public @interface SpringBootApplication { }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("org/springframework/context/event/EventListener.java",
                EVENT_LISTENER_STUB);
        this.myFixture.addFileToProject("org/springframework/transaction/event/TransactionalEventListener.java",
                TRANSACTIONAL_EVENT_LISTENER_STUB);
        this.myFixture.addFileToProject("org/springframework/stereotype/Component.java",
                COMPONENT_STUB);
        this.myFixture.addFileToProject("org/springframework/stereotype/Service.java",
                SERVICE_STUB);
        this.myFixture.addFileToProject("org/springframework/boot/autoconfigure/SpringBootApplication.java",
                SPRING_BOOT_APPLICATION_STUB);
    }

    public void testFlagsEventListenerInNonBeanClass() {
        this.myFixture.enableInspections(new SpringEventListenerMissingComponentInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    <error descr="@EventListener method in non-Spring-managed class will never be invoked; annotate the class with @Component">@EventListener</error>
                    void onEvent(Object event) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagEventListenerInComponent() {
        this.myFixture.enableInspections(new SpringEventListenerMissingComponentInspection());
        this.myFixture.configureByText("Sample.java", """
                @Component
                class Sample {
                    @EventListener
                    void onEvent(Object event) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsTransactionalEventListenerInNonBeanClass() {
        this.myFixture.enableInspections(new SpringEventListenerMissingComponentInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    <error descr="@EventListener method in non-Spring-managed class will never be invoked; annotate the class with @Component">@TransactionalEventListener</error>
                    void onEvent(Object event) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagEventListenerInService() {
        this.myFixture.enableInspections(new SpringEventListenerMissingComponentInspection());
        this.myFixture.configureByText("Sample.java", """
                @Service
                class Sample {
                    @EventListener
                    void onEvent(Object event) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagEventListenerInSpringBootApplication() {
        this.myFixture.enableInspections(new SpringEventListenerMissingComponentInspection());
        this.myFixture.configureByText("Sample.java", """
                @SpringBootApplication
                class Sample {
                    @EventListener
                    void onEvent(Object event) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFixAddsComponentAnnotation() {
        this.myFixture.enableInspections(new SpringEventListenerMissingComponentInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    <error descr="@EventListener method in non-Spring-managed class will never be invoked; annotate the class with @Component"><caret>@EventListener</error>
                    void onEvent(Object event) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Add @Component");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("Expected @Component annotation on class, got: " + text,
                text.contains("org.springframework.stereotype.Component"));
    }
}
