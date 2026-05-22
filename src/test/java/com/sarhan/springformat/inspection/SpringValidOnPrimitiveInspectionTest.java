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
public class SpringValidOnPrimitiveInspectionTest extends BasePlatformTestCase {

    public void testReportsValidOnString() {
        this.myFixture.enableInspections(new SpringValidOnPrimitiveInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Valid { }
                @interface PostMapping { String value(); }
                @interface RestController { }

                @RestController
                class Sample {
                    @PostMapping("/create")
                    void create(@Valid String <warning descr="@Valid has no effect on primitive or String parameters; use @NotNull, @NotBlank, or @Size directly">name</warning>) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsValidOnInt() {
        this.myFixture.enableInspections(new SpringValidOnPrimitiveInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Valid { }
                class Sample {
                    void update(@Valid int <warning descr="@Valid has no effect on primitive or String parameters; use @NotNull, @NotBlank, or @Size directly">age</warning>) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportOnDto() {
        this.myFixture.enableInspections(new SpringValidOnPrimitiveInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Valid { }
                class UserDto { }
                class Sample {
                    void create(@Valid UserDto dto) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithoutValid() {
        this.myFixture.enableInspections(new SpringValidOnPrimitiveInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void create(String name) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportForNonBeanValidationValidFqn() {
        this.myFixture.enableInspections(new SpringValidOnPrimitiveInspection());
        this.myFixture.addFileToProject("com/example/Valid.java", """
                package com.example;
                public @interface Valid { }
                """);
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void create(@com.example.Valid String name) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
