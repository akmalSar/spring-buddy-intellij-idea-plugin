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
public class SpringTransactionalPostConstructInspectionTest extends BasePlatformTestCase {

    public void testReportsTransactionalPostConstructAndConvertsToApplicationReadyListener() {
        this.myFixture.enableInspections(new SpringTransactionalPostConstructInspection());
        this.myFixture.configureByText("DataLoader.java", """
                @Component
                class DataLoader {
                    Repository repo;
                    @PostConstruct
                    <error descr="@Transactional is ignored on @PostConstruct methods because the Spring proxy is not active yet"><caret>@Transactional</error>
                    public void init() {
                        repo.save(new Object());
                    }
                }
                interface Repository { void save(Object value); }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Use @EventListener(ApplicationReadyEvent.class)");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                @Component
                class DataLoader {
                    Repository repo;
                    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
                    @Transactional
                    public void init() {
                        repo.save(new Object());
                    }
                }
                interface Repository { void save(Object value); }
                """);
    }

    public void testDoesNotReportPlainPostConstruct() {
        this.myFixture.enableInspections(new SpringTransactionalPostConstructInspection());
        this.myFixture.configureByText("DataLoader.java", """
                @Component
                class DataLoader {
                    @PostConstruct
                    public void init() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
