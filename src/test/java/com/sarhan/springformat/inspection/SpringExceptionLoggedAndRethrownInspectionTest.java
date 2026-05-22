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
public class SpringExceptionLoggedAndRethrownInspectionTest extends BasePlatformTestCase {

    private static final String LOGGER_STUB = """
            package org.slf4j;
            public interface Logger {
                void error(String msg);
                void error(String msg, Throwable t);
                void error(String format, Object... args);
                void warn(String msg);
                void warn(String msg, Throwable t);
                void info(String msg);
                void debug(String msg);
                void trace(String msg);
            }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("org/slf4j/Logger.java", LOGGER_STUB);
    }

    public void testFlagsLogAndRethrowAndRemovesLog() {
        this.myFixture.enableInspections(new SpringExceptionLoggedAndRethrownInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.slf4j.Logger;
                
                class Sample {
                    private static final Logger log = null;
                    void doIt() throws Exception {
                        try {
                            throw new Exception();
                        }
                        catch (Exception ex) {
                            <warning descr="Exception is logged and rethrown — the caller will log it again"><caret>log.error("oops");</warning>
                            throw ex;
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(true, false, true, true);
        IntentionAction fix = this.myFixture.getAvailableIntentions().stream()
                .filter((IntentionAction intention) -> "Remove redundant log statement".equals(intention.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("quick-fix not found among: " + this.myFixture.getAvailableIntentions()));
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertFalse("log statement must be removed: " + text, text.contains("log.error"));
        assertTrue("rethrow must remain: " + text, text.contains("throw ex;"));
    }

    public void testDoesNotFlagLogWithoutRethrow() {
        this.myFixture.enableInspections(new SpringExceptionLoggedAndRethrownInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.slf4j.Logger;
                
                class Sample {
                    private static final Logger log = null;
                    void doIt() {
                        try {
                            throw new RuntimeException();
                        }
                        catch (RuntimeException ex) {
                            log.error("oops");
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(true, false, true, true);
    }

    public void testDoesNotFlagRethrowOfDifferentVariable() {
        this.myFixture.enableInspections(new SpringExceptionLoggedAndRethrownInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.slf4j.Logger;
                
                class Sample {
                    private static final Logger log = null;
                    void doIt() {
                        try {
                            throw new RuntimeException();
                        }
                        catch (RuntimeException ex) {
                            log.error("oops");
                            throw new IllegalStateException(ex);
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsLombokLogAndRethrow() {
        this.myFixture.addFileToProject("lombok/extern/slf4j/Slf4j.java", """
                package lombok.extern.slf4j;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.SOURCE)
                @Target(ElementType.TYPE)
                public @interface Slf4j { }
                """);
        this.myFixture.enableInspections(new SpringExceptionLoggedAndRethrownInspection());
        this.myFixture.configureByText("Sample.java", """
                import lombok.extern.slf4j.Slf4j;
                
                @Slf4j
                class Sample {
                    void doIt() throws Exception {
                        try {
                            throw new Exception();
                        }
                        catch (Exception ex) {
                            <warning descr="Exception is logged and rethrown — the caller will log it again"><caret>log.error("oops");</warning>
                            throw ex;
                        }
                    }
                }
                """);
        this.myFixture.checkHighlighting(true, false, true, true);
    }
}
