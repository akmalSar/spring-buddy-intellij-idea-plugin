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
public class SpringJpaEntityMissingIdInspectionTest extends BasePlatformTestCase {

    public void testReportsEntityWithoutId() {
        this.myFixture.enableInspections(new SpringJpaEntityMissingIdInspection());
        this.myFixture.configureByText("User.java", """
                @interface Entity { }
                @Entity
                class <error descr="JPA @Entity has no @Id or @EmbeddedId in its class hierarchy; Hibernate fails startup">User</error> {
                    String name;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportEntityWithIdInSuperclass() {
        this.myFixture.enableInspections(new SpringJpaEntityMissingIdInspection());
        this.myFixture.configureByText("User.java", """
                @interface Entity { }
                @interface MappedSuperclass { }
                @interface Id { }
                @MappedSuperclass
                class BaseEntity { @Id Long id; }
                @Entity
                class User extends BaseEntity { String name; }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
