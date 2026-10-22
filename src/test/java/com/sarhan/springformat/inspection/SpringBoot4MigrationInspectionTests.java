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
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.assertj.core.api.Assertions;

/**
 * Tests for {@link SpringBoot4MigrationInspection}. The light test project is
 * reused between tests, so the Boot target is reset in {@link #tearDown()}.
 *
 * @author Akmal Sarhan
 */
public class SpringBoot4MigrationInspectionTests extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        SpringBoot4TestSupport.setBoot4(getProject());
    }

    @Override
    protected void tearDown() throws Exception {
        SpringBoot4TestSupport.reset(getProject());
        super.tearDown();
    }

    public void testMockBean_reportsRemovedAnnotation() {
        this.myFixture.addFileToProject("org/springframework/boot/test/mock/mockito/MockBean.java",
                "package org.springframework.boot.test.mock.mockito;\npublic @interface MockBean { }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.MockBean());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.test.mock.mockito.MockBean;
                import org.example.Service;

                class Sample {
                    @MockBean
                    Service service;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "removed in Spring Boot 4"))
                .as("field-level @MockBean report and import report")
                .isEqualTo(2);
    }

    public void testMockBean_lookalikeShortNameIsIgnored() {
        this.myFixture.addFileToProject("org/example/MockBean.java",
                "package org.example;\npublic @interface MockBean { }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.MockBean());
        this.myFixture.configureByText("Sample.java", """
                import org.example.MockBean;

                class Sample {
                    @MockBean
                    String name;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "removed in Spring Boot 4"))
                .as("unrelated MockBean annotation")
                .isZero();
    }

    public void testMockBean_quickFixReplacesAnnotation() {
        this.myFixture.addFileToProject("org/springframework/boot/test/mock/mockito/MockBean.java",
                "package org.springframework.boot.test.mock.mockito;\npublic @interface MockBean {\n"
                        + "    String name() default \"\";\n}\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.MockBean());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.test.mock.mockito.MockBean;

                class Sample {
                    @<caret>MockBean(name = "svc")
                    String service;
                }
                """);
        this.myFixture.doHighlighting();

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with @MockitoBean"));

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("migrated MockitoBean annotation keeps name attribute")
                .contains("MockitoBean")
                .contains("(name = \"svc\")");
    }

    public void testMockBean_targetBoot3IsSilent() {
        SpringBoot4TestSupport.setBoot3(getProject());
        this.myFixture.addFileToProject("org/springframework/boot/test/mock/mockito/MockBean.java",
                "package org.springframework.boot.test.mock.mockito;\npublic @interface MockBean { }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.MockBean());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.test.mock.mockito.MockBean;

                class Sample {
                    @MockBean
                    String service;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "removed in Spring Boot 4"))
                .as("Boot 4 inspection must stay silent on Boot 3 target")
                .isZero();
    }

    public void testJacksonPackage_reportsDatabindImport() {
        this.myFixture.addFileToProject("com/fasterxml/jackson/databind/ObjectMapper.java",
                "package com.fasterxml.jackson.databind;\npublic class ObjectMapper { }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.JacksonPackage());
        this.myFixture.configureByText("Sample.java", """
                import com.fasterxml.jackson.databind.ObjectMapper;

                class Sample {
                    ObjectMapper mapper;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "tools.jackson"))
                .as("Jackson 2 databind import")
                .isEqualTo(1);
    }

    public void testJacksonPackage_jacksonAnnotationsIsIgnored() {
        this.myFixture.addFileToProject("com/fasterxml/jackson/annotation/JsonIgnore.java",
                "package com.fasterxml.jackson.annotation;\npublic @interface JsonIgnore { }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.JacksonPackage());
        this.myFixture.configureByText("Sample.java", """
                import com.fasterxml.jackson.annotation.JsonIgnore;

                class Sample {
                    @JsonIgnore
                    String name;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "tools.jackson"))
                .as("jackson.annotation package did not move")
                .isZero();
    }

    public void testJacksonRenamedType_reportsType() {
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.JacksonRenamedType());
        this.myFixture.configureByText("Sample.java", """
                import com.fasterxml.jackson.databind.JsonSerializer;

                class Sample {
                    JsonSerializer serializer;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "renamed in Jackson 3"))
                .as("renamed Jackson type import and reference")
                .isGreaterThanOrEqualTo(1);
    }

    public void testBootJacksonApi_reportsRenamedAnnotation() {
        this.myFixture.addFileToProject("org/springframework/boot/jackson/JsonComponent.java",
                "package org.springframework.boot.jackson;\npublic @interface JsonComponent { }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.BootJacksonApi());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.jackson.JsonComponent;

                @<caret>JsonComponent
                class Sample { }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "renamed in Spring Boot 4"))
                .as("Boot Jackson rename for annotation and import")
                .isEqualTo(2);
    }

    public void testListenableFuture_reportsRemovedType() {
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.ListenableFuture());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.util.concurrent.ListenableFuture;

                class Sample {
                    ListenableFuture<String> future() { return null; }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "ListenableFuture API was removed"))
                .as("removed ListenableFuture import")
                .isGreaterThanOrEqualTo(1);
    }

    public void testSecurityAndChain_reportsRemovedAndMethod() {
        this.myFixture.addFileToProject("org/springframework/security/config/annotation/web/Configurer.java",
                """
                        package org.springframework.security.config.annotation.web;
                        public class Configurer {
                            public Configurer and() { return this; }
                        }
                        """);
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.SecurityAndChain());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.security.config.annotation.web.Configurer;

                class Sample {
                    void configure(Configurer configurer) {
                        configurer.<caret>and();
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "and() was removed"))
                .as("removed and() on Security configurer")
                .isEqualTo(1);
    }

    public void testSecurityAndChain_ignoresUnresolvedAndMethod() {
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.SecurityAndChain());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    void configure(Object other) {
                        other.and();
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "and() was removed"))
                .as("unresolved receiver")
                .isZero();
    }

    public void testSecurityNonLambdaDsl_reportsZeroArgConfigurer() {
        this.myFixture.addFileToProject("org/springframework/security/config/annotation/web/builders/HttpSecurity.java",
                """
                        package org.springframework.security.config.annotation.web.builders;
                        public class HttpSecurity {
                            public HttpSecurity csrf() { return this; }
                            public HttpSecurity csrf(Customizer customizer) { return this; }
                        }
                        class Customizer { }
                        """);
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.SecurityNonLambdaDsl());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;

                class Sample {
                    void configure(HttpSecurity http) {
                        http.<caret>csrf();
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "without a Customizer argument"))
                .as("zero-arg csrf DSL call")
                .isEqualTo(1);
    }

    public void testSecurityNonLambdaDsl_lambaVariantIsIgnored() {
        this.myFixture.addFileToProject("org/springframework/security/config/annotation/web/builders/HttpSecurity.java",
                """
                        package org.springframework.security.config.annotation.web.builders;
                        public class HttpSecurity {
                            public HttpSecurity csrf(Customizer customizer) { return this; }
                        }
                        class Customizer { }
                        """);
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.SecurityNonLambdaDsl());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.security.config.annotation.web.builders.HttpSecurity;

                class Sample {
                    void configure(HttpSecurity http, Customizer customizer) {
                        http.csrf(customizer);
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "without a Customizer argument"))
                .as("lambda DSL variant already present")
                .isZero();
    }

    public void testAntPathRequestMatcher_reportsRemovedType() {
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.AntPathRequestMatcher());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

                class Sample {
                    AntPathRequestMatcher matcher;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "Spring Security 7"))
                .as("removed AntPathRequestMatcher import")
                .isGreaterThanOrEqualTo(1);
    }

    public void testSpringLangNullability_reportsNullableWithFix() {
        this.myFixture.addFileToProject("org/springframework/lang/Nullable.java",
                "package org.springframework.lang;\npublic @interface Nullable { }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.SpringLangNullability());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.lang.Nullable;

                class Sample {
                    @<caret>Nullable
                    String name;
                }
                """);
        this.myFixture.doHighlighting();

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with JSpecify annotation"));

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("JSpecify annotation replacement")
                .contains("org.jspecify.annotations.Nullable");
    }

    public void testSpringLangNullability_jetbrainsNullableIsIgnored() {
        this.myFixture.addFileToProject("org/jetbrains/annotations/Nullable.java",
                "package org.jetbrains.annotations;\npublic @interface Nullable { }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.SpringLangNullability());
        this.myFixture.configureByText("Sample.java", """
                import org.jetbrains.annotations.Nullable;

                class Sample {
                    @Nullable
                    String name;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "Spring Framework 7"))
                .as("JetBrains Nullable is unrelated")
                .isZero();
    }

    public void testRelocatedBootType_reportsRelocatedAnnotation() {
        this.myFixture.addFileToProject(
                "org/springframework/boot/test/autoconfigure/web/servlet/WebMvcTest.java",
                """
                        package org.springframework.boot.test.autoconfigure.web.servlet;
                        public @interface WebMvcTest { }
                        """);
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.RelocatedBootType());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

                @WebMvcTest
                class Sample { }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "relocated by the Boot 4 modularisation"))
                .as("relocated @WebMvcTest annotation and import")
                .isEqualTo(2);
    }

    public void testUndertow_reportsEmbeddedUndertowType() {
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.Undertow());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;

                class Sample {
                    UndertowServletWebServerFactory factory;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "Undertow is no longer a supported embedded server"))
                .as("Undertow import")
                .isEqualTo(1);
    }

    public void testUndertow_reportsInAutoModeOnBoot3Project() {
        SpringBoot4TestSupport.reset(getProject());
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.Undertow());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;

                class Sample {
                    UndertowServletWebServerFactory factory;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "Undertow is no longer a supported embedded server"))
                .as("auto-detect keeps the migration pre-flight active on a Boot 3 classpath")
                .isEqualTo(1);
    }

    public void testUndertow_explicitBoot3TargetIsSilent() {
        SpringBoot4TestSupport.setBoot3(getProject());
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.Undertow());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;

                class Sample {
                    UndertowServletWebServerFactory factory;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "Undertow is no longer a supported embedded server"))
                .as("explicit Boot 3 target opts out of the migration checks")
                .isZero();
    }

    public void testPathMatchSuffix_reportsRemovedSetter() {
        this.myFixture.addFileToProject(
                "org/springframework/web/servlet/config/annotation/PathMatchConfigurer.java",
                """
                        package org.springframework.web.servlet.config.annotation;
                        public class PathMatchConfigurer {
                            public PathMatchConfigurer setUseSuffixPatternMatch(boolean value) { return this; }
                        }
                        """);
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.PathMatchSuffix());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;

                class Sample {
                    void configure(PathMatchConfigurer configurer) {
                        configurer.<caret>setUseSuffixPatternMatch(true);
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "was removed in Spring Framework 7"))
                .as("removed PathMatchConfigurer setter")
                .isEqualTo(1);
    }

    public void testHibernateRemovedAnnotation_reportsWhereAnnotation() {
        this.myFixture.addFileToProject("org/hibernate/annotations/Where.java",
                "package org.hibernate.annotations;\npublic @interface Where { String clause() default \"\"; }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.HibernateRemovedAnnotation());
        this.myFixture.configureByText("Sample.java", """
                import org.hibernate.annotations.Where;

                @interface Entity { }

                @Entity
                class Sample {
                    @<caret>Where(clause = "deleted = false")
                    String name;
                }
                """);
        this.myFixture.doHighlighting();

        this.myFixture.launchAction(this.myFixture.findSingleIntention("@Where(clause = ...) to @SQLRestriction"));

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("SQLRestriction replacement keeps clause")
                .contains("@org.hibernate.annotations.SQLRestriction(\"deleted = false\")");
    }

    public void testHibernateRemovedAnnotation_whereLookalikeIsIgnored() {
        this.myFixture.addFileToProject("org/example/annotations/Where.java",
                "package org.example.annotations;\npublic @interface Where { String clause() default \"\"; }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.HibernateRemovedAnnotation());
        this.myFixture.configureByText("Sample.java", """
                import org.example.annotations.Where;

                class Sample {
                    @Where(clause = "x")
                    String name;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "removed in Hibernate 7"))
                .as("non-Hibernate @Where lookalike")
                .isZero();
    }

    public void testJunit4Runner_reportsSpringRunner() {
        this.myFixture.addFileToProject("org/springframework/test/context/junit4/SpringRunner.java",
                "package org.springframework.test.context.junit4;\npublic class SpringRunner { }\n");
        this.myFixture.addFileToProject("org/example/OtherRunner.java",
                "package org.example;\npublic class OtherRunner { }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.Junit4Runner());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.test.context.junit4.SpringRunner;

                @interface RunWith { Class<?> value(); }

                @RunWith(SpringRunner.class)
                class Sample { }

                @RunWith(org.example.OtherRunner.class)
                class Other { }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "JUnit 4 runner is deprecated"))
                .as("Spring runner import and annotation reported, other runner not")
                .isEqualTo(2);
    }

    public void testRelocatedBootType_autoConfigureMockMvcMovesHtmlUnitFlags() {
        this.myFixture.addFileToProject(
                "org/springframework/boot/test/autoconfigure/web/servlet/AutoConfigureMockMvc.java",
                """
                        package org.springframework.boot.test.autoconfigure.web.servlet;
                        public @interface AutoConfigureMockMvc {
                            boolean addFilters() default true;
                            boolean webClientEnabled() default true;
                            boolean webDriverEnabled() default true;
                        }
                        """);
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.RelocatedBootType());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

                @<caret>AutoConfigureMockMvc(addFilters = false, webClientEnabled = false, webDriverEnabled = false)
                class Sample { }
                """);
        this.myFixture.doHighlighting();

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with relocated annotation"));

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("HtmlUnit flags moved under htmlUnit attribute")
                .contains("addFilters = false")
                .contains("htmlUnit = @org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc"
                        + ".HtmlUnit(webClient = false, webDriver = false)")
                .doesNotContain("webClientEnabled");
    }

    public void testRelocatedBootType_bootstrapRegistryReported() {
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.RelocatedBootType());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.BootstrapRegistry;

                class Sample {
                    BootstrapRegistry registry;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "relocated by the Boot 4 modularisation"))
                .as("BootstrapRegistry moved to org.springframework.boot.bootstrap")
                .isEqualTo(1);
    }

    public void testRelocatedBootType_environmentPostProcessorIsWarning() {
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.RelocatedBootType());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.env.EnvironmentPostProcessor;

                class Sample { }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(highlights)
                .as("deprecated EnvironmentPostProcessor reported as warning")
                .anyMatch((HighlightInfo h) -> h.getDescription() != null
                        && h.getDescription().contains("deprecated in Boot 4")
                        && h.getSeverity() == com.intellij.lang.annotation.HighlightSeverity.WARNING);
    }

    public void testRelocatedBootType_skipPropertyMappingReportedWithoutFix() {
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.RelocatedBootType());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.test.autoconfigure.properties.<caret>SkipPropertyMapping;

                class Sample { }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "now org.springframework.boot.test.context.PropertyMapping.Skip"))
                .as("renamed nested enum reported with target")
                .isEqualTo(1);
        Assertions.assertThat(this.myFixture.filterAvailableIntentions("Replace with relocated type"))
                .as("simple name changes, so no import swap fix")
                .isEmpty();
    }

    public void testMockBean_classLevelBecomesMockitoBeanTypes() {
        this.myFixture.addFileToProject("org/springframework/boot/test/mock/mockito/MockBean.java",
                "package org.springframework.boot.test.mock.mockito;\npublic @interface MockBean {\n"
                        + "    Class<?>[] value() default {};\n}\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.MockBean());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                }

                @<caret>org.springframework.boot.test.mock.mockito.MockBean({String.class, Integer.class})
                class Other { }
                """);
        this.myFixture.doHighlighting();

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with @MockitoBean"));

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("class-level classes mapped to types attribute")
                .contains("MockitoBean(types = {String.class, Integer.class})");
    }

    public void testMockBean_fieldFixKeepsResetAndAnswer() {
        this.myFixture.addFileToProject("org/springframework/boot/test/mock/mockito/MockBean.java",
                "package org.springframework.boot.test.mock.mockito;\npublic @interface MockBean {\n"
                        + "    MockReset reset() default MockReset.AFTER;\n    String answer() default \"\";\n}\n");
        this.myFixture.addFileToProject("org/springframework/boot/test/mock/mockito/MockReset.java",
                "package org.springframework.boot.test.mock.mockito;\npublic enum MockReset { BEFORE, AFTER, NONE }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.MockBean());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.boot.test.mock.mockito.MockBean;
                import org.springframework.boot.test.mock.mockito.MockReset;

                class Sample {
                    @<caret>MockBean(reset = MockReset.BEFORE, answer = "x")
                    String service;
                }
                """);
        this.myFixture.doHighlighting();

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with @MockitoBean"));

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("reset and answer carried over")
                .contains("reset = org.springframework.test.context.bean.override.mockito.MockReset.BEFORE")
                .contains("answers = \"x\"");
    }

    public void testJacksonPackage_wildcardImportReported() {
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.JacksonPackage());
        this.myFixture.configureByText("Sample.java", """
                import com.fasterxml.jackson.databind.*;

                class Sample { }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "tools.jackson"))
                .as("on-demand databind import")
                .isEqualTo(1);
    }

    public void testJacksonPackage_datatypeModuleReportedWithoutFix() {
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.JacksonPackage());
        this.myFixture.configureByText("Sample.java", """
                import com.fasterxml.jackson.datatype.jsr310.<caret>JavaTimeModule;

                class Sample { }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "Jackson 2 modules moved"))
                .as("jsr310 module import")
                .isEqualTo(1);
        Assertions.assertThat(this.myFixture.filterAvailableIntentions("Replace with tools.jackson import"))
                .as("module packages have no mechanical replacement")
                .isEmpty();
    }

    public void testJacksonRenamedType_genericQualifiedReferenceReported() {
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.JacksonRenamedType());
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    com.fasterxml.jackson.databind.JsonSerializer<String> serializer;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "renamed in Jackson 3"))
                .as("qualified generic reference matched without type arguments")
                .isEqualTo(1);
    }

    public void testSpringBootTestAutoConfigure_reportsMockMvcAndAddsAnnotation() {
        addSpringBootTestStubs();
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.SpringBootTestAutoConfigure());
        this.myFixture.configureByText("SampleTests.java", """
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.test.web.servlet.MockMvc;

                @SpringBootTest
                class SampleTests {
                    @Autowired
                    MockMvc <caret>mockMvc;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        Assertions.assertThat(countDescriptions(highlights, "no longer configures MockMvc"))
                .as("MockMvc injected without @AutoConfigureMockMvc")
                .isEqualTo(1);

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add @AutoConfigureMockMvc"));

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("annotation added and imported")
                .contains("import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;")
                .contains("@AutoConfigureMockMvc");
    }

    public void testSpringBootTestAutoConfigure_reportsTestRestTemplate() {
        addSpringBootTestStubs();
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.SpringBootTestAutoConfigure());
        this.myFixture.configureByText("SampleTests.java", """
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.resttestclient.TestRestTemplate;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                class SampleTests {
                    @Autowired
                    TestRestTemplate restTemplate;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "add @AutoConfigureTestRestTemplate"))
                .as("TestRestTemplate injected without @AutoConfigureTestRestTemplate")
                .isEqualTo(1);
    }

    public void testSpringBootTestAutoConfigure_annotatedOrNonSpringBootTestIsIgnored() {
        addSpringBootTestStubs();
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.SpringBootTestAutoConfigure());
        this.myFixture.configureByText("SampleTests.java", """
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
                import org.springframework.test.web.servlet.MockMvc;

                @SpringBootTest
                @AutoConfigureMockMvc
                class SampleTests {
                    @Autowired
                    MockMvc mockMvc;
                }

                class NotSpringBootTests {
                    @Autowired
                    MockMvc mockMvc;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "no longer configures"))
                .as("already annotated and non-@SpringBootTest classes")
                .isZero();
    }

    public void testMockitoAnnotations_reportsMockInSpringTestAndAddsExtension() {
        addMockitoStubs();
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.MockitoAnnotations());
        this.myFixture.configureByText("SampleTests.java", """
                import org.mockito.Mock;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                class SampleTests {
                    @Mock
                    Runnable <caret>task;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        Assertions.assertThat(countDescriptions(highlights, "MockitoTestExecutionListener was removed"))
                .as("@Mock without MockitoExtension")
                .isEqualTo(1);

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add @ExtendWith(MockitoExtension.class)"));

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("MockitoExtension registered")
                .contains("@ExtendWith(MockitoExtension.class)")
                .contains("import org.mockito.junit.jupiter.MockitoExtension;");
    }

    public void testMockitoAnnotations_extensionOrOpenMocksIsIgnored() {
        addMockitoStubs();
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.MockitoAnnotations());
        this.myFixture.configureByText("SampleTests.java", """
                import org.junit.jupiter.api.extension.ExtendWith;
                import org.mockito.Mock;
                import org.mockito.MockitoAnnotations;
                import org.mockito.junit.jupiter.MockitoExtension;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest
                @ExtendWith(MockitoExtension.class)
                class SampleTests {
                    @Mock
                    Runnable task;
                }

                @SpringBootTest
                class OpenMocksTests {
                    @Mock
                    Runnable task;

                    void setUp() {
                        MockitoAnnotations.openMocks(this);
                    }
                }

                class PlainTests {
                    @Mock
                    Runnable task;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "MockitoTestExecutionListener was removed"))
                .as("Mockito initialised or not a Spring test")
                .isZero();
    }

    public void testObjectMapperBean_reportsObjectMapperButNotJsonMapper() {
        addBeanStub();
        this.myFixture.addFileToProject("com/fasterxml/jackson/databind/ObjectMapper.java",
                "package com.fasterxml.jackson.databind;\npublic class ObjectMapper { }\n");
        this.myFixture.addFileToProject("tools/jackson/databind/json/JsonMapper.java",
                "package tools.jackson.databind.json;\npublic class JsonMapper { }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.ObjectMapperBean());
        this.myFixture.configureByText("JacksonConfig.java", """
                import com.fasterxml.jackson.databind.ObjectMapper;
                import org.springframework.context.annotation.Bean;
                import tools.jackson.databind.json.JsonMapper;

                class JacksonConfig {
                    @Bean
                    ObjectMapper objectMapper() {
                        return new ObjectMapper();
                    }

                    @Bean
                    JsonMapper jsonMapper() {
                        return new JsonMapper();
                    }

                    ObjectMapper notABean() {
                        return new ObjectMapper();
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "ObjectMapper bean no longer replaces"))
                .as("only the ObjectMapper @Bean")
                .isEqualTo(1);
    }

    public void testHttpMessageConverterBean_reportsConverterSubtype() {
        addBeanStub();
        this.myFixture.addFileToProject("org/springframework/http/converter/HttpMessageConverter.java",
                "package org.springframework.http.converter;\npublic interface HttpMessageConverter<T> { }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.HttpMessageConverterBean());
        this.myFixture.configureByText("WebConfig.java", """
                import org.springframework.context.annotation.Bean;
                import org.springframework.http.converter.HttpMessageConverter;

                class CsvConverter implements HttpMessageConverter<String> { }

                class WebConfig {
                    @Bean
                    CsvConverter csvConverter() {
                        return new CsvConverter();
                    }

                    @Bean
                    String notAConverter() {
                        return "";
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "HttpMessageConverter beans are no longer picked up"))
                .as("converter subtype bean reported once")
                .isEqualTo(1);
    }

    public void testActuatorSpringLangNullable_reportsAndReplacesWithJSpecify() {
        addSpringLangNullableStub();
        this.myFixture.addFileToProject("org/springframework/boot/actuate/endpoint/annotation/ReadOperation.java",
                "package org.springframework.boot.actuate.endpoint.annotation;\npublic @interface ReadOperation { }\n");
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.ActuatorSpringLangNullable(),
                new SpringBoot4MigrationInspection.SpringLangNullability());
        this.myFixture.configureByText("StatusEndpoint.java", """
                import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
                import org.springframework.lang.Nullable;

                class StatusEndpoint {
                    @ReadOperation
                    String status(@<caret>Nullable String name) {
                        return name;
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();
        Assertions.assertThat(countDescriptions(highlights, "no longer makes an actuator operation parameter optional"))
                .as("actuator parameter reported by dedicated inspection")
                .isEqualTo(1);
        Assertions.assertThat(countDescriptions(highlights, "deprecated in Spring Framework 7 in favour of JSpecify"))
                .as("generic nullability warning only on the import, not on the parameter annotation")
                .isEqualTo(1);

        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with JSpecify annotation"));

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("parameter now uses JSpecify")
                .contains("@org.jspecify.annotations.Nullable String name");
    }

    public void testActuatorSpringLangNullable_nonOperationIsIgnored() {
        addSpringLangNullableStub();
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.ActuatorSpringLangNullable());
        this.myFixture.configureByText("Service.java", """
                import org.springframework.lang.Nullable;

                class Service {
                    String status(@Nullable String name) {
                        return name;
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "actuator operation parameter"))
                .as("plain method parameter")
                .isZero();
    }

    public void testPropertyMapperAlwaysApplying_reportsRemovedCall() {
        this.myFixture.addFileToProject("org/springframework/boot/context/properties/PropertyMapper.java", """
                package org.springframework.boot.context.properties;
                public final class PropertyMapper {
                    public static PropertyMapper get() { return new PropertyMapper(); }
                    public PropertyMapper alwaysApplyingWhenNonNull() { return this; }
                }
                """);
        this.myFixture.addFileToProject("org/example/OtherMapper.java", """
                package org.example;
                public class OtherMapper {
                    public OtherMapper alwaysApplyingWhenNonNull() { return this; }
                }
                """);
        this.myFixture.enableInspections(new SpringBoot4MigrationInspection.PropertyMapperAlwaysApplying());
        this.myFixture.configureByText("Sample.java", """
                import org.example.OtherMapper;
                import org.springframework.boot.context.properties.PropertyMapper;

                class Sample {
                    void map(OtherMapper other) {
                        PropertyMapper.get().alwaysApplyingWhenNonNull();
                        other.alwaysApplyingWhenNonNull();
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countDescriptions(highlights, "alwaysApplyingWhenNonNull() was removed"))
                .as("only the Boot PropertyMapper call")
                .isEqualTo(1);
    }

    private void addSpringBootTestStubs() {
        this.myFixture.addFileToProject("org/springframework/boot/test/context/SpringBootTest.java",
                "package org.springframework.boot.test.context;\npublic @interface SpringBootTest { }\n");
        this.myFixture.addFileToProject("org/springframework/beans/factory/annotation/Autowired.java",
                "package org.springframework.beans.factory.annotation;\npublic @interface Autowired { }\n");
        this.myFixture.addFileToProject("org/springframework/test/web/servlet/MockMvc.java",
                "package org.springframework.test.web.servlet;\npublic final class MockMvc { }\n");
        this.myFixture.addFileToProject("org/springframework/boot/resttestclient/TestRestTemplate.java",
                "package org.springframework.boot.resttestclient;\npublic class TestRestTemplate { }\n");
        this.myFixture.addFileToProject(
                "org/springframework/boot/webmvc/test/autoconfigure/AutoConfigureMockMvc.java",
                "package org.springframework.boot.webmvc.test.autoconfigure;\npublic @interface AutoConfigureMockMvc { }\n");
        this.myFixture.addFileToProject(
                "org/springframework/boot/resttestclient/autoconfigure/AutoConfigureTestRestTemplate.java",
                "package org.springframework.boot.resttestclient.autoconfigure;\n"
                        + "public @interface AutoConfigureTestRestTemplate { }\n");
    }

    private void addMockitoStubs() {
        this.myFixture.addFileToProject("org/springframework/boot/test/context/SpringBootTest.java",
                "package org.springframework.boot.test.context;\npublic @interface SpringBootTest { }\n");
        this.myFixture.addFileToProject("org/mockito/Mock.java", "package org.mockito;\npublic @interface Mock { }\n");
        this.myFixture.addFileToProject("org/mockito/MockitoAnnotations.java",
                "package org.mockito;\npublic final class MockitoAnnotations {\n"
                        + "    public static AutoCloseable openMocks(Object test) { return null; }\n}\n");
        this.myFixture.addFileToProject("org/junit/jupiter/api/extension/ExtendWith.java",
                "package org.junit.jupiter.api.extension;\npublic @interface ExtendWith {\n"
                        + "    Class<?>[] value();\n}\n");
        this.myFixture.addFileToProject("org/mockito/junit/jupiter/MockitoExtension.java",
                "package org.mockito.junit.jupiter;\npublic class MockitoExtension { }\n");
    }

    private void addBeanStub() {
        this.myFixture.addFileToProject("org/springframework/context/annotation/Bean.java",
                "package org.springframework.context.annotation;\npublic @interface Bean { }\n");
    }

    private void addSpringLangNullableStub() {
        this.myFixture.addFileToProject("org/springframework/lang/Nullable.java",
                "package org.springframework.lang;\npublic @interface Nullable { }\n");
    }

    private static long countDescriptions(List<HighlightInfo> highlights, String description) {
        return highlights.stream()
                .filter((HighlightInfo h) -> h.getDescription() != null)
                .filter((HighlightInfo h) -> h.getDescription().contains(description))
                .map((HighlightInfo h) -> h.startOffset + ":" + h.getDescription())
                .distinct()
                .count();
    }

}
