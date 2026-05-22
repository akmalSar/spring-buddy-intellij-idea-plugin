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

package com.sarhan.lombok;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class LombokBuilderHierarchyInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addLombokStubs();
    }

    // ── BUILDER-002: @Builder on subclass whose parent also has @Builder ───
    public void testFlagsBuilderOnSubclassWithBuilderParent() {
        this.myFixture.addFileToProject("BaseDto.java", """
                import lombok.Builder;
                @Builder public class BaseDto { private int id; }
                """);
        this.myFixture.configureByText("SubDto.java", """
                import lombok.Builder;
                <error descr="@Builder on a subclass whose parent also has @Builder causes a constructor conflict at compile time. Use @SuperBuilder on both classes instead.">@Builder</error>
                public class SubDto extends BaseDto { private int name; }
                """);
        this.myFixture.enableInspections(new LombokBuilderHierarchyInspection.BuilderOnSubclassWithBuilderParent());
        this.myFixture.testHighlighting(true, false, false);
    }

    // ── BUILDER-003: @Builder with partial constructor ─────────────────────
    public void testFlagsPartialConstructor() {
        this.myFixture.configureByText("PartialDto.java", """
                import lombok.Builder;
                <warning descr="@Builder is present but the only available constructor covers 1 fields while the class declares 2 fields. The builder will silently leave unmatched fields null. Either add an all-args constructor or move @Builder onto the partial constructor explicitly.">@Builder</warning>
                public class PartialDto {
                    private int a;
                    private int b;
                    public PartialDto(int a) { this.a = a; }
                }
                """);
        this.myFixture.enableInspections(new LombokBuilderHierarchyInspection.BuilderWithPartialConstructor());
        this.myFixture.testHighlighting(true, false, false);
    }

    public void testNoWarningWhenConstructorCoversAllFields() {
        this.myFixture.configureByText("FullDto.java", """
                import lombok.Builder;
                @Builder
                public class FullDto {
                    private int a;
                    private int b;
                    public FullDto(int a, int b) { this.a = a; this.b = b; }
                }
                """);
        this.myFixture.enableInspections(new LombokBuilderHierarchyInspection.BuilderWithPartialConstructor());
        this.myFixture.testHighlighting(true, false, false);
    }

    public void testNoWarningWhenNoExplicitConstructor() {
        this.myFixture.configureByText("NoCtor.java", """
                import lombok.Builder;
                @Builder
                public class NoCtor { private int a; private int b; }
                """);
        this.myFixture.enableInspections(new LombokBuilderHierarchyInspection.BuilderWithPartialConstructor());
        this.myFixture.testHighlighting(true, false, false);
    }

    // ── SUPERBUILDER-001: @SuperBuilder without chain ──────────────────────
    public void testFlagsSuperBuilderWithoutChain() {
        this.myFixture.addFileToProject("Base.java", "public class Base { private int id; }");
        this.myFixture.configureByText("Sub.java", """
                import lombok.SuperBuilder;
                <error descr="@SuperBuilder requires the superclass chain to also use @SuperBuilder. Add @SuperBuilder to the parent class.">@SuperBuilder</error>
                public class Sub extends Base { private int name; }
                """);
        this.myFixture.enableInspections(new LombokBuilderHierarchyInspection.SuperBuilderMissingParent());
        this.myFixture.testHighlighting(true, false, false);
    }

    public void testNoWarningWhenSuperBuilderChainComplete() {
        this.myFixture.configureByText("Chain.java", """
                import lombok.SuperBuilder;
                @SuperBuilder class Base { private int id; }
                @SuperBuilder class Sub extends Base { private int name; }
                """);
        this.myFixture.enableInspections(new LombokBuilderHierarchyInspection.SuperBuilderMissingParent());
        this.myFixture.testHighlighting(true, false, false);
    }

    public void testNoWarningWhenGenericMappedSuperclassChainHasSuperBuilder() {
        this.myFixture.configureByText("DemoBaseEntity.java", """
                import lombok.SuperBuilder;
                @interface MappedSuperclass { }
                interface Persistable<I> { }
                interface Serializable { }
                class UUID implements Serializable { }

                @SuperBuilder
                @MappedSuperclass
                abstract class BaseEntity<I extends Serializable> implements Persistable<I> { }

                @SuperBuilder
                @MappedSuperclass
                abstract class BaseAuditedEntity<I extends Serializable> extends BaseEntity<I> { }

                @SuperBuilder
                @MappedSuperclass
                abstract class DemoBaseEntity extends BaseAuditedEntity<UUID> { }
                """);
        this.myFixture.enableInspections(new LombokBuilderHierarchyInspection.SuperBuilderMissingParent());
        this.myFixture.testHighlighting(true, false, false);
    }

    public void testNoWarningWhenSuperBuilderParentComesFromLibraryStub() {
        this.myFixture.addFileToProject("BaseAuditedEntity.java", """
                import lombok.experimental.SuperBuilder;
                @SuperBuilder
                public abstract class BaseAuditedEntity<I> { }
                """);
        this.myFixture.configureByText("BaseUUIDAuditedEntity.java", """
                import lombok.experimental.SuperBuilder;
                @SuperBuilder
                public class BaseUUIDAuditedEntity extends BaseAuditedEntity<Id> { }
                class Id { }
                """);
        this.myFixture.enableInspections(new LombokBuilderHierarchyInspection.SuperBuilderMissingParent());
        this.myFixture.testHighlighting(true, false, false);
    }

    // ── helpers ────────────────────────────────────────────────────────────
    private void addLombokStubs() {
        this.myFixture.addFileToProject("lombok/Builder.java", """
                package lombok; public @interface Builder {}
                """);
        this.myFixture.addFileToProject("lombok/SuperBuilder.java", """
                package lombok; public @interface SuperBuilder {}
                """);
        this.myFixture.addFileToProject("lombok/experimental/SuperBuilder.java", """
                package lombok.experimental; public @interface SuperBuilder {}
                """);
    }
}
