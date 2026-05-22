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
public class SpringJpaModifyingWithoutTransactionalInspectionTest extends BasePlatformTestCase {

    public void testFlagsModifyingWithoutTransactionalAndFixes() {
        this.myFixture.enableInspections(new SpringJpaModifyingWithoutTransactionalInspection());
        this.myFixture.configureByText("UserRepository.java", """
                @interface Modifying { }
                @interface Query { String value(); }
                interface UserRepository {
                    <error descr="@Modifying query requires an active transaction; add @Transactional"><caret>@Modifying</error>
                    @Query("UPDATE User u SET u.active=false WHERE u.id=:id")
                    void deactivate(Long id);
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Add @Transactional");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                import org.springframework.transaction.annotation.*;

                @interface Modifying { }
                @interface Query { String value(); }
                interface UserRepository {
                    @Transactional
                    @Modifying
                    @Query("UPDATE User u SET u.active=false WHERE u.id=:id")
                    void deactivate(Long id);
                }
                """);
    }

    public void testDoesNotFlagMethodTransactional() {
        this.myFixture.enableInspections(new SpringJpaModifyingWithoutTransactionalInspection());
        this.myFixture.configureByText("UserRepository.java", """
                @interface Modifying { }
                @interface Transactional { }
                interface UserRepository {
                    @Modifying
                    @Transactional
                    void deactivate(Long id);
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagClassTransactional() {
        this.myFixture.enableInspections(new SpringJpaModifyingWithoutTransactionalInspection());
        this.myFixture.configureByText("UserRepository.java", """
                @interface Modifying { }
                @interface Transactional { }
                @Transactional
                interface UserRepository {
                    @Modifying
                    void deactivate(Long id);
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
