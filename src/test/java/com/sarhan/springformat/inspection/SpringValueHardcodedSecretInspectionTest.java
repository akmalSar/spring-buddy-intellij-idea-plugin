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
public class SpringValueHardcodedSecretInspectionTest extends BasePlatformTestCase {

    public void testReportsHardcodedPassword() {
        this.myFixture.enableInspections(new SpringValueHardcodedSecretInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Value { String value(); }
                @interface Component { }

                @Component
                class Sample {
                    @Value(<error descr="Hardcoded secret in @Value field — use a property placeholder (${...}) or externalized config instead">"superSecret123"</error>)
                    String dbPassword;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithPropertyPlaceholder() {
        this.myFixture.enableInspections(new SpringValueHardcodedSecretInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Value { String value(); }
                @interface Component { }

                @Component
                class Sample {
                    @Value("${db.password}")
                    String dbPassword;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithSpEL() {
        this.myFixture.enableInspections(new SpringValueHardcodedSecretInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Value { String value(); }
                @interface Component { }

                @Component
                class Sample {
                    @Value("#{systemProperties['secret']}")
                    String apiToken;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportOnNonSecretField() {
        this.myFixture.enableInspections(new SpringValueHardcodedSecretInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Value { String value(); }
                @interface Component { }

                @Component
                class Sample {
                    @Value("localhost")
                    String serverHost;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
