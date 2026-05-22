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

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class SpringMutableExceptionInspectionTest extends BasePlatformTestCase {

    public void testFlagsMutableFieldInException() {
        this.myFixture.enableInspections(new SpringMutableExceptionInspection());
        this.myFixture.configureByText("SampleException.java", """
                class SampleException extends RuntimeException {
                    <warning descr="Exception field should be final">private String code;</warning>
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsMutableStaticFieldInException() {
        this.myFixture.enableInspections(new SpringMutableExceptionInspection());
        this.myFixture.configureByText("SampleException.java", """
                class SampleException extends RuntimeException {
                    <warning descr="Exception field should be final">static String code;</warning>
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsMutableFieldInError() {
        this.myFixture.enableInspections(new SpringMutableExceptionInspection());
        this.myFixture.configureByText("SampleError.java", """
                class SampleError extends Error {
                    <warning descr="Exception field should be final">private int status;</warning>
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagFinalFieldInException() {
        this.myFixture.enableInspections(new SpringMutableExceptionInspection());
        this.myFixture.configureByText("SampleException.java", """
                class SampleException extends Exception {
                    private final String code;

                    SampleException(String code) {
                        this.code = code;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagNonExceptionClass() {
        this.myFixture.enableInspections(new SpringMutableExceptionInspection());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    private String code;
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

}
