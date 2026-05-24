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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Spring Boot 3 migration inspections for configuration, build, and resource files.
 *
 * @author Akmal Sarhan
 */
public class SpringBoot3FileMigrationInspection extends LocalInspectionTool {

    private static final List<String> METRICS_PRODUCTS = List.of("prometheus", "datadog", "influx", "cloudwatch",
            "dynatrace", "elastic", "ganglia", "graphite", "humio", "jmx", "kairos", "newrelic", "otlp",
            "signalfx", "stackdriver", "statsd", "wavefront");

    private static final List<String> LEGACY_JAVA_EE_DEPENDENCIES = List.of("javax.annotation:javax.annotation-api",
            "javax.ejb:javax.ejb-api", "javax.el:javax.el-api", "javax.enterprise:cdi-api",
            "javax.faces:javax.faces-api", "javax.inject:javax.inject", "javax.jms:javax.jms-api",
            "javax.json:javax.json-api", "javax.mail:javax.mail-api", "javax.persistence:javax.persistence-api",
            "javax.servlet:javax.servlet-api", "javax.transaction:javax.transaction-api",
            "javax.validation:validation-api", "javax.websocket:javax.websocket-api",
            "javax.ws.rs:javax.ws.rs-api", "javax.xml.bind:jaxb-api");

    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile(
            "((?:<java.version>|<maven.compiler.source>|<maven.compiler.target>|sourceCompatibility\\s*=\\s*['\"]?|targetCompatibility\\s*=\\s*['\"]?))(?:1\\.)?(\\d+)");

    private final Kind kind;

    protected SpringBoot3FileMigrationInspection(@NotNull Kind kind) {
        this.kind = kind;
    }

    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        PsiFile file = holder.getFile();
        if (matches(file)) {
            LocalQuickFix fix = this.kind.fix(file);
            if (fix == null) {
                holder.registerProblem(file, this.kind.message);
            }
            else {
                holder.registerProblem(file, this.kind.message, fix);
            }
        }
        return PsiElementVisitor.EMPTY_VISITOR;
    }

    @Override
    public ProblemDescriptor @NotNull [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager,
            boolean isOnTheFly) {
        if (!matches(file)) {
            return ProblemDescriptor.EMPTY_ARRAY;
        }
        LocalQuickFix fix = this.kind.fix(file);
        return new ProblemDescriptor[] {
                manager.createProblemDescriptor(file, TextRange.from(0, Math.min(1, file.getTextLength())),
                        this.kind.message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly,
                        fix == null ? LocalQuickFix.EMPTY_ARRAY : new LocalQuickFix[] { fix })
        };
    }

    private boolean matches(PsiFile file) {
        String name = file.getName();
        VirtualFile virtualFile = file.getVirtualFile();
        String path = virtualFile == null ? name : virtualFile.getPath();
        if (!isRelevantFile(this.kind, name, path)) {
            return false;
        }
        String text = file.getText();
        return switch (this.kind) {
            case JAVAX_SERVLET_DEPENDENCY -> hasAll(text, "javax.servlet", "javax.servlet-api")
                    || text.contains("javax.servlet:javax.servlet-api");
            case INCOMPATIBLE_JAVA_VERSION -> hasLegacyJavaVersion(text);
            case LEGACY_JAVA_EE_DEPENDENCIES -> hasLegacyJavaEeDependency(text);
            case MYSQL_DRIVER_COORDINATES -> hasAll(text, "<groupId>mysql</groupId>", "<artifactId>mysql-connector-java</artifactId>")
                    || text.contains("mysql:mysql-connector-java");
            case SPRING_FACTORIES_AUTO_CONFIG -> name.equals("spring.factories")
                    && text.contains("org.springframework.boot.autoconfigure.EnableAutoConfiguration");
            case IMAGE_BANNER -> path.contains("/src/main/resources/")
                    && (name.equals("banner.gif") || name.equals("banner.jpg") || name.equals("banner.png"));
            case MAX_HTTP_HEADER_SIZE -> text.contains("server.max-http-header-size");
            case HTTPTRACE_PROPERTY -> text.contains("httptrace");
            case METRICS_EXPORT_PROPERTY -> hasOldMetricsExportProperty(text);
            case CASSANDRA_PROPERTIES -> text.contains("spring.data.cassandra.");
            case REDIS_PROPERTIES -> text.contains("spring.redis.");
            case HIBERNATE_ID_GENERATOR_PROPERTY -> text.contains("spring.jpa.hibernate.use-new-id-generator-mappings");
            case HIBERNATE_GROUP_ID -> hasAll(text, "<groupId>org.hibernate</groupId>", "<artifactId>hibernate-core</artifactId>")
                    || text.contains("org.hibernate:hibernate-core");
            case APACHE_HTTP_CLIENT -> text.contains("org.apache.httpcomponents:httpclient")
                    || hasAll(text, "<groupId>org.apache.httpcomponents</groupId>", "<artifactId>httpclient</artifactId>");
            case ELASTICSEARCH_REST_CLIENT -> text.contains("org.elasticsearch.client.RestHighLevelClient")
                    || text.contains("org.elasticsearch.client.RequestOptions")
                    || text.contains("org.springframework.data.elasticsearch.client.erhlc")
                    || text.contains("ReactiveElasticsearchRestClientAutoConfiguration")
                    || text.contains("elasticsearch-rest-high-level-client");
            case ELASTICSEARCH_AUTO_CONFIG_EXCLUSION -> text.contains("ReactiveElasticsearchRestClientAutoConfiguration");
            case FLAPDOODLE_MONGO -> text.contains("de.flapdoodle.embed:de.flapdoodle.embed.mongo")
                    || hasAll(text, "<groupId>de.flapdoodle.embed</groupId>", "<artifactId>de.flapdoodle.embed.mongo</artifactId>");
            case SAML2_IDENTITY_PROVIDER_PROPERTY -> text.contains(".identity-provider.");
            case SECURITY_DISPATCH_TYPES -> isApplicationConfig(name) && !text.contains("spring.security.filter.dispatcher-types");
            case BATCH_JOB_NAMES_PROPERTY -> text.contains("spring.batch.job.names");
            case SESSION_STORE_TYPE -> text.contains("spring.session.store-type");
            case JMX_ENDPOINT_EXPOSURE -> text.contains("management.endpoints.jmx.exposure.include=*");
            case ACTUATOR_SANITIZATION -> text.contains("management.endpoint.env.keys-to-sanitize");
            case PROPERTIES_MIGRATOR_LEFT_IN_DEPS -> text.contains("spring-boot-properties-migrator");
            case GIT_COMMIT_ID_PLUGIN -> hasAll(text, "pl.project13.maven", "git-commit-id-plugin");
            case MAVEN_FORK_ATTRIBUTE -> hasAll(text, "spring-boot-maven-plugin", "<fork>");
            case EHCACHE3_CLASSIFIER -> (text.contains("org.ehcache:ehcache") || hasAll(text,
                    "<groupId>org.ehcache</groupId>", "<artifactId>ehcache</artifactId>")) && !text.contains("jakarta");
            case R2DBC_BOM_VERSION -> text.contains("r2dbc-bom.version");
            case ACTIVEMQ_REMOVED -> text.contains("spring-boot-starter-activemq")
                    || text.contains("org.apache.activemq:activemq-broker")
                    || hasAll(text, "<groupId>org.apache.activemq</groupId>", "<artifactId>activemq-broker</artifactId>");
            case ATOMIKOS_REMOVED -> text.contains("transactions-spring-boot3-starter")
                    || text.contains("spring-boot-starter-jta-atomikos");
            case EHCACHE2_REMOVED -> text.contains("net.sf.ehcache:ehcache")
                    || hasAll(text, "<groupId>net.sf.ehcache</groupId>", "<artifactId>ehcache</artifactId>");
            case SOLR_REMOVED -> text.contains("spring-boot-starter-data-solr")
                    || text.contains("spring-data-solr");
            case RENAMED_PROPERTIES -> hasRenamedProperty(text);
        };
    }

    private static boolean hasAll(String text, String first, String second) {
        return text.contains(first) && text.contains(second);
    }

    private static boolean isRelevantFile(Kind kind, String name, String path) {
        return switch (kind) {
            case JAVAX_SERVLET_DEPENDENCY, MYSQL_DRIVER_COORDINATES, HIBERNATE_GROUP_ID, FLAPDOODLE_MONGO,
                    PROPERTIES_MIGRATOR_LEFT_IN_DEPS, GIT_COMMIT_ID_PLUGIN, MAVEN_FORK_ATTRIBUTE,
                    EHCACHE3_CLASSIFIER, R2DBC_BOM_VERSION, ACTIVEMQ_REMOVED, ATOMIKOS_REMOVED, EHCACHE2_REMOVED,
                    SOLR_REMOVED, INCOMPATIBLE_JAVA_VERSION, LEGACY_JAVA_EE_DEPENDENCIES, APACHE_HTTP_CLIENT -> isBuildFile(name);
            case SPRING_FACTORIES_AUTO_CONFIG -> name.equals("spring.factories");
            case IMAGE_BANNER -> path.contains("/src/main/resources/")
                    && (name.equals("banner.gif") || name.equals("banner.jpg") || name.equals("banner.png"));
            case MAX_HTTP_HEADER_SIZE, HTTPTRACE_PROPERTY, METRICS_EXPORT_PROPERTY, CASSANDRA_PROPERTIES,
                    REDIS_PROPERTIES, HIBERNATE_ID_GENERATOR_PROPERTY, SAML2_IDENTITY_PROVIDER_PROPERTY,
                    SECURITY_DISPATCH_TYPES, BATCH_JOB_NAMES_PROPERTY, SESSION_STORE_TYPE, JMX_ENDPOINT_EXPOSURE,
                    ACTUATOR_SANITIZATION, RENAMED_PROPERTIES -> isApplicationConfig(name);
            case ELASTICSEARCH_REST_CLIENT, ELASTICSEARCH_AUTO_CONFIG_EXCLUSION -> isBuildFile(name) || isJavaFile(name)
                    || isApplicationConfig(name);
        };
    }

    private static boolean isBuildFile(String name) {
        return name.equals("pom.xml") || name.equals("build.gradle") || name.equals("build.gradle.kts")
                || name.equals("settings.gradle") || name.equals("settings.gradle.kts") || name.equals("gradle.properties");
    }

    private static boolean isJavaFile(String name) {
        return name.endsWith(".java");
    }

    private static boolean isApplicationConfig(String name) {
        return isSpringConfig(name, "application") || isSpringConfig(name, "bootstrap");
    }

    private static boolean isSpringConfig(String name, String prefix) {
        return name.equals(prefix + ".properties") || name.equals(prefix + ".yml") || name.equals(prefix + ".yaml")
                || name.startsWith(prefix + "-") && (name.endsWith(".properties") || name.endsWith(".yml")
                        || name.endsWith(".yaml"));
    }

    private static boolean hasOldMetricsExportProperty(String text) {
        for (String product : METRICS_PRODUCTS) {
            if (text.contains("management.metrics.export." + product + ".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRenamedProperty(String text) {
        return text.contains("spring.redis.") || text.contains("spring.data.cassandra.")
                || text.contains("server.max-http-header-size") || hasOldMetricsExportProperty(text)
                || text.contains("spring.session.store-type") || text.contains("spring.batch.job.names")
                || text.contains("management.endpoint.httptrace.") || text.contains("httptrace")
                || text.contains(".identity-provider.")
                || text.contains("spring.jpa.hibernate.use-new-id-generator-mappings")
                || text.contains("management.endpoint.env.keys-to-sanitize");
    }

    private static boolean hasLegacyJavaVersion(String text) {
        Matcher matcher = JAVA_VERSION_PATTERN.matcher(text);
        while (matcher.find()) {
            if (Integer.parseInt(matcher.group(2)) < 17) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLegacyJavaEeDependency(String text) {
        for (String coordinate : LEGACY_JAVA_EE_DEPENDENCIES) {
            if (text.contains(coordinate) || hasMavenCoordinate(text, coordinate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMavenCoordinate(String text, String coordinate) {
        String[] parts = coordinate.split(":");
        return hasAll(text, "<groupId>" + parts[0] + "</groupId>", "<artifactId>" + parts[1] + "</artifactId>");
    }

    private enum Kind {
        JAVAX_SERVLET_DEPENDENCY("javax.servlet-api dependency should be migrated to jakarta.servlet-api"),
        INCOMPATIBLE_JAVA_VERSION("Spring Boot 3 requires Java 17 or later"),
        LEGACY_JAVA_EE_DEPENDENCIES("Legacy Java EE dependencies should be migrated to Jakarta EE coordinates"),
        MYSQL_DRIVER_COORDINATES("MySQL driver coordinates changed to com.mysql:mysql-connector-j"),
        SPRING_FACTORIES_AUTO_CONFIG("Spring Boot 3 auto-configuration imports moved out of spring.factories"),
        IMAGE_BANNER("Image banner files are no longer supported in Spring Boot 3"),
        MAX_HTTP_HEADER_SIZE("server.max-http-header-size was renamed in Spring Boot 3"),
        HTTPTRACE_PROPERTY("httptrace actuator properties were renamed to httpexchanges"),
        METRICS_EXPORT_PROPERTY("management.metrics.export.* properties moved under management.<product>.metrics.export.*"),
        CASSANDRA_PROPERTIES("spring.data.cassandra.* properties were renamed to spring.cassandra.*"),
        REDIS_PROPERTIES("spring.redis.* properties were renamed to spring.data.redis.*"),
        HIBERNATE_ID_GENERATOR_PROPERTY("spring.jpa.hibernate.use-new-id-generator-mappings was removed in Spring Boot 3"),
        HIBERNATE_GROUP_ID("Hibernate ORM dependencies should use org.hibernate.orm groupId"),
        APACHE_HTTP_CLIENT("Apache HttpClient 4 should be migrated to HttpClient 5 for Spring Boot 3"),
        ELASTICSEARCH_REST_CLIENT("Elasticsearch high-level REST client was removed from Spring Boot 3 support"),
        ELASTICSEARCH_AUTO_CONFIG_EXCLUSION("ReactiveElasticsearchRestClientAutoConfiguration was renamed in Spring Boot 3"),
        FLAPDOODLE_MONGO("Flapdoodle embedded MongoDB is no longer auto-configured by Spring Boot 3"),
        SAML2_IDENTITY_PROVIDER_PROPERTY("SAML2 identity-provider property segment was renamed to asserting-party"),
        SECURITY_DISPATCH_TYPES("Spring Security 6 applies authorization to all dispatcher types by default"),
        BATCH_JOB_NAMES_PROPERTY("spring.batch.job.names was renamed to spring.batch.job.name"),
        SESSION_STORE_TYPE("spring.session.store-type is no longer supported in Spring Boot 3"),
        JMX_ENDPOINT_EXPOSURE("Spring Boot 3 JMX endpoint exposure defaults changed"),
        ACTUATOR_SANITIZATION("Actuator keys-to-sanitize was replaced by show-values controls"),
        PROPERTIES_MIGRATOR_LEFT_IN_DEPS("spring-boot-properties-migrator should be removed after migration"),
        GIT_COMMIT_ID_PLUGIN("git-commit-id-plugin coordinates changed"),
        MAVEN_FORK_ATTRIBUTE("spring-boot-maven-plugin fork configuration was removed"),
        EHCACHE3_CLASSIFIER("Ehcache 3 should use the jakarta classifier for Spring Boot 3"),
        R2DBC_BOM_VERSION("r2dbc-bom.version no longer works in Spring Boot 3"),
        ACTIVEMQ_REMOVED("ActiveMQ Classic support was removed from Spring Boot 3"),
        ATOMIKOS_REMOVED("Atomikos auto-configuration was removed from Spring Boot 3"),
        EHCACHE2_REMOVED("EhCache 2 support was removed from Spring Boot 3"),
        SOLR_REMOVED("Apache Solr support was removed from Spring Boot 3"),
        RENAMED_PROPERTIES("Spring Boot 3 renamed or removed this configuration property");

        private final String message;

        Kind(String message) {
            this.message = message;
        }

        private LocalQuickFix fix(PsiFile file) {
            String name = file.getName();
            String text = file.getText();
            return switch (this) {
                case INCOMPATIBLE_JAVA_VERSION -> new JavaVersionFix();
                case JAVAX_SERVLET_DEPENDENCY -> new ReplaceFileTextFix("Replace javax.servlet-api with jakarta.servlet-api",
                        List.of(replacement("javax.servlet:javax.servlet-api", "jakarta.servlet:jakarta.servlet-api"),
                                replacement("<groupId>javax.servlet</groupId>", "<groupId>jakarta.servlet</groupId>"),
                                replacement("<artifactId>javax.servlet-api</artifactId>", "<artifactId>jakarta.servlet-api</artifactId>")));
                case LEGACY_JAVA_EE_DEPENDENCIES -> legacyJavaEeFix(text);
                case MYSQL_DRIVER_COORDINATES -> new ReplaceFileTextFix("Replace MySQL driver coordinates",
                        List.of(replacement("mysql:mysql-connector-java", "com.mysql:mysql-connector-j"),
                                replacement("<groupId>mysql</groupId>", "<groupId>com.mysql</groupId>"),
                                replacement("<artifactId>mysql-connector-java</artifactId>",
                                        "<artifactId>mysql-connector-j</artifactId>")));
                case MAX_HTTP_HEADER_SIZE -> replace("Rename server.max-http-header-size",
                        "server.max-http-header-size", "server.max-http-request-header-size");
                case HTTPTRACE_PROPERTY -> replace("Rename httptrace to httpexchanges", "httptrace", "httpexchanges");
                case CASSANDRA_PROPERTIES -> replace("Rename spring.data.cassandra prefix", "spring.data.cassandra.",
                        "spring.cassandra.");
                case REDIS_PROPERTIES -> replace("Rename spring.redis prefix", "spring.redis.", "spring.data.redis.");
                case HIBERNATE_ID_GENERATOR_PROPERTY -> new DeleteMatchingLinesFix(
                        "Delete spring.jpa.hibernate.use-new-id-generator-mappings",
                        "spring.jpa.hibernate.use-new-id-generator-mappings");
                case HIBERNATE_GROUP_ID -> new ReplaceFileTextFix("Replace Hibernate groupId",
                        List.of(replacement("org.hibernate:hibernate-core", "org.hibernate.orm:hibernate-core"),
                                replacement("<groupId>org.hibernate</groupId>",
                                        "<groupId>org.hibernate.orm</groupId>")));
                case APACHE_HTTP_CLIENT -> new ReplaceFileTextFix("Replace Apache HttpClient coordinates",
                        List.of(replacement("org.apache.httpcomponents:httpclient",
                                "org.apache.httpcomponents.client5:httpclient5"),
                                replacement("<groupId>org.apache.httpcomponents</groupId>",
                                        "<groupId>org.apache.httpcomponents.client5</groupId>"),
                                replacement("<artifactId>httpclient</artifactId>", "<artifactId>httpclient5</artifactId>")));
                case ELASTICSEARCH_AUTO_CONFIG_EXCLUSION -> replace("Rename Elasticsearch auto-configuration exclusion",
                        "ReactiveElasticsearchRestClientAutoConfiguration",
                        "ReactiveElasticsearchClientAutoConfiguration");
                case BATCH_JOB_NAMES_PROPERTY -> replace("Rename spring.batch.job.names", "spring.batch.job.names",
                        "spring.batch.job.name");
                case SESSION_STORE_TYPE -> new DeleteMatchingLinesFix("Delete spring.session.store-type",
                        "spring.session.store-type");
                case GIT_COMMIT_ID_PLUGIN -> replace("Replace git-commit-id-plugin coordinates",
                        "pl.project13.maven:git-commit-id-plugin",
                        "io.github.git-commit-id:git-commit-id-maven-plugin");
                case MAVEN_FORK_ATTRIBUTE -> new RegexReplaceFix("Remove spring-boot-maven-plugin fork option",
                        Pattern.compile("\\s*<fork>[^<]*</fork>"), "");
                case RENAMED_PROPERTIES -> renamedPropertyFix(text);
                default -> null;
            };
        }

        private static LocalQuickFix legacyJavaEeFix(String text) {
            for (String coordinate : SpringBoot3FileMigrationInspection.LEGACY_JAVA_EE_DEPENDENCIES) {
                String[] parts = coordinate.split(":");
                String jakartaGroup = parts[0].replaceFirst("^javax", "jakarta");
                String jakartaArtifact = jakartaArtifact(parts[1]);
                if (text.contains(coordinate) || hasMavenCoordinate(text, coordinate)) {
                    return new ReplaceFileTextFix("Replace Java EE dependency with Jakarta EE coordinates",
                            List.of(replacement(coordinate, jakartaGroup + ":" + jakartaArtifact),
                                    replacement("<groupId>" + parts[0] + "</groupId>",
                                            "<groupId>" + jakartaGroup + "</groupId>"),
                                    replacement("<artifactId>" + parts[1] + "</artifactId>",
                                            "<artifactId>" + jakartaArtifact + "</artifactId>")));
                }
            }
            return null;
        }

        private static String jakartaArtifact(String artifactId) {
            return switch (artifactId) {
                case "cdi-api" -> "jakarta.enterprise.cdi-api";
                case "javax.inject" -> "jakarta.inject-api";
                case "validation-api" -> "jakarta.validation-api";
                case "jaxb-api" -> "jakarta.xml.bind-api";
                default -> artifactId.replaceFirst("^javax", "jakarta");
            };
        }

        private static LocalQuickFix renamedPropertyFix(String text) {
            if (text.contains("server.max-http-header-size")) {
                return replace("Rename server.max-http-header-size", "server.max-http-header-size",
                        "server.max-http-request-header-size");
            }
            if (text.contains("httptrace")) {
                return replace("Rename httptrace to httpexchanges", "httptrace", "httpexchanges");
            }
            if (text.contains("spring.data.cassandra.")) {
                return replace("Rename spring.data.cassandra prefix", "spring.data.cassandra.", "spring.cassandra.");
            }
            if (text.contains("spring.redis.")) {
                return replace("Rename spring.redis prefix", "spring.redis.", "spring.data.redis.");
            }
            if (text.contains("spring.batch.job.names")) {
                return replace("Rename spring.batch.job.names", "spring.batch.job.names", "spring.batch.job.name");
            }
            if (text.contains("spring.session.store-type")) {
                return new DeleteMatchingLinesFix("Delete spring.session.store-type", "spring.session.store-type");
            }
            if (text.contains("spring.jpa.hibernate.use-new-id-generator-mappings")) {
                return new DeleteMatchingLinesFix("Delete spring.jpa.hibernate.use-new-id-generator-mappings",
                        "spring.jpa.hibernate.use-new-id-generator-mappings");
            }
            return null;
        }

        private static LocalQuickFix replace(String name, String oldText, String newText) {
            return new ReplaceFileTextFix(name, List.of(replacement(oldText, newText)));
        }
    }

    private record Replacement(String oldText, String newText) {
    }

    private static Replacement replacement(String oldText, String newText) {
        return new Replacement(oldText, newText);
    }

    private static final class ReplaceFileTextFix implements LocalQuickFix {

        private final String name;

        private final List<Replacement> replacements;

        private ReplaceFileTextFix(String name, List<Replacement> replacements) {
            this.name = name;
            this.replacements = replacements;
        }

        @Override
        @NotNull
        public String getName() {
            return this.name;
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.name;
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile file = descriptor.getPsiElement().getContainingFile();
            String text = file.getText();
            for (Replacement replacement : this.replacements) {
                text = text.replace(replacement.oldText(), replacement.newText());
            }
            replaceFileText(project, file, text);
        }
    }

    private static final class RegexReplaceFix implements LocalQuickFix {

        private final String name;

        private final Pattern pattern;

        private final String replacement;

        private RegexReplaceFix(String name, Pattern pattern, String replacement) {
            this.name = name;
            this.pattern = pattern;
            this.replacement = replacement;
        }

        @Override
        @NotNull
        public String getName() {
            return this.name;
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.name;
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile file = descriptor.getPsiElement().getContainingFile();
            replaceFileText(project, file, this.pattern.matcher(file.getText()).replaceAll(this.replacement));
        }
    }

    private static final class JavaVersionFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Set Java version to 17";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile file = descriptor.getPsiElement().getContainingFile();
            Matcher matcher = JAVA_VERSION_PATTERN.matcher(file.getText());
            StringBuilder result = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + "17"));
            }
            matcher.appendTail(result);
            replaceFileText(project, file, result.toString());
        }
    }

    private static final class DeleteMatchingLinesFix implements LocalQuickFix {

        private final String name;

        private final String marker;

        private DeleteMatchingLinesFix(String name, String marker) {
            this.name = name;
            this.marker = marker;
        }

        @Override
        @NotNull
        public String getName() {
            return this.name;
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.name;
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile file = descriptor.getPsiElement().getContainingFile();
            StringBuilder result = new StringBuilder();
            for (String line : file.getText().split("\\R", -1)) {
                if (!line.contains(this.marker)) {
                    result.append(line).append("\n");
                }
            }
            replaceFileText(project, file, result.toString());
        }
    }

    private static void replaceFileText(Project project, PsiFile file, String text) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            com.intellij.openapi.editor.Document document = PsiDocumentManager.getInstance(project).getDocument(file);
            if (document != null) {
                document.setText(text);
                PsiDocumentManager.getInstance(project).commitDocument(document);
            }
        });
    }

    public static class JavaxServletDependency extends SpringBoot3FileMigrationInspection {
        public JavaxServletDependency() { super(Kind.JAVAX_SERVLET_DEPENDENCY); }
    }

    public static class IncompatibleJavaVersion extends SpringBoot3FileMigrationInspection {
        public IncompatibleJavaVersion() { super(Kind.INCOMPATIBLE_JAVA_VERSION); }
    }

    public static class LegacyJavaEeDependencies extends SpringBoot3FileMigrationInspection {
        public LegacyJavaEeDependencies() { super(Kind.LEGACY_JAVA_EE_DEPENDENCIES); }
    }

    public static class MySqlDriverCoordinates extends SpringBoot3FileMigrationInspection {
        public MySqlDriverCoordinates() { super(Kind.MYSQL_DRIVER_COORDINATES); }
    }

    public static class SpringFactoriesAutoConfig extends SpringBoot3FileMigrationInspection {
        public SpringFactoriesAutoConfig() { super(Kind.SPRING_FACTORIES_AUTO_CONFIG); }
    }

    public static class ImageBanner extends SpringBoot3FileMigrationInspection {
        public ImageBanner() { super(Kind.IMAGE_BANNER); }
    }

    public static class MaxHttpHeaderSize extends SpringBoot3FileMigrationInspection {
        public MaxHttpHeaderSize() { super(Kind.MAX_HTTP_HEADER_SIZE); }
    }

    public static class HttptraceProperty extends SpringBoot3FileMigrationInspection {
        public HttptraceProperty() { super(Kind.HTTPTRACE_PROPERTY); }
    }

    public static class MetricsExportProperty extends SpringBoot3FileMigrationInspection {
        public MetricsExportProperty() { super(Kind.METRICS_EXPORT_PROPERTY); }
    }

    public static class CassandraProperties extends SpringBoot3FileMigrationInspection {
        public CassandraProperties() { super(Kind.CASSANDRA_PROPERTIES); }
    }

    public static class RedisProperties extends SpringBoot3FileMigrationInspection {
        public RedisProperties() { super(Kind.REDIS_PROPERTIES); }
    }

    public static class HibernateIdGeneratorProperty extends SpringBoot3FileMigrationInspection {
        public HibernateIdGeneratorProperty() { super(Kind.HIBERNATE_ID_GENERATOR_PROPERTY); }
    }

    public static class HibernateGroupId extends SpringBoot3FileMigrationInspection {
        public HibernateGroupId() { super(Kind.HIBERNATE_GROUP_ID); }
    }

    public static class ApacheHttpClient extends SpringBoot3FileMigrationInspection {
        public ApacheHttpClient() { super(Kind.APACHE_HTTP_CLIENT); }
    }

    public static class ElasticsearchRestClient extends SpringBoot3FileMigrationInspection {
        public ElasticsearchRestClient() { super(Kind.ELASTICSEARCH_REST_CLIENT); }
    }

    public static class ElasticsearchAutoConfigExclusion extends SpringBoot3FileMigrationInspection {
        public ElasticsearchAutoConfigExclusion() { super(Kind.ELASTICSEARCH_AUTO_CONFIG_EXCLUSION); }
    }

    public static class FlapdoodleMongo extends SpringBoot3FileMigrationInspection {
        public FlapdoodleMongo() { super(Kind.FLAPDOODLE_MONGO); }
    }

    public static class Saml2IdentityProviderProperty extends SpringBoot3FileMigrationInspection {
        public Saml2IdentityProviderProperty() { super(Kind.SAML2_IDENTITY_PROVIDER_PROPERTY); }
    }

    public static class SecurityDispatchTypes extends SpringBoot3FileMigrationInspection {
        public SecurityDispatchTypes() { super(Kind.SECURITY_DISPATCH_TYPES); }
    }

    public static class BatchJobNamesProperty extends SpringBoot3FileMigrationInspection {
        public BatchJobNamesProperty() { super(Kind.BATCH_JOB_NAMES_PROPERTY); }
    }

    public static class SessionStoreType extends SpringBoot3FileMigrationInspection {
        public SessionStoreType() { super(Kind.SESSION_STORE_TYPE); }
    }

    public static class JmxEndpointExposure extends SpringBoot3FileMigrationInspection {
        public JmxEndpointExposure() { super(Kind.JMX_ENDPOINT_EXPOSURE); }
    }

    public static class ActuatorSanitization extends SpringBoot3FileMigrationInspection {
        public ActuatorSanitization() { super(Kind.ACTUATOR_SANITIZATION); }
    }

    public static class PropertiesMigratorLeftInDeps extends SpringBoot3FileMigrationInspection {
        public PropertiesMigratorLeftInDeps() { super(Kind.PROPERTIES_MIGRATOR_LEFT_IN_DEPS); }
    }

    public static class GitCommitIdPlugin extends SpringBoot3FileMigrationInspection {
        public GitCommitIdPlugin() { super(Kind.GIT_COMMIT_ID_PLUGIN); }
    }

    public static class MavenForkAttribute extends SpringBoot3FileMigrationInspection {
        public MavenForkAttribute() { super(Kind.MAVEN_FORK_ATTRIBUTE); }
    }

    public static class Ehcache3Classifier extends SpringBoot3FileMigrationInspection {
        public Ehcache3Classifier() { super(Kind.EHCACHE3_CLASSIFIER); }
    }

    public static class R2dbcBomVersion extends SpringBoot3FileMigrationInspection {
        public R2dbcBomVersion() { super(Kind.R2DBC_BOM_VERSION); }
    }

    public static class ActiveMQRemoved extends SpringBoot3FileMigrationInspection {
        public ActiveMQRemoved() { super(Kind.ACTIVEMQ_REMOVED); }
    }

    public static class AtomikosRemoved extends SpringBoot3FileMigrationInspection {
        public AtomikosRemoved() { super(Kind.ATOMIKOS_REMOVED); }
    }

    public static class EhCache2Removed extends SpringBoot3FileMigrationInspection {
        public EhCache2Removed() { super(Kind.EHCACHE2_REMOVED); }
    }

    public static class SolrRemoved extends SpringBoot3FileMigrationInspection {
        public SolrRemoved() { super(Kind.SOLR_REMOVED); }
    }

    public static class RenamedProperties extends SpringBoot3FileMigrationInspection {
        public RenamedProperties() { super(Kind.RENAMED_PROPERTIES); }
    }
}
