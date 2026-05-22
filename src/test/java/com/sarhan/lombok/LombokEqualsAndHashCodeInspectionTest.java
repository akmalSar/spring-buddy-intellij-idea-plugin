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
public class LombokEqualsAndHashCodeInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("lombok/EqualsAndHashCode.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface EqualsAndHashCode {
                    boolean callSuper() default false;
                    boolean onlyExplicitlyIncluded() default false;
                }
                """);
        this.myFixture.addFileToProject("lombok/Data.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface Data {}
                """);
        this.myFixture.addFileToProject("lombok/ToString.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface ToString {
                    boolean callSuper() default false;
                }
                """);
    }

    public void testFlagsEqualsAndHashCodeWithoutCallSuperOnSubclass() {
        this.myFixture.enableInspections(new LombokEqualsAndHashCodeInspection.EqualsAndHashCodeWithoutCallSuper());
        this.myFixture.configureByText("SubDto.java", """
                import lombok.EqualsAndHashCode;
                <warning descr="@EqualsAndHashCode on a subclass excludes parent fields from equality checks unless callSuper = true is set. Two objects that differ only in parent fields will incorrectly be considered equal.">@EqualsAndHashCode</warning>
                public class SubDto extends BaseDto {
                    private int name;
                }
                class BaseDto { private int id; }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixAddsCallSuperTrue() {
        this.myFixture.enableInspections(new LombokEqualsAndHashCodeInspection.EqualsAndHashCodeWithoutCallSuper());
        this.myFixture.configureByText("SubDto.java", """
                import lombok.EqualsAndHashCode;
                @EqualsAndHashCode
                public class SubDto extends BaseDto {
                    private int name;
                }
                class BaseDto { private int id; }
                """);

        this.myFixture.doHighlighting();

        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@EqualsAndHashCode");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@EqualsAndHashCode".length() / 2));

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add callSuper = true"));

        String result = this.myFixture.getFile().getText();
        assertTrue("Should add callSuper = true: " + result, result.contains("callSuper = true"));
    }

    public void testFixAddsCallSuperTrueWhenOtherAttributesExist() {
        this.myFixture.enableInspections(new LombokEqualsAndHashCodeInspection.EqualsAndHashCodeWithoutCallSuper());
        this.myFixture.configureByText("SubDto.java", """
                import lombok.EqualsAndHashCode;
                @EqualsAndHashCode(exclude = "name")
                public class SubDto extends BaseDto {
                    private int name;
                    private int age;
                }
                class BaseDto { private int id; }
                """);

        this.myFixture.doHighlighting();

        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@EqualsAndHashCode");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@EqualsAndHashCode".length() / 2));

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add callSuper = true"));

        String result = this.myFixture.getFile().getText();
        assertTrue("Should add callSuper = true: " + result, result.contains("callSuper = true"));
        assertTrue("Should keep exclude: " + result, result.contains("exclude"));
    }

    public void testNoWarningWhenCallSuperTrueAlreadySet() {
        this.myFixture.enableInspections(new LombokEqualsAndHashCodeInspection.EqualsAndHashCodeWithoutCallSuper());
        this.myFixture.configureByText("SubDto.java", """
                import lombok.EqualsAndHashCode;
                @EqualsAndHashCode(callSuper = true)
                public class SubDto extends BaseDto {
                    private int name;
                }
                class BaseDto { private int id; }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testNoWarningWhenNotSubclass() {
        this.myFixture.enableInspections(new LombokEqualsAndHashCodeInspection.EqualsAndHashCodeWithoutCallSuper());
        this.myFixture.configureByText("Dto.java", """
                import lombok.EqualsAndHashCode;
                @EqualsAndHashCode
                public class Dto {
                    private int id;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testNoWarningWithoutAnnotation() {
        this.myFixture.enableInspections(new LombokEqualsAndHashCodeInspection.EqualsAndHashCodeWithoutCallSuper());
        this.myFixture.configureByText("SubDto.java", """
                public class SubDto extends BaseDto {
                    private int name;
                }
                class BaseDto { private int id; }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFlagsDataOnSubclass() {
        this.myFixture.enableInspections(new LombokEqualsAndHashCodeInspection.DataOnSubclassWithoutCallSuper());
        this.myFixture.configureByText("SubDto.java", """
                import lombok.Data;
                <warning descr="@Data on a subclass implies @EqualsAndHashCode without callSuper = true, silently excluding parent fields from equality. Use explicit @EqualsAndHashCode(callSuper = true) and @ToString(callSuper = true) instead.">@Data</warning>
                public class SubDto extends BaseDto {
                    private int name;
                }
                class BaseDto { private int id; }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testDoesNotFlagDataOnSubclassWhenExplicitCallSuperAnnotationsExist() {
        this.myFixture.enableInspections(new LombokEqualsAndHashCodeInspection.DataOnSubclassWithoutCallSuper());
        this.myFixture.configureByText("AccountStatement.java", """
                import lombok.Data;
                import lombok.EqualsAndHashCode;
                import lombok.ToString;
                @Data
                @ToString(callSuper = true)
                @EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
                public class AccountStatement extends EuropaceBaseEntity { }
                class EuropaceBaseEntity { }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixAddsExplicitCallSuperAnnotationsForDataSubclass() {
        this.myFixture.enableInspections(new LombokEqualsAndHashCodeInspection.DataOnSubclassWithoutCallSuper());
        this.myFixture.configureByText("SubDto.java", """
                import lombok.Data;
                @Data
                public class SubDto extends BaseDto {
                    private int name;
                }
                class BaseDto { private int id; }
                """);

        this.myFixture.doHighlighting();
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@Data");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@Data".length() / 2));

        this.myFixture.launchAction(
                this.myFixture.findSingleIntention("Add explicit callSuper annotations"));

        String result = this.myFixture.getFile().getText();
        assertTrue("Should import EqualsAndHashCode but got:\n" + result,
                result.contains("import lombok.EqualsAndHashCode;"));
        assertTrue("Should import ToString but got:\n" + result,
                result.contains("import lombok.ToString;"));
        assertTrue("Should add @EqualsAndHashCode(callSuper = true) but got:\n" + result,
                result.contains("@EqualsAndHashCode(callSuper = true)"));
        assertTrue("Should add @ToString(callSuper = true, onlyExplicitlyIncluded = true) but got:\n" + result,
                result.contains("@ToString(callSuper = true, onlyExplicitlyIncluded = true)"));
        assertTrue("Should keep @Data but got:\n" + result,
                result.contains("@Data"));
    }

    public void testInspectionHasDisplayName() {
        LombokEqualsAndHashCodeInspection inspection = new LombokEqualsAndHashCodeInspection.EqualsAndHashCodeWithoutCallSuper();
        assertNotNull(inspection.getDisplayName());
        assertFalse(inspection.getDisplayName().isBlank());
    }
}
