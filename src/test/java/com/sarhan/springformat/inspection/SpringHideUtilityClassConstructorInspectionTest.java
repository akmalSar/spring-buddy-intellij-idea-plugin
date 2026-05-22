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
public class SpringHideUtilityClassConstructorInspectionTest extends BasePlatformTestCase {

    public void testDoesNotReportSpringManagedClasses() {
        this.myFixture.enableInspections(new SpringHideUtilityClassConstructorInspection());
        this.myFixture.configureByText("Sample.java", """
                @SpringBootApplication
                class App { static void main(String[] args) { } }
                
                @Configuration
                class Config { static String bean() { return ""; } }
                
                @EnableAutoConfiguration
                class AutoConfig { static String bean() { return ""; } }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsOtherSpringStereotypes() {
        this.myFixture.enableInspections(new SpringHideUtilityClassConstructorInspection());
        this.myFixture.configureByText("Sample.java", """
                @Service
                class <warning descr="Utility class 'ServiceClass' has an implicit public constructor; add a private no-arg constructor (Spring convention)">ServiceClass</warning> { static String helper() { return ""; } }
                
                @Component
                class <warning descr="Utility class 'ComponentClass' has an implicit public constructor; add a private no-arg constructor (Spring convention)">ComponentClass</warning> { static String helper() { return ""; } }
                
                @Repository
                class <warning descr="Utility class 'RepositoryClass' has an implicit public constructor; add a private no-arg constructor (Spring convention)">RepositoryClass</warning> { static String helper() { return ""; } }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testAddsPrivateConstructor() {
        this.myFixture.enableInspections(new SpringHideUtilityClassConstructorInspection());
        this.myFixture.configureByText("Sample.java", """
                class <warning descr="Utility class 'Sample' has an implicit public constructor; add a private no-arg constructor (Spring convention)"><caret>Sample</warning> {
                    static String helper() { return ""; }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Add private no-arg constructor");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("private Sample()"));
    }
}
