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

import java.util.List;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.assertj.core.api.Assertions;

/**
 * @author Akmal Sarhan
 */
public class SpringBoot3MigrationInspectionTests extends BasePlatformTestCase {

    public void testJavaxImportUsage_reportsOnlyJakartaEePackages() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.JavaxImport());
        this.myFixture.configureByText("Sample.java", """
                import javax.servlet.http.HttpServletRequest;
                import javax.persistence.Entity;
                import javax.swing.JButton;

                class Sample {
                    HttpServletRequest request;
                    Entity entity;
                    JButton button;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "javax.* type moved to jakarta.*"))
                .as("Jakarta EE javax imports")
                .isEqualTo(2);
    }

    public void testJavaxFqnUsage_reportsReferencesAndStrings() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.JavaxFqn());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    javax.servlet.http.HttpServletRequest request;
                    String type = "javax.persistence.Entity";
                    String safe = "javax.swing.JButton";
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "javax.* type moved to jakarta.*"))
                .as("fully-qualified javax usage")
                .isEqualTo(2);
    }

    public void testJavaxFqnUsage_ignoresImportedShortType() {
        this.myFixture.enableInspections(
                new SpringBoot3MigrationInspection.JavaxImport(),
                new SpringBoot3MigrationInspection.JavaxFqn());
        this.myFixture.configureByText("Sample.java", """
                import javax.servlet.http.HttpServletRequest;

                class Sample {
                    HttpServletRequest request;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "javax.* type moved to jakarta.*"))
                .as("import warning without short type overlap")
                .isEqualTo(1);
    }

    public void testConstructorBindingType_reportsSingleConstructorConfigurationProperties() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.ConstructorBindingType());
        this.myFixture.configureByText("Sample.java", """
                @interface ConfigurationProperties { String value(); }
                @interface ConstructorBinding { }

                @ConfigurationProperties("app")
                @<caret>ConstructorBinding
                class Sample {
                    Sample(int name) { }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("type-level constructor binding")
                .isEqualTo(1);
    }

    public void testConstructorBindingType_ignoresConstructorAnnotation() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.ConstructorBindingType());
        this.myFixture.configureByText("Sample.java", """
                @interface ConfigurationProperties { String value(); }
                @interface ConstructorBinding { }

                @ConfigurationProperties("app")
                class Sample {
                    @ConstructorBinding
                    Sample(String name) { }
                    Sample() { }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("constructor-level constructor binding")
                .isEqualTo(0);
    }

    public void testJavaxImportUsage_quickFixReplacesImport() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.JavaxImport());
        this.myFixture.configureByText("Sample.java", """
                import javax.servlet.http.HttpServletRequest;

                class Sample {
                    HttpServletRequest request;
                }
                """);
        this.myFixture.doHighlighting();

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with jakarta.* import"));

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("migrated import")
                .contains("import jakarta.servlet.http.HttpServletRequest;")
                .doesNotContain("import javax.servlet.http.HttpServletRequest;");
    }

    public void testConstructorBindingType_quickFixRemovesAnnotation() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.ConstructorBindingType());
        this.myFixture.configureByText("Sample.java", """
                @interface ConfigurationProperties { String value(); }
                @interface ConstructorBinding { }

                @ConfigurationProperties("app")
                @<caret>ConstructorBinding
                class Sample {
                    Sample(int name) { }
                }
                """);
        this.myFixture.doHighlighting();

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Remove @ConstructorBinding"));

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("constructor binding annotation removed")
                .doesNotContain("@ConstructorBinding\nclass Sample");
    }

    public void testRemovedSpringBootApis_reportProblems() {
        this.myFixture.enableInspections(
                new SpringBoot3MigrationInspection.YamlJsonParser(),
                new SpringBoot3MigrationInspection.HttpTraceRepository(),
                new SpringBoot3MigrationInspection.WebMvcMetricsFilter());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.json.YamlJsonParser;
                import org.springframework.boot.actuate.trace.http.HttpTraceRepository;
                import org.springframework.boot.actuate.metrics.web.servlet.WebMvcMetricsFilter;

                class Sample {
                    YamlJsonParser parser;
                    HttpTraceRepository repository;
                    WebMvcMetricsFilter filter;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countErrors(highlights))
                .as("removed Spring Boot APIs")
                .isEqualTo(6);
    }

    public void testJvmInfoMetricsBean_reportsManualBean() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.JvmInfoMetrics());
        this.myFixture.addFileToProject("io/micrometer/core/instrument/binder/jvm/JvmInfoMetrics.java", """
                package io.micrometer.core.instrument.binder.jvm;

                public class JvmInfoMetrics { }
                """);
        this.myFixture.configureByText("Sample.java", """
                package sample;

                @interface Bean { }
                class Config {
                    @Bean
                    io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics jvmInfoMetrics() {
                        return null;
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "JvmInfoMetrics is auto-configured"))
                .as("manual JvmInfoMetrics bean")
                .isEqualTo(1);
    }

    public void testEnableBatchProcessing_reportsAnnotation() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.EnableBatchProcessing());
        this.myFixture.configureByText("Sample.java", """
                @interface EnableBatchProcessing { }

                @EnableBatchProcessing
                class Sample { }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("EnableBatchProcessing annotation")
                .isEqualTo(1);
    }

    public void testTrailingSlashMatching_reportsRemovedSetter() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.TrailingSlashMatching());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void configure(Config configurer) {
                        configurer.setUseTrailingSlashMatch(true);
                    }
                }
                class Config {
                    void setUseTrailingSlashMatch(boolean enabled) { }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("removed trailing slash setter")
                .isEqualTo(1);
    }

    public void testTrailingSlashMatching_reportsMappingLiteral() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.TrailingSlashMatching());
        this.myFixture.configureByText("Sample.java", """
                @interface GetMapping { String value(); }

                class Sample {
                    @GetMapping("/greeting/")
                    void greeting() { }

                    @GetMapping("/health")
                    void health() { }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "Trailing slash route"))
                .as("trailing slash mapping literal")
                .isEqualTo(1);
    }

    public void testApacheHttpClient_reportsVersionFourUsage() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.ApacheHttpClient());
        this.myFixture.configureByText("Sample.java", """
                import org.apache.http.impl.client.CloseableHttpClient;

                class Sample {
                    CloseableHttpClient client;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "Apache HttpClient 4"))
                .as("Apache HttpClient 4 usage")
                .isEqualTo(1);
    }

    public void testSecurityConfiguration_reportsEnableSecurityWithoutConfiguration() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.SecurityConfiguration());
        this.myFixture.configureByText("Sample.java", """
                @interface EnableWebSecurity { }
                @interface Configuration { }

                @EnableWebSecurity
                class SecurityConfig { }

                @Configuration
                @EnableWebSecurity
                class ExistingConfig { }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "@Enable*Security"))
                .as("security configuration annotation")
                .isEqualTo(1);
    }

    public void testNestedConfigurationPropertiesValid_reportsNestedFieldWithoutValid() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.NestedConfigurationPropertiesValid());
        this.myFixture.configureByText("Sample.java", """
                @interface ConfigurationProperties { String value(); }
                @interface Validated { }
                @interface Valid { }

                @ConfigurationProperties("app")
                @Validated
                class Sample {
                    Nested nested;
                    @Valid Nested valid;
                    String name;
                }

                class Nested { String value; }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "Nested @ConfigurationProperties"))
                .as("nested configuration properties validation")
                .isEqualTo(1);
    }

    public void testPreciseBeanType_reportsImpreciseBeanReturnType() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.PreciseBeanType());
        this.myFixture.configureByText("Sample.java", """
                @interface Bean { }
                interface Service { }
                class DefaultService implements Service { }
                class Config {
                    @Bean
                    Service service() {
                        return new DefaultService();
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "@Bean methods should return concrete types"))
                .as("precise bean return type")
                .isEqualTo(1);
    }

    public void testSolrAutoConfigurationExclude_reportsRemovedExclusion() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.SolrAutoConfigurationExclude());
        this.myFixture.configureByText("Sample.java", """
                @interface SpringBootApplication { Class<?>[] exclude(); }
                class SolrAutoConfiguration { }

                @SpringBootApplication(exclude = { SolrAutoConfiguration.class })
                class Sample { }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "SolrAutoConfiguration"))
                .as("Solr auto-configuration exclusion")
                .isEqualTo(1);
    }

    public void testReactorContextPropagation_reportsHooksCall() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.ReactorContextPropagation());
        this.myFixture.configureByText("Sample.java", """
                class Hooks {
                    static void enableAutomaticContextPropagation() { }
                }
                class Sample {
                    void init() {
                        Hooks.enableAutomaticContextPropagation();
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "spring.reactor.context-propagation"))
                .as("Reactor context propagation hook")
                .isEqualTo(1);
    }

    public void testRestTemplateRequestFactoryBuilder_reportsOldMethod() {
        this.myFixture.enableInspections(new SpringBoot3MigrationInspection.RestTemplateRequestFactoryBuilder());
        this.myFixture.configureByText("Sample.java", """
                class RestTemplateBuilder {
                    RestTemplateBuilder requestFactory(Object factory) { return this; }
                }
                class Sample {
                    void configure(RestTemplateBuilder builder, Object factory) {
                        builder.requestFactory(factory);
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "requestFactoryBuilder"))
                .as("RestTemplateBuilder requestFactory migration")
                .isEqualTo(1);
    }

    private static long countErrors(List<HighlightInfo> highlights) {
        return highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.ERROR)
                .filter((HighlightInfo h) -> h.getDescription() != null)
                .count();
    }

    private static long countWarnings(List<HighlightInfo> highlights) {
        return highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .filter((HighlightInfo h) -> h.getDescription() != null)
                .count();
    }

    private static long countDescriptions(List<HighlightInfo> highlights, String description) {
        return highlights.stream()
                .filter((HighlightInfo h) -> h.getDescription() != null)
                .filter((HighlightInfo h) -> h.getDescription().contains(description))
                .count();
    }
}
