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
public class SpringEntityEqualsHashCodeOnIdInspectionTest extends BasePlatformTestCase {

    public void testFlagsEqualsAndHashCodeReferencingId() {
        this.myFixture.enableInspections(new SpringEntityEqualsHashCodeOnIdInspection());
        this.myFixture.configureByText("UserEntity.java", """
                @interface Entity {}
                @interface Id {}
                @Entity
                class UserEntity {
                    @Id Long id;
                    String name;
                    @Override
                    public boolean <warning descr="@Entity equals() references the @Id field; generated identifiers are null before persist">equals</warning>(Object o) {
                        if (!(o instanceof UserEntity)) return false;
                        return id != null && id.equals(((UserEntity) o).id);
                    }
                    @Override
                    public int <warning descr="@Entity hashCode() references the @Id field; generated identifiers are null before persist">hashCode</warning>() {
                        return id == null ? 0 : id.hashCode();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagBusinessKeyEquals() {
        this.myFixture.enableInspections(new SpringEntityEqualsHashCodeOnIdInspection());
        this.myFixture.configureByText("UserEntity.java", """
                @interface Entity {}
                @interface Id {}
                @Entity
                class UserEntity {
                    @Id Long id;
                    String email;
                    @Override
                    public boolean equals(Object o) {
                        if (!(o instanceof UserEntity)) return false;
                        return email.equals(((UserEntity) o).email);
                    }
                    @Override
                    public int hashCode() {
                        return email.hashCode();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagNonEntity() {
        this.myFixture.enableInspections(new SpringEntityEqualsHashCodeOnIdInspection());
        this.myFixture.configureByText("Pojo.java", """
                @interface Id {}
                class Pojo {
                    @Id Long id;
                    @Override
                    public boolean equals(Object o) { return o instanceof Pojo p && id.equals(p.id); }
                    @Override
                    public int hashCode() { return id.hashCode(); }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
