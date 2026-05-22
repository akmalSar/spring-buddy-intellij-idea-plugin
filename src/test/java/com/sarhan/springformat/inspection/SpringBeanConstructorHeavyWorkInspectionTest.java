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
public class SpringBeanConstructorHeavyWorkInspectionTest extends BasePlatformTestCase {

    public void testReportsConstructorWorkOnInjectedDependencyAndMovesToPostConstruct() {
        this.myFixture.enableInspections(new SpringBeanConstructorHeavyWorkInspection());
        this.myFixture.configureByText("MyService.java", """
                @Service
                class MyService {
                    MyService(Dep dep) {
                        <warning descr="Constructor performs work on an injected dependency; use @PostConstruct after dependency injection is complete"><caret>dep.doHeavyWork()</warning>;
                    }
                }
                interface Dep { void doHeavyWork(); }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Move call to @PostConstruct method");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                @Service
                class MyService {
                    MyService(Dep dep) {
                    }

                    @PostConstruct
                    private void init() {
                        dep.doHeavyWork();
                    }
                }
                interface Dep { void doHeavyWork(); }
                """);
    }

    public void testDoesNotReportPlainDependencyAssignment() {
        this.myFixture.enableInspections(new SpringBeanConstructorHeavyWorkInspection());
        this.myFixture.configureByText("MyService.java", """
                @Service
                class MyService {
                    private final Dep dep;
                    MyService(Dep dep) {
                        this.dep = dep;
                    }
                }
                interface Dep { void doHeavyWork(); }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportNonSpringClassConstructorWork() {
        this.myFixture.enableInspections(new SpringBeanConstructorHeavyWorkInspection());
        this.myFixture.configureByText("MyService.java", """
                class MyService {
                    MyService(Dep dep) {
                        dep.doHeavyWork();
                    }
                }
                interface Dep { void doHeavyWork(); }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
