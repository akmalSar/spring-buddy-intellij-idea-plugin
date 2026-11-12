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
public class SpringMockitoArgumentMatcherMixInspectionTest extends BasePlatformTestCase {

    public void testFlagsRawValueAlongsideMatcherInWhen() {
        this.myFixture.enableInspections(new SpringMockitoArgumentMatcherMixInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> T when(T t) { return t; }
                    static <T> T any() { return null; }
                    static class Service {
                        String lookup(String a, int b) { return ""; }
                    }
                    void doIt(Service s) {
                        when(s.lookup(any(), <warning descr="Mixing matchers and raw values in a Mockito call — wrap with eq(...)">42</warning>));
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testWrapsRawValueWithEq() {
        this.myFixture.enableInspections(new SpringMockitoArgumentMatcherMixInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> T when(T t) { return t; }
                    static <T> T any() { return null; }
                    static <T> T eq(T v) { return v; }
                    static class Service {
                        String lookup(String a, int b) { return ""; }
                    }
                    void doIt(Service s) {
                        when(s.lookup(any(), <warning descr="Mixing matchers and raw values in a Mockito call — wrap with eq(...)"><caret>42</warning>));
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Wrap raw value with eq(...)");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    static <T> T when(T t) { return t; }
                    static <T> T any() { return null; }
                    static <T> T eq(T v) { return v; }
                    static class Service {
                        String lookup(String a, int b) { return ""; }
                    }
                    void doIt(Service s) {
                        when(s.lookup(any(), eq(42)));
                    }
                }
                """);
    }

    public void testWrapsRawValueWithQualifiedEqWhenMatcherIsQualified() {
        this.myFixture.enableInspections(new SpringMockitoArgumentMatcherMixInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static class Mockito {
                        static <T> T any() { return null; }
                        static <T> T eq(T v) { return v; }
                    }
                    static <T> T when(T t) { return t; }
                    static class Service {
                        String lookup(String a, String b) { return ""; }
                    }
                    void doIt(Service s) {
                        when(s.lookup(Mockito.any(), <warning descr="Mixing matchers and raw values in a Mockito call — wrap with eq(...)"><caret>"raw"</warning>));
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Wrap raw value with eq(...)");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                class Sample {
                    static class Mockito {
                        static <T> T any() { return null; }
                        static <T> T eq(T v) { return v; }
                    }
                    static <T> T when(T t) { return t; }
                    static class Service {
                        String lookup(String a, String b) { return ""; }
                    }
                    void doIt(Service s) {
                        when(s.lookup(Mockito.any(), Mockito.eq("raw")));
                    }
                }
                """);
    }

    public void testFlagsRawValueOnVerify() {
        this.myFixture.enableInspections(new SpringMockitoArgumentMatcherMixInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> T verify(T t) { return t; }
                    static <T> T eq(T v) { return v; }
                    static class Service { void save(String a, String b) {} }
                    void doIt(Service s) {
                        verify(s).save(eq("a"), <warning descr="Mixing matchers and raw values in a Mockito call — wrap with eq(...)">"b"</warning>);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagAllRaw() {
        this.myFixture.enableInspections(new SpringMockitoArgumentMatcherMixInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> T when(T t) { return t; }
                    static class Service { String lookup(String a, int b) { return ""; } }
                    void doIt(Service s) {
                        when(s.lookup("x", 42));
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagArgumentCaptorCapture() {
        this.myFixture.enableInspections(new SpringMockitoArgumentMatcherMixInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> T verify(T t) { return t; }
                    static <T> T any() { return null; }
                    static class ArgumentCaptor<T> { T capture() { return null; } }
                    static class Url { }
                    static class Service { void send(Url a, Url b, Url c) {} }
                    void doIt(Service s, ArgumentCaptor<Url> urlCaptor) {
                        verify(s).send(any(), urlCaptor.capture(), any());
                    }
                }
                """);
        this.myFixture.checkHighlighting(true, false, true, false);
    }

    public void testDoesNotFlagAdditionalMatchers() {
        this.myFixture.enableInspections(new SpringMockitoArgumentMatcherMixInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> T when(T t) { return t; }
                    static <T> T any() { return null; }
                    static int anyInt() { return 0; }
                    static int gt(int v) { return 0; }
                    static class Result { }
                    static class Service { Result lookup(int a, int b) { return null; } }
                    void doIt(Service s) {
                        when(s.lookup(anyInt(), gt(5)));
                    }
                }
                """);
        this.myFixture.checkHighlighting(true, false, true, false);
    }

    public void testDoesNotFlagAllMatchers() {
        this.myFixture.enableInspections(new SpringMockitoArgumentMatcherMixInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> T when(T t) { return t; }
                    static <T> T any() { return null; }
                    static int anyInt() { return 0; }
                    static class Service { String lookup(String a, int b) { return ""; } }
                    void doIt(Service s) {
                        when(s.lookup(any(), anyInt()));
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
