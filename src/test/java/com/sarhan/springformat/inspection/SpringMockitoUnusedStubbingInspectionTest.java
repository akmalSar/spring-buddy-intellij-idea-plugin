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
public class SpringMockitoUnusedStubbingInspectionTest extends BasePlatformTestCase {

    private static final String JUPITER_TEST_STUB = """
            package org.junit.jupiter.api;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            public @interface Test { }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("org/junit/jupiter/api/Test.java", JUPITER_TEST_STUB);
    }

    public void testFlagsStubbingForMethodNeverInvoked() {
        this.myFixture.enableInspections(new SpringMockitoUnusedStubbingInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static <T> T when(T t) { return t; }
                    static class Service { String lookup() { return ""; } String other() { return ""; } }
                    @Test
                    void unusedStub() {
                        Service s = new Service();
                        when(<warning descr="Stubbed method 'lookup' is never invoked in this test">s.lookup()</warning>);
                        s.other();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testRemovesUnusedStubbingStatement() {
        this.myFixture.enableInspections(new SpringMockitoUnusedStubbingInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static <T> Stub<T> when(T t) { return new Stub<>(); }
                    static class Stub<T> { Stub<T> thenReturn(T value) { return this; } }
                    static class Service { String lookup() { return ""; } String other() { return ""; } }
                    @Test
                    void unusedStub() {
                        Service s = new Service();
                        when(<warning descr="Stubbed method 'lookup' is never invoked in this test"><caret>s.lookup()</warning>).thenReturn("unused");
                        s.other();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Remove unused Mockito stubbing");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static <T> Stub<T> when(T t) { return new Stub<>(); }
                    static class Stub<T> { Stub<T> thenReturn(T value) { return this; } }
                    static class Service { String lookup() { return ""; } String other() { return ""; } }
                    @Test
                    void unusedStub() {
                        Service s = new Service();
                        s.other();
                    }
                }
                """);
    }

    public void testMarksUnusedStubbingLenient() {
        this.myFixture.enableInspections(new SpringMockitoUnusedStubbingInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static <T> Stub<T> when(T t) { return new Stub<>(); }
                    static <T> Stub<T> lenient() { return new Stub<>(); }
                    static class Stub<T> { Stub<T> when(T value) { return this; } Stub<T> thenReturn(T value) { return this; } }
                    static class Service { String lookup() { return ""; } String other() { return ""; } }
                    @Test
                    void unusedStub() {
                        Service s = new Service();
                        when(<warning descr="Stubbed method 'lookup' is never invoked in this test"><caret>s.lookup()</warning>).thenReturn("unused");
                        s.other();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Mark stubbing as lenient");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText(), this.myFixture.getFile().getText().contains("lenient().when(s.lookup()).thenReturn(\"unused\")"));
    }

    public void testAddsVerifyForUnusedStubbing() {
        this.myFixture.enableInspections(new SpringMockitoUnusedStubbingInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static <T> Stub<T> given(T t) { return new Stub<>(); }
                    static class Stub<T> { Stub<T> willReturn(T value) { return this; } }
                    static class Mockito { static <T> T verify(T value) { return value; } }
                    static class Service { String lookup(String key) { return \"\"; } String other() { return \"\"; } }
                    @Test
                    void unusedStub() {
                        Service s = new Service();
                        given(<warning descr="Stubbed method 'lookup' is never invoked in this test"><caret>s.lookup("id")</warning>).willReturn("unused");
                        s.other();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Verify stubbed method is invoked");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static <T> Stub<T> given(T t) { return new Stub<>(); }
                    static class Stub<T> { Stub<T> willReturn(T value) { return this; } }
                    static class Mockito { static <T> T verify(T value) { return value; } }
                    static class Service { String lookup(String key) { return ""; } String other() { return ""; } }
                    @Test
                    void unusedStub() {
                        Service s = new Service();
                        given(s.lookup("id")).willReturn("unused");
                        s.other();
                        Mockito.verify(s).lookup("id");
                    }
                }
                """);
    }

    public void testDoesNotFlagLenientToStringStubbing() {
        this.myFixture.enableInspections(new SpringMockitoUnusedStubbingInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static <T> Stub<T> when(T t) { return new Stub<>(); }
                    static Lenient lenient() { return new Lenient(); }
                    static class Lenient { <T> Stub<T> when(T t) { return new Stub<>(); } }
                    static class Stub<T> { Stub<T> thenReturn(T value) { return this; } }
                    static class Mockito { static <T> T mock(Class<T> type) { return null; } }
                    interface Predicate { }
                    @Test
                    void unusedStub() {
                        Predicate orPred = Mockito.mock(Predicate.class);
                        lenient().when(orPred.toString()).thenReturn("orPred");
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotOfferVerifyForToStringStubbing() {
        this.myFixture.enableInspections(new SpringMockitoUnusedStubbingInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static <T> Stub<T> when(T t) { return new Stub<>(); }
                    static class Stub<T> { Stub<T> thenReturn(T value) { return this; } }
                    static class Mockito { static <T> T mock(Class<T> type) { return null; } }
                    interface Predicate { }
                    @Test
                    void unusedStub() {
                        Predicate orPred = Mockito.mock(Predicate.class);
                        when(<warning descr="Stubbed method 'toString' is never invoked in this test"><caret>orPred.toString()</warning>).thenReturn("orPred");
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        assertEmpty(this.myFixture.filterAvailableIntentions("Verify stubbed method is invoked"));
    }

    public void testDoesNotFlagFieldBasedMvcStubbing() {
        this.myFixture.enableInspections(new SpringMockitoUnusedStubbingInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static <T> Stub<T> when(T t) { return new Stub<>(); }
                    static class Stub<T> { Stub<T> thenReturn(T value) { return this; } }
                    static class Service { String lookup(String key) { return ""; } String other() { return ""; } }
                    private Service uploadService;
                    @Test
                    void unusedStub() {
                        String errorId = "id";
                        when(this.uploadService.lookup(errorId)).thenReturn("unused");
                        performMvcRequest(errorId);
                    }

                    void performMvcRequest(String errorId) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagThisQualifiedFieldBasedMvcStubbing() {
        this.myFixture.enableInspections(new SpringMockitoUnusedStubbingInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static <T> Stub<T> when(T t) { return new Stub<>(); }
                    static class Stub<T> { Stub<T> thenReturn(T value) { return this; } }
                    static class Service { Error findErrorById(String id) { return new Error(); } }
                    static class Error { }
                    private Service uploadService;
                    @Test
                    void getError() {
                        String uploadId = "upload";
                        String errorId = "error";
                        Error error = new Error();
                        when(this.uploadService.findErrorById(errorId)).thenReturn(error);

                        mockMvcPerform(uploadId, errorId);
                    }

                    void mockMvcPerform(String uploadId, String errorId) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagWhenStubbedMethodIsInvoked() {
        this.myFixture.enableInspections(new SpringMockitoUnusedStubbingInspection());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.Test;
                class SampleTests {
                    static <T> T when(T t) { return t; }
                    static class Service { String lookup() { return ""; } }
                    @Test
                    void usedStub() {
                        Service s = new Service();
                        when(s.lookup());
                        String r = s.lookup();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagOutsideTestMethod() {
        this.myFixture.enableInspections(new SpringMockitoUnusedStubbingInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static <T> T when(T t) { return t; }
                    static class Service { String lookup() { return ""; } }
                    void plain() {
                        Service s = new Service();
                        when(s.lookup());
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
