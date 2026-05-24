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
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.assertj.core.api.Assertions;

/**
 * Tests for Hibernate migration inspections.
 *
 * @author Akmal Sarhan
 */
public class SpringHibernateMigrationInspectionTests extends BasePlatformTestCase {

    public void testReportsAndFixesHqlStringMigrationProblems() {
        this.myFixture.enableInspections(
                new SpringHibernateMigrationInspection.ImplicitSelect(),
                new SpringHibernateMigrationInspection.BarePositionalParameters(),
                new SpringHibernateMigrationInspection.FetchJoinProjection(),
                new SpringHibernateMigrationInspection.UpdateFrom());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                interface Repo {
                    @Query("FROM User u JOIN u.roles r WHERE r.name = :role")
                    Object implicit();
                    @Query("SELECT u FROM User u WHERE u.email = ? AND u.status = ?")
                    Object params();
                    @Query("SELECT u.email FROM User u JOIN FETCH u.orders")
                    Object fetch();
                    @Query("UPDATE FROM Employee e SET e.status = 'ACTIVE' WHERE e.id = :id")
                    Object update();
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("query migration warnings")
                .isEqualTo(4);
    }

    public void testReportsAnnotationAndFieldMigrationProblems() {
        this.myFixture.enableInspections(
                new SpringHibernateMigrationInspection.LegacyType(),
                new SpringHibernateMigrationInspection.SequenceAllocation(),
                new SpringHibernateMigrationInspection.SchemaTypeDefaults(),
                new SpringHibernateMigrationInspection.HibernateCascade());
        this.myFixture.configureByText("Sample.java", """
                @interface TypeDef { }
                @interface Type { String type(); }
                @interface SequenceGenerator { String name(); int allocationSize() default 50; }
                @interface Cascade { CascadeType[] value(); }
                @interface Entity { }
                enum CascadeType { SAVE_UPDATE, DELETE, PERSIST, MERGE }
                @Entity
                class Entity {
                    @TypeDef
                    Object def;
                    @Type(type = "org.hibernate.type.TextType")
                    String text;
                    @SequenceGenerator(name = "seq")
                    Long id;
                    java.time.Duration timeout;
                    @Cascade(CascadeType.SAVE_UPDATE)
                    Object children;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("annotation and field migration warnings")
                .isEqualTo(5);
    }

    public void testReportsContractAndSessionMigrationProblems() {
        this.myFixture.addFileToProject("org/hibernate/usertype/UserType.java", """
                package org.hibernate.usertype;
                public interface UserType { }
                """);
        this.myFixture.addFileToProject("org/hibernate/id/IdentifierGenerator.java", """
                package org.hibernate.id;
                public interface IdentifierGenerator { }
                """);
        this.myFixture.addFileToProject("org/hibernate/Session.java", """
                package org.hibernate;
                public interface Session { void save(Object o); void update(Object o); }
                """);
        this.myFixture.addFileToProject("org/hibernate/engine/spi/SharedSessionContractImplementor.java", """
                package org.hibernate.engine.spi;
                public interface SharedSessionContractImplementor { }
                """);
        this.myFixture.enableInspections(
                new SpringHibernateMigrationInspection.UserTypeSignature(),
                new SpringHibernateMigrationInspection.IdentifierGenerator(),
                new SpringHibernateMigrationInspection.SessionLifecycle());
        this.myFixture.configureByText("Sample.java", """
                import java.io.Serializable;
                import java.sql.ResultSet;
                import org.hibernate.Session;
                import org.hibernate.engine.spi.SharedSessionContractImplementor;
                class CustomType implements org.hibernate.usertype.UserType {
                    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) { return null; }
                }
                class CustomGenerator implements org.hibernate.id.IdentifierGenerator {
                    public Serializable generate(SharedSessionContractImplementor session, Object object) { return null; }
                }
                class Service {
                    void save(Session session) { session.save(new Object()); }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("contract and session migration warnings")
                .isEqualTo(3);
    }

    public void testReportsAdditionalQueryAndAnnotationMigrationProblems() {
        this.myFixture.enableInspections(
                new SpringHibernateMigrationInspection.DistinctFetch(),
                new SpringHibernateMigrationInspection.FetchJoinProjection(),
                new SpringHibernateMigrationInspection.TemporalJavaTime(),
                new SpringHibernateMigrationInspection.GlobalSequence(),
                new SpringHibernateMigrationInspection.HibernateTable(),
                new SpringHibernateMigrationInspection.ArrayMapping(),
                new SpringHibernateMigrationInspection.CompositeUserType(),
                new SpringHibernateMigrationInspection.EmbeddableReuse());
        this.myFixture.addFileToProject("org/hibernate/annotations/Table.java", """
                package org.hibernate.annotations;
                public @interface Table { String appliesTo(); }
                """);
        this.myFixture.configureByText("Sample.java", """
                import org.hibernate.annotations.Table;
                @interface Query { String value(); }
                @interface Temporal { TemporalType value(); }
                @interface GeneratedValue { GenerationType strategy(); String generator() default ""; }
                @interface Embedded { }
                @interface AttributeOverride { String name(); }
                @interface Type { Class value(); }
                @interface Entity { }
                enum TemporalType { TIMESTAMP }
                enum GenerationType { SEQUENCE }
                interface MyCompositeUserType { }
                class MyMonetaryAmountType implements MyCompositeUserType { }
                interface Repo {
                    @Query("SELECT DISTINCT u FROM User u JOIN FETCH u.roles")
                    Object fetch();
                    @Query("select u.email from User u left join fetch u.roles")
                    Object leftFetchProjection();
                }
                @Table(appliesTo = "users")
                @Entity
                class Entity {
                    @GeneratedValue(strategy = GenerationType.SEQUENCE)
                    Long id;
                    @Temporal(TemporalType.TIMESTAMP)
                    java.time.LocalDateTime createdAt;
                    String[] tags;
                    @Type(MyMonetaryAmountType.class)
                    Object price;
                    @Embedded
                    Address home;
                    @Embedded
                    @AttributeOverride(name = "street")
                    Address work;
                }
                class Address { }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("additional query and annotation migration warnings")
                .isEqualTo(8);
    }

    public void testReportsAdditionalContractMigrationProblems() {
        this.myFixture.addFileToProject("org/hibernate/query/Query.java", """
                package org.hibernate.query;
                public interface Query {
                    Query setResultTransformer(Object transformer);
                }
                """);
        this.myFixture.addFileToProject("org/hibernate/ScrollableResults.java", """
                package org.hibernate;
                public interface ScrollableResults {
                    Object get(int index);
                }
                """);
        this.myFixture.addFileToProject("org/hibernate/Session.java", """
                package org.hibernate;
                public interface Session {
                    org.hibernate.query.Query createQuery(String query);
                }
                """);
        this.myFixture.addFileToProject("org/hibernate/dialect/Dialect.java", """
                package org.hibernate.dialect;
                public class Dialect {
                    protected void registerFunction(String name, Object function) { }
                }
                """);
        this.myFixture.enableInspections(
                new SpringHibernateMigrationInspection.ResultTransformer(),
                new SpringHibernateMigrationInspection.ScrollableResultsAccess(),
                new SpringHibernateMigrationInspection.UntypedCreateQuery(),
                new SpringHibernateMigrationInspection.FunctionRegistration());
        this.myFixture.configureByText("Sample.java", """
                class MyDialect extends org.hibernate.dialect.Dialect {
                    MyDialect() {
                        this.registerFunction("custom_match", new Object());
                    }
                }
                class Service {
                    void run(org.hibernate.query.Query query, org.hibernate.ScrollableResults rows, org.hibernate.Session session) {
                        query.setResultTransformer(new Object());
                        rows.get(0);
                        session.createQuery("FROM User");
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("additional contract migration warnings")
                .isEqualTo(4);
    }

    public void testReportsNewestHibernateMigrationProblems() {
        this.myFixture.addFileToProject("org/hibernate/EmptyInterceptor.java", """
                package org.hibernate;
                public class EmptyInterceptor { }
                """);
        this.myFixture.addFileToProject("org/hibernate/type/Type.java", """
                package org.hibernate.type;
                public interface Type { }
                """);
        this.myFixture.addFileToProject("org/hibernate/BaseSessionEventListener.java", """
                package org.hibernate;
                public class BaseSessionEventListener { }
                """);
        this.myFixture.enableInspections(
                new SpringHibernateMigrationInspection.SessionEventListener(),
                new SpringHibernateMigrationInspection.SpatialType(),
                new SpringHibernateMigrationInspection.NativeCountCast(),
                new SpringHibernateMigrationInspection.InterceptorSignature(),
                new SpringHibernateMigrationInspection.ScrollModeUsage(),
                new SpringHibernateMigrationInspection.EmbeddedSubPropertyOverride());
        this.myFixture.configureByText("Sample.java", """
                import java.io.Serializable;
                import java.math.BigInteger;
                @interface Type { String type(); }
                @interface AttributeOverride { String name(); }
                @interface Embedded { }
                enum ScrollMode { SCROLL_SENSITIVE, FORWARD_ONLY }
                interface EntityManager { Query createNativeQuery(String query); }
                interface Query { Object getSingleResult(); }
                interface SessionImplementor {
                    EventListenerManager getEventListenerManager();
                }
                interface EventListenerManager {
                    void addListener(Object listener);
                }
                class Listener extends org.hibernate.BaseSessionEventListener { }
                class Entity {
                    @Type(type = "org.hibernate.spatial.JTSGeometryType")
                    Object location;
                    @AttributeOverride(name = "zipCode")
                    Address billingAddress;
                }
                class Address {
                    @Embedded
                    Info info;
                }
                class Info {
                    String zipCode;
                }
                class CustomInterceptor extends org.hibernate.EmptyInterceptor {
                    public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, org.hibernate.type.Type[] types) { return false; }
                }
                class Service {
                    void run(Object session, Object listener, EntityManager em, Query query) {
                        ((SessionImplementor) session).getEventListenerManager().addListener(listener);
                        BigInteger count = (BigInteger) em.createNativeQuery("SELECT count(*) FROM users").getSingleResult();
                        query.scroll(ScrollMode.SCROLL_SENSITIVE);
                    }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("newest Hibernate migration warnings")
                .isEqualTo(7);
    }

    public void testReportsOracleJpaQueryMigrationProblems() {
        this.myFixture.enableInspections(
                new SpringHibernateMigrationInspection.RownumPseudoColumn(),
                new SpringHibernateMigrationInspection.OracleFunctions(),
                new SpringHibernateMigrationInspection.SysdateKeyword(),
                new SpringHibernateMigrationInspection.BooleanCountExists(),
                new SpringHibernateMigrationInspection.FunctionReturnType(),
                new SpringHibernateMigrationInspection.BareOracleFunctions(),
                new SpringHibernateMigrationInspection.TemporalSubtraction(),
                new SpringHibernateMigrationInspection.CastInOnClause(),
                new SpringHibernateMigrationInspection.DbDateFormatCompare(),
                new SpringHibernateMigrationInspection.ConcatTypeCast(),
                new SpringHibernateMigrationInspection.AddToDateFunction(),
                new SpringHibernateMigrationInspection.FunctionParameterType());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); boolean nativeQuery() default false; }
                interface Repo {
                    @Query("select n.id from PartnerRel n where n.partner.id=:partnerId and ROWNUM = 1")
                    Object rownum();
                    @Query("select p from Product p where :validFrom <= NVL(p.validUntil, :fallback)")
                    Object nvl();
                    @Query("select ADD_MONTHS(c.startDate, 1) from Contract c")
                    Object addMonths();
                    @Query("select x from PaymentDetail x where FUNCTION('trunc', sysdate) > x.validFrom")
                    Object sysdate();
                    @Query("select case when count(x) > 0 then true else false end from BuildingHistory x where x.id = :id")
                    Object exists();
                    @Query("select count(sp) from MehrwertHistory sp where FUNCTION('trunc',sp.validFrom) >= FUNCTION('trunc',:validFrom)")
                    Object trunc();
                    @Query("select c from Contract c where trunc(c.requestDeploymentDate) - trunc(current_date) <= 8")
                    Object temporalSubtraction();
                    @Query("select sr from SapReference sr inner join Partner p on to_char(p.customerNumber) = sr.partnerId")
                    Object castOnClause();
                    @Query("select c from Contract c where (FUNCTION('to_char',c.contractStartDate,'YYYYMMDD')) < :endDate")
                    Object formattedDateCompare();
                    @Query("select concat(c.contractNumber, ' ', c.contractType.name) from Contract c")
                    Object concat();
                    @Query("select n from Contract n where add_to_date(trunc(n.contractStartDate),305) = FUNCTION('trunc',CURRENT_DATE)")
                    Object addToDate();
                    @Query("select count(bh) from BuildingHistory bh where FUNCTION('trunc', :validFrom) = FUNCTION('trunc', bh.validFrom)")
                    Object functionParameter();
                    @Query(value = "select * from partner_rel where ROWNUM = 1 and value <= NVL(value, 1)", nativeQuery = true)
                    Object nativeQuery();
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("Oracle JPQL migration warnings")
                .isEqualTo(20);
    }

    public void testWrapsBareOracleFunctionsInQuery() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.BareOracleFunctions());
        this.myFixture.configureByText("Sample.java", """
                import java.util.Date;
                import java.util.List;
                @interface Query { String value(); boolean nativeQuery() default false; }
                interface Repo {
                    @Query("SELECT CASE WHEN COUNT(bh) > 0 THEN true ELSE false END FROM BuildingHistory bh WHERE (<caret>trunc(bh.validUntil) > trunc(:startDate) OR bh.validUntil is null)")
                    boolean isBuildingValid(Date startDate, Long apartmentId, List<ContractType> contractTypes);
                }
                class ContractType { }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Wrap Oracle functions in FUNCTION"));

        String expected = """
                import java.util.Date;
                import java.util.List;
                @interface Query { String value(); boolean nativeQuery() default false; }
                interface Repo {
                    @Query(\"\"\"
                            SELECT CASE WHEN COUNT(bh) > 0 THEN true ELSE false END
                            FROM BuildingHistory bh
                            WHERE (FUNCTION('trunc', bh.validUntil) > FUNCTION('trunc', :startDate)
                            OR bh.validUntil is null)
                            \"\"\")
                    boolean isBuildingValid(Date startDate, Long apartmentId, List<ContractType> contractTypes);
                }
                class ContractType { }
                """;
        this.myFixture.checkResult(expected);
    }

    public void testWrapsAddMonthsOracleFunctionInQuery() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.OracleFunctions());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); boolean nativeQuery() default false; }
                interface Repo {
                    @Query("<caret>select ADD_MONTHS(c.startDate, 1) from Contract c")
                    Object addMonths();
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Wrap ADD_MONTHS in FUNCTION"));

        this.myFixture.checkResult("""
                @interface Query { String value(); boolean nativeQuery() default false; }
                interface Repo {
                    @Query(\"\"\"
                            select FUNCTION('add_months', c.startDate, 1)
                            from Contract c
                            \"\"\")
                    Object addMonths();
                }
                """);
    }

    public void testQuickFixUsesTabsForTextBlockIndentationWhenConfigured() {
        CodeStyleSettingsManager.getSettings(this.getProject())
                .getCommonSettings(JavaLanguage.INSTANCE)
                .getIndentOptions()
                .USE_TAB_CHARACTER = true;
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.ImplicitSelect());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                interface Repo {
                \t@Query(<caret>"FROM User u JOIN u.roles r " + "WHERE r.name = :role")
                \tObject implicit();
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add explicit SELECT"));

        this.myFixture.checkResult("""
                @interface Query { String value(); }
                interface Repo {
                \t@Query(\"\"\"
                \t\t\tSELECT u
                \t\t\tFROM User u
                \t\t\tJOIN u.roles r
                \t\t\tWHERE r.name = :role
                \t\t\t\"\"\")
                \tObject implicit();
                }
                """);
    }

    public void testAddToDateMigrationQuickFix() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.AddToDateFunction());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                @interface Component { }
                @Component
                interface Repo {
                    @Query("<caret>select n from Contract n where add_to_date(trunc(n.contractStartDate),305) = FUNCTION('trunc',CURRENT_DATE)")
                    Object addToDate();
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Use JPA interval math"));

        this.myFixture.checkResult("""
                @interface Query { String value(); }
                @interface Component { }
                @Component
                interface Repo {
                    @Query(\"\"\"
                            select n
                            from Contract n
                            where function('date_add', cast(n.contractStartDate as LocalDate), 305, day) = FUNCTION('trunc',CURRENT_DATE)
                            \"\"\")
                    Object addToDate();
                }
                """);
    }

    public void testDoesNotReportModernHibernateUsage() {
        this.myFixture.enableInspections(
                new SpringHibernateMigrationInspection.ImplicitSelect(),
                new SpringHibernateMigrationInspection.BarePositionalParameters(),
                new SpringHibernateMigrationInspection.SequenceAllocation(),
                new SpringHibernateMigrationInspection.SchemaTypeDefaults(),
                new SpringHibernateMigrationInspection.DistinctFetch(),
                new SpringHibernateMigrationInspection.TemporalJavaTime(),
                new SpringHibernateMigrationInspection.GlobalSequence(),
                new SpringHibernateMigrationInspection.ArrayMapping());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                @interface SequenceGenerator { String name(); int allocationSize() default 50; }
                @interface JdbcTypeCode { int value(); }
                @interface GeneratedValue { GenerationType strategy(); String generator() default ""; }
                @interface Entity { }
                enum GenerationType { SEQUENCE }
                interface Repo {
                    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = ?1")
                    Object ok();
                    @Query("SELECT u FROM User u JOIN FETCH u.roles")
                    Object fetch();
                    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles")
                    Object leftFetch();
                }
                @Entity
                class Entity {
                    @SequenceGenerator(name = "seq", allocationSize = 1)
                    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq")
                    Long id;
                    @JdbcTypeCode(1)
                    java.util.UUID uuid;
                    @JdbcTypeCode(1)
                    String[] tags;
                    java.time.LocalDateTime createdAt;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("modern Hibernate usage warnings")
                .isZero();
    }

    public void testArrayMappingFixClearsWarning() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.ArrayMapping());
        this.myFixture.configureByText("Sample.java", """
                @interface Entity { }
                @Entity
                class Entity {
                    byte[] <caret>payload;
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add @JdbcTypeCode"));
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("array mapping warning after quick fix")
                .isZero();
    }

    public void testDoesNotReportArrayMappingOnSerializableDto() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.ArrayMapping());
        this.myFixture.configureByText("Sample.java", """
                import java.io.Serializable;
                import java.util.Base64;
                import java.util.Optional;
                @interface Getter { }
                @interface Setter { }
                @interface AllArgsConstructor { }
                @interface NoArgsConstructor { }
                @Getter
                @Setter
                @AllArgsConstructor
                @NoArgsConstructor
                class ByteStream implements Serializable {
                    private String mimeType;
                    private String filename;
                    private byte[] data;
                    Base64File toBase64File() {
                        return new Base64File(mimeType, filename,
                                Optional.ofNullable(data).map(d -> Base64.getEncoder().encodeToString(d)).orElse(null));
                    }
                }
                class Base64File {
                    Base64File(String mimeType, String filename, String data) { }
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("serializable DTO array mapping warnings")
                .isZero();
    }

    public void testSchemaTypeDefaultFixClearsUuidWarning() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.SchemaTypeDefaults());
        this.myFixture.configureByText("Sample.java", """
                @interface Entity { }
                @Entity
                class Entity {
                    java.util.UUID <caret>id;
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add @JdbcTypeCode"));
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("UUID schema mapping warning after quick fix")
                .isZero();
    }

    public void testDoesNotReportSchemaTypeDefaultsOnDto() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.SchemaTypeDefaults());
        this.myFixture.configureByText("Sample.java", """
                @interface Getter { }
                @interface Setter { }
                @interface AllArgsConstructor { }
                @interface NoArgsConstructor { }
                @interface Builder { }
                class UUID { }
                @Getter
                @Setter
                @AllArgsConstructor
                @NoArgsConstructor
                @Builder
                class ListSubscriberServiceReducedAddonsRequest {
                    java.util.UUID clientId;
                    java.util.UUID mandantId;
                    java.util.UUID tenantId;
                    java.util.UUID subscriberServiceId;
                    SubscriberServiceReducedAssignedAddonFilterDto filter;
                    UserInfoWithPermissions userInfoWithPermissions;
                }
                class SubscriberServiceReducedAssignedAddonFilterDto { }
                class UserInfoWithPermissions { }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("DTO schema mapping warnings")
                .isZero();
    }

    public void testSequenceAllocationFixClearsWarning() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.SequenceAllocation());
        this.myFixture.configureByText("Sample.java", """
                @interface SequenceGenerator { String name(); int allocationSize() default 50; }
                @<caret>SequenceGenerator(name = "entity_seq")
                class Entity {
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add allocationSize = 1"));
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("sequence allocation warning after quick fix")
                .isZero();
    }

    public void testTemporalJavaTimeFixClearsWarning() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.TemporalJavaTime());
        this.myFixture.configureByText("Sample.java", """
                @interface Temporal { TemporalType value(); }
                @interface Entity { }
                enum TemporalType { TIMESTAMP }
                @Entity
                class Entity {
                    @<caret>Temporal(TemporalType.TIMESTAMP)
                    java.time.Instant createdAt;
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Remove @Temporal"));
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("temporal Java time warning after quick fix")
                .isZero();
    }

    public void testDoesNotReportJpaFieldMappingsOnDto() {
        this.myFixture.enableInspections(
                new SpringHibernateMigrationInspection.TemporalJavaTime(),
                new SpringHibernateMigrationInspection.GlobalSequence(),
                new SpringHibernateMigrationInspection.CompositeUserType());
        this.myFixture.configureByText("Sample.java", """
                @interface Temporal { TemporalType value(); }
                @interface GeneratedValue { GenerationType strategy(); String generator() default ""; }
                @interface Type { Class value(); }
                enum TemporalType { TIMESTAMP }
                enum GenerationType { SEQUENCE }
                interface MyCompositeUserType { }
                class MyMonetaryAmountType implements MyCompositeUserType { }
                class RequestDto {
                    @Temporal(TemporalType.TIMESTAMP)
                    java.time.Instant createdAt;
                    @GeneratedValue(strategy = GenerationType.SEQUENCE)
                    Long id;
                    @Type(MyMonetaryAmountType.class)
                    Object price;
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("DTO JPA field mapping warnings")
                .isZero();
    }

    public void testLegacyTextTypeFixClearsWarning() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.LegacyType());
        this.myFixture.configureByText("Sample.java", """
                @interface Type { String type(); }
                class Entity {
                    @<caret>Type(type = "org.hibernate.type.TextType")
                    String body;
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with @JdbcTypeCode"));
        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("legacy text type warning after quick fix")
                .isZero();
    }

    public void testFixesStringConcatenatedQuery() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.ImplicitSelect());
        this.myFixture.configureByText("Sample.java", """
                @interface Query { String value(); }
                interface Repo {
                    @Query(<caret>"FROM User u JOIN u.roles r " + "WHERE r.name = :role")
                    Object implicit();
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Add explicit SELECT"));

        this.myFixture.checkResult("""
                @interface Query { String value(); }
                interface Repo {
                    @Query(\"\"\"
                            SELECT u
                            FROM User u
                            JOIN u.roles r
                            WHERE r.name = :role
                            \"\"\")
                    Object implicit();
                }
                """);
    }

    public void testFixesConcatenatedFetchJoinProjection() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.FetchJoinProjection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.List;
                import java.util.UUID;
                @interface Query { String value(); }
                @interface Param { String value(); }
                interface Repo {
                    @Query(<caret>"SELECT r from SubscriberServiceReplacementAssignment ra " +
                            "join fetch SubscriberServiceExtended r on ra.replacementServiceId = r.id " +
                            "where ra.originServiceId = :originId")
                    List<SubscriberServiceExtended> getReplacements(@Param("originId") UUID originId);
                }
                class SubscriberServiceExtended { }
                class SubscriberServiceReplacementAssignment { }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Remove FETCH from scalar projection"));

        this.myFixture.checkResult("""
                import java.util.List;
                import java.util.UUID;
                @interface Query { String value(); }
                @interface Param { String value(); }
                interface Repo {
                    @Query(\"\"\"
                            SELECT r
                            from SubscriberServiceReplacementAssignment ra
                            JOIN SubscriberServiceExtended r
                            on ra.replacementServiceId = r.id
                            where ra.originServiceId = :originId
                            \"\"\")
                    List<SubscriberServiceExtended> getReplacements(@Param("originId") UUID originId);
                }
                class SubscriberServiceExtended { }
                class SubscriberServiceReplacementAssignment { }
                """);
    }

    public void testReplacesHibernateTableAnnotation() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.HibernateTable());
        this.myFixture.addFileToProject("org/hibernate/annotations/Table.java", """
                package org.hibernate.annotations;
                public @interface Table {
                    String appliesTo() default "";
                    String name() default "";
                    String schema() default "";
                }
                """);
        this.myFixture.configureByText("Sample.java", """
                import org.hibernate.annotations.Table;
                @Table(<caret>name = "V_ARCHIVABLE_PROCESSES", schema = "public")
                class Entity {
                }
                """);

        this.myFixture.doHighlighting();
        this.myFixture.launchAction(this.myFixture.findSingleIntention("Replace with @jakarta.persistence.Table"));

        this.myFixture.checkResult("""
                import org.hibernate.annotations.Table;
                @jakarta.persistence.Table(name = "V_ARCHIVABLE_PROCESSES", schema = "public")
                class Entity {
                }
                """);
    }

    public void testDoesNotReportJakartaTableWildcardImport() {
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.HibernateTable());
        this.myFixture.addFileToProject("jakarta/persistence/Table.java", """
                package jakarta.persistence;
                public @interface Table { String name() default ""; }
                """);
        this.myFixture.configureByText("Sample.java", """
                import jakarta.persistence.*;
                @Table(name = "V_ARCHIVABLE_PROCESSES")
                class Entity {
                }
                """);

        List<HighlightInfo> highlights = this.myFixture.doHighlighting();

        Assertions.assertThat(countWarnings(highlights))
                .as("jakarta Table warning count")
                .isZero();
    }

    private static long countWarnings(List<HighlightInfo> highlights) {
        return highlights.stream()
                .filter((HighlightInfo h) -> h.getSeverity() == HighlightSeverity.WARNING)
                .count();
    }
}
