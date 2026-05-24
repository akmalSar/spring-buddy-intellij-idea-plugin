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

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.assertj.core.api.Assertions;

/**
 * @author Akmal Sarhan
 */
public class SpringBoot3FileMigrationInspectionTests extends BasePlatformTestCase {

    public void testBuildFileInspections_reportRemovedCoordinates() {
        this.myFixture.enableInspections(
                new SpringBoot3FileMigrationInspection.JavaxServletDependency(),
                new SpringBoot3FileMigrationInspection.IncompatibleJavaVersion(),
                new SpringBoot3FileMigrationInspection.LegacyJavaEeDependencies(),
                new SpringBoot3FileMigrationInspection.MySqlDriverCoordinates(),
                new SpringBoot3FileMigrationInspection.HibernateGroupId(),
                new SpringBoot3FileMigrationInspection.ApacheHttpClient(),
                new SpringBoot3FileMigrationInspection.MavenForkAttribute());
        this.myFixture.configureByText("pom.xml", """
                <project>
                    <properties><java.version>11</java.version></properties>
                    <dependencies>
                        <dependency><groupId>javax.servlet</groupId><artifactId>javax.servlet-api</artifactId></dependency>
                        <dependency><groupId>javax.persistence</groupId><artifactId>javax.persistence-api</artifactId></dependency>
                        <dependency><groupId>mysql</groupId><artifactId>mysql-connector-java</artifactId></dependency>
                        <dependency><groupId>org.hibernate</groupId><artifactId>hibernate-core</artifactId></dependency>
                        <dependency><groupId>org.apache.httpcomponents</groupId><artifactId>httpclient</artifactId></dependency>
                    </dependencies>
                    <build><plugins><plugin><artifactId>spring-boot-maven-plugin</artifactId><configuration><fork>true</fork></configuration></plugin></plugins></build>
                </project>
                """);

        long count = countFileProblems(
                new SpringBoot3FileMigrationInspection.JavaxServletDependency(),
                new SpringBoot3FileMigrationInspection.IncompatibleJavaVersion(),
                new SpringBoot3FileMigrationInspection.LegacyJavaEeDependencies(),
                new SpringBoot3FileMigrationInspection.MySqlDriverCoordinates(),
                new SpringBoot3FileMigrationInspection.HibernateGroupId(),
                new SpringBoot3FileMigrationInspection.ApacheHttpClient(),
                new SpringBoot3FileMigrationInspection.MavenForkAttribute());

        Assertions.assertThat(count)
                .as("build migration problems")
                .isEqualTo(7);
    }

    public void testPropertyInspections_reportRenamedProperties() {
        this.myFixture.enableInspections(
                new SpringBoot3FileMigrationInspection.RedisProperties(),
                new SpringBoot3FileMigrationInspection.CassandraProperties(),
                new SpringBoot3FileMigrationInspection.MaxHttpHeaderSize(),
                new SpringBoot3FileMigrationInspection.MetricsExportProperty(),
                new SpringBoot3FileMigrationInspection.RenamedProperties());
        this.myFixture.configureByText("application.properties", """
                spring.redis.host=localhost
                spring.data.cassandra.keyspace-name=app
                server.max-http-header-size=16KB
                management.metrics.export.prometheus.enabled=true
                """);

        long count = countFileProblems(
                new SpringBoot3FileMigrationInspection.RedisProperties(),
                new SpringBoot3FileMigrationInspection.CassandraProperties(),
                new SpringBoot3FileMigrationInspection.MaxHttpHeaderSize(),
                new SpringBoot3FileMigrationInspection.MetricsExportProperty(),
                new SpringBoot3FileMigrationInspection.RenamedProperties());

        Assertions.assertThat(count)
                .as("property migration problems")
                .isEqualTo(5);
    }

    public void testFileInspections_ignoreLogFiles() {
        this.myFixture.configureByText("application.log", """
                spring.redis.host=localhost
                javax.servlet:javax.servlet-api
                management.metrics.export.prometheus.enabled=true
                """);

        long count = countFileProblems(
                new SpringBoot3FileMigrationInspection.RedisProperties(),
                new SpringBoot3FileMigrationInspection.JavaxServletDependency(),
                new SpringBoot3FileMigrationInspection.MetricsExportProperty(),
                new SpringBoot3FileMigrationInspection.RenamedProperties());

        Assertions.assertThat(count)
                .as("log files")
                .isEqualTo(0);
    }

    public void testPropertyInspections_reportProfileConfigFiles() {
        this.myFixture.configureByText("application-prod.properties", """
                spring.redis.host=localhost
                """);

        long count = countFileProblems(
                new SpringBoot3FileMigrationInspection.RedisProperties(),
                new SpringBoot3FileMigrationInspection.RenamedProperties());

        Assertions.assertThat(count)
                .as("profile application config")
                .isEqualTo(2);
    }

    public void testSpringFactoriesAutoConfiguration_reportsLegacyKey() {
        this.myFixture.enableInspections(new SpringBoot3FileMigrationInspection.SpringFactoriesAutoConfig());
        this.myFixture.configureByText("spring.factories", """
                org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
                com.example.MyAutoConfiguration
                """);

        long count = countFileProblems(new SpringBoot3FileMigrationInspection.SpringFactoriesAutoConfig());

        Assertions.assertThat(count)
                .as("spring.factories auto configuration")
                .isEqualTo(1);
    }

    public void testElasticsearchAutoConfigExclusion_reportsLegacyClass() {
        this.myFixture.configureByText("Sample.java", """
                class Sample {
                    String exclusion = "ReactiveElasticsearchRestClientAutoConfiguration";
                }
                """);

        long count = countFileProblems(new SpringBoot3FileMigrationInspection.ElasticsearchAutoConfigExclusion());

        Assertions.assertThat(count)
                .as("renamed Elasticsearch auto-configuration exclusion")
                .isEqualTo(1);
    }

    public void testJavaVersionInspection_acceptsJavaSeventeen() {
        this.myFixture.configureByText("build.gradle", """
                java {
                    sourceCompatibility = '17'
                    targetCompatibility = '21'
                }
                """);

        long count = countFileProblems(new SpringBoot3FileMigrationInspection.IncompatibleJavaVersion());

        Assertions.assertThat(count)
                .as("Java 17 or later")
                .isEqualTo(0);
    }

    public void testLogbackSpringConfig_reportsSpringTags() {
        this.myFixture.configureByText("logback.xml", """
                <configuration>
                    <springProfile name="dev"></springProfile>
                </configuration>
                """);

        long count = countFileProblems(new SpringBoot3FileMigrationInspection.LogbackSpringConfig());

        Assertions.assertThat(count)
                .as("logback Spring extensions")
                .isEqualTo(1);
    }

    public void testAdditionalDependencyMigrations_reportLegacyArtifacts() {
        this.myFixture.configureByText("pom.xml", """
                <project>
                    <dependencies>
                        <dependency><artifactId>thymeleaf-spring5</artifactId></dependency>
                        <dependency><artifactId>metrics-servlet</artifactId></dependency>
                        <dependency><artifactId>cf-java-logging-support-servlet</artifactId></dependency>
                        <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-ui</artifactId></dependency>
                    </dependencies>
                </project>
                """);

        long count = countFileProblems(
                new SpringBoot3FileMigrationInspection.ThymeleafSpring6(),
                new SpringBoot3FileMigrationInspection.DropwizardJakartaMetrics(),
                new SpringBoot3FileMigrationInspection.SapCfJakartaLogging(),
                new SpringBoot3FileMigrationInspection.SpringdocV2());

        Assertions.assertThat(count)
                .as("additional dependency migrations")
                .isEqualTo(4);
    }

    public void testAdditionalPropertyMigrations_reportLegacyKeys() {
        this.myFixture.configureByText("application.properties", """
                spring.liquibase.labels=prod
                server.undertow.allow-encoded-slash=true
                management.trace.http.enabled=true
                management.metrics.web.server.request.metric-name=http.server.requests
                """);

        long count = countFileProblems(
                new SpringBoot3FileMigrationInspection.LiquibaseLabelFilter(),
                new SpringBoot3FileMigrationInspection.UndertowDecodeSlash(),
                new SpringBoot3FileMigrationInspection.HttpTraceRecordingProperties(),
                new SpringBoot3FileMigrationInspection.ObservationMetricNames());

        Assertions.assertThat(count)
                .as("additional property migrations")
                .isEqualTo(4);
    }

    public void testJavaVersionInspection_quickFixSetsVersionToSeventeen() {
        this.myFixture.configureByText("pom.xml", """
                <project>
                    <properties><java.version>1.8</java.version></properties>
                </project>
                """);

        applySingleFix(new SpringBoot3FileMigrationInspection.IncompatibleJavaVersion());

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("Java version quick-fix")
                .contains("<java.version>17</java.version>");
    }

    public void testLegacyJavaEeInspection_quickFixUpdatesCoordinates() {
        this.myFixture.configureByText("pom.xml", """
                <project>
                    <dependency><groupId>javax.validation</groupId><artifactId>validation-api</artifactId></dependency>
                </project>
                """);

        applySingleFix(new SpringBoot3FileMigrationInspection.LegacyJavaEeDependencies());

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("Java EE coordinates quick-fix")
                .contains("<groupId>jakarta.validation</groupId>")
                .contains("<artifactId>jakarta.validation-api</artifactId>");
    }

    public void testDependencyInspection_quickFixUpdatesCoordinates() {
        this.myFixture.configureByText("pom.xml", """
                <project>
                    <dependency><groupId>mysql</groupId><artifactId>mysql-connector-java</artifactId></dependency>
                </project>
                """);

        applySingleFix(new SpringBoot3FileMigrationInspection.MySqlDriverCoordinates());

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("MySQL coordinates quick-fix")
                .contains("<groupId>com.mysql</groupId>")
                .contains("<artifactId>mysql-connector-j</artifactId>");
    }

    public void testPropertyInspection_quickFixRenamesProperty() {
        this.myFixture.configureByText("application.properties", """
                server.max-http-header-size=16KB
                """);

        applySingleFix(new SpringBoot3FileMigrationInspection.MaxHttpHeaderSize());

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("renamed property quick-fix")
                .contains("server.max-http-request-header-size=16KB");
    }

    public void testRemovedPropertyInspection_quickFixDeletesPropertyLine() {
        this.myFixture.configureByText("application.properties", """
                spring.jpa.hibernate.use-new-id-generator-mappings=false
                spring.application.name=sample
                """);

        applySingleFix(new SpringBoot3FileMigrationInspection.HibernateIdGeneratorProperty());

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("removed property quick-fix")
                .doesNotContain("spring.jpa.hibernate.use-new-id-generator-mappings")
                .contains("spring.application.name=sample");
    }

    public void testSpringdocV2_quickFixUsesBootThreeStarter() {
        this.myFixture.configureByText("pom.xml", """
                <project>
                    <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-ui</artifactId></dependency>
                </project>
                """);

        applySingleFix(new SpringBoot3FileMigrationInspection.SpringdocV2());

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("Springdoc starter quick-fix")
                .contains("<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>");
    }

    public void testHttpTraceRecordingProperties_quickFixRenamesKey() {
        this.myFixture.configureByText("application.properties", """
                management.trace.http.enabled=true
                """);

        applySingleFix(new SpringBoot3FileMigrationInspection.HttpTraceRecordingProperties());

        Assertions.assertThat(this.myFixture.getFile().getText())
                .as("HTTP trace recording quick-fix")
                .contains("management.httpexchanges.recording.enabled=true");
    }

    private long countFileProblems(SpringBoot3FileMigrationInspection... inspections) {
        InspectionManager manager = InspectionManager.getInstance(this.getProject());
        long count = 0;
        for (SpringBoot3FileMigrationInspection inspection : inspections) {
            ProblemDescriptor[] descriptors = inspection.checkFile(this.myFixture.getFile(), manager, true);
            count += descriptors.length;
        }
        return count;
    }

    private void applySingleFix(SpringBoot3FileMigrationInspection inspection) {
        InspectionManager manager = InspectionManager.getInstance(this.getProject());
        ProblemDescriptor[] descriptors = inspection.checkFile(this.myFixture.getFile(), manager, true);
        Assertions.assertThat(descriptors)
                .as("problem descriptors")
                .hasSize(1);
        Assertions.assertThat(descriptors[0].getFixes())
                .as("quick fixes")
                .hasSize(1);

        descriptors[0].getFixes()[0].applyFix(this.getProject(), descriptors[0]);
    }
}
