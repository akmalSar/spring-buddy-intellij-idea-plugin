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
public class SpringCglibFinalProxyInspectionTest extends BasePlatformTestCase {

    public void testReportsAndFixesFinalSpringBeanClass() {
        this.myFixture.enableInspections(new SpringCglibFinalProxyInspection());
        this.myFixture.configureByText("OrderService.java", """
                @Service
                public final class <warning descr="Final Spring bean class cannot be subclass-proxied by CGLIB"><caret>OrderService</warning> {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Remove final modifier");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("public class OrderService"));
    }

    public void testReportsAndFixesFinalTransactionalMethod() {
        this.myFixture.enableInspections(new SpringCglibFinalProxyInspection());
        this.myFixture.configureByText("OrderService.java", """
                @Service
                public class OrderService {
                    @Transactional
                    public final void <warning descr="Final Spring bean method cannot be subclass-proxied by CGLIB"><caret>save</warning>() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Remove final modifier");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("public void save()"));
    }

    public void testDoesNotReportFinalNonSpringClass() {
        this.myFixture.enableInspections(new SpringCglibFinalProxyInspection());
        this.myFixture.configureByText("OrderService.java", """
                public final class OrderService {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportPlainFinalHelperMethodWithoutAdvice() {
        this.myFixture.enableInspections(new SpringCglibFinalProxyInspection());
        this.myFixture.configureByText("OrderService.java", """
                @Service
                public class OrderService {
                    public final void helper() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportIntellijServiceComponent() {
        this.myFixture.enableInspections(new SpringCglibFinalProxyInspection());
        this.myFixture.configureByText("IntellijService.java", """
                package com.intellij.openapi.components;
                public @interface Service { }
                """);
        this.myFixture.configureByText("MyService.java", """
                import com.intellij.openapi.components.Service;
                @Service
                public final class MyService {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
