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
public class SpringMultipartFileUploadMissingValidationInspectionTest extends BasePlatformTestCase {

    public void testReportsMissingValidation() {
        this.myFixture.enableInspections(new SpringMultipartFileUploadMissingValidationInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface RestController { }
                @interface PostMapping { String value(); }
                class MultipartFile { }

                @RestController
                class Sample {
                    @PostMapping("/upload")
                    void upload(<warning descr="MultipartFile upload should specify size limits (@Size, @Max) or content-type validation to prevent storage exhaustion">file</warning> MultipartFile) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithSizeAnnotation() {
        this.myFixture.enableInspections(new SpringMultipartFileUploadMissingValidationInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface RestController { }
                @interface PostMapping { String value(); }
                @interface Max { long value(); }
                class MultipartFile { }

                @RestController
                class Sample {
                    @PostMapping("/upload")
                    void upload(@Max(10485760) MultipartFile file) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithContentTypeCheck() {
        this.myFixture.enableInspections(new SpringMultipartFileUploadMissingValidationInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface RestController { }
                @interface PostMapping { String value(); }
                class MultipartFile {
                    String getContentType() { return ""; }
                }

                @RestController
                class Sample {
                    @PostMapping("/upload")
                    void upload(MultipartFile file) {
                        if (file.getContentType().contains("image")) { }
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportOnNonController() {
        this.myFixture.enableInspections(new SpringMultipartFileUploadMissingValidationInspection());
        this.myFixture.configureByText("Sample.java", """
                class MultipartFile { }

                class Sample {
                    void upload(MultipartFile file) { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
