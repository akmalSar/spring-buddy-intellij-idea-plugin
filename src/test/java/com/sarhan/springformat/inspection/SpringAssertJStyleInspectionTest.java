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
public class SpringAssertJStyleInspectionTest extends BasePlatformTestCase {

    public void testConvertsSimpleAssertions() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                import static org.junit.jupiter.api.Assertions.*;
                
                class Sample {
                    void test(String actual) {
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>assertNotNull(actual)</warning>;
                        assertEquals("test", actual);
                        assertTrue(actual.isEmpty());
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("Assertions.assertThat(actual).isNotNull();"));
        assertTrue(text.contains("Assertions.assertThat(actual).isEqualTo(\"test\");"));
        assertTrue(text.contains("Assertions.assertThat(actual.isEmpty()).isTrue();"));
    }

    public void testConvertsTryCatchFailWithoutMutatingCapturedVariableInLambda() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                import static org.junit.jupiter.api.Assertions.*;
                
                class Sample {
                    private final ObjectMapper objectMapper = new ObjectMapper();
                
                    private void assertResponseOk(String response) {
                        SbcResponse.Ok ok = null;
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>try {
                            ok = objectMapper.readValue(response, SbcResponse.Ok.class);
                        }
                        catch (JsonProcessingException e) {
                            fail(e);
                        }</warning>
                        assertNotNull(ok);
                        assertNotNull(ok.getMessages());
                        assertEquals(Message.REG_OK.toSbcResponse(), ok);
                    }
                
                    static class ObjectMapper {
                        SbcResponse.Ok readValue(String response, Class<SbcResponse.Ok> type) throws JsonProcessingException { return null; }
                    }
                    static class SbcResponse { static class Ok { Object getMessages() { return null; } } }
                    static class JsonProcessingException extends Exception { }
                    enum Message { REG_OK; Object toSbcResponse() { return null; } }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("SbcResponse.Ok[] okValue = new SbcResponse.Ok[1]"));
        assertTrue(text.contains("Assertions.assertThatCode(() -> okValue[0] = objectMapper.readValue(response, SbcResponse.Ok.class)).doesNotThrowAnyException()"));
        assertTrue(text.contains("ok = okValue[0]"));
        assertTrue(text.contains("Assertions.assertThat(ok).isNotNull();"));
        assertTrue(text.contains("Assertions.assertThat(ok.getMessages()).isNotNull();"));
        assertTrue(text.contains("Assertions.assertThat(ok).isEqualTo(Message.REG_OK.toSbcResponse());"));
    }

    public void testConvertsFailThrowableToAssertJFailWithMessageAndCause() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test() {
                        try {
                            throw new Exception();
                        }
                        catch (Exception ex) {
                            <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>fail(ex)</warning>;
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        assertNotNull(fix);
    }

    public void testDoesNotReportImportSeparatelyFromCalls() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                import static org.junit.jupiter.api.Assertions.*;
                
                class Sample {
                    void test(String actual) {
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)">assertNotNull(actual)</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testConvertsAssertDoesNotThrowAssignmentWithoutAssertJPrefix() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                import static org.junit.jupiter.api.Assertions.*;
                import java.util.UUID;
                
                class Sample {
                    void test(Event initialEventlog) {
                        final var activityId = <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>assertDoesNotThrow(() -> UUID.fromString(initialEventlog.getActivityId()))</warning>;
                    }
                    interface Event { String getActivityId(); }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        assertTrue(this.myFixture.getFile().getText()
                .contains("final var activityId = UUID.fromString(initialEventlog.getActivityId());"));
    }

    public void testConvertsQualifiedAssertionsCalls() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test(DocumentRef docRef) {
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>Assertions.assertEquals(WriteOffRelatedDocument.Target.INVOICE.name(), docRef.getTarget())</warning>;
                        Assertions.assertNull(docRef.getDunningfeeInitial());
                    }
                    interface DocumentRef { String getTarget(); Object getDunningfeeInitial(); }
                    static class WriteOffRelatedDocument { enum Target { INVOICE } }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("Assertions.assertThat(docRef.getTarget()).isEqualTo(WriteOffRelatedDocument.Target.INVOICE.name());"));
        assertTrue(text.contains("Assertions.assertThat(docRef.getDunningfeeInitial()).isNull();"));
    }

    public void testConvertsAssertThrowsAssertInstanceOfAndFail() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test(Object value) {
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>Assertions.assertThrows(IllegalArgumentException.class, () -> failFast())</warning>;
                        Assertions.assertInstanceOf(String.class, value);
                        Assertions.fail();
                    }
                    void failFast() { throw new IllegalArgumentException(); }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("Assertions.assertThatThrownBy(() -> failFast()).isInstanceOf(IllegalArgumentException.class);"));
        assertTrue(text.contains("Assertions.assertThat(value).isInstanceOf(String.class);"));
        assertTrue(text.contains("Assertions.fail(\"exception\");"));
    }

    public void testConvertsJunitAssertionsFailMethodReference() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.junit.jupiter.api.Assertions;
                
                class Sample {
                    Object invalidInit(Validator validator) {
                        return validator.validate().orElseGet(<warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>Assertions::fail</warning>);
                    }
                    interface Validator { Result validate(); }
                    interface Result { Object orElseGet(java.util.function.Supplier<Object> supplier); }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("fail(\"exception\")"));
    }

    public void testConvertsAllSiblingQualifiedAssertions() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void test(Mandant mandant, String loadedValue, java.util.List<String> fail, String bodyStr) {
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>Assertions.assertEquals(mandant.getName(), loadedValue)</warning>;
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)">Assertions.assertTrue(fail.isEmpty())</warning>;
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)">Assertions.assertNotNull(bodyStr)</warning>;
                    }
                    interface Mandant { String getName(); }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("assertEquals not converted: " + text,
                text.contains("Assertions.assertThat(loadedValue).isEqualTo(mandant.getName());"));
        assertTrue("assertTrue not converted: " + text,
                text.contains("Assertions.assertThat(fail.isEmpty()).isTrue();"));
        assertTrue("assertNotNull not converted: " + text,
                text.contains("Assertions.assertThat(bodyStr).isNotNull();"));
    }

    public void testConvertsTryCatchFailWithGenericVariableTypeWithoutGenericArrayCreation() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                import static org.junit.jupiter.api.Assertions.*;
                
                class Sample {
                    void test() {
                        Pair<String, Integer> user = null;
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>try {
                            user = load();
                        }
                        catch (MyException e) {
                            fail("failed", e);
                        }</warning>
                    }
                    Pair<String, Integer> load() throws MyException { return null; }
                    static class Pair<A, B> { }
                    static class MyException extends Exception { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertFalse("must not produce generic array creation: " + text,
                text.contains("new Pair<"));
        assertTrue("expected erased array type: " + text,
                text.contains("Pair[] userValue = new Pair[1]"));
        assertTrue("expected cast on read back: " + text,
                text.contains("user = (Pair<String, Integer>) userValue[0]"));
    }

    public void testUsesShortAssertionsNameAndAddsImport() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                import static org.junit.jupiter.api.Assertions.*;
                
                class Sample {
                    void test(String actual) {
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>assertNotNull(actual)</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertFalse("must not use FQN: " + text, text.contains("org.assertj.core.api.Assertions."));
        assertTrue("must contain assertj import: " + text, text.contains("import org.assertj.core.api.Assertions;"));
        assertTrue("must use short name: " + text, text.contains("Assertions.assertThat(actual).isNotNull();"));
    }

    public void testDoesNotReportAssertJAssertionsFail() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.assertj.core.api.Assertions;
                
                class Sample {
                    void test(String loadClass, Exception ex) {
                        Assertions.fail("%s %s", loadClass, ex);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testConvertsMixedJunit4AndJunit5StressAssertions() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("JUnitMigrationStressTest.java", """
                import org.junit.Assert;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;
                import java.util.Arrays;
                import java.util.List;
                import java.util.Optional;
                import java.time.Duration;
                
                import static org.junit.Assert.*;
                import static org.junit.jupiter.api.Assertions.assertThrows;
                import static org.junit.jupiter.api.Assertions.assertTimeout;
                
                class JUnitMigrationStressTest {
                    @Test
                    void testBasicAssertions() {
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>assertTrue(true)</warning>;
                        Assertions.assertTrue(true, "J5 message at the end");
                        Assert.assertTrue("J4 message at the start", true);
                
                        assertFalse(false);
                        Assertions.assertFalse(false);
                
                        assertNull(null);
                        assertNotNull(new Object());
                    }
                
                    @Test
                    void testEqualityVariants() {
                        String expected = "text";
                        String actual = "text";
                
                        assertEquals(expected, actual);
                        assertEquals("Should match", expected, actual);
                        Assertions.assertEquals(expected, actual, "Should match");
                
                        assertEquals(3.14, 3.14159, 0.01);
                        Assertions.assertEquals(3.14, 3.14159, 0.01, "Float precision check");
                
                        Object obj = new Object();
                        assertSame(obj, obj);
                        assertNotSame(new Object(), new Object());
                    }
                
                    @Test
                    void testArraysAndCollections() {
                        int[] expectedArr = {1, 2, 3};
                        int[] actualArr = {1, 2, 3};
                        List<String> listA = Arrays.asList("a", "b");
                        List<String> listB = Arrays.asList("a", "b");
                
                        assertArrayEquals(expectedArr, actualArr);
                        Assertions.assertIterableEquals(listA, listB);
                        Assertions.assertLinesMatch(Arrays.asList("a", "b"), Arrays.asList("a", "b"));
                    }
                
                    @Test
                    void testExceptions() {
                        assertThrows(IllegalArgumentException.class, () -> {
                            throw new IllegalArgumentException("Error");
                        });
                
                        Exception exception = Assertions.assertThrows(RuntimeException.class, () -> {
                            throw new RuntimeException("Capture me");
                        });
                        assertEquals("Capture me", exception.getMessage());
                    }
                
                    @Test
                    void testTimeouts() {
                        Assertions.assertTimeout(Duration.ofMillis(100), () -> {
                            Thread.sleep(10);
                        });
                    }
                
                    @Test
                    void testBadPractices() {
                        assertEquals(true, 1 == 1);
                        assertEquals(false, 1 == 2);
                        assertEquals(null, null);
                
                        Optional<String> opt = Optional.of("value");
                        assertEquals(Optional.of("value"), opt);
                    }
                
                    @Test
                    void testFailAndMixedNamespaces() {
                        if (false) {
                            Assert.fail("Manual J4 fail");
                            Assertions.fail("Manual J5 fail");
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("Assertions.assertThat(true).isTrue();"));
        assertTrue(text.contains("Assertions.assertThat(true).as(\"J5 message at the end\").isTrue();"));
        assertTrue(text.contains("Assertions.assertThat(true).as(\"J4 message at the start\").isTrue();"));
        assertTrue(text.contains("Assertions.assertThat((Object) null).isNull();"));
        assertTrue(text.contains("Assertions.assertThat(actual).as(\"Should match\").isEqualTo(expected);"));
        assertTrue(text.contains("Assertions.assertThat(3.14159).isCloseTo(3.14, Assertions.within(0.01));"));
        assertTrue(text.contains("Assertions.assertThat(3.14159).as(\"Float precision check\").isCloseTo(3.14, Assertions.within(0.01));"));
        assertTrue(text.contains("Assertions.assertThat(actualArr).containsExactly(expectedArr);"));
        assertTrue(text.contains("Assertions.assertThat(listB).containsExactlyElementsOf(listA);"));
        assertTrue(text.contains("Exception exception = Assertions.catchThrowableOfType(() -> {"));
        assertTrue(text.contains("}, RuntimeException.class);"));
        assertTrue(text.contains("Assertions.assertThat(1 == 1).isTrue();"));
        assertTrue(text.contains("Assertions.assertThat(1 == 2).isFalse();"));
        assertFalse(text.contains("assertTrue("));
        assertFalse(text.contains("assertFalse("));
        assertFalse(text.contains("assertEquals("));
        assertFalse(text.contains("assertArrayEquals("));
        assertFalse(text.contains("assertIterableEquals("));
        assertFalse(text.contains("assertLinesMatch("));
    }

    public void testConvertsUltimateMigrationChallengeAssertions() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("UltimateMigrationChallenge.java", """
                import org.junit.Assert;
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;
                import org.hamcrest.CoreMatchers;
                
                import java.util.*;
                import java.time.Duration;
                
                import static org.junit.Assert.*;
                import static org.junit.jupiter.api.Assertions.*;
                
                class UltimateMigrationChallenge {
                    @Test
                    void testTheDeltaDoubleDilemma() {
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>Assert.assertEquals("J4 Double", 3.14, 3.141, 0.01)</warning>;
                        Assertions.assertEquals(3.14, 3.141, 0.01, "J5 Double");
                
                        Double expectedObj = 1.0;
                        double actualPri = 1.0;
                        Assertions.assertEquals(expectedObj, actualPri, 0.0);
                    }
                
                    @Test
                    void testLegacyHamcrestBridge() {
                        Assert.assertThat("Text", CoreMatchers.is("Text"));
                        Assert.assertThat("Error message", 10, CoreMatchers.equalTo(10));
                        Assert.assertThat(Arrays.asList("a", "b"), CoreMatchers.hasItems("a"));
                    }
                
                    @Test
                    void testTypeMismatchNormalization() {
                        Assertions.assertEquals(100L, 100);
                        assertNotEquals("Should not be same type", 50, 50L);
                    }
                
                    @Test
                    void testArrayAmbiguity() {
                        Object[] expected = { "a", "b" };
                        String[] actual = { "a", "b" };
                
                        Assertions.assertArrayEquals(expected, actual);
                        Assertions.assertIterableEquals(List.of("a"), List.of("a"));
                    }
                
                    @Test
                    void testFormatStretching() {
                        Assertions.
                                assertEquals(
                                        "expectedValue",
                                        "actualValue",
                                        "This message is on a new line"
                                );
                    }
                
                    @Test
                    void testBooleanLogicSimplification() {
                        List<String> items = List.of("Alpha", "Beta");
                
                        Assertions.assertTrue(items.contains("Alpha"));
                        Assertions.assertFalse(items.isEmpty());
                        assertEquals(2, items.size());
                    }
                
                    @Test
                    void testFailSemantics() {
                        if (System.currentTimeMillis() < 0) {
                            Assert.fail("Old school fail");
                            Assertions.fail(() -> "Supplied fail message");
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains("Assertions.assertThat(3.141).as(\"J4 Double\").isCloseTo(3.14, Assertions.within(0.01));"));
        assertTrue(text.contains("Assertions.assertThat(3.141).as(\"J5 Double\").isCloseTo(3.14, Assertions.within(0.01));"));
        assertTrue(text.contains("Assertions.assertThat(actualPri).isCloseTo(expectedObj, Assertions.within(0.0));"));
        assertTrue(text.contains("Assertions.assertThat(\"Text\").isEqualTo(\"Text\");"));
        assertTrue(text.contains("Assertions.assertThat(10).as(\"Error message\").isEqualTo(10);"));
        assertTrue(text.contains("Assertions.assertThat(Arrays.asList(\"a\", \"b\")).contains(\"a\");"));
        assertTrue(text.contains("Assertions.assertThat(100).isEqualTo(100L);"));
        assertTrue(text.contains("Assertions.assertThat(50L).as(\"Should not be same type\").isNotEqualTo(50);"));
        assertTrue(text.contains("Assertions.assertThat(actual).isEqualTo(expected);"));
        assertTrue(text.contains("Assertions.assertThat(List.of(\"a\")).containsExactlyElementsOf(List.of(\"a\"));"));
        assertTrue(text.contains("Assertions.assertThat(\"actualValue\").as(\"This message is on a new line\").isEqualTo(\"expectedValue\");"));
        assertTrue(text.contains("Assertions.assertThat(items.contains(\"Alpha\")).isTrue();"));
        assertTrue(text.contains("Assertions.assertThat(items.isEmpty()).isFalse();"));
        assertTrue(text.contains("Assertions.assertThat(items.size()).isEqualTo(2);"));
        assertTrue("expected supplier fail conversion, got: " + text,
                text.contains("Assertions.fail(\"Supplied fail message\");"));
        assertFalse(text.contains(".as(3.141)"));
        assertFalse(text.contains(".as(0.0)"));
        assertFalse(text.contains("assertArrayEquals("));
        assertFalse(text.contains("assertIterableEquals("));
    }

    public void testConvertsNestedAssertAllWithoutLeavingJunitAssertAllCall() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.junit.jupiter.api.Assertions;
                import org.junit.jupiter.api.Test;
                import java.time.Duration;
                
                class Sample {
                    @Test
                    void testNestedFunctionalAssertions() {
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>Assertions.assertAll("Grouped",
                                () -> Assertions.assertThrows(NullPointerException.class, () -> {
                                    String s = null;
                                    s.length();
                                }),
                                () -> Assertions.assertTimeout(Duration.ofSeconds(1), () -> {
                                    System.out.println("Quick task");
                                }),
                                () -> Assertions.assertTrue(true, () -> "Lazy message " + "evaluation")
                        )</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("expected AssertJ soft assertion wrapper, got: " + text,
                text.contains("SoftAssertions.assertSoftly((SoftAssertions softly) -> {"));
        assertTrue("expected SoftAssertions import, got: " + text,
                text.contains("import org.assertj.core.api.SoftAssertions;"));
        assertTrue(text.contains("Assertions.assertThatThrownBy(() -> {"));
        assertTrue(text.contains("Assertions.assertThatCode(() -> {"));
        assertTrue(text.contains("Assertions.assertThat(true).as(\"Lazy message \" + \"evaluation\").isTrue();"));
        assertFalse("must not leave JUnit assertAll resolved against AssertJ Assertions: " + text,
                text.contains("Assertions.assertAll("));
        assertFalse(text.contains(".as(() ->"));
    }

    public void testConvertsInsaneJupiterChallengeCases() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("UltimateJupiterChallenge.java", """
                import org.junit.jupiter.api.*;
                import org.junit.jupiter.params.ParameterizedTest;
                import org.junit.jupiter.params.provider.CsvSource;
                
                import java.time.Duration;
                import java.util.List;
                import java.util.concurrent.atomic.AtomicInteger;
                
                import static org.junit.jupiter.api.Assertions.*;
                
                @DisplayName("Complex JUnit 5 Suite")
                class UltimateJupiterChallenge {
                    @Test
                    void testBooleanSuppliers() {
                        AtomicInteger counter = new AtomicInteger(0);
                
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>assertTrue(() -> counter.get() == 0, () -> "Lazy message " + "from supplier")</warning>;
                        assertFalse(counter.incrementAndGet() > 5, "Incrementing inside assertion");
                    }
                
                    @Test
                    void testNestedAssertAll() {
                        assertAll("User Profile",
                                () -> {
                                    String name = "Gemini";
                                    assertNotNull(name);
                                    assertAll("Name Details",
                                            () -> assertTrue(name.startsWith("G")),
                                            () -> assertEquals(6, name.length())
                                    );
                                },
                                () -> assertEquals(2026, 1000 + 1026, "Math check")
                        );
                    }
                
                    @Test
                    void testLinesMatch() {
                        List<String> expectedLines = List.of("^[0-9]*$", ">> skip 2 lines >>", "final");
                        List<String> actualLines = List.of("123", "ignore1", "ignore2", "final");
                
                        assertLinesMatch(expectedLines, actualLines);
                    }
                
                    @Test
                    void testExceptionAssignment() {
                        ArithmeticException ex = assertThrows(ArithmeticException.class, () -> {
                            int i = 1 / 0;
                        });
                
                        assertTrue(ex.getMessage().contains("/ by zero"));
                    }
                
                    @Test
                    void testTimeoutPreemptively() {
                        String result = assertTimeoutPreemptively(Duration.ofMillis(500), () -> {
                            Thread.sleep(10);
                            return "Done";
                        });
                        assertEquals("Done", result);
                    }
                
                    @ParameterizedTest
                    @CsvSource({ "apple,  1", "banana, 2" })
                    void testParameterized(String fruit, int rank) {
                        assertNotNull(fruit);
                        assertNotEquals(0, rank);
                    }
                
                    @Test
                    void testLazyAndMethodReferences() {
                        List<String> data = List.of("Gemini");
                
                        assertTrue(data::isEmpty);
                        assertTrue(() -> data.contains("Gemini"), () -> "Failure on " + System.currentTimeMillis());
                    }
                
                    @Test
                    void testTimeoutWithResult() {
                        String result = assertTimeout(Duration.ofMinutes(1), () -> {
                            return "Expected Result";
                        });
                
                        assertEquals("Expected Result", result);
                    }
                
                    @Test
                    void testTheMessageOverloadAmbiguity() {
                        assertEquals("Deeply", "Deeply", () -> "Message generated at " + System.nanoTime());
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("expected BooleanSupplier conversion, got: " + text,
                text.contains("Assertions.assertThat(counter.get() == 0).as(\"Lazy message \" + \"from supplier\").isTrue();"));
        assertTrue(text.contains("Assertions.assertThat(counter.incrementAndGet() > 5).as(\"Incrementing inside assertion\").isFalse();"));
        assertTrue(text.contains("SoftAssertions.assertSoftly((SoftAssertions softly) -> {"));
        assertTrue(text.contains("SoftAssertions.assertSoftly((SoftAssertions softly1) -> {"));
        assertTrue(text.contains("import org.assertj.core.api.SoftAssertions;"));
        assertTrue(text.contains("Assertions.assertThat(actualLines).containsExactlyElementsOf(expectedLines);"));
        assertTrue(text.contains("ArithmeticException ex = Assertions.catchThrowableOfType(() -> {"));
        assertTrue(text.contains("String result = \"Done\";"));
        assertTrue(text.contains("Assertions.assertThat(data.isEmpty()).isTrue();"));
        assertTrue(text.contains("Assertions.assertThat(data.contains(\"Gemini\")).as(\"Failure on \" + System.currentTimeMillis()).isTrue();"));
        assertTrue(text.contains("String result = \"Expected Result\";"));
        assertTrue(text.contains("Assertions.assertThat(\"Deeply\").as(\"Message generated at \" + System.nanoTime()).isEqualTo(\"Deeply\");"));
        assertFalse(text.contains("assertAll("));
        assertFalse(text.contains("assertTimeout("));
        assertFalse(text.contains("assertTimeoutPreemptively("));
        assertFalse(text.contains("assertTrue("));
    }

    public void testReportsJupiterAssertionsFromWildcardStaticImportWithoutWarningMarkup() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("UltimateJupiterChallenge.java", """
                import org.junit.jupiter.api.*;
                import org.junit.jupiter.params.ParameterizedTest;
                import org.junit.jupiter.params.provider.CsvSource;
                
                import java.time.Duration;
                import java.util.List;
                import java.util.concurrent.atomic.AtomicInteger;
                import java.util.stream.Stream;
                
                import static org.junit.jupiter.api.Assertions.*;
                
                @DisplayName("Complex JUnit 5 Suite")
                class UltimateJupiterChallenge {
                    @Test
                    @DisplayName("The Boolean Supplier Trap")
                    void testBooleanSuppliers() {
                        AtomicInteger counter = new AtomicInteger(0);
                
                        assertTrue(() -> counter.get() == 0, () -> "Lazy message " + "from supplier");
                        assertFalse(counter.incrementAndGet() > 5, "Incrementing inside assertion");
                    }
                
                    @Test
                    @DisplayName("Deeply Nested Soft Assertions")
                    void testNestedAssertAll() {
                        assertAll("User Profile",
                                () -> {
                                    String name = "Gemini";
                                    assertNotNull(name);
                                    assertAll("Name Details",
                                            () -> assertTrue(name.startsWith("G")),
                                            () -> assertEquals(6, name.length())
                                    );
                                },
                                () -> assertEquals(2026, 1000 + 1026, "Math check")
                        );
                    }
                
                    @Test
                    @DisplayName("Lines Match Regex Challenge")
                    void testLinesMatch() {
                        List<String> expectedLines = List.of("^[0-9]*$", ">> skip 2 lines >>", "final");
                        List<String> actualLines = List.of("123", "ignore1", "ignore2", "final");
                
                        assertLinesMatch(expectedLines, actualLines);
                    }
                
                    @Test
                    @DisplayName("Exception Capture and Reuse")
                    void testExceptionAssignment() {
                        ArithmeticException ex = assertThrows(ArithmeticException.class, () -> {
                            int i = 1 / 0;
                        });
                
                        assertTrue(ex.getMessage().contains("/ by zero"));
                    }
                
                    @Test
                    @DisplayName("Timeout with Return Value")
                    void testTimeoutPreemptively() {
                        String result = assertTimeoutPreemptively(Duration.ofMillis(500), () -> {
                            Thread.sleep(10);
                            return "Done";
                        });
                        assertEquals("Done", result);
                    }
                
                    @ParameterizedTest
                    @CsvSource({ "apple,  1", "banana, 2" })
                    void testParameterized(String fruit, int rank) {
                        assertNotNull(fruit);
                        assertNotEquals(0, rank);
                    }
                
                    @Test
                    void testLazyAndMethodReferences() {
                        List<String> data = List.of("Gemini");
                
                        assertTrue(data::isEmpty);
                        assertTrue(() -> data.contains("Gemini"), () -> "Failure on " + System.currentTimeMillis());
                    }
                
                    @Test
                    void testAssignmentFromAssertThrows() {
                        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                            throw new IllegalArgumentException("Target Message");
                        });
                
                        assertTrue(exception.getMessage().contains("Target"));
                    }
                
                    @Test
                    void testAssertAllWithInjectedLogic() {
                        assertAll("Outer Group",
                                () -> {
                                    int value = 10;
                                    assertAll("Inner Group",
                                            () -> assertEquals(10, value),
                                            () -> assertTrue(value > 0)
                                    );
                                },
                                () -> assertNotNull(new Object())
                        );
                    }
                
                    @Test
                    void testTimeoutWithResult() {
                        String result = assertTimeout(Duration.ofMinutes(1), () -> {
                            return "Expected Result";
                        });
                
                        assertEquals("Expected Result", result);
                    }
                
                    @Test
                    void testTheMessageOverloadAmbiguity() {
                        assertEquals("Deeply", "Deeply", () -> "Message generated at " + System.nanoTime());
                    }
                }
                """);
        this.myFixture.doHighlighting();
        java.util.List<IntentionAction> fixes = this.myFixture.getAllQuickFixes();
        IntentionAction fix = null;
        for (IntentionAction candidate : fixes) {
            if ("Convert JUnit assertions to AssertJ".equals(candidate.getFamilyName())) {
                fix = candidate;
                break;
            }
        }
        assertNotNull("expected JUnit-to-AssertJ quick-fix for wildcard static import, got: " + fixes, fix);
    }

    public void testConvertsJupiterAssumptionsAndAddsAssertJImports() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("DeepLogicMigrationTest.java", """
                import org.junit.jupiter.api.*;
                import java.util.*;
                import java.util.stream.Stream;
                
                import static org.junit.jupiter.api.Assertions.*;
                import static org.junit.jupiter.api.Assumptions.*;
                
                class DeepLogicMigrationTest {
                    @Test
                    void testAssumptions() {
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>assumeTrue(System.getProperty("os.name").contains("Linux"), "Only runs on Linux")</warning>;
                        assumeFalse(() -> false, () -> "Lazy assumption message");
                    }
                
                    @Test
                    void testGenericsAndTypeErasure() {
                        List<String> actual = List.of("A", "B");
                        List<String> expected = List.of("A", "B");
                
                        assertEquals(expected, actual);
                    }
                
                    @Test
                    @SuppressWarnings("AssertBetweenInconvertibleTypes")
                    void testInconvertibleTypes() {
                        assertEquals("1", 1, "This might be intentional legacy weirdness");
                    }
                
                    @Test
                    void testStreamAssertion() {
                        Stream<String> stringStream = Stream.of("alpha", "beta", "gamma");
                
                        assertTrue(stringStream.anyMatch(s -> s.startsWith("a")));
                    }
                
                    @Test
                    void testNestedLambdaWithOuterVariable() {
                        String outer = "top";
                        assertAll("Outer",
                                () -> {
                                    String middle = "mid";
                                    assertAll("Inner",
                                            () -> {
                                                assertEquals("top", outer);
                                                assertEquals("mid", middle);
                                            }
                                    );
                                }
                        );
                    }
                
                    @Test
                    void testNullLiteralAmbiguity() {
                        Object thing = null;
                        assertEquals(null, thing);
                        assertNotEquals(null, new Object());
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("expected AssertJ Assertions import, got: " + text,
                text.contains("import org.assertj.core.api.Assertions;"));
        assertTrue("expected AssertJ Assumptions import, got: " + text,
                text.contains("import org.assertj.core.api.Assumptions;"));
        assertTrue(text.contains("import org.assertj.core.api.SoftAssertions;"));
        assertFalse("must remove JUnit assumptions static import: " + text,
                text.contains("import static org.junit.jupiter.api.Assumptions.*;"));
        assertTrue(text.contains("Assumptions.assumeThat(System.getProperty(\"os.name\").contains(\"Linux\")).as(\"Only runs on Linux\").isTrue();"));
        assertTrue(text.contains("Assumptions.assumeThat(false).as(\"Lazy assumption message\").isFalse();"));
        assertTrue(text.contains("Assertions.assertThat(actual).isEqualTo(expected);"));
        assertTrue(text.contains("SoftAssertions.assertSoftly((SoftAssertions softly) -> {"));
        assertTrue(text.contains("SoftAssertions.assertSoftly((SoftAssertions softly1) -> {"));
        assertTrue(text.contains("Assertions.assertThat(thing).isNull();"));
        assertTrue(text.contains("Assertions.assertThat(new Object()).isNotEqualTo(null);"));
    }

    public void testAddsAssertJAssertionsImportWhenReplacingJunit4StaticWildcardImport() {
        this.myFixture.enableInspections(new SpringAssertJStyleInspection());
        this.myFixture.configureByText("Sample.java", """
                import static org.junit.Assert.*;
                
                class Sample {
                    void test(String actual) {
                        <warning descr="Use AssertJ assertions instead of 'Assertions' (Spring convention)"><caret>assertEquals("expected", actual)</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert JUnit assertions to AssertJ");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("expected AssertJ Assertions import, got: " + text,
                text.contains("import org.assertj.core.api.Assertions;"));
        assertFalse("must remove JUnit 4 static wildcard import: " + text,
                text.contains("import static org.junit.Assert.*;"));
        assertTrue(text.contains("Assertions.assertThat(actual).isEqualTo(\"expected\");"));
    }
}
