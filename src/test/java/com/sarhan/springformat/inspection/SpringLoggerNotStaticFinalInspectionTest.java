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
public class SpringLoggerNotStaticFinalInspectionTest extends BasePlatformTestCase {

    private static final String LOGGER_STUB = """
            package org.slf4j;
            public interface Logger { }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("org/slf4j/Logger.java", LOGGER_STUB);
    }

    public void testFlagsInstanceLoggerFieldAndAddsModifiers() {
        this.myFixture.enableInspections(new SpringLoggerNotStaticFinalInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.slf4j.Logger;
                
                class Sample {
                    private final Logger <warning descr="SLF4J Logger field 'log' should be private static final"><caret>log</warning> = null;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Make logger field 'private static final'");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue("expected private static final, got: " + text,
                text.contains("private static final Logger log"));
    }

    public void testFlagsPublicLoggerAndDropsConflictingModifier() {
        this.myFixture.enableInspections(new SpringLoggerNotStaticFinalInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.slf4j.Logger;
                
                class Sample {
                    public static Logger <warning descr="SLF4J Logger field 'log' should be private static final"><caret>log</warning> = null;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Make logger field 'private static final'");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertFalse("must drop public: " + text, text.contains("public static"));
        assertTrue("expected private static final, got: " + text,
                text.contains("private static final Logger log"));
    }

    public void testDoesNotFlagPrivateStaticFinalLogger() {
        this.myFixture.enableInspections(new SpringLoggerNotStaticFinalInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.slf4j.Logger;
                
                class Sample {
                    private static final Logger log = null;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagNonLoggerType() {
        this.myFixture.enableInspections(new SpringLoggerNotStaticFinalInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    private String log = "x";
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
