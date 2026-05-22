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
public class SpringConfigurationMutableStateInspectionTest extends BasePlatformTestCase {

    public void testReportsMutableField() {
        this.myFixture.enableInspections(new SpringConfigurationMutableStateInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Configuration { }

                @Configuration
                class Sample {
                    int <warning descr="Mutable instance field in @Configuration class is not thread-safe during context refresh — make it final or externalize state">counter</warning> = 0;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportFinalField() {
        this.myFixture.enableInspections(new SpringConfigurationMutableStateInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Configuration { }

                @Configuration
                class Sample {
                    final int counter = 0;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportAutowiredField() {
        this.myFixture.enableInspections(new SpringConfigurationMutableStateInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Configuration { }
                @interface Autowired { }

                @Configuration
                class Sample {
                    @Autowired
                    Object service;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportValueField() {
        this.myFixture.enableInspections(new SpringConfigurationMutableStateInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Configuration { }
                @interface Value { String value(); }

                @Configuration
                class Sample {
                    @Value("${prop}")
                    String prop;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportOnNonConfiguration() {
        this.myFixture.enableInspections(new SpringConfigurationMutableStateInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    int counter = 0;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
