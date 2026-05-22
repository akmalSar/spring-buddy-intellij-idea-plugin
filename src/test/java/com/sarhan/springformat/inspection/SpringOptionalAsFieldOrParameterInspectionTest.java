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
public class SpringOptionalAsFieldOrParameterInspectionTest extends BasePlatformTestCase {

    public void testFlagsOptionalField() {
        this.myFixture.enableInspections(new SpringOptionalAsFieldOrParameterInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.Optional;
                class Sample {
                    private <warning descr="Optional should not be used as a field type; use a nullable field or a real value">Optional<String></warning> name;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsOptionalParameter() {
        this.myFixture.enableInspections(new SpringOptionalAsFieldOrParameterInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.Optional;
                class Sample {
                    void m(<warning descr="Optional should not be used as a method parameter type; accept the value (possibly nullable) or overload">Optional<String></warning> name) {}
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagOptionalReturnType() {
        this.myFixture.enableInspections(new SpringOptionalAsFieldOrParameterInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.Optional;
                class Sample {
                    Optional<String> find() { return Optional.empty(); }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
