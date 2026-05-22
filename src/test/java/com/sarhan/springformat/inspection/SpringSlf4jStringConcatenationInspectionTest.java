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
public class SpringSlf4jStringConcatenationInspectionTest extends BasePlatformTestCase {

    private static final String LOGGER_STUB = """
            package org.slf4j;
            public interface Logger {
                void trace(String msg);
                void trace(String format, Object arg);
                void trace(String format, Object... args);
                void debug(String msg);
                void debug(String format, Object arg);
                void debug(String format, Object... args);
                void info(String msg);
                void info(String format, Object arg);
                void info(String format, Object... args);
                void warn(String msg);
                void warn(String format, Object arg);
                void warn(String format, Object... args);
                void error(String msg);
                void error(String format, Object arg);
                void error(String format, Object... args);
            }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("org/slf4j/Logger.java", LOGGER_STUB);
    }

    public void testFlagsConcatenationAndConvertsToPlaceholder() {
        this.myFixture.enableInspections(new SpringSlf4jStringConcatenationInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.slf4j.Logger;
                
                class Sample {
                    private final Logger log = null;
                    void doIt(String x, int y) {
                        log.info(<warning descr="SLF4J log message uses String concatenation; use '[{}]' placeholders instead"><caret>"x=" + x + ", y=" + y</warning>);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert to SLF4J placeholder ('[{}]') message");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("expected placeholder format, got: " + text,
                text.contains("log.info(\"x=[{}], y=[{}]\", x, y);"));
    }

    public void testDoesNotFlagPlainPlaceholderCall() {
        this.myFixture.enableInspections(new SpringSlf4jStringConcatenationInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.slf4j.Logger;
                
                class Sample {
                    private final Logger log = null;
                    void doIt(String x) {
                        log.info("x={}", x);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagNonSlf4jLogger() {
        this.myFixture.enableInspections(new SpringSlf4jStringConcatenationInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static class Other { void info(String msg) {} }
                    private final Other log = new Other();
                    void doIt(String x) {
                        log.info("x=" + x);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsLombokSlf4jAnnotatedClass() {
        this.myFixture.addFileToProject("lombok/extern/slf4j/Slf4j.java", """
                package lombok.extern.slf4j;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.SOURCE)
                @Target(ElementType.TYPE)
                public @interface Slf4j { }
                """);
        this.myFixture.enableInspections(new SpringSlf4jStringConcatenationInspection());
        this.myFixture.configureByText("Sample.java", """
                import lombok.extern.slf4j.Slf4j;
                
                @Slf4j
                class Sample {
                    void doIt(String x) {
                        log.info(<warning descr="SLF4J log message uses String concatenation; use '[{}]' placeholders instead"><caret>"x=" + x</warning>);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert to SLF4J placeholder ('[{}]') message");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("expected placeholder, got: " + text,
                text.contains("log.info(\"x=[{}]\", x);"));
    }

    public void testDoesNotFlagPlainLogFieldWithoutLombokAnnotation() {
        this.myFixture.enableInspections(new SpringSlf4jStringConcatenationInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    static class FakeLog { void info(String msg) {} }
                    private final FakeLog log = new FakeLog();
                    void doIt(String x) {
                        log.info("x=" + x);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsConcatenationWithTrailingThrowableArgument() {
        this.myFixture.enableInspections(new SpringSlf4jStringConcatenationInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.slf4j.Logger;
                
                class Sample {
                    private final Logger log = null;
                    void doIt(String op, Throwable ex) {
                        log.error(<warning descr="SLF4J log message uses String concatenation; use '[{}]' placeholders instead">"failed op=" + op</warning>, ex);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testPreservesExistingPlaceholdersAndConcatenatesStaticConstant() {
        this.myFixture.enableInspections(new SpringSlf4jStringConcatenationInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.slf4j.Logger;
                
                class Sample {
                    private static final String USER_INFO_PATH = "/userinfo";
                    private final Logger log = null;
                
                    void doIt(String plainBaseUrl, Client client, String token, Exception e) {
                        log.error(<warning descr="SLF4J log message uses String concatenation; use '[{}]' placeholders instead"><caret>"Failed to call endpoint. URL: {}{}" + USER_INFO_PATH + "\\n[{}]"</warning>,
                                plainBaseUrl, getRealm(client), token, e);
                    }
                
                    String getRealm(Client client) { return "realm"; }
                    interface Client { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert to SLF4J placeholder ('[{}]') message");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("must preserve existing placeholders and static constant concatenation, got: " + text,
                text.contains("log.error(\"Failed to call endpoint. URL: {}{}\" + USER_INFO_PATH + \"\\n[{}]\", " +
                        "plainBaseUrl, getRealm(client), token, e);"));
        assertFalse("must not escape SLF4J placeholders: " + text, text.contains("\\{}"));
    }

    public void testInsertsNewArgumentAfterExistingPlaceholderArguments() {
        this.myFixture.enableInspections(new SpringSlf4jStringConcatenationInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.slf4j.Logger;
                
                class Sample {
                    private final Logger log = null;
                
                    String doIt(User user) {
                        final var defaultLocale = "EN";
                        log.error(<warning descr="SLF4J log message uses String concatenation; use '[{}]' placeholders instead"><caret>"No locale attribute on user {}. Default " + defaultLocale + " will be applied."</warning>,
                                user.getId());
                        return defaultLocale;
                    }
                
                    interface User { String getId(); }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert to SLF4J placeholder ('[{}]') message");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("must keep existing placeholder argument before newly extracted argument, got: " + text,
                text.contains("log.error(\"No locale attribute on user {}. Default [{}] will be applied.\", " +
                        "user.getId(), defaultLocale);"));
        assertFalse("must not put new argument before existing placeholder argument: " + text,
                text.contains("defaultLocale, user.getId()"));
        assertFalse("must not escape SLF4J placeholders: " + text, text.contains("\\{}"));
    }

    public void testConvertsStaticConstantConcatenationWhenExistingArgumentIsUnused() {
        this.myFixture.enableInspections(new SpringSlf4jStringConcatenationInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.slf4j.Logger;
                
                class Sample {
                    private static final String TO_DEACTIVATE_MESSAGE = "Set property [{}] to false";
                    private static final String ENABLED_PROPERTY = "app.enabled";
                    private final Logger log = null;
                
                    void doIt() {
                        log.info(<warning descr="SLF4J log message uses String concatenation; use '[{}]' placeholders instead"><caret>"InstanceInformationService created. " + TO_DEACTIVATE_MESSAGE</warning>, ENABLED_PROPERTY);
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Convert to SLF4J placeholder ('[{}]') message");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("expected static constant to become placeholder, got: " + text,
                text.contains("log.info(\"InstanceInformationService created. Set property [{}] to false\", ENABLED_PROPERTY);"));
    }
}
