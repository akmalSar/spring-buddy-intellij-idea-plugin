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
public class SpringDuplicateRequestMappingInspectionTest extends BasePlatformTestCase {

    public void testReportsDuplicateGetMapping() {
        this.myFixture.enableInspections(new SpringDuplicateRequestMappingInspection());
        this.myFixture.configureByText("Controller.java", """
                @interface RestController { }
                @interface GetMapping { String value() default ""; }
                @RestController
                class Controller {
                    @GetMapping("/users")
                    String <error descr="Duplicate Spring MVC mapping 'GET /users params= headers= consumes= produces=' also declared by method 'second'; application startup fails with ambiguous mapping">first</error>() { return ""; }
                    @GetMapping("/users")
                    String <error descr="Duplicate Spring MVC mapping 'GET /users params= headers= consumes= produces=' also declared by method 'first'; application startup fails with ambiguous mapping">second</error>() { return ""; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportDifferentHttpMethods() {
        this.myFixture.enableInspections(new SpringDuplicateRequestMappingInspection());
        this.myFixture.configureByText("Controller.java", """
                @interface RestController { }
                @interface GetMapping { String value() default ""; }
                @interface PostMapping { String value() default ""; }
                @RestController
                class Controller {
                    @GetMapping("/users")
                    String first() { return ""; }
                    @PostMapping("/users")
                    String second() { return ""; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
