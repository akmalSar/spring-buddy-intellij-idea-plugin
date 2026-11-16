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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class SpringConstructorInjectionInspectionTest extends BasePlatformTestCase {

    private static final String SOURCE = """
            @interface Service { }
            @interface Autowired { }
            @interface Inject { }
            @interface Value { }
            @Service
            class OrderService {
                @Autowired
                OrderRepository %s;
                @Inject
                Clock %s;
                @Value
                String name;
                private final Mailer mailer;
                OrderService(Mailer mailer) { this.mailer = mailer; }
            }
            class Helper {
                @Autowired
                OrderRepository repository;
            }
            interface OrderRepository { }
            interface Clock { }
            interface Mailer { }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new SpringConstructorInjectionInspection());
    }

    public void testOffByDefault() {
        this.myFixture.configureByText("OrderService.java", SOURCE.formatted("repository", "clock"));
        assertEquals(0, architectureProblems());
    }

    public void testReportsFieldInjectionWhenEnabled() {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", """
                { "version": 1, "presets": { "constructorInjection": "warning" } }
                """);
        String warning = "<warning descr=\"[SB-ARCH-005 constructor-injection] Field injection in a Spring component. "
                + "Why: hidden dependencies cannot be final and the class cannot be created without the container. "
                + "Fix: Declare the field final and inject it through the constructor. Suppressing or working around "
                + "this check is not a fix.\">%s</warning>";
        this.myFixture.configureByText("OrderService.java",
                SOURCE.formatted(warning.formatted("repository"), warning.formatted("clock")));
        this.myFixture.checkHighlighting(true, false, true, true);
        assertEquals(2, architectureProblems());
    }

    private long architectureProblems() {
        return this.myFixture.doHighlighting().stream()
                .filter((HighlightInfo info) -> info.getDescription() != null
                        && info.getDescription().startsWith("[SB-ARCH-"))
                .count();
    }
}
