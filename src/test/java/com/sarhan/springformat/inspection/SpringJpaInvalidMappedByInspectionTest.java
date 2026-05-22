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
public class SpringJpaInvalidMappedByInspectionTest extends BasePlatformTestCase {

    public void testReportsMissingMappedByTargetField() {
        this.myFixture.enableInspections(new SpringJpaInvalidMappedByInspection());
        this.myFixture.configureByText("Order.java", """
                class List<T> { }
                @interface OneToMany { String mappedBy() default ""; }
                @interface ManyToOne { }
                @interface Entity { }
                @Entity
                class Order {
                    <error descr="mappedBy references 'order', but the target entity has no matching owning-side field">@OneToMany(mappedBy = "order")</error>
                    List<LineItem> items;
                }
                @Entity
                class LineItem { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotReportValidMappedBy() {
        this.myFixture.enableInspections(new SpringJpaInvalidMappedByInspection());
        this.myFixture.configureByText("Order.java", """
                class List<T> { }
                @interface OneToMany { String mappedBy() default ""; }
                @interface ManyToOne { }
                @interface Entity { }
                @Entity
                class Order {
                    @OneToMany(mappedBy = "order")
                    List<LineItem> items;
                }
                @Entity
                class LineItem {
                    @ManyToOne Order order;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
