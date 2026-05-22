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
public class SpringAssertJChainableInspectionTest extends BasePlatformTestCase {

    public void testFlagsTwoSiblingAssertThatOnSameSubject() {
        this.myFixture.enableInspections(new SpringAssertJChainableInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(String s) {
                        <weak_warning descr="Multiple assertThat() calls on the same subject — consider chaining them">assertThat(s).isNotNull();</weak_warning>
                        <weak_warning descr="Multiple assertThat() calls on the same subject — consider chaining them">assertThat(s).isNotEmpty();</weak_warning>
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testChainsTwoSiblingAssertThatOnSameSubject() {
        this.myFixture.enableInspections(new SpringAssertJChainableInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(String s) {
                        <weak_warning descr="Multiple assertThat() calls on the same subject — consider chaining them"><caret>assertThat(s).isNotNull();</weak_warning>
                        <weak_warning descr="Multiple assertThat() calls on the same subject — consider chaining them">assertThat(s).isNotEmpty();</weak_warning>
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Chain AssertJ assertions");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(String s) {
                        assertThat(s).isNotNull().isNotEmpty();
                    }
                }
                """);
    }

    public void testChainsWholeRunWhenInvokedOnLaterAssertion() {
        this.myFixture.enableInspections(new SpringAssertJChainableInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(String s) {
                        <weak_warning descr="Multiple assertThat() calls on the same subject — consider chaining them">assertThat(s).isNotNull();</weak_warning>
                        <weak_warning descr="Multiple assertThat() calls on the same subject — consider chaining them"><caret>assertThat(s).startsWith("a");</weak_warning>
                        <weak_warning descr="Multiple assertThat() calls on the same subject — consider chaining them">assertThat(s).endsWith("z");</weak_warning>
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Chain AssertJ assertions");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(String s) {
                        assertThat(s).isNotNull().startsWith("a").endsWith("z");
                    }
                }
                """);
    }

    public void testDoesNotFlagDifferentSubjects() {
        this.myFixture.enableInspections(new SpringAssertJChainableInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(String a, String b) {
                        assertThat(a).isNotNull();
                        assertThat(b).isNotNull();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagXmlAssertXPathAssertions() {
        this.myFixture.enableInspections(new SpringAssertJChainableInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static class XmlAssert { static Xml assertThat(String value) { return null; } }
                    interface Xml { Value valueByXPath(String xpath); Nodes nodesByXPath(String xpath); }
                    interface Value { void isEqualTo(Object value); }
                    interface Nodes { void exist(); }
                    void doIt(String responseBody, String expectedAmount) {
                        XmlAssert.assertThat(responseBody).valueByXPath("//ARufnummer").isEqualTo("123");
                        XmlAssert.assertThat(responseBody).valueByXPath("//amount").isEqualTo(expectedAmount);
                        XmlAssert.assertThat(responseBody).valueByXPath("//usageType").isEqualTo("postpaidUsage");
                        XmlAssert.assertThat(responseBody).nodesByXPath("//date").exist();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagJsonPathAssertions() {
        this.myFixture.enableInspections(new SpringAssertJChainableInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static class Assertions { static Assert assertThat(Object value) { return null; } }
                    interface Assert {
                        Assert extractingJsonPathNumberValue(String path);
                        Assert extractingJsonPathStringValue(String path);
                        Assert isNotNull();
                        Assert isEqualTo(Object value);
                    }
                    static class Json { Object from(String value) { return null; } }
                    void doIt(Json json, String valueAsString) {
                        Assertions.assertThat(json.from(valueAsString))
                            .extractingJsonPathNumberValue("@.parent.id")
                            .isNotNull();
                        Assertions.assertThat(json.from(valueAsString))
                            .extractingJsonPathStringValue("@.parent.displayName")
                            .isEqualTo("test");
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagSingleAssertion() {
        this.myFixture.enableInspections(new SpringAssertJChainableInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> Object assertThat(T t) { return null; }
                    void doIt(String s) {
                        assertThat(s).isNotNull();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
