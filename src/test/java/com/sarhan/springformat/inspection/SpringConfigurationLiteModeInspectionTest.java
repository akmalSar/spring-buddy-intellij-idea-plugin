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
public class SpringConfigurationLiteModeInspectionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.addFileToProject("org/springframework/context/annotation/Bean.java",
                "package org.springframework.context.annotation;\npublic @interface Bean { }");
        this.myFixture.addFileToProject("org/springframework/context/annotation/Configuration.java",
                "package org.springframework.context.annotation;\npublic @interface Configuration { }");
        this.myFixture.addFileToProject("org/springframework/stereotype/Component.java",
                "package org.springframework.stereotype;\npublic @interface Component { }");
        this.myFixture.addFileToProject("org/springframework/stereotype/Service.java",
                "package org.springframework.stereotype;\npublic @interface Service { }");
    }

    public void testFlagsBeanMethodCallInComponent() {
        this.myFixture.enableInspections(new SpringConfigurationLiteModeInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.context.annotation.Bean;
                import org.springframework.stereotype.Component;

                @Component
                class SampleConfig {
                    @Bean
                    String foo() { return "foo"; }
                    @Bean
                    String bar() {
                        return <warning descr="Calling @Bean method from another @Bean method in a non-@Configuration class uses 'lite mode' and may create a duplicate instance; annotate the class with @Configuration">foo()</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagBeanMethodCallInConfiguration() {
        this.myFixture.enableInspections(new SpringConfigurationLiteModeInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                @Configuration
                class SampleConfig {
                    @Bean
                    String foo() { return "foo"; }
                    @Bean
                    String bar() {
                        return foo();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagRegularMethodCall() {
        this.myFixture.enableInspections(new SpringConfigurationLiteModeInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.stereotype.Component;

                @Component
                class SampleService {
                    String helper() { return "helper"; }
                    String process() {
                        return helper();
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFixReplacesComponentWithConfiguration() {
        this.myFixture.enableInspections(new SpringConfigurationLiteModeInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.context.annotation.Bean;
                import org.springframework.stereotype.Component;

                @Component
                class SampleConfig {
                    @Bean
                    String foo() { return "foo"; }
                    @Bean
                    String bar() {
                        return <warning descr="Calling @Bean method from another @Bean method in a non-@Configuration class uses 'lite mode' and may create a duplicate instance; annotate the class with @Configuration"><caret>foo()</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Annotate class with @Configuration");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text, text.contains("@Configuration"));
        assertTrue(text, text.contains("import org.springframework.context.annotation.Configuration;"));
    }
}
