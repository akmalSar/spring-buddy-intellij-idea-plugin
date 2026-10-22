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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.sarhan.springformat.settings.SpringBootVersionResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Spring Boot 4 (Spring Framework 7, Spring Security 7, Jackson 3)
 * migration inspections. Migration checks run before the upgrade; they are
 * silenced only when the project is explicitly pinned to Boot 3.x.
 *
 * @author Akmal Sarhan
 */
public class SpringBoot4MigrationInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String JSPECIFY_PREFIX = "org.jspecify.annotations.";

    /**
     * Jackson 3 renamed types (verified against Jackson 3.0 sources).
     */
    private static final Map<String, String> JACKSON_RENAMED_TYPES = Map.ofEntries(
            Map.entry("com.fasterxml.jackson.core.JsonProcessingException", "tools.jackson.core.JacksonException"),
            Map.entry("com.fasterxml.jackson.databind.JsonMappingException", "tools.jackson.databind.DatabindException"),
            Map.entry("com.fasterxml.jackson.databind.JsonSerializer", "tools.jackson.databind.ValueSerializer"),
            Map.entry("com.fasterxml.jackson.databind.JsonDeserializer", "tools.jackson.databind.ValueDeserializer"),
            Map.entry("com.fasterxml.jackson.databind.SerializerProvider", "tools.jackson.databind.SerializationContext"),
            Map.entry("com.fasterxml.jackson.databind.DeserializationContext",
                    "tools.jackson.databind.DeserializationContext"));

    /**
     * Spring Boot 4 Jackson integration renames (verified against the
     * spring-boot-jackson module on main).
     */
    private static final Map<String, String> BOOT_JACKSON_RENAMES = Map.of(
            "org.springframework.boot.jackson.JsonComponent", "org.springframework.boot.jackson.JacksonComponent",
            "org.springframework.boot.jackson.JsonMixin", "org.springframework.boot.jackson.JacksonMixin",
            "org.springframework.boot.jackson.JsonComponentModule",
            "org.springframework.boot.jackson.JacksonComponentModule",
            "org.springframework.boot.jackson.JsonMixinModule", "org.springframework.boot.jackson.JacksonMixinModule",
            "org.springframework.boot.jackson.JsonMixinModuleEntries",
            "org.springframework.boot.jackson.JacksonMixinModuleEntries",
            "org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer",
            "org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer",
            "org.springframework.boot.jackson.JsonObjectSerializer", "org.springframework.boot.jackson.ObjectValueSerializer",
            "org.springframework.boot.jackson.JsonObjectDeserializer", "org.springframework.boot.jackson.ObjectValueDeserializer");

    /**
     * Types relocated by the Spring Boot 4 modularisation (verified against
     * spring-boot main).
     */
    private static final Map<String, String> RELOCATED_TYPES = Map.ofEntries(
            Map.entry("org.springframework.boot.autoconfigure.domain.EntityScan",
                    "org.springframework.boot.persistence.autoconfigure.EntityScan"),
            Map.entry("org.springframework.boot.test.web.client.TestRestTemplate",
                    "org.springframework.boot.resttestclient.TestRestTemplate"),
            Map.entry("org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest",
                    "org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest"),
            Map.entry("org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest",
                    "org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest"),
            Map.entry("org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc",
                    "org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc"),
            Map.entry("org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint",
                    "org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint"),
            Map.entry("org.springframework.boot.test.autoconfigure.properties.PropertyMapping",
                    "org.springframework.boot.test.context.PropertyMapping"),
            Map.entry("org.springframework.boot.test.autoconfigure.properties.SkipPropertyMapping",
                    "org.springframework.boot.test.context.PropertyMapping.Skip"),
            Map.entry("org.springframework.boot.BootstrapContext",
                    "org.springframework.boot.bootstrap.BootstrapContext"),
            Map.entry("org.springframework.boot.BootstrapContextClosedEvent",
                    "org.springframework.boot.bootstrap.BootstrapContextClosedEvent"),
            Map.entry("org.springframework.boot.BootstrapRegistry",
                    "org.springframework.boot.bootstrap.BootstrapRegistry"),
            Map.entry("org.springframework.boot.BootstrapRegistryInitializer",
                    "org.springframework.boot.bootstrap.BootstrapRegistryInitializer"),
            Map.entry("org.springframework.boot.ConfigurableBootstrapContext",
                    "org.springframework.boot.bootstrap.ConfigurableBootstrapContext"),
            Map.entry("org.springframework.boot.DefaultBootstrapContext",
                    "org.springframework.boot.bootstrap.DefaultBootstrapContext"),
            Map.entry("org.springframework.boot.env.EnvironmentPostProcessor",
                    "org.springframework.boot.EnvironmentPostProcessor"));

    /**
     * Relocated types whose old location still exists (deprecated) in Boot 4;
     * reported as a warning instead of an error.
     */
    private static final Set<String> DEPRECATED_RELOCATIONS = Set.of(
            "org.springframework.boot.env.EnvironmentPostProcessor");

    private static final Set<String> RELOCATED_ANNOTATIONS = Set.of(
            "org.springframework.boot.autoconfigure.domain.EntityScan",
            "org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest",
            "org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest",
            "org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc",
            "org.springframework.boot.test.autoconfigure.properties.PropertyMapping");

    private static final String AUTO_CONFIGURE_MOCK_MVC =
            "org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc";

    private static final String PROPERTY_MAPPING_SKIP = "org.springframework.boot.test.context.PropertyMapping.Skip";

    private static final String MOCK_BEAN = "org.springframework.boot.test.mock.mockito.MockBean";

    private static final String SPY_BEAN = "org.springframework.boot.test.mock.mockito.SpyBean";

    private static final String MOCKITO_RESET = "org.springframework.test.context.bean.override.mockito.MockReset";

    private static final String SPRING_BOOT_TEST = "org.springframework.boot.test.context.SpringBootTest";

    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

    private static final String MOCK_MVC = "org.springframework.test.web.servlet.MockMvc";

    private static final Set<String> AUTO_CONFIGURE_MOCK_MVC_FQNS = Set.of(
            "org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc",
            "org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc");

    private static final Set<String> TEST_REST_TEMPLATE_FQNS = Set.of(
            "org.springframework.boot.resttestclient.TestRestTemplate",
            "org.springframework.boot.test.web.client.TestRestTemplate");

    private static final String AUTO_CONFIGURE_TEST_REST_TEMPLATE =
            "org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate";

    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";

    private static final String SPRING_EXTENSION = "org.springframework.test.context.junit.jupiter.SpringExtension";

    private static final String MOCKITO_EXTENSION = "org.mockito.junit.jupiter.MockitoExtension";

    private static final String MOCKITO_SETTINGS = "org.mockito.junit.jupiter.MockitoSettings";

    private static final String MOCKITO_ANNOTATIONS_CLASS = "org.mockito.MockitoAnnotations";

    /**
     * Spring TestContext entry points that used to run {@code MockitoTestExecutionListener};
     * meta-annotated slices are found via {@code @ExtendWith(SpringExtension.class)}.
     */
    private static final Set<String> SPRING_TEST_ANNOTATIONS = Set.of(
            SPRING_BOOT_TEST,
            "org.springframework.test.context.junit.jupiter.SpringJUnitConfig",
            "org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig",
            "org.springframework.test.context.ContextConfiguration");

    private static final Set<String> MOCKITO_FIELD_ANNOTATIONS = Set.of(
            "org.mockito.Mock", "org.mockito.Spy", "org.mockito.Captor", "org.mockito.InjectMocks");

    private static final String BEAN = "org.springframework.context.annotation.Bean";

    private static final Set<String> OBJECT_MAPPER_FQNS = Set.of(
            "com.fasterxml.jackson.databind.ObjectMapper", "tools.jackson.databind.ObjectMapper");

    private static final String HTTP_MESSAGE_CONVERTER = "org.springframework.http.converter.HttpMessageConverter";

    private static final Set<String> ACTUATOR_OPERATIONS = Set.of(
            "org.springframework.boot.actuate.endpoint.annotation.ReadOperation",
            "org.springframework.boot.actuate.endpoint.annotation.WriteOperation",
            "org.springframework.boot.actuate.endpoint.annotation.DeleteOperation");

    private static final String SPRING_LANG_NULLABLE_FQN = "org.springframework.lang.Nullable";

    private static final String PROPERTY_MAPPER = "org.springframework.boot.context.properties.PropertyMapper";

    /**
     * Jackson 2 packages whose Jackson 3 home is not a plain prefix swap (java.time,
     * Jdk8 and parameter-names support were merged into databind; other modules
     * may not be ported), so they are reported without a quick fix.
     */
    private static final List<String> JACKSON_MODULE_PREFIXES = List.of(
            "com.fasterxml.jackson.datatype.", "com.fasterxml.jackson.module.");

    private static final Set<String> REMOVED_LISTENABLE_TYPES = Set.of(
            "org.springframework.util.concurrent.ListenableFuture",
            "org.springframework.util.concurrent.SettableListenableFuture",
            "org.springframework.util.concurrent.ListenableFutureCallback",
            "org.springframework.util.concurrent.ListenableFutureAdapter",
            "org.springframework.scheduling.annotation.AsyncResult");

    private static final Set<String> SECURITY_MATCHER_TYPES = Set.of(
            "org.springframework.security.web.util.matcher.AntPathRequestMatcher",
            "org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher",
            "org.springframework.security.web.util.matcher.MvcRequestMatcher");

    private static final Set<String> HTTP_SECURITY_CONFIGURER_METHODS = Set.of(
            "csrf", "cors", "formLogin", "httpBasic", "headers", "sessionManagement", "exceptionHandling",
            "oauth2Login", "oauth2ResourceServer", "authorizeHttpRequests", "authorizeRequests", "rememberMe",
            "logout", "anonymous", "jee", "x509", "saml2Login", "saml2Logout", "oidcLogout", "requiresChannel");

    private static final Set<String> HIBERNATE_REMOVED_ANNOTATIONS = Set.of(
            "org.hibernate.annotations.Where",
            "org.hibernate.annotations.WhereJoinTable",
            "org.hibernate.annotations.Loader",
            "org.hibernate.annotations.Polymorphism",
            "org.hibernate.annotations.SelectBeforeUpdate",
            "org.hibernate.annotations.Proxy");

    private static final Set<String> SPRING_LANG_NULLABLE = Set.of(
            "org.springframework.lang.Nullable",
            "org.springframework.lang.NonNull");

    private static final Set<String> SPRING_LANG_PACKAGE_ANNOTATIONS = Set.of(
            "org.springframework.lang.NonNullApi",
            "org.springframework.lang.NonNullFields");

    private static final List<String> HTTP_SECURITY_FQNS = List.of(
            "org.springframework.security.config.annotation.web.builders.HttpSecurity");

    private static final String PATH_MATCH_CONFIGURER = "org.springframework.web.servlet.config.annotation.PathMatchConfigurer";

    private static final Set<String> PATH_MATCH_REMOVED_METHODS = Set.of(
            "setUseSuffixPatternMatch", "setUseRegisteredSuffixPatternMatch", "setUseTrailingSlashMatch");

    private static final List<String> JUNIT4_RUNNER_FQNS = List.of(
            "org.springframework.test.context.junit4.SpringRunner",
            "org.springframework.test.context.junit4.SpringJUnit4ClassRunner");

    private static final List<String> SPRING_SECURITY_CONFIG_PACKAGES = List.of(
            "org.springframework.security.config.annotation.web.",
            "org.springframework.security.config.annotation.authentication.");

    private final Kind kind;

    protected SpringBoot4MigrationInspection(@NotNull Kind kind) {
        this.kind = kind;
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())
                || !SpringBootVersionResolver.migrationChecksActive(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitImportStatement(@NotNull PsiImportStatement statement) {
                super.visitImportStatement(statement);
                SpringBoot4MigrationInspection.this.visitImport(statement, holder);
            }

            @Override
            public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
                super.visitReferenceElement(reference);
                SpringBoot4MigrationInspection.this.visitCodeReference(reference, holder);
            }

            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                SpringBoot4MigrationInspection.this.visitReference(expression, holder);
            }

            @Override
            public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
                super.visitLiteralExpression(expression);
                SpringBoot4MigrationInspection.this.visitLiteral(expression, holder);
            }

            @Override
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                super.visitAnnotation(annotation);
                SpringBoot4MigrationInspection.this.visitAnnotationUsage(annotation, holder);
            }

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                SpringBoot4MigrationInspection.this.visitMethodCall(expression, holder);
            }

            @Override
            public void visitField(@NotNull PsiField field) {
                super.visitField(field);
                SpringBoot4MigrationInspection.this.visitFieldDeclaration(field, holder);
            }

            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                SpringBoot4MigrationInspection.this.visitMethodDeclaration(method, holder);
            }
        };
    }

    private void visitImport(PsiImportStatement statement, ProblemsHolder holder) {
        String fqn = statement.getQualifiedName();
        if (fqn == null) {
            return;
        }
        if (this.kind == Kind.MOCK_BEAN && (MOCK_BEAN.equals(fqn) || SPY_BEAN.equals(fqn))) {
            holder.registerProblem(statement,
                    "@MockBean/@SpyBean was removed in Spring Boot 4; use @MockitoBean/@MockitoSpyBean",
                    new ReplaceTextFix("Replace with Context bean override import", fqn, replacementMockBeanFqn(fqn)));
        }
        // On-demand imports report the package without a trailing dot.
        String packageAware = statement.isOnDemand() ? fqn + "." : fqn;
        if (this.kind == Kind.JACKSON_PACKAGE && isJacksonDatabindPackage(packageAware)) {
            holder.registerProblem(statement, "Jackson 2 databind packages moved to tools.jackson in Spring Boot 4",
                    new ReplaceTextFix("Replace with tools.jackson import", fqn, toJackson3(fqn)));
        }
        if (this.kind == Kind.JACKSON_PACKAGE && isJacksonModulePackage(packageAware)) {
            holder.registerProblem(statement, JACKSON_MODULE_MESSAGE);
        }
        if (this.kind == Kind.JACKSON_RENAMED_TYPE && JACKSON_RENAMED_TYPES.containsKey(fqn)) {
            holder.registerProblem(statement, "Jackson type was renamed in Jackson 3 (Spring Boot 4)",
                    new ReplaceTextFix("Replace with Jackson 3 type", fqn, JACKSON_RENAMED_TYPES.get(fqn)));
        }
        if (this.kind == Kind.BOOT_JACKSON_API && BOOT_JACKSON_RENAMES.containsKey(fqn)) {
            holder.registerProblem(statement, "Spring Boot Jackson integration API was renamed in Spring Boot 4",
                    new ReplaceTextFix("Replace with renamed Boot Jackson API", fqn, BOOT_JACKSON_RENAMES.get(fqn)));
        }
        if (this.kind == Kind.RELOCATED_BOOT_TYPE && RELOCATED_TYPES.containsKey(fqn)) {
            registerRelocation(holder, statement, fqn, "Replace with relocated type");
        }
        if (this.kind == Kind.LISTENABLE_FUTURE && REMOVED_LISTENABLE_TYPES.contains(fqn)) {
            holder.registerProblem(statement, "ListenableFuture API was removed in Spring Framework 7; use CompletableFuture");
        }
        if (this.kind == Kind.ANT_PATH_REQUEST_MATCHER && SECURITY_MATCHER_TYPES.contains(fqn)) {
            holder.registerProblem(statement,
                    "AntPathRequestMatcher/MvcRequestMatcher were removed in Spring Security 7; use PathPatternRequestMatcher");
        }
        if (this.kind == Kind.UNDERTOW && fqn.startsWith("org.springframework.boot.web.embedded.undertow.")) {
            holder.registerProblem(statement, "Undertow is no longer a supported embedded server in Spring Boot 4");
        }
        if (this.kind == Kind.SPRING_LANG_NULLABILITY && SPRING_LANG_NULLABLE.contains(fqn)) {
            holder.registerProblem(statement, "org.springframework.lang nullability annotations are deprecated in"
                    + " Spring Framework 7 in favour of JSpecify",
                    new ReplaceTextFix("Replace with JSpecify annotation", fqn, toJSpecify(fqn)));
        }
        if (this.kind == Kind.SPRING_LANG_NULLABILITY && SPRING_LANG_PACKAGE_ANNOTATIONS.contains(fqn)) {
            holder.registerProblem(statement, "org.springframework.lang nullability annotations are deprecated in"
                    + " Spring Framework 7; consider switching the package to @NullMarked (JSpecify)");
        }
        if (this.kind == Kind.JUNIT4_RUNNER && JUNIT4_RUNNER_FQNS.contains(fqn)) {
            holder.registerProblem(statement,
                    "Spring's JUnit 4 runner is deprecated in Spring Framework 7; migrate the test to JUnit Jupiter",
                    ProblemHighlightType.WARNING);
        }
    }

    private void visitCodeReference(PsiJavaCodeReferenceElement reference, ProblemsHolder holder) {
        if (reference.getParent() instanceof PsiImportStatement
                || reference.getParent() instanceof PsiAnnotation
                || reference.getParent() instanceof PsiJavaCodeReferenceElement) {
            // Imports and annotations are reported from their own visit methods and
            // qualifiers through their outermost reference; otherwise the same
            // occurrence would be flagged twice.
            return;
        }
        String text = referenceTextWithoutTypeArguments(reference);
        if (this.kind == Kind.JACKSON_PACKAGE && isJacksonDatabindPackage(text)) {
            holder.registerProblem(reference, "Jackson 2 databind packages moved to tools.jackson in Spring Boot 4",
                    new ReplaceTextFix("Replace with tools.jackson reference", text, toJackson3(text)));
        }
        if (this.kind == Kind.JACKSON_PACKAGE && isJacksonModulePackage(text)) {
            holder.registerProblem(reference, JACKSON_MODULE_MESSAGE);
        }
        if (this.kind == Kind.JACKSON_RENAMED_TYPE && JACKSON_RENAMED_TYPES.containsKey(text)) {
            holder.registerProblem(reference, "Jackson type was renamed in Jackson 3 (Spring Boot 4)",
                    new ReplaceTextFix("Replace with Jackson 3 type", text, JACKSON_RENAMED_TYPES.get(text)));
        }
        if (this.kind == Kind.BOOT_JACKSON_API && BOOT_JACKSON_RENAMES.containsKey(text)) {
            holder.registerProblem(reference, "Spring Boot Jackson integration API was renamed in Spring Boot 4",
                    new ReplaceTextFix("Replace with renamed Boot Jackson API", text, BOOT_JACKSON_RENAMES.get(text)));
        }
        if (this.kind == Kind.RELOCATED_BOOT_TYPE && RELOCATED_TYPES.containsKey(text)) {
            registerRelocation(holder, reference, text, "Replace with relocated type");
        }
        if (this.kind == Kind.LISTENABLE_FUTURE && REMOVED_LISTENABLE_TYPES.contains(text)) {
            holder.registerProblem(reference, "ListenableFuture API was removed in Spring Framework 7;"
                    + " use CompletableFuture");
        }
        if (this.kind == Kind.ANT_PATH_REQUEST_MATCHER && SECURITY_MATCHER_TYPES.contains(text)) {
            holder.registerProblem(reference,
                    "AntPathRequestMatcher/MvcRequestMatcher were removed in Spring Security 7;"
                            + " use PathPatternRequestMatcher");
        }
        if (this.kind == Kind.UNDERTOW && text.startsWith("org.springframework.boot.web.embedded.undertow.")) {
            holder.registerProblem(reference, "Undertow is no longer a supported embedded server in Spring Boot 4");
        }
        if (this.kind == Kind.MOCK_BEAN && (MOCK_BEAN.equals(text) || SPY_BEAN.equals(text))) {
            holder.registerProblem(reference,
                    "@MockBean/@SpyBean was removed in Spring Boot 4; use @MockitoBean/@MockitoSpyBean");
        }
    }

    private void visitReference(PsiReferenceExpression expression, ProblemsHolder holder) {
        if (this.kind != Kind.PATH_MATCH_SUFFIX) {
            return;
        }
        PsiMethod resolved = expression.resolve() instanceof PsiMethod method ? method : null;
        if (resolved == null || !PATH_MATCH_REMOVED_METHODS.contains(resolved.getName())) {
            return;
        }
        PsiClass containing = resolved.getContainingClass();
        if (containing == null || !PATH_MATCH_CONFIGURER.equals(containing.getQualifiedName())) {
            return;
        }
        holder.registerProblem(expression,
                resolved.getName() + "() was removed in Spring Framework 7; use explicit PathPattern matching",
                new DeleteCallStatementFix());
    }

    private void visitLiteral(PsiLiteralExpression expression, ProblemsHolder holder) {
        Object value = expression.getValue();
        if (!(value instanceof String text)) {
            return;
        }
        if (this.kind == Kind.JACKSON_RENAMED_TYPE && JACKSON_RENAMED_TYPES.containsKey(text)) {
            String replacement = JACKSON_RENAMED_TYPES.get(text);
            holder.registerProblem(expression, "Jackson type was renamed in Jackson 3 (Spring Boot 4)",
                    new ReplaceTextFix("Replace with Jackson 3 type", text, replacement));
        }
        if (this.kind == Kind.BOOT_JACKSON_API && BOOT_JACKSON_RENAMES.containsKey(text)) {
            holder.registerProblem(expression, "Spring Boot Jackson integration API was renamed in Spring Boot 4",
                    new ReplaceTextFix("Replace with renamed Boot Jackson API", text, BOOT_JACKSON_RENAMES.get(text)));
        }
        if (this.kind == Kind.RELOCATED_BOOT_TYPE && RELOCATED_TYPES.containsKey(text)) {
            registerRelocation(holder, expression, text, "Replace with relocated type");
        }
        if (this.kind == Kind.LISTENABLE_FUTURE && REMOVED_LISTENABLE_TYPES.contains(text)) {
            holder.registerProblem(expression, "ListenableFuture API was removed in Spring Framework 7;"
                    + " use CompletableFuture");
        }
        if (this.kind == Kind.ANT_PATH_REQUEST_MATCHER && SECURITY_MATCHER_TYPES.contains(text)) {
            holder.registerProblem(expression,
                    "AntPathRequestMatcher/MvcRequestMatcher were removed in Spring Security 7;"
                            + " use PathPatternRequestMatcher");
        }
        if (this.kind == Kind.UNDERTOW && text.startsWith("org.springframework.boot.web.embedded.undertow.")) {
            holder.registerProblem(expression, "Undertow is no longer a supported embedded server in Spring Boot 4");
        }
    }

    private void visitAnnotationUsage(PsiAnnotation annotation, ProblemsHolder holder) {
        boolean fieldOwner = isFieldOwner(annotation);
        if (this.kind == Kind.MOCK_BEAN
                && (annotationFqnMatches(annotation, MOCK_BEAN) || annotationFqnMatches(annotation, SPY_BEAN))
                && (fieldOwner || isClassOwner(annotation))) {
            String replacement = annotationFqnMatches(annotation, SPY_BEAN)
                    ? "org.springframework.test.context.bean.override.mockito.MockitoSpyBean"
                    : "org.springframework.test.context.bean.override.mockito.MockitoBean";
            holder.registerProblem(annotation,
                    "@MockBean/@SpyBean was removed in Spring Boot 4; use @MockitoBean/@MockitoSpyBean",
                    new ReplaceAnnotationFix("Replace with @" + shortName(replacement), replacement,
                            mockitoBeanAttributesText(annotation, !fieldOwner)));
        }
        if (this.kind == Kind.RELOCATED_BOOT_TYPE) {
            String relocated = matchingTableFqn(annotation, RELOCATED_TYPES);
            if (relocated != null && RELOCATED_ANNOTATIONS.contains(relocated)) {
                holder.registerProblem(annotation, "This Spring Boot type was relocated by the Boot 4 modularisation",
                        new ReplaceAnnotationFix("Replace with relocated annotation",
                                RELOCATED_TYPES.get(relocated), relocatedAttributesText(annotation)));
            }
        }
        if (this.kind == Kind.BOOT_JACKSON_API) {
            String renamed = matchingTableFqn(annotation, BOOT_JACKSON_RENAMES);
            if (renamed != null && isBootJacksonAnnotation(renamed)) {
                holder.registerProblem(annotation, "Spring Boot Jackson integration API was renamed in Spring Boot 4",
                        new ReplaceAnnotationFix("Replace with renamed annotation",
                                BOOT_JACKSON_RENAMES.get(renamed), attributesText(annotation)));
            }
        }
        if (this.kind == Kind.SPRING_LANG_NULLABILITY) {
            String nullable = matchingSetFqn(annotation, SPRING_LANG_NULLABLE);
            // Actuator operation parameters are reported by the dedicated ERROR inspection.
            if (nullable != null && !isActuatorOperationParameterNullable(annotation)) {
                holder.registerProblem(annotation, "org.springframework.lang nullability annotations are deprecated"
                        + " in Spring Framework 7 in favour of JSpecify",
                        new ReplaceAnnotationFix("Replace with JSpecify annotation",
                                toJSpecify(nullable), attributesText(annotation)));
            }
            String packageAnnotation = matchingSetFqn(annotation, SPRING_LANG_PACKAGE_ANNOTATIONS);
            if (packageAnnotation != null) {
                holder.registerProblem(annotation, "org.springframework.lang nullability annotations are deprecated"
                        + " in Spring Framework 7; consider switching the package to @NullMarked (JSpecify)");
            }
        }
        if (this.kind == Kind.HIBERNATE_REMOVED_ANNOTATION) {
            String removed = matchingSetFqn(annotation, HIBERNATE_REMOVED_ANNOTATIONS);
            if (removed != null) {
                LocalQuickFix fix = hibernateRemovalFix(removed, annotation);
                if (fix == null) {
                    holder.registerProblem(annotation,
                            removeAnnotation(removed) + " was removed in Hibernate 7 (Spring Boot 4)");
                }
                else {
                    holder.registerProblem(annotation,
                            removeAnnotation(removed) + " was removed in Hibernate 7 (Spring Boot 4)", fix);
                }
            }
        }
        if (this.kind == Kind.JUNIT4_RUNNER && isJunit4SpringRunner(annotation)) {
            holder.registerProblem(annotation,
                    "Spring's JUnit 4 runner is deprecated in Spring Framework 7; migrate the test to JUnit Jupiter",
                    ProblemHighlightType.WARNING);
        }
    }

    private void visitFieldDeclaration(PsiField field, ProblemsHolder holder) {
        PsiClass owner = field.getContainingClass();
        if (owner == null) {
            return;
        }
        if (this.kind == Kind.SPRING_BOOT_TEST_AUTO_CONFIGURE) {
            String missing = missingAutoConfigureAnnotation(field, owner);
            if (missing != null) {
                holder.registerProblem(field.getNameIdentifier(), "@SpringBootTest no longer configures "
                                + shortName(fieldTypeFqn(field)) + " in Spring Boot 4; add @" + shortName(missing),
                        new AddClassAnnotationFix("Add @" + shortName(missing), missing));
            }
        }
        if (this.kind == Kind.MOCKITO_ANNOTATIONS && hasAnyAnnotation(field.getModifierList(), MOCKITO_FIELD_ANNOTATIONS)
                && isSpringTestClass(owner) && !initialisesMockito(owner)) {
            holder.registerProblem(field.getNameIdentifier(), "MockitoTestExecutionListener was removed in Spring Boot 4;"
                            + " this Mockito field stays null without MockitoExtension",
                    new AddClassAnnotationFix("Add @ExtendWith(MockitoExtension.class)",
                            EXTEND_WITH + "(" + MOCKITO_EXTENSION + ".class)"));
        }
    }

    private void visitMethodDeclaration(PsiMethod method, ProblemsHolder holder) {
        PsiElement anchor = (method.getNameIdentifier() != null) ? method.getNameIdentifier() : method;
        boolean bean = hasAnyAnnotation(method.getModifierList(), Set.of(BEAN));
        PsiClass returned = bean ? SpringInspectionUtil.resolvedClass(method.getReturnType()) : null;
        if (this.kind == Kind.OBJECT_MAPPER_BEAN && returned != null
                && OBJECT_MAPPER_FQNS.contains(returned.getQualifiedName())) {
            holder.registerProblem(anchor, "An ObjectMapper bean no longer replaces the auto-configured mapper in"
                    + " Spring Boot 4; define a JsonMapper (or XmlMapper) bean instead");
        }
        if (this.kind == Kind.HTTP_MESSAGE_CONVERTER_BEAN && returned != null
                && (HTTP_MESSAGE_CONVERTER.equals(returned.getQualifiedName())
                        || InheritanceUtil.isInheritor(returned, HTTP_MESSAGE_CONVERTER))) {
            holder.registerProblem(anchor, "HttpMessageConverter beans are no longer picked up in Spring Boot 4;"
                    + " register the converter through a ServerHttpMessageConvertersCustomizer"
                    + " or ClientHttpMessageConvertersCustomizer");
        }
        if (this.kind == Kind.ACTUATOR_SPRING_LANG_NULLABLE
                && hasAnyAnnotation(method.getModifierList(), ACTUATOR_OPERATIONS)) {
            for (PsiParameter parameter : method.getParameterList().getParameters()) {
                PsiModifierList modifiers = parameter.getModifierList();
                if (modifiers == null) {
                    continue;
                }
                for (PsiAnnotation annotation : modifiers.getAnnotations()) {
                    if (annotationFqnMatches(annotation, SPRING_LANG_NULLABLE_FQN)) {
                        holder.registerProblem(annotation, "org.springframework.lang.Nullable no longer makes an"
                                        + " actuator operation parameter optional in Spring Boot 4",
                                new ReplaceAnnotationFix("Replace with JSpecify annotation",
                                        toJSpecify(SPRING_LANG_NULLABLE_FQN), null));
                    }
                }
            }
        }
    }

    private void visitMethodCall(PsiMethodCallExpression expression, ProblemsHolder holder) {
        if (this.kind == Kind.PROPERTY_MAPPER_ALWAYS_APPLYING && isAlwaysApplyingWhenNonNull(expression)) {
            holder.registerProblem(expression, "PropertyMapper.alwaysApplyingWhenNonNull() was removed in Spring Boot 4;"
                    + " null values are now skipped by default (use always() to map nulls)");
        }
        if (this.kind == Kind.SECURITY_AND_CHAIN) {
            PsiMethod resolved = expression.resolveMethod();
            PsiClass containing = (resolved == null) ? null : resolved.getContainingClass();
            if (resolved != null && containing != null && "and".equals(resolved.getName())
                    && isSpringSecurityConfigPackage(containing.getQualifiedName())) {
                holder.registerProblem(expression,
                        "and() was removed from the HttpSecurity DSL in Spring Security 7;"
                                + " use the lambda DSL instead");
            }
        }
        if (this.kind == Kind.SECURITY_NON_LAMBDA_DSL) {
            PsiMethod resolved = expression.resolveMethod();
            PsiClass containing = (resolved == null) ? null : resolved.getContainingClass();
            if (resolved != null && containing != null
                    && HTTP_SECURITY_CONFIGURER_METHODS.contains(resolved.getName())
                    && HTTP_SECURITY_FQNS.contains(containing.getQualifiedName())
                    && resolved.getParameterList().getParameters().length == 0) {
                LocalQuickFix fix = disableCallFix(expression, resolved.getName());
                if (fix == null) {
                    holder.registerProblem(expression,
                            resolved.getName() + "() without a Customizer argument was removed in Spring Security 7;"
                                    + " use the lambda DSL");
                }
                else {
                    holder.registerProblem(expression,
                            resolved.getName() + "() without a Customizer argument was removed in Spring Security 7;"
                                    + " use the lambda DSL", fix);
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static final String JACKSON_MODULE_MESSAGE = "Jackson 2 modules moved to tools.jackson in Spring Boot 4;"
            + " java.time/Jdk8/parameter-names support is now built into Jackson 3 databind";

    /**
     * Registers a relocation; the quick fix is only offered when the simple name is
     * unchanged, otherwise references to the old simple name would no longer compile.
     */
    private static void registerRelocation(ProblemsHolder holder, PsiElement element, String fqn, String fixName) {
        String target = RELOCATED_TYPES.get(fqn);
        boolean deprecated = DEPRECATED_RELOCATIONS.contains(fqn);
        String message = deprecated
                ? "This Spring Boot type is deprecated in Boot 4; use " + target
                : "This Spring Boot type was relocated by the Boot 4 modularisation";
        ProblemHighlightType level = deprecated
                ? ProblemHighlightType.WARNING
                : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        if (shortName(fqn).equals(shortName(target))) {
            holder.registerProblem(element, message, level, new ReplaceTextFix(fixName, fqn, target));
        }
        else {
            holder.registerProblem(element, message + " (now " + target + ")", level);
        }
    }

    /**
     * The removed method no longer resolves against Boot 4, so an unresolved call
     * also matches when its qualifier is typed as {@code PropertyMapper}.
     */
    private static boolean isAlwaysApplyingWhenNonNull(PsiMethodCallExpression expression) {
        if (!"alwaysApplyingWhenNonNull".equals(expression.getMethodExpression().getReferenceName())) {
            return false;
        }
        PsiMethod resolved = expression.resolveMethod();
        if (resolved != null) {
            PsiClass containing = resolved.getContainingClass();
            return containing != null && PROPERTY_MAPPER.equals(containing.getQualifiedName());
        }
        PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
        PsiClass qualifierClass = (qualifier != null) ? SpringInspectionUtil.resolvedClass(qualifier.getType()) : null;
        return qualifierClass != null && PROPERTY_MAPPER.equals(qualifierClass.getQualifiedName());
    }

    @Nullable
    private static String missingAutoConfigureAnnotation(PsiField field, PsiClass owner) {
        if (!hasAnyAnnotation(field.getModifierList(), Set.of(AUTOWIRED))
                || !hasAnnotationInHierarchy(owner, (PsiAnnotation a) -> matchingSetFqn(a, Set.of(SPRING_BOOT_TEST)) != null)) {
            return null;
        }
        String type = fieldTypeFqn(field);
        if (MOCK_MVC.equals(type) && !hasAnnotationInHierarchy(owner,
                (PsiAnnotation a) -> matchingSetFqn(a, AUTO_CONFIGURE_MOCK_MVC_FQNS) != null)) {
            return AUTO_CONFIGURE_MOCK_MVC;
        }
        if (TEST_REST_TEMPLATE_FQNS.contains(type) && !hasAnnotationInHierarchy(owner,
                (PsiAnnotation a) -> matchingSetFqn(a, Set.of(AUTO_CONFIGURE_TEST_REST_TEMPLATE)) != null)) {
            return AUTO_CONFIGURE_TEST_REST_TEMPLATE;
        }
        return null;
    }

    private static String fieldTypeFqn(PsiField field) {
        PsiClass type = SpringInspectionUtil.resolvedClass(field.getType());
        String fqn = (type != null) ? type.getQualifiedName() : null;
        return (fqn != null) ? fqn : "";
    }

    private static boolean isSpringTestClass(PsiClass owner) {
        return hasAnnotationInHierarchy(owner, (PsiAnnotation a) -> matchingSetFqn(a, SPRING_TEST_ANNOTATIONS) != null
                || extendsWith(a, SPRING_EXTENSION));
    }

    private static boolean initialisesMockito(PsiClass owner) {
        if (hasAnnotationInHierarchy(owner, (PsiAnnotation a) -> extendsWith(a, MOCKITO_EXTENSION)
                || matchingSetFqn(a, Set.of(MOCKITO_SETTINGS)) != null)) {
            return true;
        }
        for (PsiClass current = owner; current != null; current = current.getSuperClass()) {
            for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(current, PsiMethodCallExpression.class)) {
                String name = call.getMethodExpression().getReferenceName();
                if (!"openMocks".equals(name) && !"initMocks".equals(name)) {
                    continue;
                }
                PsiMethod target = call.resolveMethod();
                PsiClass containing = (target != null) ? target.getContainingClass() : null;
                // Unresolved openMocks/initMocks: assume initialisation rather than risk a false positive.
                if (target == null || (containing != null && MOCKITO_ANNOTATIONS_CLASS.equals(containing.getQualifiedName()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks the class, its superclasses and (transitively) the meta-annotations of
     * their annotations, mirroring how JUnit and Spring discover test annotations.
     */
    private static boolean hasAnnotationInHierarchy(PsiClass owner, Predicate<PsiAnnotation> predicate) {
        Set<PsiClass> visited = new HashSet<>();
        for (PsiClass current = owner; current != null && visited.add(current); current = current.getSuperClass()) {
            if (isAnnotatedWith(current.getModifierList(), predicate, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnnotatedWith(@Nullable PsiModifierList modifiers, Predicate<PsiAnnotation> predicate,
            Set<PsiClass> visited) {
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (predicate.test(annotation)) {
                return true;
            }
            PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
            PsiElement resolved = (reference != null) ? reference.resolve() : null;
            if (resolved instanceof PsiClass type && type.isAnnotationType() && visited.add(type)
                    && isAnnotatedWith(type.getModifierList(), predicate, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean extendsWith(PsiAnnotation annotation, String extensionFqn) {
        if (!annotationFqnMatches(annotation, EXTEND_WITH)) {
            return false;
        }
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value == null) {
            return false;
        }
        List<PsiClassObjectAccessExpression> classes = new ArrayList<>(
                PsiTreeUtil.findChildrenOfType(value, PsiClassObjectAccessExpression.class));
        if (value instanceof PsiClassObjectAccessExpression single) {
            classes.add(single);
        }
        for (PsiClassObjectAccessExpression expression : classes) {
            PsiType type = expression.getOperand().getType();
            PsiClass resolved = (type instanceof PsiClassType classType) ? classType.resolve() : null;
            if (resolved != null && extensionFqn.equals(resolved.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyAnnotation(@Nullable PsiModifierList modifiers, Set<String> fqns) {
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (matchingSetFqn(annotation, fqns) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean isActuatorOperationParameterNullable(PsiAnnotation annotation) {
        PsiParameter parameter = PsiTreeUtil.getParentOfType(annotation, PsiParameter.class);
        PsiMethod method = (parameter != null) ? PsiTreeUtil.getParentOfType(parameter, PsiMethod.class) : null;
        return method != null && method.getParameterList() == parameter.getParent()
                && hasAnyAnnotation(method.getModifierList(), ACTUATOR_OPERATIONS);
    }

    private static String referenceTextWithoutTypeArguments(PsiJavaCodeReferenceElement reference) {
        String text = reference.getText();
        int typeArguments = text.indexOf('<');
        return (typeArguments < 0) ? text : text.substring(0, typeArguments).trim();
    }

    @Nullable
    private static String annotationQualifiedName(PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return name;
    }

    /**
     * Matches an annotation against a fully-qualified target, tolerant of the
     * import-only (short name) form; the ambiguous short form is validated via
     * reference resolution.
     */
    private static boolean annotationFqnMatches(PsiAnnotation annotation, String target) {
        String name = annotationQualifiedName(annotation);
        if (name == null) {
            return false;
        }
        if (target.equals(name)) {
            return true;
        }
        return shortName(target).equals(name) && resolvesTo(annotation, target);
    }

    @Nullable
    private static String matchingTableFqn(PsiAnnotation annotation, Map<String, String> table) {
        for (String fqn : table.keySet()) {
            if (annotationFqnMatches(annotation, fqn)) {
                return fqn;
            }
        }
        return null;
    }

    @Nullable
    private static String matchingSetFqn(PsiAnnotation annotation, Set<String> fqns) {
        for (String fqn : fqns) {
            if (annotationFqnMatches(annotation, fqn)) {
                return fqn;
            }
        }
        return null;
    }

    private static boolean resolvesTo(PsiAnnotation annotation, String fqn) {
        PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
        PsiElement resolved = (reference != null) ? reference.resolve() : null;
        return resolved instanceof PsiClass psiClass
                && fqn.equals(psiClass.getQualifiedName());
    }

    private static String shortName(String fqn) {
        int index = fqn.lastIndexOf('.');
        return (index < 0) ? fqn : fqn.substring(index + 1);
    }

    private static String toJSpecify(String fqn) {
        return JSPECIFY_PREFIX + shortName(fqn);
    }

    private static String toJackson3(String fqn) {
        if (fqn.startsWith("com.fasterxml.jackson.")) {
            return "tools.jackson." + fqn.substring("com.fasterxml.jackson.".length());
        }
        return fqn;
    }

    /**
     * Packages that move to {@code tools.jackson} by a plain prefix swap: core,
     * databind and the dataformat modules.
     */
    private static boolean isJacksonDatabindPackage(String fqn) {
        return (fqn.startsWith("com.fasterxml.jackson.databind.") || fqn.startsWith("com.fasterxml.jackson.core.")
                || fqn.startsWith("com.fasterxml.jackson.dataformat."))
                && !JACKSON_RENAMED_TYPES.containsKey(fqn);
    }

    private static boolean isJacksonModulePackage(String fqn) {
        for (String prefix : JACKSON_MODULE_PREFIXES) {
            if (fqn.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBootJacksonAnnotation(String fqn) {
        return fqn.endsWith("JsonComponent") || fqn.endsWith("JsonMixin");
    }

    private static boolean isFieldOwner(PsiAnnotation annotation) {
        return annotation.getParent() instanceof com.intellij.psi.PsiModifierList modifierList
                && modifierList.getParent() instanceof com.intellij.psi.PsiField;
    }

    private static boolean isClassOwner(PsiAnnotation annotation) {
        return annotation.getParent() instanceof com.intellij.psi.PsiModifierList modifierList
                && modifierList.getParent() instanceof PsiClass;
    }

    private static boolean isSpringSecurityConfigPackage(@Nullable String fqn) {
        if (fqn == null) {
            return false;
        }
        for (String prefix : SPRING_SECURITY_CONFIG_PACKAGES) {
            if (fqn.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isJunit4SpringRunner(PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(null);
        if (!(value instanceof PsiClassObjectAccessExpression classObjectAccess)) {
            return false;
        }
        PsiJavaCodeReferenceElement reference = classObjectAccess.getOperand()
                .getInnermostComponentReferenceElement();
        PsiElement resolved = (reference != null) ? reference.resolve() : null;
        return resolved instanceof PsiClass psiClass
                && JUNIT4_RUNNER_FQNS.contains(psiClass.getQualifiedName());
    }

    @Nullable
    private static LocalQuickFix hibernateRemovalFix(String fqn, PsiAnnotation annotation) {
        if (!"org.hibernate.annotations.Where".equals(fqn)
                && !"org.hibernate.annotations.WhereJoinTable".equals(fqn)) {
            return null;
        }
        PsiAnnotationMemberValue clause = (annotation.findAttributeValue(null) != null)
                ? annotation.findAttributeValue(null)
                : annotation.findDeclaredAttributeValue("clause");
        String restriction = (clause != null && clause.getText() != null) ? clause.getText() : null;
        boolean hasClause = restriction != null && !restriction.isBlank() && !"null".equals(restriction);
        return new ReplaceAnnotationFix("@" + shortName(fqn) + "(clause = ...) to @"
                        + shortName(replaceWhereAnnotation(fqn)),
                replaceWhereAnnotation(fqn), hasClause ? "(" + restriction + ")" : null);
    }

    private static String removeAnnotation(String fqn) {
        return "@" + shortName(fqn);
    }

    private static String replaceWhereAnnotation(String fqn) {
        return "org.hibernate.annotations.Where".equals(fqn)
                ? "org.hibernate.annotations.SQLRestriction"
                : "org.hibernate.annotations.SQLJoinTableRestriction";
    }

    @Nullable
    private LocalQuickFix disableCallFix(PsiMethodCallExpression expression, String method) {
        PsiExpressionStatement statement = PsiTreeUtil.getParentOfType(expression, PsiExpressionStatement.class, false);
        if (statement == null) {
            return null;
        }
        PsiExpression top = statement.getExpression();
        if (!(top instanceof PsiMethodCallExpression topCall)
                || !"disable".equals(topCall.getMethodExpression().getReferenceName())) {
            return null;
        }
        PsiExpression receiver = topCall.getMethodExpression().getQualifierExpression();
        if (receiver == null) {
            return null;
        }
        return new LambdaDslDisableFix(receiver.getText(), method);
    }

    @Nullable
    private static String replacementMockBeanFqn(String fqn) {
        return "org.springframework.boot.test.mock.mockito.SpyBean".equals(fqn)
                ? "org.springframework.test.context.bean.override.mockito.MockitoSpyBean"
                : "org.springframework.test.context.bean.override.mockito.MockitoBean";
    }

    @Nullable
    private static String attributesText(PsiAnnotation annotation) {
        String text = annotation.getParameterList().getText();
        return (text != null && !text.isBlank()) ? text : null;
    }

    /**
     * Maps @MockBean/@SpyBean attributes onto @MockitoBean/@MockitoSpyBean.
     * {@code value}/{@code classes} become {@code types} (type-level only),
     * {@code answer} becomes {@code answers} and {@code reset} switches to the
     * Spring Framework {@code MockReset}. {@code proxyTargetAware} has no equivalent.
     */
    @Nullable
    private static String mockitoBeanAttributesText(PsiAnnotation annotation, boolean typeLevel) {
        List<String> attributes = new ArrayList<>();
        List<String> types = new ArrayList<>();
        for (PsiNameValuePair attribute : annotation.getParameterList().getAttributes()) {
            PsiAnnotationMemberValue value = attribute.getValue();
            if (value == null) {
                continue;
            }
            String name = attribute.getAttributeName();
            switch (name) {
                case "value", "classes" -> {
                    if (typeLevel) {
                        types.add(arrayElementsText(value));
                    }
                }
                case "answer" -> attributes.add("answers = " + value.getText());
                case "reset" -> attributes.add("reset = " + enumConstantText(value, MOCKITO_RESET));
                case "proxyTargetAware" -> {
                }
                default -> attributes.add(name + " = " + value.getText());
            }
        }
        if (!types.isEmpty()) {
            attributes.add(0, "types = {" + String.join(", ", types) + "}");
        }
        return attributes.isEmpty() ? null : "(" + String.join(", ", attributes) + ")";
    }

    /**
     * Rewrites attributes whose type changed with the relocation: the HtmlUnit flags
     * of {@code @AutoConfigureMockMvc} move into {@code htmlUnit = @HtmlUnit(...)}
     * and {@code @PropertyMapping(skip = ...)} switches to {@code PropertyMapping.Skip}.
     */
    @Nullable
    private static String relocatedAttributesText(PsiAnnotation annotation) {
        List<String> attributes = new ArrayList<>();
        List<String> htmlUnit = new ArrayList<>();
        for (PsiNameValuePair attribute : annotation.getParameterList().getAttributes()) {
            PsiAnnotationMemberValue value = attribute.getValue();
            if (value == null) {
                continue;
            }
            String name = attribute.getName();
            if ("webClientEnabled".equals(name)) {
                htmlUnit.add("webClient = " + value.getText());
            }
            else if ("webDriverEnabled".equals(name)) {
                htmlUnit.add("webDriver = " + value.getText());
            }
            else if ("skip".equals(name)) {
                attributes.add("skip = " + enumConstantText(value, PROPERTY_MAPPING_SKIP));
            }
            else {
                attributes.add((name == null) ? value.getText() : name + " = " + value.getText());
            }
        }
        if (!htmlUnit.isEmpty()) {
            attributes.add("htmlUnit = @" + AUTO_CONFIGURE_MOCK_MVC + ".HtmlUnit(" + String.join(", ", htmlUnit) + ")");
        }
        return attributes.isEmpty() ? null : "(" + String.join(", ", attributes) + ")";
    }

    private static String enumConstantText(PsiAnnotationMemberValue value, String enumFqn) {
        return (value instanceof PsiReferenceExpression reference && reference.getReferenceName() != null)
                ? enumFqn + "." + reference.getReferenceName()
                : value.getText();
    }

    private static String arrayElementsText(PsiAnnotationMemberValue value) {
        String text = value.getText();
        return (value instanceof com.intellij.psi.PsiArrayInitializerMemberValue)
                ? text.substring(1, text.length() - 1).trim()
                : text;
    }

    private enum Kind {
        MOCK_BEAN,
        JACKSON_PACKAGE,
        JACKSON_RENAMED_TYPE,
        BOOT_JACKSON_API,
        SECURITY_AND_CHAIN,
        SECURITY_NON_LAMBDA_DSL,
        ANT_PATH_REQUEST_MATCHER,
        LISTENABLE_FUTURE,
        SPRING_LANG_NULLABILITY,
        RELOCATED_BOOT_TYPE,
        UNDERTOW,
        PATH_MATCH_SUFFIX,
        HIBERNATE_REMOVED_ANNOTATION,
        JUNIT4_RUNNER,
        SPRING_BOOT_TEST_AUTO_CONFIGURE,
        MOCKITO_ANNOTATIONS,
        OBJECT_MAPPER_BEAN,
        HTTP_MESSAGE_CONVERTER_BEAN,
        ACTUATOR_SPRING_LANG_NULLABLE,
        PROPERTY_MAPPER_ALWAYS_APPLYING
    }

    // ── Quick fixes ───────────────────────────────────────────────────────

    private static class ReplaceTextFix implements LocalQuickFix {
        private final String name;
        private final String from;
        private final String to;

        ReplaceTextFix(String name, String from, String to) {
            this.name = name;
            this.from = from;
            this.to = to;
        }

        @Override
        public @NotNull String getName() {
            return this.name;
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Spring Boot 4 migration";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element == null) {
                return;
            }
            String text = element.getText();
            if (text == null || !text.contains(this.from)) {
                return;
            }
            String replaced = text.replace(this.from, this.to);
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiElement newElement;
            if (element instanceof PsiImportStatement) {
                PsiJavaFile file = (PsiJavaFile) PsiFileFactory.getInstance(project)
                        .createFileFromText("Import.java", JavaLanguage.INSTANCE,
                                replaced + "\nclass ImportPlaceholder { }");
                newElement = (file.getImportList() == null || file.getImportList().getImportStatements().length == 0)
                        ? null : file.getImportList().getImportStatements()[0];
            }
            else if (element instanceof PsiLiteralExpression) {
                newElement = factory.createExpressionFromText(replaced, element.getParent());
            }
            else {
                newElement = factory.createReferenceFromText(replaced, element.getParent());
            }
            if (newElement == null) {
                return;
            }
            element.replace(newElement);
        }
    }

    private static class ReplaceAnnotationFix implements LocalQuickFix {
        private final String name;
        private final String annotationFqn;
        private final String attributes;

        ReplaceAnnotationFix(String name, String annotationFqn, String attributes) {
            this.name = name;
            this.annotationFqn = annotationFqn;
            this.attributes = attributes;
        }

        @Override
        public @NotNull String getName() {
            return this.name;
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Spring Boot 4 migration";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiAnnotation annotation = (element instanceof PsiAnnotation psiAnnotation)
                    ? psiAnnotation
                    : PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
            if (annotation == null) {
                return;
            }
            String attributeText = (this.attributes == null || this.attributes.isBlank()) ? "" : this.attributes;
            String text = "@" + this.annotationFqn + attributeText;
            PsiAnnotation replacement = JavaPsiFacade.getElementFactory(project).createAnnotationFromText(text,
                    annotation.getParent());
            annotation.replace(replacement);
        }
    }

    /**
     * Adds a (fully-qualified) annotation to the class enclosing the problem and
     * shortens it to an import.
     */
    private static class AddClassAnnotationFix implements LocalQuickFix {
        private final String name;
        private final String annotationText;

        AddClassAnnotationFix(String name, String annotationText) {
            this.name = name;
            this.annotationText = annotationText;
        }

        @Override
        public @NotNull String getName() {
            return this.name;
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Spring Boot 4 migration";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor) {
            PsiClass psiClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class, false);
            PsiModifierList modifiers = (psiClass != null) ? psiClass.getModifierList() : null;
            if (modifiers == null) {
                return;
            }
            PsiAnnotation added = modifiers.addAnnotation(this.annotationText);
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(added);
        }
    }

    private static class DeleteCallStatementFix implements LocalQuickFix {
        @Override
        public @NotNull String getName() {
            return "Remove removed call";
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Spring Boot 4 migration";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor) {
            PsiExpressionStatement statement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(),
                    PsiExpressionStatement.class, false, PsiClass.class, PsiMethod.class);
            if (statement != null) {
                statement.delete();
            }
        }
    }

    private static class LambdaDslDisableFix implements LocalQuickFix {
        private final String receiver;
        private final String method;

        LambdaDslDisableFix(String receiver, String method) {
            this.receiver = receiver;
            this.method = method;
        }

        @Override
        public @NotNull String getName() {
            return "Convert to lambda DSL: " + this.method + "((customizer) -> customizer.disable())";
        }

        @Override
        public @NotNull String getFamilyName() {
            return "Spring Boot 4 migration";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull com.intellij.codeInspection.ProblemDescriptor descriptor) {
            PsiExpressionStatement statement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(),
                    PsiExpressionStatement.class, false, PsiClass.class, PsiMethod.class);
            if (statement == null) {
                return;
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            String text = this.receiver + "." + this.method + "((customizer) -> customizer.disable())";
            PsiExpression replacement = factory.createExpressionFromText(text, statement);
            statement.getExpression().replace(replacement);
        }
    }

    // ── Public registration classes ───────────────────────────────────────

    public static class MockBean extends SpringBoot4MigrationInspection {
        public MockBean() {
            super(Kind.MOCK_BEAN);
        }
    }

    public static class JacksonPackage extends SpringBoot4MigrationInspection {
        public JacksonPackage() {
            super(Kind.JACKSON_PACKAGE);
        }
    }

    public static class JacksonRenamedType extends SpringBoot4MigrationInspection {
        public JacksonRenamedType() {
            super(Kind.JACKSON_RENAMED_TYPE);
        }
    }

    public static class BootJacksonApi extends SpringBoot4MigrationInspection {
        public BootJacksonApi() {
            super(Kind.BOOT_JACKSON_API);
        }
    }

    public static class SecurityAndChain extends SpringBoot4MigrationInspection {
        public SecurityAndChain() {
            super(Kind.SECURITY_AND_CHAIN);
        }
    }

    public static class SecurityNonLambdaDsl extends SpringBoot4MigrationInspection {
        public SecurityNonLambdaDsl() {
            super(Kind.SECURITY_NON_LAMBDA_DSL);
        }
    }

    public static class AntPathRequestMatcher extends SpringBoot4MigrationInspection {
        public AntPathRequestMatcher() {
            super(Kind.ANT_PATH_REQUEST_MATCHER);
        }
    }

    public static class ListenableFuture extends SpringBoot4MigrationInspection {
        public ListenableFuture() {
            super(Kind.LISTENABLE_FUTURE);
        }
    }

    public static class SpringLangNullability extends SpringBoot4MigrationInspection {
        public SpringLangNullability() {
            super(Kind.SPRING_LANG_NULLABILITY);
        }
    }

    public static class RelocatedBootType extends SpringBoot4MigrationInspection {
        public RelocatedBootType() {
            super(Kind.RELOCATED_BOOT_TYPE);
        }
    }

    public static class Undertow extends SpringBoot4MigrationInspection {
        public Undertow() {
            super(Kind.UNDERTOW);
        }
    }

    public static class PathMatchSuffix extends SpringBoot4MigrationInspection {
        public PathMatchSuffix() {
            super(Kind.PATH_MATCH_SUFFIX);
        }
    }

    public static class HibernateRemovedAnnotation extends SpringBoot4MigrationInspection {
        public HibernateRemovedAnnotation() {
            super(Kind.HIBERNATE_REMOVED_ANNOTATION);
        }
    }

    public static class Junit4Runner extends SpringBoot4MigrationInspection {
        public Junit4Runner() {
            super(Kind.JUNIT4_RUNNER);
        }
    }

    public static class SpringBootTestAutoConfigure extends SpringBoot4MigrationInspection {
        public SpringBootTestAutoConfigure() {
            super(Kind.SPRING_BOOT_TEST_AUTO_CONFIGURE);
        }
    }

    public static class MockitoAnnotations extends SpringBoot4MigrationInspection {
        public MockitoAnnotations() {
            super(Kind.MOCKITO_ANNOTATIONS);
        }
    }

    public static class ObjectMapperBean extends SpringBoot4MigrationInspection {
        public ObjectMapperBean() {
            super(Kind.OBJECT_MAPPER_BEAN);
        }
    }

    public static class HttpMessageConverterBean extends SpringBoot4MigrationInspection {
        public HttpMessageConverterBean() {
            super(Kind.HTTP_MESSAGE_CONVERTER_BEAN);
        }
    }

    public static class ActuatorSpringLangNullable extends SpringBoot4MigrationInspection {
        public ActuatorSpringLangNullable() {
            super(Kind.ACTUATOR_SPRING_LANG_NULLABLE);
        }
    }

    public static class PropertyMapperAlwaysApplying extends SpringBoot4MigrationInspection {
        public PropertyMapperAlwaysApplying() {
            super(Kind.PROPERTY_MAPPER_ALWAYS_APPLYING);
        }
    }
}
