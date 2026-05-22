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
 * Tests for {@link SpringOrphanedApplicationEventInspection}.
 *
 * @author Akmal Sarhan
 */
public class SpringOrphanedApplicationEventInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        SpringOrphanedApplicationEventInspection.clearProjectCache(getProject());
    }

    public void testReportsOrphanedPublisher() {
        this.myFixture.enableInspections(new SpringOrphanedApplicationEventInspection());
        this.myFixture.configureByText("Publisher.java", """
                class MyEvent { }
                class ApplicationEventPublisher {
                    void publishEvent(Object event) { }
                }
                class Publisher {
                    ApplicationEventPublisher ep = new ApplicationEventPublisher();
                    void publish() {
                        ep.publishEvent(new MyEvent());
                    }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("no matching @EventListener"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testReportsOrphanedListener() {
        this.myFixture.enableInspections(new SpringOrphanedApplicationEventInspection());
        this.myFixture.configureByText("Listener.java", """
                @interface EventListener { }
                class MyEvent { }
                class Listener {
                    @EventListener
                    void handle(MyEvent event) { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("is never published"))
                .count();
        assertEquals(1, (int) count);
    }

    public void testDoesNotReportWhenEventHasMatchingListener() {
        this.myFixture.enableInspections(new SpringOrphanedApplicationEventInspection());
        this.myFixture.configureByText("Publisher.java", """
                class MyEvent { }
                class ApplicationEventPublisher {
                    void publishEvent(Object event) { }
                }
                class Publisher {
                    ApplicationEventPublisher ep = new ApplicationEventPublisher();
                    void publish() {
                        ep.publishEvent(new MyEvent());
                    }
                }
                """);
        this.myFixture.configureByText("Listener.java", """
                @interface EventListener { }
                class MyEvent { }
                class Listener {
                    @EventListener
                    void handle(MyEvent event) { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && (h.getDescription().contains("no matching @EventListener")
                            || h.getDescription().contains("is never published")))
                .count();
        assertEquals(0, (int) count);
    }

    public void testDoesNotReportFrameworkLifecycleEventListener() {
        this.myFixture.enableInspections(new SpringOrphanedApplicationEventInspection());
        this.myFixture.configureByText("Listener.java", """
                package org.springframework.context.event;
                public class ContextRefreshedEvent extends org.springframework.context.ApplicationEvent {
                    public ContextRefreshedEvent(Object source) { super(source); }
                }
                """);
        this.myFixture.configureByText("AppListener.java", """
                import org.springframework.context.event.ContextRefreshedEvent;
                import org.springframework.context.event.EventListener;
                class AppListener {
                    @EventListener
                    void onContextRefreshed(ContextRefreshedEvent event) { }
                }
                """);
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        long count = highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("is never published"))
                .count();
        assertEquals(0, (int) count);
    }
}
