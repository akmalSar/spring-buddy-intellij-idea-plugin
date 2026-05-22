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
public class SpringRequestMappingMissingMethodInspectionTest extends BasePlatformTestCase {

    public void testReportsMissingMethod() {
        this.myFixture.enableInspections(new SpringRequestMappingMissingMethodInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class Sample {
                    @RequestMapping("/hello")
                    String <warning descr="@RequestMapping without explicit HTTP method maps all methods — prefer @GetMapping/@PostMapping">hello</warning>() { return "hi"; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithMethodAttribute() {
        this.myFixture.enableInspections(new SpringRequestMappingMissingMethodInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestMethod;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class Sample {
                    @RequestMapping(value = "/hello", method = RequestMethod.GET)
                    String hello() { return "hi"; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithComposedMapping() {
        this.myFixture.enableInspections(new SpringRequestMappingMissingMethodInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class Sample {
                    @GetMapping("/hello")
                    String hello() { return "hi"; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportOnNonController() {
        this.myFixture.enableInspections(new SpringRequestMappingMissingMethodInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.web.bind.annotation.RequestMapping;

                class Sample {
                    @RequestMapping("/hello")
                    String hello() { return "hi"; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportForNonSpringRequestMappingFqn() {
        this.myFixture.enableInspections(new SpringRequestMappingMissingMethodInspection());
        this.myFixture.addFileToProject("com/example/RequestMapping.java", """
                package com.example;
                public @interface RequestMapping { String value() default ""; }
                """);
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    @com.example.RequestMapping("/hello")
                    String hello() { return "hi"; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFixPreservesPathAndNameAttributes() {
        this.myFixture.enableInspections(new SpringRequestMappingMissingMethodInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface RequestMapping {
                    String value() default "";
                    String name() default "";
                }
                @interface GetMapping {
                    String value() default "";
                    String name() default "";
                }
                @interface RestController { }

                @RestController
                class Sample {
                    @<warning descr="@RequestMapping without explicit HTTP method maps all methods — prefer @GetMapping/@PostMapping">RequestMapping<caret></warning>(value = "/hello", name = "helloRoute")
                    String hello() { return "hi"; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);

        IntentionAction fix = this.myFixture.findSingleIntention("Replace with @GetMapping");
        this.myFixture.launchAction(fix);

        this.myFixture.checkResult("""
                @interface RequestMapping {
                    String value() default "";
                    String name() default "";
                }
                @interface GetMapping {
                    String value() default "";
                    String name() default "";
                }
                @interface RestController { }

                @RestController
                class Sample {
                    @GetMapping(value = "/hello", name = "helloRoute")
                    String hello() { return "hi"; }
                }
                """);
    }
}
