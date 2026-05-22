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
public class SpringJpaModifyingWithoutClearAutomaticallyInspectionTest extends BasePlatformTestCase {

    public void testFlagsModifyingWithoutClearAutomatically() {
        this.myFixture.enableInspections(new SpringJpaModifyingWithoutClearAutomaticallyInspection());
        this.myFixture.configureByText("Repo.java", """
                @interface Modifying { boolean clearAutomatically() default false; boolean flushAutomatically() default false; }
                interface Repo {
                    <warning descr="@Modifying without clearAutomatically=true may leave stale entities in the persistence context">@Modifying</warning>
                    int updateAll();
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testQuickFixAddsClearAutomatically() {
        this.myFixture.enableInspections(new SpringJpaModifyingWithoutClearAutomaticallyInspection());
        this.myFixture.configureByText("Repo.java", """
                @interface Modifying { boolean clearAutomatically() default false; boolean flushAutomatically() default false; }
                interface Repo {
                    @Mod<caret>ifying
                    int updateAll();
                }
                """);
        com.intellij.codeInsight.intention.IntentionAction fix =
                this.myFixture.findSingleIntention("Add clearAutomatically = true");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getEditor().getDocument().getText();
        assertTrue("Expected clearAutomatically = true to be inserted, was:\n" + text,
                text.contains("clearAutomatically = true"));
    }

    public void testDoesNotFlagWhenAlreadyTrue() {
        this.myFixture.enableInspections(new SpringJpaModifyingWithoutClearAutomaticallyInspection());
        this.myFixture.configureByText("Repo.java", """
                @interface Modifying { boolean clearAutomatically() default false; boolean flushAutomatically() default false; }
                interface Repo {
                    @Modifying(clearAutomatically = true)
                    int updateAll();
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
