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

import java.util.HashMap;
import java.util.Map;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Spring Boot 3 migration inspections for Java source.
 *
 * @author Akmal Sarhan
 */
public class SpringBoot3MigrationInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String CONFIGURATION_PROPERTIES = "org.springframework.boot.context.properties.ConfigurationProperties";
    private static final String CONSTRUCTOR_BINDING = "org.springframework.boot.context.properties.ConstructorBinding";

    private static final String JAKARTA_MESSAGE = "javax.* type moved to jakarta.* in Spring Boot 3";

    private static final Map<String, String> REMOVED_CLASS_RENAMES = new HashMap<>();

    static {
        REMOVED_CLASS_RENAMES.put("org.springframework.boot.actuate.trace.http.HttpTraceRepository",
                "org.springframework.boot.actuate.web.exchanges.HttpExchangeRepository");
        REMOVED_CLASS_RENAMES.put("org.springframework.boot.actuate.trace.http.InMemoryHttpTraceRepository",
                "org.springframework.boot.actuate.web.exchanges.InMemoryHttpExchangeRepository");
        REMOVED_CLASS_RENAMES.put("org.springframework.boot.actuate.trace.http.HttpTrace",
                "org.springframework.boot.actuate.web.exchanges.HttpExchange");
        REMOVED_CLASS_RENAMES.put("org.springframework.boot.actuate.autoconfigure.trace.http.HttpTraceAutoConfiguration",
                "org.springframework.boot.actuate.autoconfigure.web.exchanges.HttpExchangesAutoConfiguration");
        REMOVED_CLASS_RENAMES.put("org.springframework.boot.actuate.trace.http.HttpTraceEndpoint",
                "org.springframework.boot.actuate.web.exchanges.HttpExchangesEndpoint");
        REMOVED_CLASS_RENAMES.put("org.springframework.boot.actuate.autoconfigure.trace.http.HttpTraceProperties",
                "org.springframework.boot.actuate.autoconfigure.web.exchanges.HttpExchangesProperties");
    }

    private final Kind kind;

    protected SpringBoot3MigrationInspection(@NotNull Kind kind) {
        this.kind = kind;
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitImportStatement(@NotNull PsiImportStatement statement) {
                super.visitImportStatement(statement);
                SpringBoot3MigrationInspection.this.visitImport(statement, holder);
            }

            @Override
            public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                SpringBoot3MigrationInspection.this.visitReference(expression, holder);
            }

            @Override
            public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
                super.visitReferenceElement(reference);
                SpringBoot3MigrationInspection.this.visitCodeReference(reference, holder);
            }

            @Override
            public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
                super.visitLiteralExpression(expression);
                SpringBoot3MigrationInspection.this.visitLiteral(expression, holder);
            }

            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                super.visitClass(aClass);
                SpringBoot3MigrationInspection.this.visitClassDeclaration(aClass, holder);
            }

            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                SpringBoot3MigrationInspection.this.visitMethodDeclaration(method, holder);
            }

            @Override
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                super.visitAnnotation(annotation);
                SpringBoot3MigrationInspection.this.visitAnnotationDeclaration(annotation, holder);
            }

            @Override
            public void visitField(@NotNull PsiField field) {
                super.visitField(field);
                SpringBoot3MigrationInspection.this.visitFieldDeclaration(field, holder);
            }

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                SpringBoot3MigrationInspection.this.visitMethodCall(expression, holder);
            }
        };
    }

    private void visitImport(PsiImportStatement statement, ProblemsHolder holder) {
        String qualifiedName = statement.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        if (this.kind == Kind.JAVAX_IMPORT && isJakartaJavaxName(qualifiedName)) {
            holder.registerProblem(statement, JAKARTA_MESSAGE, new ReplaceTextFix("Replace with jakarta.* import",
                    qualifiedName, toJakarta(qualifiedName)));
        }
        if (this.kind == Kind.YAML_JSON_PARSER && "org.springframework.boot.json.YamlJsonParser".equals(qualifiedName)) {
            holder.registerProblem(statement, "YamlJsonParser was removed in Spring Boot 3");
        }
        if (this.kind == Kind.HTTP_TRACE_REPOSITORY && REMOVED_CLASS_RENAMES.containsKey(qualifiedName)) {
            holder.registerProblem(statement, "HTTP trace API was renamed to HTTP exchanges in Spring Boot 3",
                    new ReplaceTextFix("Replace with HTTP exchange API", qualifiedName, REMOVED_CLASS_RENAMES.get(qualifiedName)));
        }
        if (this.kind == Kind.WEB_MVC_METRICS_FILTER && isOldMetricsClass(qualifiedName)) {
            holder.registerProblem(statement, "Spring Boot 2 web metrics instrumentation API was removed in Spring Boot 3");
        }
        if (this.kind == Kind.APACHE_HTTP_CLIENT && qualifiedName.startsWith("org.apache.http.")) {
            holder.registerProblem(statement, "Apache HttpClient 4 is no longer auto-detected by Spring Boot 3");
        }
        if (this.kind == Kind.WEB_SECURITY_CONFIGURER_ADAPTER
                && "org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter"
                        .equals(qualifiedName)) {
            holder.registerProblem(statement, "WebSecurityConfigurerAdapter was removed in Spring Security 6");
        }
    }

    private void visitReference(PsiReferenceExpression expression, ProblemsHolder holder) {
        String text = expression.getText();
        if (this.kind == Kind.TRAILING_SLASH_MATCHING && "setUseTrailingSlashMatch".equals(expression.getReferenceName())) {
            holder.registerProblem(expression, "setUseTrailingSlashMatch is removed; declare explicit slash routes or use a filter",
                    new DeleteCallStatementFix());
        }
        if (this.kind == Kind.HTTP_TRACE_REPOSITORY && REMOVED_CLASS_RENAMES.containsKey(text)) {
            holder.registerProblem(expression, "HTTP trace API was renamed to HTTP exchanges in Spring Boot 3",
                    new ReplaceTextFix("Replace with HTTP exchange API", text, REMOVED_CLASS_RENAMES.get(text)));
        }
    }

    private void visitCodeReference(PsiJavaCodeReferenceElement reference, ProblemsHolder holder) {
        String text = reference.getText();
        if (this.kind == Kind.JAVAX_FQN && shouldSkipJavaxFqnReference(reference)) {
            return;
        }
        if (this.kind == Kind.JAVAX_FQN && isJakartaJavaxName(text)) {
            holder.registerProblem(reference, JAKARTA_MESSAGE, new ReplaceTextFix("Replace with jakarta.* reference",
                    text, toJakarta(text)));
        }
        if (this.kind == Kind.YAML_JSON_PARSER && "YamlJsonParser".equals(text)) {
            holder.registerProblem(reference, "YamlJsonParser was removed in Spring Boot 3");
        }
        if (this.kind == Kind.WEB_MVC_METRICS_FILTER && isOldMetricsShortName(text)) {
            holder.registerProblem(reference, "Spring Boot 2 web metrics instrumentation API was removed in Spring Boot 3");
        }
        if (this.kind == Kind.APACHE_HTTP_CLIENT && !shouldSkipJavaxFqnReference(reference)
                && text.startsWith("org.apache.http.")) {
            holder.registerProblem(reference, "Apache HttpClient 4 is no longer auto-detected by Spring Boot 3");
        }
    }

    private void visitLiteral(PsiLiteralExpression expression, ProblemsHolder holder) {
        Object value = expression.getValue();
        if (!(value instanceof String text)) {
            return;
        }
        if (this.kind == Kind.JAVAX_FQN && isJakartaJavaxName(text)) {
            holder.registerProblem(expression, JAKARTA_MESSAGE, new ReplaceTextFix("Replace with jakarta.* string", text,
                    toJakarta(text)));
        }
        String replacement = REMOVED_CLASS_RENAMES.get(text);
        if (this.kind == Kind.HTTP_TRACE_REPOSITORY && replacement != null) {
            holder.registerProblem(expression, "HTTP trace API was renamed to HTTP exchanges in Spring Boot 3",
                    new ReplaceTextFix("Replace with HTTP exchange API", text, replacement));
        }
    }

    private void visitClassDeclaration(PsiClass psiClass, ProblemsHolder holder) {
        PsiModifierList modifiers = psiClass.getModifierList();
        if (this.kind == Kind.CONSTRUCTOR_BINDING_TYPE && SpringInspectionUtil.hasAnnotation(modifiers,
                CONFIGURATION_PROPERTIES, "ConfigurationProperties")) {
            PsiAnnotation annotation = SpringInspectionUtil.findAnnotation(modifiers, CONSTRUCTOR_BINDING,
                    "ConstructorBinding");
            if (annotation != null && psiClass.getConstructors().length <= 1) {
                holder.registerProblem(annotationTarget(annotation),
                        "@ConstructorBinding on a configuration-properties type is no longer needed",
                        new DeleteElementFix("Remove @ConstructorBinding"));
            }
        }
        if (this.kind == Kind.ENABLE_BATCH_PROCESSING) {
            PsiAnnotation annotation = SpringInspectionUtil.findAnnotation(modifiers,
                    "org.springframework.batch.core.configuration.annotation.EnableBatchProcessing", "EnableBatchProcessing");
            if (annotation != null) {
                holder.registerProblem(annotationTarget(annotation),
                        "@EnableBatchProcessing makes Spring Boot 3 Batch auto-configuration back off",
                        new DeleteElementFix("Remove @EnableBatchProcessing"));
            }
        }
        if (this.kind == Kind.SECURITY_CONFIGURATION) {
            PsiAnnotation annotation = findEnableSecurityAnnotation(modifiers);
            if (annotation != null && !SpringInspectionUtil.hasAnnotation(modifiers,
                    "org.springframework.context.annotation.Configuration", "Configuration")) {
                holder.registerProblem(annotationTarget(annotation),
                        "@Enable*Security no longer implies @Configuration in Spring Security 6",
                        new AddAnnotationToClassFix("Add @Configuration", "Configuration"));
            }
        }
        if (this.kind == Kind.WEB_SECURITY_CONFIGURER_ADAPTER && extendsWebSecurityConfigurerAdapter(psiClass)) {
            holder.registerProblem(psiClass.getNameIdentifier() == null ? psiClass : psiClass.getNameIdentifier(),
                    "WebSecurityConfigurerAdapter was removed in Spring Security 6");
        }
    }

    private void visitMethodDeclaration(PsiMethod method, ProblemsHolder holder) {
        PsiModifierList modifiers = method.getModifierList();
        if (this.kind == Kind.JVM_INFO_METRICS) {
            if (!SpringInspectionUtil.hasAnnotation(modifiers, "Bean")) {
                return;
            }
            String returnType = method.getReturnTypeElement() == null ? null : method.getReturnTypeElement().getText();
            if (method.getReturnType() != null
                    && ("io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics".equals(method.getReturnType().getCanonicalText())
                            || "io.micrometer.core.instrument.binder.jvm.JvmInfoMetrics".equals(returnType)
                            || "JvmInfoMetrics".equals(returnType))) {
                holder.registerProblem(method.getNameIdentifier() == null ? method : method.getNameIdentifier(),
                        "JvmInfoMetrics is auto-configured in Spring Boot 3");
            }
        }
        if (this.kind == Kind.PRECISE_BEAN_TYPE && SpringInspectionUtil.hasAnnotation(modifiers,
                "org.springframework.context.annotation.Bean", "Bean")) {
            PsiTypeElement returnTypeElement = method.getReturnTypeElement();
            String concreteType = returnedNewType(method);
            if (returnTypeElement != null && concreteType != null && isImpreciseBeanReturnType(method.getReturnType())) {
                holder.registerProblem(returnTypeElement, "@Bean methods should return concrete types for Spring 6 AOT",
                        new ReplaceTextFix("Use concrete @Bean return type", returnTypeElement.getText(), concreteType));
            }
        }
    }

    private void visitAnnotationDeclaration(PsiAnnotation annotation, ProblemsHolder holder) {
        if (this.kind == Kind.TRAILING_SLASH_MATCHING && isMappingAnnotation(annotation)) {
            for (PsiNameValuePair attribute : annotation.getParameterList().getAttributes()) {
                PsiElement value = attribute.getValue();
                if (value instanceof PsiLiteralExpression literal && literal.getValue() instanceof String text
                        && text.length() > 1 && text.endsWith("/")) {
                    holder.registerProblem(value, "Trailing slash route will not match slashless requests in Spring Boot 3",
                            new ReplaceTextFix("Remove trailing slash", value.getText(), quote(text.substring(0,
                                    text.length() - 1))));
                }
            }
        }
        if (this.kind == Kind.SOLR_AUTO_CONFIGURATION_EXCLUDE && isAutoConfigurationAnnotation(annotation)) {
            for (PsiNameValuePair attribute : annotation.getParameterList().getAttributes()) {
                PsiElement value = attribute.getValue();
                if ("exclude".equals(attribute.getName()) && value != null && value.getText().contains("SolrAutoConfiguration")) {
                    holder.registerProblem(value, "SolrAutoConfiguration was removed in Spring Boot 3");
                }
            }
        }
    }

    private void visitFieldDeclaration(PsiField field, ProblemsHolder holder) {
        if (this.kind != Kind.NESTED_CONFIGURATION_PROPERTIES_VALID) {
            return;
        }
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null || !SpringInspectionUtil.hasAnnotation(containingClass.getModifierList(),
                CONFIGURATION_PROPERTIES, "ConfigurationProperties")
                || !SpringInspectionUtil.hasAnnotation(containingClass.getModifierList(),
                        "org.springframework.validation.annotation.Validated", "Validated")
                || field.hasModifierProperty("static")
                || SpringInspectionUtil.hasAnnotation(field.getModifierList(), "jakarta.validation.Valid", "Valid")
                || SpringInspectionUtil.hasAnnotation(field.getModifierList(), "javax.validation.Valid", "Valid")
                || isCommonType(field.getType())) {
            return;
        }
        holder.registerProblem(field.getNameIdentifier() == null ? field : field.getNameIdentifier(),
                "Nested @ConfigurationProperties field should be annotated with @Valid",
                new AddAnnotationToFieldFix("Add @Valid", "Valid"));
    }

    private void visitMethodCall(PsiMethodCallExpression expression, ProblemsHolder holder) {
        String methodName = expression.getMethodExpression().getReferenceName();
        if (this.kind == Kind.REACTOR_CONTEXT_PROPAGATION && "enableAutomaticContextPropagation".equals(methodName)
                && expression.getMethodExpression().getText().contains("Hooks.enableAutomaticContextPropagation")) {
            holder.registerProblem(expression, "Use spring.reactor.context-propagation=auto instead of Reactor Hooks call",
                    new DeleteCallStatementFix("Remove Hooks.enableAutomaticContextPropagation call"));
        }
        if (this.kind == Kind.REST_TEMPLATE_REQUEST_FACTORY_BUILDER && "requestFactory".equals(methodName)
                && expression.getArgumentList().getExpressionCount() == 1) {
            holder.registerProblem(expression.getMethodExpression(),
                    "RestTemplateBuilder.requestFactory(Function) was replaced by requestFactoryBuilder in Spring Boot 3.4");
        }
        if (this.kind == Kind.SECURITY_MATCHERS && isRemovedSecurityDslMethod(methodName)
                && isSpringSecurityDslCall(expression)) {
            holder.registerProblem(expression.getMethodExpression(),
                    "Spring Security 6 replaced this authorization DSL method",
                    new RenameSecurityDslMethodFix(methodName, securityDslReplacement(methodName)));
        }
    }

    private static boolean isJakartaJavaxName(String name) {
        return name.startsWith("javax.servlet.") || name.startsWith("javax.persistence.")
                || name.startsWith("javax.validation.") || name.startsWith("javax.transaction.")
                || name.startsWith("javax.xml.bind.") || name.startsWith("javax.ws.rs.")
                || name.startsWith("javax.inject.") || name.startsWith("javax.enterprise.")
                || name.startsWith("javax.faces.") || name.startsWith("javax.el.")
                || name.startsWith("javax.websocket.") || name.startsWith("javax.mail.")
                || name.startsWith("javax.json.") || name.startsWith("javax.security.auth.message.")
                || name.startsWith("javax.security.enterprise.") || isMovedJavaxAnnotation(name);
    }

    private static boolean shouldSkipJavaxFqnReference(PsiJavaCodeReferenceElement reference) {
        PsiElement parent = reference.getParent();
        return parent instanceof PsiJavaCodeReferenceElement || parent instanceof PsiImportStatement;
    }

    private static boolean isMovedJavaxAnnotation(String name) {
        return "javax.annotation.Generated".equals(name) || "javax.annotation.PostConstruct".equals(name)
                || "javax.annotation.PreDestroy".equals(name) || "javax.annotation.Resource".equals(name);
    }

    private static boolean isOldMetricsClass(String name) {
        return name.startsWith("org.springframework.boot.actuate.metrics.web.servlet.")
                || name.startsWith("org.springframework.boot.actuate.metrics.web.client.");
    }

    private static boolean isOldMetricsShortName(String name) {
        return "WebMvcMetricsFilter".equals(name) || "WebMvcTagsProvider".equals(name)
                || "WebMvcTagsContributor".equals(name) || "MetricsRestTemplateCustomizer".equals(name)
                || "RestTemplateExchangeTagsProvider".equals(name) || "DefaultWebMvcTagsProvider".equals(name);
    }

    private static boolean isRemovedSecurityDslMethod(String methodName) {
        return "authorizeRequests".equals(methodName) || "antMatchers".equals(methodName)
                || "mvcMatchers".equals(methodName) || "regexMatchers".equals(methodName);
    }

    private static String securityDslReplacement(String methodName) {
        if ("authorizeRequests".equals(methodName)) {
            return "authorizeHttpRequests";
        }
        return "requestMatchers";
    }

    private static boolean isSpringSecurityDslCall(PsiMethodCallExpression expression) {
        PsiMethod target = expression.resolveMethod();
        if (target != null && isSecurityDslClass(target.getContainingClass())) {
            return true;
        }
        PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
        PsiType qualifierType = qualifier == null ? null : qualifier.getType();
        if (qualifierType == null) {
            return false;
        }
        String text = qualifierType.getCanonicalText();
        return "HttpSecurity".equals(text)
                || "org.springframework.security.config.annotation.web.builders.HttpSecurity".equals(text)
                || "org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer.ExpressionInterceptUrlRegistry"
                        .equals(text);
    }

    private static boolean isSecurityDslClass(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        String qualifiedName = psiClass.getQualifiedName();
        if ("org.springframework.security.config.annotation.web.builders.HttpSecurity".equals(qualifiedName)) {
            return true;
        }
        String name = psiClass.getName();
        return "HttpSecurity".equals(name) || "ExpressionInterceptUrlRegistry".equals(name);
    }

    private static boolean extendsWebSecurityConfigurerAdapter(PsiClass psiClass) {
        PsiReferenceList extendsList = psiClass.getExtendsList();
        if (extendsList == null) {
            return false;
        }
        for (PsiJavaCodeReferenceElement reference : extendsList.getReferenceElements()) {
            String qualifiedName = reference.getQualifiedName();
            if (isWebSecurityConfigurerAdapterName(reference.getText())
                    || isWebSecurityConfigurerAdapterName(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWebSecurityConfigurerAdapterName(String name) {
        return "WebSecurityConfigurerAdapter".equals(name)
                || "org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter"
                        .equals(name);
    }

    private static boolean isMappingAnnotation(PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return "RequestMapping".equals(name) || "GetMapping".equals(name) || "PostMapping".equals(name)
                || "PutMapping".equals(name) || "PatchMapping".equals(name) || "DeleteMapping".equals(name)
                || "org.springframework.web.bind.annotation.RequestMapping".equals(name)
                || "org.springframework.web.bind.annotation.GetMapping".equals(name)
                || "org.springframework.web.bind.annotation.PostMapping".equals(name)
                || "org.springframework.web.bind.annotation.PutMapping".equals(name)
                || "org.springframework.web.bind.annotation.PatchMapping".equals(name)
                || "org.springframework.web.bind.annotation.DeleteMapping".equals(name);
    }

    private static PsiAnnotation findEnableSecurityAnnotation(PsiModifierList modifiers) {
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotationName(annotation);
            if (name != null && (name.startsWith("org.springframework.security.config.annotation.")
                    || !name.contains(".")) && name.startsWith("Enable") && name.endsWith("Security")
                    && !"EnableRSocketSecurity".equals(name)) {
                return annotation;
            }
        }
        return null;
    }

    private static boolean isAutoConfigurationAnnotation(PsiAnnotation annotation) {
        String name = annotationName(annotation);
        return "SpringBootApplication".equals(name) || "EnableAutoConfiguration".equals(name)
                || "org.springframework.boot.autoconfigure.SpringBootApplication".equals(name)
                || "org.springframework.boot.autoconfigure.EnableAutoConfiguration".equals(name);
    }

    private static String annotationName(PsiAnnotation annotation) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf('.') + 1);
        }
        return name;
    }

    private static boolean isCommonType(PsiType type) {
        String text = type.getCanonicalText();
        return type instanceof com.intellij.psi.PsiPrimitiveType || text.startsWith("java.lang.")
                || text.startsWith("java.time.") || text.startsWith("java.util.") || text.equals("String");
    }

    private static boolean isImpreciseBeanReturnType(PsiType type) {
        if (type == null) {
            return false;
        }
        String text = type.getCanonicalText();
        return "java.lang.Object".equals(text) || text.startsWith("java.util.") || !text.contains(".");
    }

    private static String returnedNewType(PsiMethod method) {
        PsiReturnStatement[] returns = PsiTreeUtil.findChildrenOfType(method, PsiReturnStatement.class)
                .toArray(PsiReturnStatement[]::new);
        if (returns.length != 1) {
            return null;
        }
        PsiExpression value = returns[0].getReturnValue();
        if (!(value instanceof PsiNewExpression newExpression) || newExpression.getClassReference() == null) {
            return null;
        }
        return newExpression.getClassReference().getReferenceName();
    }

    private static String toJakarta(String name) {
        return name.replaceFirst("^javax\\.", "jakarta.");
    }

    private static String quote(String text) {
        return "\"" + text + "\"";
    }

    private static PsiElement annotationTarget(PsiAnnotation annotation) {
        PsiElement target = annotation.getNameReferenceElement();
        return target == null ? annotation : target;
    }

    private enum Kind {
        JAVAX_IMPORT,
        JAVAX_FQN,
        CONSTRUCTOR_BINDING_TYPE,
        YAML_JSON_PARSER,
        TRAILING_SLASH_MATCHING,
        HTTP_TRACE_REPOSITORY,
        WEB_MVC_METRICS_FILTER,
        JVM_INFO_METRICS,
        ENABLE_BATCH_PROCESSING,
        APACHE_HTTP_CLIENT,
        SECURITY_CONFIGURATION,
        NESTED_CONFIGURATION_PROPERTIES_VALID,
        PRECISE_BEAN_TYPE,
        SOLR_AUTO_CONFIGURATION_EXCLUDE,
        REACTOR_CONTEXT_PROPAGATION,
        REST_TEMPLATE_REQUEST_FACTORY_BUILDER,
        WEB_SECURITY_CONFIGURER_ADAPTER,
        SECURITY_MATCHERS
    }

    private static final class ReplaceTextFix implements LocalQuickFix {

        private final String name;
        private final String oldText;
        private final String newText;

        private ReplaceTextFix(String name, String oldText, String newText) {
            this.name = name;
            this.oldText = oldText;
            this.newText = newText;
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
            PsiElement element = descriptor.getPsiElement();
            if (element == null || !element.isValid()) {
                return;
            }
            String replaced = element.getText().replace(this.oldText, this.newText);
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiElement newElement;
            if (element instanceof PsiImportStatement) {
                PsiJavaFile file = (PsiJavaFile) PsiFileFactory.getInstance(project)
                        .createFileFromText("Import.java", JavaLanguage.INSTANCE, replaced + "\nclass ImportPlaceholder { }");
                newElement = file.getImportList() == null ? null : file.getImportList().getImportStatements()[0];
            }
            else if (element instanceof PsiJavaCodeReferenceElement) {
                newElement = factory.createReferenceFromText(replaced, element.getParent());
            }
            else {
                newElement = factory.createExpressionFromText(replaced, element.getParent());
            }
            if (newElement == null) {
                return;
            }
            element.replace(newElement);
        }
    }

    private static final class RenameSecurityDslMethodFix implements LocalQuickFix {

        private final String oldName;
        private final String newName;

        private RenameSecurityDslMethodFix(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }

        @Override
        @NotNull
        public String getName() {
            return "Replace with Spring Security 6 DSL method";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element instanceof PsiReferenceExpression reference
                    && this.oldName.equals(reference.getReferenceName())) {
                reference.handleElementRename(this.newName);
            }
        }
    }

    private static final class DeleteElementFix implements LocalQuickFix {

        private final String name;

        private DeleteElementFix(String name) {
            this.name = name;
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
            PsiElement element = descriptor.getPsiElement();
            PsiAnnotation annotation = element instanceof PsiAnnotation psiAnnotation
                    ? psiAnnotation
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(element, PsiAnnotation.class);
            if (annotation != null) {
                annotation.delete();
                return;
            }
            element.delete();
        }
    }

    private static final class DeleteCallStatementFix implements LocalQuickFix {

        private final String name;

        private DeleteCallStatementFix() {
            this("Remove setUseTrailingSlashMatch call");
        }

        private DeleteCallStatementFix(String name) {
            this.name = name;
        }

        @Override
        @NotNull
        public String getName() {
            return this.name;
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiElement statement = element;
            while (statement != null && !(statement instanceof com.intellij.psi.PsiStatement)) {
                statement = statement.getParent();
            }
            if (statement != null) {
                statement.delete();
            }
        }
    }

    private static final class AddAnnotationToClassFix implements LocalQuickFix {

        private final String name;

        private final String annotationText;

        private AddAnnotationToClassFix(String name, String annotationText) {
            this.name = name;
            this.annotationText = annotationText;
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
            PsiClass psiClass = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
            if (psiClass == null || psiClass.getModifierList() == null) {
                return;
            }
            PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText("@" + this.annotationText, psiClass);
            psiClass.getModifierList().addBefore(annotation, psiClass.getModifierList().getFirstChild());
        }
    }

    private static final class AddAnnotationToFieldFix implements LocalQuickFix {

        private final String name;

        private final String annotationText;

        private AddAnnotationToFieldFix(String name, String annotationText) {
            this.name = name;
            this.annotationText = annotationText;
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
            PsiField field = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
            if (field == null || field.getModifierList() == null) {
                return;
            }
            PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText("@" + this.annotationText, field);
            field.getModifierList().addBefore(annotation, field.getModifierList().getFirstChild());
        }
    }

    public static class JavaxImport extends SpringBoot3MigrationInspection {
        public JavaxImport() {
            super(Kind.JAVAX_IMPORT);
        }
    }

    public static class JavaxFqn extends SpringBoot3MigrationInspection {
        public JavaxFqn() {
            super(Kind.JAVAX_FQN);
        }
    }

    public static class ConstructorBindingType extends SpringBoot3MigrationInspection {
        public ConstructorBindingType() {
            super(Kind.CONSTRUCTOR_BINDING_TYPE);
        }
    }

    public static class YamlJsonParser extends SpringBoot3MigrationInspection {
        public YamlJsonParser() {
            super(Kind.YAML_JSON_PARSER);
        }
    }

    public static class TrailingSlashMatching extends SpringBoot3MigrationInspection {
        public TrailingSlashMatching() {
            super(Kind.TRAILING_SLASH_MATCHING);
        }
    }

    public static class HttpTraceRepository extends SpringBoot3MigrationInspection {
        public HttpTraceRepository() {
            super(Kind.HTTP_TRACE_REPOSITORY);
        }
    }

    public static class WebMvcMetricsFilter extends SpringBoot3MigrationInspection {
        public WebMvcMetricsFilter() {
            super(Kind.WEB_MVC_METRICS_FILTER);
        }
    }

    public static class JvmInfoMetrics extends SpringBoot3MigrationInspection {
        public JvmInfoMetrics() {
            super(Kind.JVM_INFO_METRICS);
        }
    }

    public static class EnableBatchProcessing extends SpringBoot3MigrationInspection {
        public EnableBatchProcessing() {
            super(Kind.ENABLE_BATCH_PROCESSING);
        }
    }

    public static class ApacheHttpClient extends SpringBoot3MigrationInspection {
        public ApacheHttpClient() {
            super(Kind.APACHE_HTTP_CLIENT);
        }
    }

    public static class SecurityConfiguration extends SpringBoot3MigrationInspection {
        public SecurityConfiguration() {
            super(Kind.SECURITY_CONFIGURATION);
        }
    }

    public static class NestedConfigurationPropertiesValid extends SpringBoot3MigrationInspection {
        public NestedConfigurationPropertiesValid() {
            super(Kind.NESTED_CONFIGURATION_PROPERTIES_VALID);
        }
    }

    public static class PreciseBeanType extends SpringBoot3MigrationInspection {
        public PreciseBeanType() {
            super(Kind.PRECISE_BEAN_TYPE);
        }
    }

    public static class SolrAutoConfigurationExclude extends SpringBoot3MigrationInspection {
        public SolrAutoConfigurationExclude() {
            super(Kind.SOLR_AUTO_CONFIGURATION_EXCLUDE);
        }
    }

    public static class ReactorContextPropagation extends SpringBoot3MigrationInspection {
        public ReactorContextPropagation() {
            super(Kind.REACTOR_CONTEXT_PROPAGATION);
        }
    }

    public static class RestTemplateRequestFactoryBuilder extends SpringBoot3MigrationInspection {
        public RestTemplateRequestFactoryBuilder() {
            super(Kind.REST_TEMPLATE_REQUEST_FACTORY_BUILDER);
        }
    }

    public static class WebSecurityConfigurerAdapter extends SpringBoot3MigrationInspection {
        public WebSecurityConfigurerAdapter() {
            super(Kind.WEB_SECURITY_CONFIGURER_ADAPTER);
        }
    }

    public static class SecurityMatchers extends SpringBoot3MigrationInspection {
        public SecurityMatchers() {
            super(Kind.SECURITY_MATCHERS);
        }
    }
}
