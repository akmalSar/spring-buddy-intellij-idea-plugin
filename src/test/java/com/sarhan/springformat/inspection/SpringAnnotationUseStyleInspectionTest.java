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
public class SpringAnnotationUseStyleInspectionTest extends BasePlatformTestCase {

    public void testFlagsEmptyParentheses() {
        this.myFixture.enableInspections(new SpringAnnotationUseStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                @Deprecated<warning descr="Annotation without arguments should omit parentheses">()</warning>
                class Sample {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsValuePairCompactStyle() {
        this.myFixture.enableInspections(new SpringAnnotationUseStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                @SuppressWarnings(<warning descr="Annotation value should use compact style">value = "unchecked"</warning>)
                class Sample {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsSingleElementArray() {
        this.myFixture.enableInspections(new SpringAnnotationUseStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                @SuppressWarnings(value = <warning descr="Single-element annotation array should omit braces">{"unchecked"}</warning>)
                class Sample {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsTrailingArrayComma() {
        this.myFixture.enableInspections(new SpringAnnotationUseStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                @SuppressWarnings(value = <warning descr="Annotation array should not have a trailing comma">{"unchecked", "unused",}</warning>)
                class Sample {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsCompactArrayTrailingComma() {
        this.myFixture.enableInspections(new SpringAnnotationUseStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface JsonSubTypes {
                    Type[] value();

                    @interface Type {
                        String name();
                        Class<?> value();
                    }
                }

                class PaymentAcquirerServiceProviderMetaDataPayOne {
                }

                class PaymentAcquirerServiceProviderMetaDataPayPal {
                }

                @JsonSubTypes(<warning descr="Annotation array should not have a trailing comma">{ @JsonSubTypes.Type(name = "PayOne", value = PaymentAcquirerServiceProviderMetaDataPayOne.class),
                        @JsonSubTypes.Type(name = "PayPal", value = PaymentAcquirerServiceProviderMetaDataPayPal.class), }</warning>)
                class Sample {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsJsonSubTypesTrailingComma() {
        this.myFixture.enableInspections(new SpringAnnotationUseStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.io.Serializable;

                import com.fasterxml.jackson.annotation.JsonSubTypes;
                import com.fasterxml.jackson.annotation.JsonTypeInfo;
                import lombok.Getter;
                import lombok.NoArgsConstructor;
                import lombok.Setter;

                @JsonSubTypes(<warning descr="Annotation array should not have a trailing comma">{ @JsonSubTypes.Type(name = "PayOne", value = PaymentAcquirerServiceProviderMetaDataPayOne.class),
                        @JsonSubTypes.Type(name = "PayPal", value = PaymentAcquirerServiceProviderMetaDataPayPal.class), }</warning>)
                @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
                @Getter
                @Setter
                @NoArgsConstructor
                public abstract class PaymentAcquirerServiceProviderMetaData implements Serializable {

                    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
                    public abstract String getType();

                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagCompactStyle() {
        this.myFixture.enableInspections(new SpringAnnotationUseStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                @Deprecated
                @SuppressWarnings("unchecked")
                class Sample {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNormalizeAnnotationFix() {
        this.myFixture.enableInspections(new SpringAnnotationUseStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                @SuppressWarnings(<warning descr="Annotation value should use compact style"><caret>value = {"unchecked"}</warning>)
                class Sample {
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Use compact annotation style");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText().contains("@SuppressWarnings(\"unchecked\")"));
    }
}
