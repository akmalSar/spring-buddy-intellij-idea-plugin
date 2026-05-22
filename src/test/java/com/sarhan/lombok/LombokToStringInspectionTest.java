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
public class LombokToStringInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("lombok/ToString.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface ToString {
                    boolean callSuper() default false;
                }
                """);
        this.myFixture.addFileToProject("lombok/Data.java", """
                package lombok;
                import java.lang.annotation.*;
                @Target(ElementType.TYPE)
                @Retention(RetentionPolicy.SOURCE)
                public @interface Data {}
                """);
    }

    public void testFlagsToStringWithoutCallSuperOnSubclass() {
        this.myFixture.enableInspections(new LombokToStringInspection.ToStringWithoutCallSuper());
        this.myFixture.configureByText("SubDto.java", """
                import lombok.ToString;
                <warning descr="@ToString on a subclass excludes parent fields from the string representation unless callSuper = true is set.">@ToString</warning>
                public class SubDto extends BaseDto {
                    private int name;
                }
                class BaseDto { private int id; }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixAddsCallSuperTrue() {
        this.myFixture.enableInspections(new LombokToStringInspection.ToStringWithoutCallSuper());
        this.myFixture.configureByText("SubDto.java", """
                import lombok.ToString;
                @ToString
                public class SubDto extends BaseDto {
                    private int name;
                }
                class BaseDto { private int id; }
                """);

        this.myFixture.doHighlighting();

        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@ToString");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@ToString".length() / 2));

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add callSuper = true"));

        String result = this.myFixture.getFile().getText();
        assertTrue("Should add callSuper = true: " + result, result.contains("callSuper = true"));
        assertTrue("Should add onlyExplicitlyIncluded = true: " + result,
                result.contains("onlyExplicitlyIncluded = true"));
    }

    public void testFixAddsCallSuperTrueWhenOtherAttributesExist() {
        this.myFixture.enableInspections(new LombokToStringInspection.ToStringWithoutCallSuper());
        this.myFixture.configureByText("SubDto.java", """
                import lombok.ToString;
                @ToString(exclude = "name")
                public class SubDto extends BaseDto {
                    private int name;
                    private int age;
                }
                class BaseDto { private int id; }
                """);

        this.myFixture.doHighlighting();

        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@ToString");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@ToString".length() / 2));

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add callSuper = true"));

        String result = this.myFixture.getFile().getText();
        assertTrue("Should add callSuper = true: " + result, result.contains("callSuper = true"));
        assertTrue("Should add onlyExplicitlyIncluded = true: " + result,
                result.contains("onlyExplicitlyIncluded = true"));
        assertTrue("Should keep exclude: " + result, result.contains("exclude"));
    }

    public void testNoWarningWhenCallSuperTrueAlreadySet() {
        this.myFixture.enableInspections(new LombokToStringInspection.ToStringWithoutCallSuper());
        this.myFixture.configureByText("SubDto.java", """
                import lombok.ToString;
                @ToString(callSuper = true)
                public class SubDto extends BaseDto {
                    private int name;
                }
                class BaseDto { private int id; }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testNoWarningWhenNotSubclass() {
        this.myFixture.enableInspections(new LombokToStringInspection.ToStringWithoutCallSuper());
        this.myFixture.configureByText("Dto.java", """
                import lombok.ToString;
                @ToString
                public class Dto {
                    private int id;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testNoWarningWithoutAnnotation() {
        this.myFixture.enableInspections(new LombokToStringInspection.ToStringWithoutCallSuper());
        this.myFixture.configureByText("SubDto.java", """
                public class SubDto extends BaseDto {
                    private int name;
                }
                class BaseDto { private int id; }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFlagsToStringWithCircularReference() {
        this.myFixture.enableInspections(new LombokToStringInspection.ToStringCircularReference());
        this.myFixture.configureByText("CircularDto.java", """
                import lombok.ToString;
                <warning descr="Lombok @ToString may cause a StackOverflowError due to a circular reference between CircularDto and CircularDto. Exclude one side using @ToString.Exclude.">@ToString</warning>
                public class CircularDto {
                    private CircularDto child;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixAddsToStringExcludeForCircularToStringField() {
        this.myFixture.enableInspections(new LombokToStringInspection.ToStringCircularReference());
        this.myFixture.configureByText("CircularDto.java", """
                import lombok.ToString;
                @ToString
                public class CircularDto {
                    private CircularDto child;
                }
                """);

        this.myFixture.doHighlighting();
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@ToString");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@ToString".length() / 2));

        this.myFixture.launchAction(
                this.myFixture.findSingleIntention("Add @ToString.Exclude to circular field"));

        String result = this.myFixture.getFile().getText();
        assertTrue("Should add @ToString.Exclude but got:\n" + result,
                result.contains("@ToString.Exclude"));
        assertTrue("Should annotate the circular field but got:\n" + result,
                result.contains("@ToString.Exclude\n    private CircularDto child;"));
    }

    public void testFlagsDataWithCircularReference() {
        this.myFixture.enableInspections(new LombokToStringInspection.ToStringCircularReference());
        this.myFixture.configureByText("CircularDto.java", """
                import lombok.Data;
                <warning descr="Lombok @ToString may cause a StackOverflowError due to a circular reference between CircularDto and CircularDto. Exclude one side using @ToString.Exclude.">@Data</warning>
                public class CircularDto {
                    private CircularDto child;
                }
                """);
        this.myFixture.checkHighlighting();
    }

    public void testFixAddsToStringExcludeAndImportForDataCircularField() {
        this.myFixture.enableInspections(new LombokToStringInspection.ToStringCircularReference());
        this.myFixture.configureByText("CircularDto.java", """
                import lombok.Data;
                @Data
                public class CircularDto {
                    private CircularDto child;
                }
                """);

        this.myFixture.doHighlighting();
        String text = this.myFixture.getFile().getText();
        int offset = text.indexOf("@Data");
        assertTrue(offset >= 0);
        this.myFixture.getEditor().getCaretModel().moveToOffset(offset + ("@Data".length() / 2));

        this.myFixture.launchAction(
                this.myFixture.findSingleIntention("Add @ToString.Exclude to circular field"));

        String result = this.myFixture.getFile().getText();
        assertTrue("Should import ToString but got:\n" + result,
                result.contains("import lombok.ToString;"));
        assertTrue("Should add @ToString.Exclude but got:\n" + result,
                result.contains("@ToString.Exclude"));
        assertTrue("Should keep @Data but got:\n" + result,
                result.contains("@Data"));
    }

    public void testInspectionHasDisplayName() {
        LombokToStringInspection inspection = new LombokToStringInspection.ToStringWithoutCallSuper();
        assertNotNull(inspection.getDisplayName());
        assertFalse(inspection.getDisplayName().isBlank());
    }
}
