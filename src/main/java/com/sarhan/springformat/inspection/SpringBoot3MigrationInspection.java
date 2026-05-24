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
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiReferenceExpression;
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
    }

    private void visitMethodDeclaration(PsiMethod method, ProblemsHolder holder) {
        if (this.kind != Kind.JVM_INFO_METRICS) {
            return;
        }
        PsiModifierList modifiers = method.getModifierList();
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

    private void visitAnnotationDeclaration(PsiAnnotation annotation, ProblemsHolder holder) {
        if (this.kind != Kind.TRAILING_SLASH_MATCHING || !isMappingAnnotation(annotation)) {
            return;
        }
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
        APACHE_HTTP_CLIENT
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

        @Override
        @NotNull
        public String getName() {
            return "Remove setUseTrailingSlashMatch call";
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
}
