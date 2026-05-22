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
public class SpringLeakyValueInspectionTest extends BasePlatformTestCase {

    public void testFlagsValueInNonBeanClass() {
        this.myFixture.enableInspections(new SpringLeakyValueInspection());
        this.myFixture.configureByText("Sample.java", """
                class SampleClass {
                    <error descr="@Value field in non-Spring-managed class will not be injected; annotate the class with @Component">@Value("${app.name}")</error>
                    String appName;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagValueInComponent() {
        this.myFixture.enableInspections(new SpringLeakyValueInspection());
        this.myFixture.configureByText("Sample.java", """
                @Component
                class SampleClass {
                    @Value("${app.name}")
                    String appName;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsAutowiredInNonBeanClass() {
        this.myFixture.enableInspections(new SpringLeakyValueInspection());
        this.myFixture.configureByText("Sample.java", """
                class SampleClass {
                    <error descr="@Value field in non-Spring-managed class will not be injected; annotate the class with @Component">@Autowired</error>
                    Object dependency;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagValueInTestClass() {
        this.myFixture.enableInspections(new SpringLeakyValueInspection());
        this.myFixture.configureByText("SampleTest.java", """
                class SampleTest {
                    @Value("${app.name}")
                    String appName;
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }

    public void testDoesNotFlagValueInIntegrationTestClass() {
        this.myFixture.enableInspections(new SpringLeakyValueInspection());
        this.myFixture.configureByText("SampleIT.java", """
                class SampleIT {
                    @Value("${app.name}")
                    String appName;
                }
                """);
        this.myFixture.checkHighlighting(false, false, false, true);
    }
}
