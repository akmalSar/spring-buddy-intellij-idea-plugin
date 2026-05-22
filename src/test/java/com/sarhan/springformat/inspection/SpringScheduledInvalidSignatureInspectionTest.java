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
public class SpringScheduledInvalidSignatureInspectionTest extends BasePlatformTestCase {

    public void testReportsScheduledMethodWithParameter() {
        this.myFixture.enableInspections(new SpringScheduledInvalidSignatureInspection());
        this.myFixture.configureByText("Jobs.java", """
                @interface Scheduled { long fixedRate() default -1; }
                class Jobs {
                    @Scheduled(fixedRate = 1000)
                    void <error descr="@Scheduled methods must not declare parameters; Spring cannot invoke this method">work</error>(String id) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportNoArgScheduledMethod() {
        this.myFixture.enableInspections(new SpringScheduledInvalidSignatureInspection());
        this.myFixture.configureByText("Jobs.java", """
                @interface Scheduled { long fixedRate() default -1; }
                class Jobs {
                    @Scheduled(fixedRate = 1000)
                    void work() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
