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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Hibernate 5 to 6 migration inspections.
 *
 * @author Akmal Sarhan
 */
public class SpringHibernateMigrationInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final Pattern IMPLICIT_SELECT = Pattern.compile(
            "^\\s*from\\s+\\S+\\s+(\\w+)\\b(?=.*\\b(?:left\\s+)?join\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern UPDATE_FROM = Pattern.compile("^\\s*update\\s+from\\s+",
            Pattern.CASE_INSENSITIVE);
    private static final String FETCH_JOIN_TOKEN = "\\b(?:(?:left|right|inner|outer|left\\s+outer|right\\s+outer)\\s+)?join\\s+fetch\\b";
    private static final Pattern FETCH_JOIN = Pattern.compile(
            "^\\s*select\\s+(.+?)\\s+from\\s+\\S+\\s+(\\w+)\\b(?=.*" + FETCH_JOIN_TOKEN + ")",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SELECT_DISTINCT_FETCH = Pattern.compile("\\bselect\\s+distinct\\b(?=.*" + FETCH_JOIN_TOKEN + ")",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ROWNUM = Pattern.compile("\\brownum\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROWNUM_PREDICATE = Pattern.compile(
            "(?i)\\s+(?:and\\s+)?rownum\\s*=\\s*1\\b|\\brownum\\s*=\\s*1\\s+and\\s+");
    private static final Pattern ORACLE_FUNCTION = Pattern.compile("\\b(?:nvl|add_months)\\s*\\(",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NVL_FUNCTION = Pattern.compile("\\bnvl\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern SYSDATE = Pattern.compile("\\bsysdate\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOOLEAN_COUNT = Pattern.compile(
            "^\\s*select\\s+case\\s+when\\s+count\\([^)]*\\)\\s*>\\s*0\\s+then\\s+true\\s+else\\s+false\\s+end\\s+from\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TRUNC_FUNCTION = Pattern.compile(
            "(?i)function\\(\\s*'trunc'\\s*,\\s*([^()]+?)\\s*\\)");
    private static final Pattern BARE_ORACLE_FUNCTION = Pattern.compile(
            "\\b(?:trunc|to_char|add_months|add_to_date)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEMPORAL_SUBTRACTION = Pattern.compile(
            "(?i)trunc\\(\\s*([^()]+?)\\s*\\)\\s*-\\s*trunc\\(\\s*([^()]+?)\\s*\\)\\s*<=\\s*(\\d+)\\b");
    private static final Pattern CAST_IN_ON_CLAUSE = Pattern.compile(
            "(?i)\\bon\\b[^\\n\\r]*\\b(?:to_char|cast|function)\\s*\\(");
    private static final Pattern TO_CHAR_FUNCTION_COMPARE = Pattern.compile(
            "(?i)\\(?\\s*function\\(\\s*'to_char'\\s*,\\s*([^,()]+?)\\s*,\\s*'[^']+'\\s*\\)\\s*\\)?\\s*([<>=])\\s*(:\\w+)");
    private static final Pattern TO_CHAR_FUNCTION = Pattern.compile("\\bto_char\\s*\\(([^()]+)\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONCAT_FUNCTION = Pattern.compile("\\bconcat\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_TO_DATE = Pattern.compile(
            "(?i)add_to_date\\s*\\(\\s*(?:trunc\\s*\\(\\s*)?([^(),]+?)\\s*\\)?\\s*,\\s*(\\d+)\\s*\\)");
    private static final Pattern FUNCTION_PARAMETER = Pattern.compile(
            "(?i)function\\(\\s*'[^']+'\\s*,\\s*(?::\\w+|\\?\\d+)\\b");

    private final Kind kind;

    protected SpringHibernateMigrationInspection(@NotNull Kind kind) {
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
            public void visitAnnotation(@NotNull PsiAnnotation annotation) {
                super.visitAnnotation(annotation);
                SpringHibernateMigrationInspection.this.visitHibernateAnnotation(annotation, holder);
            }

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                SpringHibernateMigrationInspection.this.visitMethodCall(expression, holder);
            }

            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                SpringHibernateMigrationInspection.this.visitMethodDeclaration(method, holder);
            }

            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                super.visitClass(psiClass);
                SpringHibernateMigrationInspection.this.visitClassDeclaration(psiClass, holder);
            }

            @Override
            public void visitField(@NotNull PsiField field) {
                super.visitField(field);
                SpringHibernateMigrationInspection.this.visitFieldDeclaration(field, holder);
            }
        };
    }

    private void visitHibernateAnnotation(@NotNull PsiAnnotation annotation, @NotNull ProblemsHolder holder) {
        if (this.kind == Kind.IMPLICIT_SELECT || this.kind == Kind.BARE_POSITIONAL_PARAMETERS
                || this.kind == Kind.FETCH_JOIN_PROJECTION || this.kind == Kind.UPDATE_FROM
                || this.kind == Kind.DISTINCT_FETCH || this.kind == Kind.ROWNUM_PSEUDO_COLUMN
                || this.kind == Kind.ORACLE_FUNCTIONS || this.kind == Kind.SYSDATE_KEYWORD
                || this.kind == Kind.BOOLEAN_COUNT_EXISTS || this.kind == Kind.FUNCTION_RETURN_TYPE
                || this.kind == Kind.BARE_ORACLE_FUNCTIONS || this.kind == Kind.TEMPORAL_SUBTRACTION
                || this.kind == Kind.CAST_IN_ON_CLAUSE || this.kind == Kind.DB_DATE_FORMAT_COMPARE
                || this.kind == Kind.CONCAT_TYPE_CAST || this.kind == Kind.ADD_TO_DATE_FUNCTION
                || this.kind == Kind.FUNCTION_PARAMETER_TYPE) {
            if (isNativeQuery(annotation) && this.isJpaQueryOnly()) {
                return;
            }
            QueryValue queryValue = queryValue(annotation);
            if (queryValue == null) {
                return;
            }
            this.visitQueryLiteral(queryValue.element(), queryValue.query(), holder);
        } else if (this.kind == Kind.LEGACY_TYPE || this.kind == Kind.SPATIAL_TYPE) {
            this.visitLegacyType(annotation, holder);
        } else if (this.kind == Kind.SEQUENCE_ALLOCATION) {
            this.visitSequenceGenerator(annotation, holder);
        } else if (this.kind == Kind.HIBERNATE_CASCADE) {
            this.visitCascade(annotation, holder);
        } else if (this.kind == Kind.HIBERNATE_TABLE) {
            this.visitHibernateTable(annotation, holder);
        } else if (this.kind == Kind.EMBEDDED_SUB_PROPERTY_OVERRIDE) {
            this.visitEmbeddedSubPropertyOverride(annotation, holder);
        }
    }

    private void visitMethodCall(@NotNull PsiMethodCallExpression expression, @NotNull ProblemsHolder holder) {
        if (this.kind == Kind.UPDATE_FROM) {
            PsiLiteralExpression literal = firstStringArgument(expression, "createQuery");
            if (literal != null && literal.getValue() instanceof String query) {
                this.visitQueryLiteral(literal, query, holder);
            }
        } else if (this.kind == Kind.SESSION_LIFECYCLE) {
            this.visitSessionLifecycle(expression, holder);
        } else if (this.kind == Kind.RESULT_TRANSFORMER) {
            this.visitResultTransformer(expression, holder);
        } else if (this.kind == Kind.FUNCTION_REGISTRATION) {
            this.visitFunctionRegistration(expression, holder);
        } else if (this.kind == Kind.SCROLLABLE_RESULTS) {
            this.visitScrollableResults(expression, holder);
        } else if (this.kind == Kind.UNTYPED_CREATE_QUERY) {
            this.visitUntypedCreateQuery(expression, holder);
        } else if (this.kind == Kind.SESSION_EVENT_LISTENER) {
            this.visitSessionEventListenerRegistration(expression, holder);
        } else if (this.kind == Kind.NATIVE_COUNT_CAST) {
            this.visitNativeCountCast(expression, holder);
        } else if (this.kind == Kind.SCROLL_MODE) {
            this.visitScrollMode(expression, holder);
        }
    }

    private void visitMethodDeclaration(@NotNull PsiMethod method, @NotNull ProblemsHolder holder) {
        if (this.kind == Kind.USER_TYPE_SIGNATURE) {
            this.visitUserTypeMethod(method, holder);
        } else if (this.kind == Kind.IDENTIFIER_GENERATOR) {
            this.visitIdentifierGeneratorMethod(method, holder);
        } else if (this.kind == Kind.INTERCEPTOR_SIGNATURE) {
            this.visitInterceptorMethod(method, holder);
        }
    }

    private void visitClassDeclaration(@NotNull PsiClass psiClass, @NotNull ProblemsHolder holder) {
        if (this.kind == Kind.USER_TYPE_SIGNATURE && implementsType(psiClass, "org.hibernate.usertype.UserType")) {
            for (PsiMethod method : psiClass.getMethods()) {
                this.visitUserTypeMethod(method, holder);
            }
        } else if (this.kind == Kind.EMBEDDABLE_REUSE) {
            this.visitEmbeddableReuse(psiClass, holder);
        } else if (this.kind == Kind.SESSION_EVENT_LISTENER) {
            this.visitSessionEventListenerClass(psiClass, holder);
        }
    }

    private void visitFieldDeclaration(@NotNull PsiField field, @NotNull ProblemsHolder holder) {
        if (this.kind == Kind.SCHEMA_TYPE_DEFAULTS) {
            this.visitSchemaTypeDefault(field, holder);
        } else if (this.kind == Kind.TEMPORAL_JAVA_TIME) {
            this.visitTemporalJavaTime(field, holder);
        } else if (this.kind == Kind.GLOBAL_SEQUENCE) {
            this.visitGlobalSequence(field, holder);
        } else if (this.kind == Kind.ARRAY_MAPPING) {
            this.visitArrayMapping(field, holder);
        } else if (this.kind == Kind.COMPOSITE_USER_TYPE) {
            this.visitCompositeUserType(field, holder);
        }
    }

    private void visitSchemaTypeDefault(@NotNull PsiField field, @NotNull ProblemsHolder holder) {
        if (hasAnnotation(field.getModifierList(), "JdbcTypeCode")) {
            return;
        }
        String canonical = field.getType().getCanonicalText();
        String code = switch (canonical) {
            case "java.time.Duration" -> "java.sql.Types.BIGINT";
            case "java.time.Instant" -> "java.sql.Types.TIMESTAMP";
            case "java.util.UUID" -> "java.sql.Types.BINARY";
            default -> null;
        };
        if (code == null) {
            return;
        }
        PsiElement target = field.getNameIdentifier() != null ? field.getNameIdentifier() : field;
        holder.registerProblem(target, "Hibernate 6 changes the default JDBC mapping for "
                + canonical + "; add @JdbcTypeCode to keep the legacy schema", ProblemHighlightType.WARNING,
                new AddJdbcTypeCodeFix(code));
    }

    private void visitQueryLiteral(@NotNull PsiElement target, @NotNull String query,
                                     @NotNull ProblemsHolder holder) {
        if (this.kind == Kind.IMPLICIT_SELECT) {
            Matcher matcher = IMPLICIT_SELECT.matcher(query);
            if (matcher.find()) {
                holder.registerProblem(target, "Hibernate 6 requires an explicit SELECT projection for joined HQL queries",
                        ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Add explicit SELECT",
                        "SELECT " + matcher.group(1) + " " + query));
            }
        } else if (this.kind == Kind.BARE_POSITIONAL_PARAMETERS && hasBareQuestionMark(query)) {
            holder.registerProblem(target, "Hibernate 6 requires numbered positional parameters like ?1",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Number positional parameters",
                    numberBareParameters(query)));
        } else if (this.kind == Kind.FETCH_JOIN_PROJECTION && invalidFetchProjection(query)) {
            holder.registerProblem(target, "Hibernate 6 requires JOIN FETCH queries to select the owning entity",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Remove FETCH from scalar projection",
                    query.replaceAll("(?i)" + FETCH_JOIN_TOKEN, "JOIN")));
        } else if (this.kind == Kind.UPDATE_FROM && UPDATE_FROM.matcher(query).find()) {
            holder.registerProblem(target, "Hibernate 6 forbids UPDATE FROM in HQL",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Remove FROM from UPDATE",
                    UPDATE_FROM.matcher(query).replaceFirst("UPDATE ")));
        } else if (this.kind == Kind.DISTINCT_FETCH && SELECT_DISTINCT_FETCH.matcher(query).find()) {
            holder.registerProblem(target, "Hibernate 6 de-duplicates JOIN FETCH results in memory; DISTINCT can slow SQL",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Remove redundant DISTINCT",
                    SELECT_DISTINCT_FETCH.matcher(query).replaceFirst("SELECT")));
        } else if (this.kind == Kind.ROWNUM_PSEUDO_COLUMN && ROWNUM.matcher(query).find()) {
            holder.registerProblem(target, "Hibernate 6 treats ROWNUM as an entity attribute in JPQL",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Use FETCH FIRST",
                    rownumToFetchFirst(query)));
        } else if (this.kind == Kind.ORACLE_FUNCTIONS && ORACLE_FUNCTION.matcher(query).find()) {
            LocalQuickFix[] fixes = NVL_FUNCTION.matcher(query).find()
                    ? new LocalQuickFix[] { new ReplaceStringLiteralFix("Replace NVL with coalesce",
                    NVL_FUNCTION.matcher(query).replaceAll("coalesce(")) } : LocalQuickFix.EMPTY_ARRAY;
            holder.registerProblem(target, "Hibernate 6 requires non-standard JPQL functions to use FUNCTION or a contributor",
                    ProblemHighlightType.WARNING, fixes);
        } else if (this.kind == Kind.SYSDATE_KEYWORD && SYSDATE.matcher(query).find()) {
            holder.registerProblem(target, "Hibernate 6 expects standard current_date/current_timestamp instead of sysdate",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Replace sysdate with current_date",
                    SYSDATE.matcher(query).replaceAll("current_date")));
        } else if (this.kind == Kind.BOOLEAN_COUNT_EXISTS && BOOLEAN_COUNT.matcher(query).find()) {
            holder.registerProblem(target, "Use EXISTS instead of COUNT for boolean existence queries",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Rewrite COUNT check to EXISTS",
                    countToExists(query)));
        } else if (this.kind == Kind.FUNCTION_RETURN_TYPE && TRUNC_FUNCTION.matcher(query).find()) {
            holder.registerProblem(target, "Hibernate 6 cannot infer FUNCTION('trunc', ...) return type reliably",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Cast trunc operands to LocalDate",
                    truncToLocalDateCast(query)));
        } else if (this.kind == Kind.BARE_ORACLE_FUNCTIONS && BARE_ORACLE_FUNCTION.matcher(query).find()) {
            holder.registerProblem(target, "Hibernate 6 requires vendor JPQL functions to use FUNCTION(...)",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Wrap Oracle functions in FUNCTION",
                    wrapBareOracleFunctions(query)));
        } else if (this.kind == Kind.TEMPORAL_SUBTRACTION && TEMPORAL_SUBTRACTION.matcher(query).find()) {
            holder.registerProblem(target, "Hibernate 6 rejects date-minus-date comparisons against numbers",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Rewrite date subtraction as interval comparison",
                    temporalSubtractionToInterval(query)));
        } else if (this.kind == Kind.CAST_IN_ON_CLAUSE && CAST_IN_ON_CLAUSE.matcher(query).find()) {
            holder.registerProblem(target, "Hibernate 6 strictly validates casts and functions inside JPQL ON clauses",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Use standard CAST in ON clause",
                    TO_CHAR_FUNCTION.matcher(query).replaceAll("cast($1 as string)")));
        } else if (this.kind == Kind.DB_DATE_FORMAT_COMPARE && TO_CHAR_FUNCTION_COMPARE.matcher(query).find()) {
            holder.registerProblem(target, "Compare native date values instead of formatting dates in the database",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Compare date field directly",
                    removeToCharDateFormatting(query)));
        } else if (this.kind == Kind.CONCAT_TYPE_CAST && concatNeedsCast(query)) {
            holder.registerProblem(target, "Hibernate 6 requires CONCAT arguments to be strings",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Cast non-string CONCAT arguments",
                    castConcatArguments(query)));
        } else if (this.kind == Kind.ADD_TO_DATE_FUNCTION && ADD_TO_DATE.matcher(query).find()) {
            holder.registerProblem(target, "Hibernate 6 does not resolve bare add_to_date in JPQL",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Use JPA interval math",
                    addToDateToInterval(query)));
        } else if (this.kind == Kind.FUNCTION_PARAMETER_TYPE && FUNCTION_PARAMETER.matcher(query).find()) {
            holder.registerProblem(target, "Native FUNCTION wrapping can hide parameter types from Hibernate 6",
                    ProblemHighlightType.WARNING, new ReplaceStringLiteralFix("Cast FUNCTION parameter",
                    castFunctionParameters(query)));
        }
    }

    private boolean isJpaQueryOnly() {
        return this.kind == Kind.ROWNUM_PSEUDO_COLUMN || this.kind == Kind.ORACLE_FUNCTIONS
                || this.kind == Kind.SYSDATE_KEYWORD || this.kind == Kind.BOOLEAN_COUNT_EXISTS
                || this.kind == Kind.FUNCTION_RETURN_TYPE || this.kind == Kind.BARE_ORACLE_FUNCTIONS
                || this.kind == Kind.TEMPORAL_SUBTRACTION || this.kind == Kind.CAST_IN_ON_CLAUSE
                || this.kind == Kind.DB_DATE_FORMAT_COMPARE || this.kind == Kind.CONCAT_TYPE_CAST
                || this.kind == Kind.ADD_TO_DATE_FUNCTION || this.kind == Kind.FUNCTION_PARAMETER_TYPE;
    }

    private void visitLegacyType(@NotNull PsiAnnotation annotation, @NotNull ProblemsHolder holder) {
        if (isAnnotation(annotation, "org.hibernate.annotations.TypeDef", "TypeDef")) {
            holder.registerProblem(annotation, "Hibernate 6 removes @TypeDef string-alias type registration",
                    ProblemHighlightType.WARNING, new DeleteAnnotationFix());
            return;
        }
        if (!isAnnotation(annotation, "org.hibernate.annotations.Type", "Type")) {
            return;
        }
        PsiNameValuePair pair = annotation.findAttributeValue("type") != null ? findPair(annotation, "type") : null;
        PsiAnnotationMemberValue value = pair != null ? pair.getValue() : null;
        if (!(value instanceof PsiLiteralExpression literal) || !(literal.getValue() instanceof String type)) {
            return;
        }
        if (this.kind == Kind.SPATIAL_TYPE && type.startsWith("org.hibernate.spatial.")) {
            holder.registerProblem(annotation, "Hibernate 6 auto-resolves spatial geometry mappings; remove legacy spatial @Type",
                    ProblemHighlightType.WARNING, new DeleteAnnotationFix("Remove spatial @Type"));
            return;
        }
        if (this.kind == Kind.SPATIAL_TYPE) {
            return;
        }
        LocalQuickFix fix = "org.hibernate.type.TextType".equals(type)
                ? new ReplaceAnnotationFix("@org.hibernate.annotations.JdbcTypeCode(java.sql.Types.LONGVARCHAR)",
                "org.hibernate.annotations.JdbcTypeCode") : null;
        holder.registerProblem(annotation, "Hibernate 6 no longer accepts string aliases in @Type(type = ...)",
                ProblemHighlightType.WARNING, fix == null ? LocalQuickFix.EMPTY_ARRAY : new LocalQuickFix[] {fix});
    }

    private void visitSequenceGenerator(@NotNull PsiAnnotation annotation, @NotNull ProblemsHolder holder) {
        if (!isAnnotation(annotation, "javax.persistence.SequenceGenerator", "SequenceGenerator")
                && !isAnnotation(annotation, "jakarta.persistence.SequenceGenerator", "SequenceGenerator")) {
            return;
        }
        if (findPair(annotation, "allocationSize") != null) {
            return;
        }
        holder.registerProblem(annotation, "Hibernate 6 sequence optimizers need an explicit allocationSize",
                ProblemHighlightType.WARNING, new AddAnnotationAttributeFix("allocationSize", "1"));
    }

    private void visitCascade(@NotNull PsiAnnotation annotation, @NotNull ProblemsHolder holder) {
        if (!isAnnotation(annotation, "org.hibernate.annotations.Cascade", "Cascade")) {
            return;
        }
        String text = annotation.getText();
        if (!text.contains("CascadeType.SAVE_UPDATE") && !text.contains("CascadeType.DELETE")) {
            return;
        }
        holder.registerProblem(annotation, "Hibernate proprietary CascadeType.SAVE_UPDATE/DELETE should be migrated",
                ProblemHighlightType.WARNING);
    }

    private void visitHibernateTable(@NotNull PsiAnnotation annotation, @NotNull ProblemsHolder holder) {
        if (isAnnotation(annotation, "org.hibernate.annotations.Table", "Table")) {
            holder.registerProblem(annotation, "Hibernate 6 deprecates proprietary @org.hibernate.annotations.Table",
                    ProblemHighlightType.WARNING);
        }
    }

    private void visitUserTypeMethod(@NotNull PsiMethod method, @NotNull ProblemsHolder holder) {
        if (!"nullSafeGet".equals(method.getName()) && !"nullSafeSet".equals(method.getName())) {
            return;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || !implementsType(containingClass, "org.hibernate.usertype.UserType")) {
            return;
        }
        int count = method.getParameterList().getParametersCount();
        if (("nullSafeGet".equals(method.getName()) && count == 4)
                || ("nullSafeSet".equals(method.getName()) && count == 4)) {
            holder.registerProblem(method.getNameIdentifier(),
                    "Hibernate 6 changes UserType method signatures; update this override",
                    ProblemHighlightType.WARNING);
        }
    }

    private void visitIdentifierGeneratorMethod(@NotNull PsiMethod method, @NotNull ProblemsHolder holder) {
        if (!"generate".equals(method.getName()) || method.getParameterList().getParametersCount() != 2) {
            return;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || !implementsType(containingClass, "org.hibernate.id.IdentifierGenerator")) {
            return;
        }
        PsiType returnType = method.getReturnType();
        if (returnType != null && "java.io.Serializable".equals(returnType.getCanonicalText())) {
            holder.registerProblem(method.getReturnTypeElement(),
                    "Hibernate 6 IdentifierGenerator.generate returns Object, not Serializable",
                    ProblemHighlightType.WARNING, new ReplaceReturnTypeFix("Object"));
        }
    }

    private void visitSessionLifecycle(@NotNull PsiMethodCallExpression expression, @NotNull ProblemsHolder holder) {
        String name = expression.getMethodExpression().getReferenceName();
        if (!"save".equals(name) && !"update".equals(name) && !"saveOrUpdate".equals(name)) {
            return;
        }
        PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
        if (qualifier == null || qualifier.getType() == null
                || !"org.hibernate.Session".equals(qualifier.getType().getCanonicalText())) {
            return;
        }
        String replacement = "save".equals(name) ? "persist" : "merge";
        PsiElement target = expression.getMethodExpression().getReferenceNameElement();
        holder.registerProblem(target, "Hibernate Session." + name + "() is deprecated; use " + replacement + "()",
                ProblemHighlightType.WARNING, new RenameMethodFix(replacement));
    }

    private void visitResultTransformer(@NotNull PsiMethodCallExpression expression, @NotNull ProblemsHolder holder) {
        if (!"setResultTransformer".equals(expression.getMethodExpression().getReferenceName())) {
            return;
        }
        PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
        if (!isHibernateQueryType(qualifier)) {
            return;
        }
        PsiElement target = expression.getMethodExpression().getReferenceNameElement();
        holder.registerProblem(target, "Hibernate 6 removed ResultTransformer; use TupleTransformer/ResultListTransformer",
                ProblemHighlightType.WARNING, new RenameMethodFix("setTupleTransformer"));
    }

    private void visitFunctionRegistration(@NotNull PsiMethodCallExpression expression, @NotNull ProblemsHolder holder) {
        if (!"registerFunction".equals(expression.getMethodExpression().getReferenceName())) {
            return;
        }
        PsiClass containingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
        if (containingClass == null || !extendsHibernateDialect(containingClass)) {
            return;
        }
        PsiElement target = expression.getMethodExpression().getReferenceNameElement();
        holder.registerProblem(target, "Hibernate 6 removed Dialect.registerFunction; use FunctionContributor",
                ProblemHighlightType.WARNING);
    }

    private void visitScrollableResults(@NotNull PsiMethodCallExpression expression, @NotNull ProblemsHolder holder) {
        if (!"get".equals(expression.getMethodExpression().getReferenceName())) {
            return;
        }
        PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
        if (qualifier == null || qualifier.getType() == null
                || !"org.hibernate.ScrollableResults".equals(qualifier.getType().getCanonicalText())) {
            return;
        }
        PsiElement target = expression.getMethodExpression().getReferenceNameElement();
        holder.registerProblem(target, "Hibernate 6 changed ScrollableResults access patterns; prefer typed streams",
                ProblemHighlightType.WARNING);
    }

    private void visitUntypedCreateQuery(@NotNull PsiMethodCallExpression expression, @NotNull ProblemsHolder holder) {
        if (!"createQuery".equals(expression.getMethodExpression().getReferenceName())) {
            return;
        }
        PsiExpression[] arguments = expression.getArgumentList().getExpressions();
        if (arguments.length != 1 || !(arguments[0] instanceof PsiLiteralExpression literal)
                || !(literal.getValue() instanceof String)) {
            return;
        }
        PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
        if (qualifier == null || qualifier.getType() == null) {
            return;
        }
        String canonical = qualifier.getType().getCanonicalText();
        if (!"org.hibernate.Session".equals(canonical) && !"jakarta.persistence.EntityManager".equals(canonical)
                && !"javax.persistence.EntityManager".equals(canonical)) {
            return;
        }
        PsiElement target = expression.getMethodExpression().getReferenceNameElement();
        holder.registerProblem(target, "Hibernate 6 deprecates untyped createQuery(String); pass a result class",
                ProblemHighlightType.WARNING);
    }

    private void visitTemporalJavaTime(@NotNull PsiField field, @NotNull ProblemsHolder holder) {
        PsiAnnotation temporal = findAnnotation(field.getModifierList(), "Temporal");
        if (temporal == null || !isModernJavaTime(field.getType().getCanonicalText())) {
            return;
        }
        holder.registerProblem(temporal, "Hibernate 6 rejects @Temporal on java.time fields",
                ProblemHighlightType.WARNING, new DeleteAnnotationFix("Remove @Temporal"));
    }

    private void visitGlobalSequence(@NotNull PsiField field, @NotNull ProblemsHolder holder) {
        PsiAnnotation generatedValue = findAnnotation(field.getModifierList(), "GeneratedValue");
        if (generatedValue == null || !annotationTextContains(generatedValue, "GenerationType.SEQUENCE")) {
            return;
        }
        if (findPair(generatedValue, "generator") != null || hasAnnotation(field.getModifierList(), "SequenceGenerator")) {
            return;
        }
        PsiClass containingClass = field.getContainingClass();
        if (containingClass != null && hasAnnotation(containingClass.getModifierList(), "SequenceGenerator")) {
            return;
        }
        holder.registerProblem(generatedValue, "Hibernate 6 no longer uses the global hibernate_sequence fallback by default",
                ProblemHighlightType.WARNING);
    }

    private void visitArrayMapping(@NotNull PsiField field, @NotNull ProblemsHolder holder) {
        if (!(field.getType() instanceof PsiArrayType) || hasAnnotation(field.getModifierList(), "JdbcTypeCode")) {
            return;
        }
        PsiElement target = field.getNameIdentifier() != null ? field.getNameIdentifier() : field;
        holder.registerProblem(target, "Hibernate 6 changes default array storage; pin legacy VARBINARY mapping if needed",
                ProblemHighlightType.WARNING);
    }

    private void visitCompositeUserType(@NotNull PsiField field, @NotNull ProblemsHolder holder) {
        PsiAnnotation type = findAnnotation(field.getModifierList(), "Type");
        if (type == null || hasAnnotation(field.getModifierList(), "AttributeOverride")
                || hasAnnotation(field.getModifierList(), "AttributeOverrides")) {
            return;
        }
        String text = type.getText();
        if (!text.contains("CompositeUserType") && !text.contains("MonetaryAmountType")) {
            return;
        }
        holder.registerProblem(type, "Hibernate 6 treats CompositeUserType as embeddable; use @AttributeOverride on the field",
                ProblemHighlightType.WARNING);
    }

    private void visitSessionEventListenerRegistration(@NotNull PsiMethodCallExpression expression,
                                                         @NotNull ProblemsHolder holder) {
        if (!"addListener".equals(expression.getMethodExpression().getReferenceName())) {
            return;
        }
        String text = expression.getText();
        if (!text.contains("getEventListenerManager")) {
            return;
        }
        PsiElement target = expression.getMethodExpression().getReferenceNameElement();
        holder.registerProblem(target, "Hibernate 6 moved session event listener registration to SessionBuilder",
                ProblemHighlightType.WARNING);
    }

    private void visitSessionEventListenerClass(@NotNull PsiClass psiClass, @NotNull ProblemsHolder holder) {
        PsiReferenceList extendsList = psiClass.getExtendsList();
        if (extendsList == null || !extendsList.getText().contains("BaseSessionEventListener")) {
            return;
        }
        PsiElement target = psiClass.getNameIdentifier() != null ? psiClass.getNameIdentifier() : psiClass;
        holder.registerProblem(target, "Hibernate 6 replaces BaseSessionEventListener with SessionEventListener",
                ProblemHighlightType.WARNING);
    }

    private void visitNativeCountCast(@NotNull PsiMethodCallExpression expression, @NotNull ProblemsHolder holder) {
        if (!"getSingleResult".equals(expression.getMethodExpression().getReferenceName())) {
            return;
        }
        PsiTypeCastExpression cast = PsiTreeUtil.getParentOfType(expression, PsiTypeCastExpression.class);
        if (cast == null || cast.getCastType() == null
                || (!"java.math.BigInteger".equals(cast.getCastType().getType().getCanonicalText())
                && !"BigInteger".equals(cast.getCastType().getText()))) {
            return;
        }
        PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
        if (!(qualifier instanceof PsiMethodCallExpression createNativeQuery)
                || !expressionTextContainsCount(createNativeQuery)) {
            return;
        }
        holder.registerProblem(cast.getCastType(), "Hibernate 6 native COUNT results are Long; BigInteger casts can fail",
                ProblemHighlightType.WARNING);
    }

    private void visitInterceptorMethod(@NotNull PsiMethod method, @NotNull ProblemsHolder holder) {
        String name = method.getName();
        if (!"onSave".equals(name) && !"onFlushDirty".equals(name) && !"onDelete".equals(name)) {
            return;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || !extendsEmptyInterceptor(containingClass)) {
            return;
        }
        if (method.getText().contains("org.hibernate.type.Type[]") || method.getText().contains("Type[]")) {
            holder.registerProblem(method.getNameIdentifier(),
                    "Hibernate 6 changed Interceptor method signatures; update this override",
                    ProblemHighlightType.WARNING);
        }
    }

    private void visitScrollMode(@NotNull PsiMethodCallExpression expression, @NotNull ProblemsHolder holder) {
        PsiExpression[] arguments = expression.getArgumentList().getExpressions();
        for (PsiExpression argument : arguments) {
            String text = argument.getText();
            if (text.contains("ScrollMode.SCROLL_SENSITIVE")) {
                holder.registerProblem(argument, "Hibernate 6 deprecates SCROLL_SENSITIVE; prefer FORWARD_ONLY or stream()",
                        ProblemHighlightType.WARNING,
                        new ReplaceExpressionFix("Use ScrollMode.FORWARD_ONLY", "ScrollMode.FORWARD_ONLY"));
            } else if (text.contains("ScrollMode.FORWARD_ONLY")) {
                holder.registerProblem(argument, "Hibernate 6 favors Query.stream() for forward-only streaming queries",
                        ProblemHighlightType.WARNING);
            }
        }
    }

    private void visitEmbeddedSubPropertyOverride(@NotNull PsiAnnotation annotation, @NotNull ProblemsHolder holder) {
        if (!isAnnotation(annotation, "", "AttributeOverride")) {
            return;
        }
        PsiNameValuePair pair = findPair(annotation, "name");
        PsiAnnotationMemberValue value = pair != null ? pair.getValue() : null;
        if (!(value instanceof PsiLiteralExpression literal) || !(literal.getValue() instanceof String name)) {
            return;
        }
        if (name.contains(".")) {
            return;
        }
        PsiField field = PsiTreeUtil.getParentOfType(annotation, PsiField.class);
        PsiClass embeddedClass = field != null && field.getType() instanceof com.intellij.psi.PsiClassType classType
                ? classType.resolve() : null;
        if (embeddedClass == null || !hasEmbeddedField(embeddedClass)) {
            return;
        }
        holder.registerProblem(literal, "Hibernate 6 requires explicit dot paths for nested embedded property overrides",
                ProblemHighlightType.WARNING);
    }

    private void visitEmbeddableReuse(@NotNull PsiClass psiClass, @NotNull ProblemsHolder holder) {
        PsiField[] fields = psiClass.getFields();
        for (int i = 0; i < fields.length; i++) {
            if (!isEmbeddedField(fields[i])) {
                continue;
            }
            String type = fields[i].getType().getCanonicalText();
            int overrides = attributeOverrideCount(fields[i]);
            for (int j = i + 1; j < fields.length; j++) {
                if (isEmbeddedField(fields[j]) && type.equals(fields[j].getType().getCanonicalText())
                        && overrides != attributeOverrideCount(fields[j])) {
                    holder.registerProblem(psiClass.getNameIdentifier() != null ? psiClass.getNameIdentifier() : psiClass,
                            "Hibernate 6 requires structurally distinct embeddable mappings to use distinct classes",
                            ProblemHighlightType.WARNING);
                    return;
                }
            }
        }
    }

    @Nullable
    private static QueryValue queryValue(@NotNull PsiAnnotation annotation) {
        if (!isAnnotation(annotation, "org.springframework.data.jpa.repository.Query", "Query")) {
            return null;
        }
        PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
        if (value == null) {
            return null;
        }
        String query = constantString(annotation, value);
        return query != null ? new QueryValue(value, query) : null;
    }

    private record QueryValue(@NotNull PsiElement element, @NotNull String query) { }

    @Nullable
    private static String constantString(@NotNull PsiAnnotation annotation, @NotNull PsiAnnotationMemberValue value) {
        if (value instanceof PsiExpression expression) {
            Object constant = JavaPsiFacade.getInstance(annotation.getProject()).getConstantEvaluationHelper()
                    .computeConstantExpression(expression);
            if (constant instanceof String query) {
                return query;
            }
        }
        String text = value.getText();
        if (!text.startsWith("\"\"\"") || !text.endsWith("\"\"\"")) {
            return null;
        }
        return text.substring(3, text.length() - 3);
    }

    private static boolean isNativeQuery(@NotNull PsiAnnotation annotation) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue("nativeQuery");
        return value != null && "true".equals(value.getText());
    }

    @Nullable
    private static PsiLiteralExpression firstStringArgument(@NotNull PsiMethodCallExpression expression,
                                                             @NotNull String methodName) {
        if (!methodName.equals(expression.getMethodExpression().getReferenceName())) {
            return null;
        }
        PsiExpressionList list = expression.getArgumentList();
        PsiExpression[] expressions = list.getExpressions();
        if (expressions.length == 0 || !(expressions[0] instanceof PsiLiteralExpression literal)
                || !(literal.getValue() instanceof String)) {
            return null;
        }
        return literal;
    }

    private static boolean hasBareQuestionMark(@NotNull String query) {
        for (int i = 0; i < query.length(); i++) {
            if (query.charAt(i) == '?' && (i + 1 == query.length() || !Character.isDigit(query.charAt(i + 1)))) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    private static String numberBareParameters(@NotNull String query) {
        StringBuilder out = new StringBuilder();
        int index = 1;
        for (int i = 0; i < query.length(); i++) {
            char ch = query.charAt(i);
            out.append(ch);
            if (ch == '?' && (i + 1 == query.length() || !Character.isDigit(query.charAt(i + 1)))) {
                out.append(index++);
            }
        }
        return out.toString();
    }

    private static boolean invalidFetchProjection(@NotNull String query) {
        Matcher matcher = FETCH_JOIN.matcher(query);
        if (!matcher.find()) {
            return false;
        }
        String projection = matcher.group(1).trim().replaceFirst("(?i)^distinct\\s+", "");
        String rootAlias = matcher.group(2);
        return !projection.equals(rootAlias) && !projection.startsWith(rootAlias + ",")
                && !projection.endsWith(", " + rootAlias) && !projection.contains(", " + rootAlias + ",");
    }

    @NotNull
    private static String rownumToFetchFirst(@NotNull String query) {
        String fixed = ROWNUM_PREDICATE.matcher(query).replaceAll(" ");
        fixed = fixed.replaceAll("\\s+", " ").trim();
        if (!fixed.matches("(?is).*\\bfetch\\s+first\\b.*")) {
            fixed = fixed + " FETCH FIRST 1 ROWS ONLY";
        }
        return fixed;
    }

    @NotNull
    private static String countToExists(@NotNull String query) {
        Matcher matcher = BOOLEAN_COUNT.matcher(query);
        if (!matcher.find()) {
            return query;
        }
        return "select case when exists(select 1 from " + matcher.group(1).trim()
                + ") then true else false end";
    }

    @NotNull
    private static String truncToLocalDateCast(@NotNull String query) {
        Matcher matcher = TRUNC_FUNCTION.matcher(query);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement("cast(" + matcher.group(1).trim() + " as LocalDate)"));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    @NotNull
    private static String wrapBareOracleFunctions(@NotNull String query) {
        StringBuilder out = new StringBuilder(query);
        Matcher matcher = BARE_ORACLE_FUNCTION.matcher(query);
        int offset = 0;
        while (matcher.find()) {
            int nameStart = matcher.start();
            if (isWrappedByFunction(query, nameStart)) {
                continue;
            }
            int open = matcher.end() - 1;
            int close = matchingParen(query, open);
            if (close < 0) {
                continue;
            }
            String name = query.substring(nameStart, open).trim().toLowerCase(Locale.ROOT);
            String args = query.substring(open + 1, close);
            String replacement = "FUNCTION('" + name + "', " + args.trim() + ")";
            out.replace(nameStart + offset, close + 1 + offset, replacement);
            offset += replacement.length() - (close + 1 - nameStart);
        }
        return out.toString();
    }

    private static boolean isWrappedByFunction(@NotNull String query, int nameStart) {
        String prefix = query.substring(0, nameStart).toLowerCase(Locale.ROOT);
        return prefix.matches("(?s).*function\\(\\s*'$");
    }

    private static int matchingParen(@NotNull String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    @NotNull
    private static String temporalSubtractionToInterval(@NotNull String query) {
        Matcher matcher = TEMPORAL_SUBTRACTION.matcher(query);
        if (!matcher.find()) {
            return query;
        }
        String replacement = matcher.group(1).trim() + " <= " + matcher.group(2).trim()
                + " + " + matcher.group(3) + " day";
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    @NotNull
    private static String removeToCharDateFormatting(@NotNull String query) {
        Matcher matcher = TO_CHAR_FUNCTION_COMPARE.matcher(query);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1).trim()
                    + " " + matcher.group(2) + " " + matcher.group(3)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static boolean concatNeedsCast(@NotNull String query) {
        Matcher matcher = CONCAT_FUNCTION.matcher(query);
        while (matcher.find()) {
            int close = matchingParen(query, matcher.end() - 1);
            if (close < 0) {
                continue;
            }
            for (String arg : splitArguments(query.substring(matcher.end(), close))) {
                if (isLikelyNonStringPath(arg.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    @NotNull
    private static String castConcatArguments(@NotNull String query) {
        StringBuilder out = new StringBuilder(query);
        Matcher matcher = CONCAT_FUNCTION.matcher(query);
        int offset = 0;
        while (matcher.find()) {
            int open = matcher.end() - 1;
            int close = matchingParen(query, open);
            if (close < 0) {
                continue;
            }
            String args = query.substring(open + 1, close);
            String replacement = splitArguments(args).stream()
                    .map(String::trim)
                    .map((String arg) -> isLikelyNonStringPath(arg) ? "cast(" + arg + " as string)" : arg)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(args);
            out.replace(open + 1 + offset, close + offset, replacement);
            offset += replacement.length() - args.length();
        }
        return out.toString();
    }

    @NotNull
    private static java.util.List<String> splitArguments(@NotNull String args) {
        java.util.List<String> result = new java.util.ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < args.length(); i++) {
            char ch = args.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                result.add(args.substring(start, i));
                start = i + 1;
            }
        }
        result.add(args.substring(start));
        return result;
    }

    private static boolean isLikelyNonStringPath(@NotNull String arg) {
        if (arg.startsWith("'") || arg.startsWith("\"") || arg.startsWith(":") || arg.startsWith("?")
                || arg.toLowerCase(Locale.ROOT).startsWith("cast(")) {
            return false;
        }
        String lower = arg.toLowerCase(Locale.ROOT);
        return arg.matches("\\w+(?:\\.\\w+)+")
                && (lower.endsWith("id") || lower.endsWith("number") || lower.endsWith("nr")
                || lower.endsWith("count") || lower.endsWith("date") || lower.endsWith("amount"));
    }

    @NotNull
    private static String addToDateToInterval(@NotNull String query) {
        Matcher matcher = ADD_TO_DATE.matcher(query);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement("function('date_add', cast("
                    + matcher.group(1).trim() + " as LocalDate), " + matcher.group(2) + ", day)"));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    @NotNull
    private static String castFunctionParameters(@NotNull String query) {
        return query.replaceAll("(?i)FUNCTION\\(\\s*'trunc'\\s*,\\s*((?::\\w+|\\?\\d+))\\s*\\)",
                "cast($1 as LocalDate)");
    }

    private static boolean implementsType(@NotNull PsiClass psiClass, @NotNull String fqn) {
        for (PsiType type : psiClass.getImplementsListTypes()) {
            if (fqn.equals(type.getCanonicalText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean extendsHibernateDialect(@NotNull PsiClass psiClass) {
        PsiClass superClass = psiClass.getSuperClass();
        while (superClass != null) {
            String qualifiedName = superClass.getQualifiedName();
            if (qualifiedName != null && qualifiedName.startsWith("org.hibernate.dialect.")) {
                return true;
            }
            superClass = superClass.getSuperClass();
        }
        return false;
    }

    private static boolean extendsEmptyInterceptor(@NotNull PsiClass psiClass) {
        PsiClass superClass = psiClass.getSuperClass();
        while (superClass != null) {
            String qualifiedName = superClass.getQualifiedName();
            if ("org.hibernate.EmptyInterceptor".equals(qualifiedName)) {
                return true;
            }
            superClass = superClass.getSuperClass();
        }
        return false;
    }

    private static boolean expressionTextContainsCount(@NotNull PsiMethodCallExpression expression) {
        PsiMethodCallExpression current = expression;
        while (current != null) {
            if ("createNativeQuery".equals(current.getMethodExpression().getReferenceName())) {
                PsiExpression[] args = current.getArgumentList().getExpressions();
                return args.length > 0 && args[0] instanceof PsiLiteralExpression literal
                        && literal.getValue() instanceof String query
                        && query.toLowerCase(Locale.ROOT).contains("count");
            }
            current = PsiTreeUtil.getParentOfType(current, PsiMethodCallExpression.class);
        }
        return false;
    }

    private static boolean hasEmbeddedField(@NotNull PsiClass psiClass) {
        for (PsiField field : psiClass.getFields()) {
            if (isEmbeddedField(field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHibernateQueryType(@Nullable PsiExpression qualifier) {
        if (qualifier == null || qualifier.getType() == null) {
            return false;
        }
        String canonical = qualifier.getType().getCanonicalText();
        return "org.hibernate.query.Query".equals(canonical) || "org.hibernate.query.NativeQuery".equals(canonical);
    }

    private static boolean isModernJavaTime(@NotNull String canonical) {
        return "java.time.Instant".equals(canonical)
                || "java.time.LocalDate".equals(canonical)
                || "java.time.LocalDateTime".equals(canonical)
                || "java.time.OffsetDateTime".equals(canonical)
                || "java.time.ZonedDateTime".equals(canonical);
    }

    private static boolean annotationTextContains(@NotNull PsiAnnotation annotation, @NotNull String text) {
        return annotation.getText().contains(text);
    }

    private static boolean isEmbeddedField(@NotNull PsiField field) {
        PsiModifierList modifiers = field.getModifierList();
        return hasAnnotation(modifiers, "Embedded") || hasAnnotation(modifiers, "ElementCollection");
    }

    private static int attributeOverrideCount(@NotNull PsiVariable variable) {
        PsiModifierList modifiers = variable.getModifierList();
        if (modifiers == null) {
            return 0;
        }
        int count = 0;
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (isAnnotation(annotation, "", "AttributeOverride")) {
                count++;
            } else if (isAnnotation(annotation, "", "AttributeOverrides")) {
                count += Math.max(1, annotation.getParameterList().getAttributes().length);
            }
        }
        return count;
    }

    private static boolean hasAnnotation(@Nullable PsiModifierList modifiers, @NotNull String shortName) {
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (isAnnotation(annotation, "", shortName)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static PsiAnnotation findAnnotation(@Nullable PsiModifierList modifiers, @NotNull String shortName) {
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (isAnnotation(annotation, "", shortName)) {
                return annotation;
            }
        }
        return null;
    }

    private static boolean isAnnotation(@NotNull PsiAnnotation annotation, @NotNull String fqn,
                                          @NotNull String shortName) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return shortName.equals(name) || fqn.equals(name);
    }

    @Nullable
    private static PsiNameValuePair findPair(@NotNull PsiAnnotation annotation, @NotNull String name) {
        for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
            if (name.equals(pair.getName())) {
                return pair;
            }
        }
        return null;
    }

    private enum Kind {
        IMPLICIT_SELECT,
        BARE_POSITIONAL_PARAMETERS,
        LEGACY_TYPE,
        USER_TYPE_SIGNATURE,
        FETCH_JOIN_PROJECTION,
        SEQUENCE_ALLOCATION,
        UPDATE_FROM,
        SCHEMA_TYPE_DEFAULTS,
        HIBERNATE_CASCADE,
        IDENTIFIER_GENERATOR,
        SESSION_LIFECYCLE,
        RESULT_TRANSFORMER,
        TEMPORAL_JAVA_TIME,
        DISTINCT_FETCH,
        FUNCTION_REGISTRATION,
        GLOBAL_SEQUENCE,
        SCROLLABLE_RESULTS,
        EMBEDDABLE_REUSE,
        HIBERNATE_TABLE,
        UNTYPED_CREATE_QUERY,
        ARRAY_MAPPING,
        COMPOSITE_USER_TYPE,
        SESSION_EVENT_LISTENER,
        SPATIAL_TYPE,
        NATIVE_COUNT_CAST,
        INTERCEPTOR_SIGNATURE,
        SCROLL_MODE,
        EMBEDDED_SUB_PROPERTY_OVERRIDE,
        ROWNUM_PSEUDO_COLUMN,
        ORACLE_FUNCTIONS,
        SYSDATE_KEYWORD,
        BOOLEAN_COUNT_EXISTS,
        FUNCTION_RETURN_TYPE,
        BARE_ORACLE_FUNCTIONS,
        TEMPORAL_SUBTRACTION,
        CAST_IN_ON_CLAUSE,
        DB_DATE_FORMAT_COMPARE,
        CONCAT_TYPE_CAST,
        ADD_TO_DATE_FUNCTION,
        FUNCTION_PARAMETER_TYPE
    }

    public static class ImplicitSelect extends SpringHibernateMigrationInspection {
        public ImplicitSelect() { super(Kind.IMPLICIT_SELECT); }
    }

    public static class BarePositionalParameters extends SpringHibernateMigrationInspection {
        public BarePositionalParameters() { super(Kind.BARE_POSITIONAL_PARAMETERS); }
    }

    public static class LegacyType extends SpringHibernateMigrationInspection {
        public LegacyType() { super(Kind.LEGACY_TYPE); }
    }

    public static class UserTypeSignature extends SpringHibernateMigrationInspection {
        public UserTypeSignature() { super(Kind.USER_TYPE_SIGNATURE); }
    }

    public static class FetchJoinProjection extends SpringHibernateMigrationInspection {
        public FetchJoinProjection() { super(Kind.FETCH_JOIN_PROJECTION); }
    }

    public static class SequenceAllocation extends SpringHibernateMigrationInspection {
        public SequenceAllocation() { super(Kind.SEQUENCE_ALLOCATION); }
    }

    public static class UpdateFrom extends SpringHibernateMigrationInspection {
        public UpdateFrom() { super(Kind.UPDATE_FROM); }
    }

    public static class SchemaTypeDefaults extends SpringHibernateMigrationInspection {
        public SchemaTypeDefaults() { super(Kind.SCHEMA_TYPE_DEFAULTS); }
    }

    public static class HibernateCascade extends SpringHibernateMigrationInspection {
        public HibernateCascade() { super(Kind.HIBERNATE_CASCADE); }
    }

    public static class IdentifierGenerator extends SpringHibernateMigrationInspection {
        public IdentifierGenerator() { super(Kind.IDENTIFIER_GENERATOR); }
    }

    public static class SessionLifecycle extends SpringHibernateMigrationInspection {
        public SessionLifecycle() { super(Kind.SESSION_LIFECYCLE); }
    }

    public static class ResultTransformer extends SpringHibernateMigrationInspection {
        public ResultTransformer() { super(Kind.RESULT_TRANSFORMER); }
    }

    public static class TemporalJavaTime extends SpringHibernateMigrationInspection {
        public TemporalJavaTime() { super(Kind.TEMPORAL_JAVA_TIME); }
    }

    public static class DistinctFetch extends SpringHibernateMigrationInspection {
        public DistinctFetch() { super(Kind.DISTINCT_FETCH); }
    }

    public static class FunctionRegistration extends SpringHibernateMigrationInspection {
        public FunctionRegistration() { super(Kind.FUNCTION_REGISTRATION); }
    }

    public static class GlobalSequence extends SpringHibernateMigrationInspection {
        public GlobalSequence() { super(Kind.GLOBAL_SEQUENCE); }
    }

    public static class ScrollableResultsAccess extends SpringHibernateMigrationInspection {
        public ScrollableResultsAccess() { super(Kind.SCROLLABLE_RESULTS); }
    }

    public static class EmbeddableReuse extends SpringHibernateMigrationInspection {
        public EmbeddableReuse() { super(Kind.EMBEDDABLE_REUSE); }
    }

    public static class HibernateTable extends SpringHibernateMigrationInspection {
        public HibernateTable() { super(Kind.HIBERNATE_TABLE); }
    }

    public static class UntypedCreateQuery extends SpringHibernateMigrationInspection {
        public UntypedCreateQuery() { super(Kind.UNTYPED_CREATE_QUERY); }
    }

    public static class ArrayMapping extends SpringHibernateMigrationInspection {
        public ArrayMapping() { super(Kind.ARRAY_MAPPING); }
    }

    public static class CompositeUserType extends SpringHibernateMigrationInspection {
        public CompositeUserType() { super(Kind.COMPOSITE_USER_TYPE); }
    }

    public static class SessionEventListener extends SpringHibernateMigrationInspection {
        public SessionEventListener() { super(Kind.SESSION_EVENT_LISTENER); }
    }

    public static class SpatialType extends SpringHibernateMigrationInspection {
        public SpatialType() { super(Kind.SPATIAL_TYPE); }
    }

    public static class NativeCountCast extends SpringHibernateMigrationInspection {
        public NativeCountCast() { super(Kind.NATIVE_COUNT_CAST); }
    }

    public static class InterceptorSignature extends SpringHibernateMigrationInspection {
        public InterceptorSignature() { super(Kind.INTERCEPTOR_SIGNATURE); }
    }

    public static class ScrollModeUsage extends SpringHibernateMigrationInspection {
        public ScrollModeUsage() { super(Kind.SCROLL_MODE); }
    }

    public static class EmbeddedSubPropertyOverride extends SpringHibernateMigrationInspection {
        public EmbeddedSubPropertyOverride() { super(Kind.EMBEDDED_SUB_PROPERTY_OVERRIDE); }
    }

    public static class RownumPseudoColumn extends SpringHibernateMigrationInspection {
        public RownumPseudoColumn() { super(Kind.ROWNUM_PSEUDO_COLUMN); }
    }

    public static class OracleFunctions extends SpringHibernateMigrationInspection {
        public OracleFunctions() { super(Kind.ORACLE_FUNCTIONS); }
    }

    public static class SysdateKeyword extends SpringHibernateMigrationInspection {
        public SysdateKeyword() { super(Kind.SYSDATE_KEYWORD); }
    }

    public static class BooleanCountExists extends SpringHibernateMigrationInspection {
        public BooleanCountExists() { super(Kind.BOOLEAN_COUNT_EXISTS); }
    }

    public static class FunctionReturnType extends SpringHibernateMigrationInspection {
        public FunctionReturnType() { super(Kind.FUNCTION_RETURN_TYPE); }
    }

    public static class BareOracleFunctions extends SpringHibernateMigrationInspection {
        public BareOracleFunctions() { super(Kind.BARE_ORACLE_FUNCTIONS); }
    }

    public static class TemporalSubtraction extends SpringHibernateMigrationInspection {
        public TemporalSubtraction() { super(Kind.TEMPORAL_SUBTRACTION); }
    }

    public static class CastInOnClause extends SpringHibernateMigrationInspection {
        public CastInOnClause() { super(Kind.CAST_IN_ON_CLAUSE); }
    }

    public static class DbDateFormatCompare extends SpringHibernateMigrationInspection {
        public DbDateFormatCompare() { super(Kind.DB_DATE_FORMAT_COMPARE); }
    }

    public static class ConcatTypeCast extends SpringHibernateMigrationInspection {
        public ConcatTypeCast() { super(Kind.CONCAT_TYPE_CAST); }
    }

    public static class AddToDateFunction extends SpringHibernateMigrationInspection {
        public AddToDateFunction() { super(Kind.ADD_TO_DATE_FUNCTION); }
    }

    public static class FunctionParameterType extends SpringHibernateMigrationInspection {
        public FunctionParameterType() { super(Kind.FUNCTION_PARAMETER_TYPE); }
    }

    public static class DialectRemoval extends LocalInspectionTool {

        private static final Pattern DIALECT = Pattern.compile(
                "(org\\.hibernate\\.dialect\\.)([A-Za-z]+?)(?:\\d+|\\d+[A-Za-z]+)(Dialect)");

        @Override
        @Nullable
        public com.intellij.codeInspection.ProblemDescriptor[] checkFile(@NotNull PsiFile file,
                                                                         @NotNull InspectionManager manager,
                                                                         boolean isOnTheFly) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".properties") && !name.endsWith(".yml") && !name.endsWith(".yaml")) {
                return null;
            }
            Matcher matcher = DIALECT.matcher(file.getText());
            if (matcher.find()) {
                return new com.intellij.codeInspection.ProblemDescriptor[] {
                        manager.createProblemDescriptor(file,
                                "Hibernate 6 removed version-specific dialect classes",
                                new ReplaceFileTextFix(matcher.group(),
                                        matcher.group(1) + matcher.group(2) + matcher.group(3)),
                                ProblemHighlightType.WARNING, isOnTheFly)
                };
            }
            return null;
        }
    }

    public static class QueryCacheProperties extends LocalInspectionTool {

        @Override
        @Nullable
        public com.intellij.codeInspection.ProblemDescriptor[] checkFile(@NotNull PsiFile file,
                                                                         @NotNull InspectionManager manager,
                                                                         boolean isOnTheFly) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".properties") && !name.endsWith(".xml")) {
                return null;
            }
            String text = file.getText();
            if (text.contains("hibernate.cache.region_prefix")
                    || text.contains("hibernate.cache.use_structured_entries")) {
                return new com.intellij.codeInspection.ProblemDescriptor[] {
                        manager.createProblemDescriptor(file,
                                "Hibernate 6 changed query cache region configuration properties",
                                LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.WARNING, isOnTheFly)
                };
            }
            return null;
        }
    }

    private static final class ReplaceStringLiteralFix implements LocalQuickFix {

        private final String name;
        private final String replacement;

        private ReplaceStringLiteralFix(@NotNull String name, @NotNull String replacement) {
            this.name = name;
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
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element == null) {
                return;
            }
            if (element.getText().startsWith("\"\"\"")) {
                PsiExpression replacementExpression = JavaPsiFacade.getElementFactory(project)
                        .createExpressionFromText(textBlockLiteral(this.replacement), element);
                element.replace(replacementExpression);
            } else if (element instanceof PsiLiteralExpression) {
                ElementManipulators.handleContentChange(element, this.replacement);
            } else {
                element.replace(JavaPsiFacade.getElementFactory(project)
                        .createExpressionFromText(doubleQuoteLiteral(this.replacement), element));
            }
        }

        @NotNull
        private static String textBlockLiteral(@NotNull String content) {
            String escaped = content.replace("\\", "\\\\").replace("\"\"\"", "\\\"\"\"");
            return "\"\"\"\n" + escaped + "\"\"\"";
        }

        @NotNull
        private static String doubleQuoteLiteral(@NotNull String content) {
            String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + escaped + "\"";
        }
    }

    private static final class DeleteAnnotationFix implements LocalQuickFix {

        private final String name;

        private DeleteAnnotationFix() {
            this("Remove legacy @TypeDef");
        }

        private DeleteAnnotationFix(@NotNull String name) {
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
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            descriptor.getPsiElement().delete();
        }
    }

    private static final class ReplaceAnnotationFix implements LocalQuickFix {

        private final String annotationText;
        private final String importName;

        private ReplaceAnnotationFix(@NotNull String annotationText, @NotNull String importName) {
            this.annotationText = annotationText;
            this.importName = importName;
        }

        @Override
        @NotNull
        public String getName() {
            return "Replace with @JdbcTypeCode";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiAnnotation oldAnnotation = descriptor.getPsiElement() instanceof PsiAnnotation a ? a
                    : PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiAnnotation.class);
            if (oldAnnotation == null) {
                return;
            }
            PsiAnnotation newAnnotation = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText(this.annotationText, oldAnnotation);
            PsiElement replaced = oldAnnotation.replace(newAnnotation);
            PsiJavaFile javaFile = PsiTreeUtil.getParentOfType(replaced, PsiJavaFile.class);
            if (javaFile != null) {
                SpringImportUtil.ensureImport(project, javaFile, this.importName);
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(javaFile);
            }
        }
    }

    private static final class AddAnnotationAttributeFix implements LocalQuickFix {

        private final String name;
        private final String value;

        private AddAnnotationAttributeFix(@NotNull String name, @NotNull String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        @NotNull
        public String getName() {
            return "Add " + this.name + " = " + this.value;
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiAnnotation annotation = descriptor.getPsiElement() instanceof PsiAnnotation a ? a
                    : PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiAnnotation.class);
            if (annotation == null) {
                return;
            }
            annotation.setDeclaredAttributeValue(this.name, JavaPsiFacade.getElementFactory(project)
                    .createExpressionFromText(this.value, annotation));
        }
    }

    private static final class AddJdbcTypeCodeFix implements LocalQuickFix {

        private final String code;

        private AddJdbcTypeCodeFix(@NotNull String code) {
            this.code = code;
        }

        @Override
        @NotNull
        public String getName() {
            return "Add @JdbcTypeCode";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiField field = descriptor.getPsiElement() instanceof PsiField f ? f
                    : PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
            if (field == null || field.getModifierList() == null) {
                return;
            }
            PsiAnnotation annotation = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText("@org.hibernate.annotations.JdbcTypeCode(" + this.code + ")", field);
            field.getModifierList().addBefore(annotation, field.getModifierList().getFirstChild());
            if (field.getContainingFile() instanceof PsiJavaFile javaFile) {
                SpringImportUtil.ensureImport(project, javaFile, "org.hibernate.annotations.JdbcTypeCode");
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(javaFile);
            }
        }
    }

    private static final class ReplaceReturnTypeFix implements LocalQuickFix {

        private final String type;

        private ReplaceReturnTypeFix(@NotNull String type) {
            this.type = type;
        }

        @Override
        @NotNull
        public String getName() {
            return "Change return type to " + this.type;
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethod method = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
            if (method == null) {
                return;
            }
            if (method.getReturnTypeElement() != null) {
                method.getReturnTypeElement().replace(JavaPsiFacade.getElementFactory(project)
                        .createTypeElementFromText("Object", method));
            }
        }
    }

    private static final class RenameMethodFix implements LocalQuickFix {

        private final String replacement;

        private RenameMethodFix(@NotNull String replacement) {
            this.replacement = replacement;
        }

        @Override
        @NotNull
        public String getName() {
            return "Use " + this.replacement + "()";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            element.replace(JavaPsiFacade.getElementFactory(project).createIdentifier(this.replacement));
        }
    }

    private static final class ReplaceExpressionFix implements LocalQuickFix {

        private final String name;
        private final String replacement;

        private ReplaceExpressionFix(@NotNull String name, @NotNull String replacement) {
            this.name = name;
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
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            element.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(this.replacement, element));
        }
    }

    private static final class ReplaceFileTextFix implements LocalQuickFix {

        private final String oldText;
        private final String newText;

        private ReplaceFileTextFix(@NotNull String oldText, @NotNull String newText) {
            this.oldText = oldText;
            this.newText = newText;
        }

        @Override
        @NotNull
        public String getName() {
            return "Use base Hibernate dialect";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiFile file = descriptor.getPsiElement().getContainingFile();
            if (file == null) {
                return;
            }
            PsiFile replacement = PsiFileFactoryUtil.createFile(project, file, file.getText().replace(this.oldText, this.newText));
            file.replace(replacement);
        }
    }

    private static final class PsiFileFactoryUtil {

        private PsiFileFactoryUtil() {
        }

        @NotNull
        private static PsiFile createFile(@NotNull Project project, @NotNull PsiFile original, @NotNull String text) {
            return com.intellij.psi.PsiFileFactory.getInstance(project)
                    .createFileFromText(original.getName(), original.getFileType(), text);
        }
    }
}
