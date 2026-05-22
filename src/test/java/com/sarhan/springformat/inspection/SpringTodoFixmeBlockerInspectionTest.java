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
public class SpringTodoFixmeBlockerInspectionTest extends BasePlatformTestCase {

    public void testFlagsTodoInServiceWithoutTicket() {
        this.myFixture.enableInspections(new SpringTodoFixmeBlockerInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Service {}
                @Service
                class Sample {
                    <warning descr="TODO/FIXME in Spring component without a ticket reference (e.g. ABC-123 or #123)">// TODO clean this up</warning>
                    void m() {}
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagTodoWithTicket() {
        this.myFixture.enableInspections(new SpringTodoFixmeBlockerInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Service {}
                @Service
                class Sample {
                    // TODO ABC-123 clean this up
                    void m() {}
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagTodoOutsideSpringComponent() {
        this.myFixture.enableInspections(new SpringTodoFixmeBlockerInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    // TODO clean this up
                    void m() {}
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsFixmeInRestController() {
        this.myFixture.enableInspections(new SpringTodoFixmeBlockerInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface RestController {}
                @RestController
                class Sample {
                    <warning descr="TODO/FIXME in Spring component without a ticket reference (e.g. ABC-123 or #123)">// FIXME race condition here</warning>
                    void m() {}
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
