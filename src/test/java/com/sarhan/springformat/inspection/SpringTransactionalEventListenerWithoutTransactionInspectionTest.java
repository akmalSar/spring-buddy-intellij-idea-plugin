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
public class SpringTransactionalEventListenerWithoutTransactionInspectionTest extends BasePlatformTestCase {

    public void testReportsTransactionalEventListenerWithoutTransactionAndAddsTransactional() {
        this.myFixture.enableInspections(new SpringTransactionalEventListenerWithoutTransactionInspection());
        this.myFixture.configureByText("Listener.java", """
                class Listener {
                    <warning descr="@TransactionalEventListener should declare a transaction boundary or be in a transactional class"><caret>@TransactionalEventListener</warning>
                    void handle(Object event) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.filterAvailableIntentions("Add @Transactional").get(0);
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Listener {
                    @org.springframework.transaction.annotation.Transactional
                    @TransactionalEventListener
                    void handle(Object event) { }
                }
                """);
    }

    public void testConvertsToPlainEventListener() {
        this.myFixture.enableInspections(new SpringTransactionalEventListenerWithoutTransactionInspection());
        this.myFixture.configureByText("Listener.java", """
                class Listener {
                    <warning descr="@TransactionalEventListener should declare a transaction boundary or be in a transactional class"><caret>@TransactionalEventListener</warning>
                    void handle(Object event) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Use @EventListener instead");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("@org.springframework.context.event.EventListener"));
    }

    public void testAddsRequiresNewTransactional() {
        this.myFixture.enableInspections(new SpringTransactionalEventListenerWithoutTransactionInspection());
        this.myFixture.configureByText("Listener.java", """
                class Listener {
                    <warning descr="@TransactionalEventListener should declare a transaction boundary or be in a transactional class"><caret>@TransactionalEventListener</warning>
                    void handle(Object event) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Add @Transactional(REQUIRES_NEW)");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Listener {
                    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
                    @TransactionalEventListener
                    void handle(Object event) { }
                }
                """);
    }

    public void testDoesNotReportMethodTransactional() {
        this.myFixture.enableInspections(new SpringTransactionalEventListenerWithoutTransactionInspection());
        this.myFixture.configureByText("Listener.java", """
                class Listener {
                    @Transactional
                    @TransactionalEventListener
                    void handle(Object event) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportClassTransactional() {
        this.myFixture.enableInspections(new SpringTransactionalEventListenerWithoutTransactionInspection());
        this.myFixture.configureByText("Listener.java", """
                @Transactional
                class Listener {
                    @TransactionalEventListener
                    void handle(Object event) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
