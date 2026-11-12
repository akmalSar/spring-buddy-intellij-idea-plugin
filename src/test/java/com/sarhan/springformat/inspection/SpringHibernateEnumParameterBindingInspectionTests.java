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
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.assertj.core.api.Assertions;

/**
 * Tests for the enum-typed query parameter binding inspection. The bug class: a String
 * (or number) is bound to a named parameter that the query compares against an enum
 * path, which Hibernate 6 rejects at runtime (older Hibernate silently converted it).
 *
 * @author Akmal Sarhan
 */
public class SpringHibernateEnumParameterBindingInspectionTests extends BasePlatformTestCase {

    private void addFixtures() {
        this.myFixture.addFileToProject("jakarta/persistence/Entity.java", """
                package jakarta.persistence;
                public @interface Entity { }
                """);
        this.myFixture.addFileToProject("jakarta/persistence/Id.java", """
                package jakarta.persistence;
                public @interface Id { }
                """);
        this.myFixture.addFileToProject("jakarta/persistence/Enumerated.java", """
                package jakarta.persistence;
                public @interface Enumerated { }
                """);
        this.myFixture.addFileToProject("jakarta/persistence/EntityManager.java", """
                package jakarta.persistence;
                public interface EntityManager {
                    Query createQuery(String qlString);
                    Query createQuery(String qlString, Class resultType);
                    Query createNativeQuery(String sqlString);
                }
                """);
        this.myFixture.addFileToProject("jakarta/persistence/Query.java", """
                package jakarta.persistence;
                public interface Query {
                    Query setParameter(String name, Object value);
                    java.util.List getResultList();
                    int executeUpdate();
                }
                """);
        this.myFixture.addFileToProject("com/example/TransferState.java", """
                package com.example;
                public enum TransferState { U, W, L }
                """);
        this.myFixture.addFileToProject("com/example/BusinessPartner.java", """
                package com.example;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Enumerated;
                import jakarta.persistence.Id;
                @Entity
                public class BusinessPartner {
                    @Id
                    private String id;
                    private String mandant;
                    @Enumerated
                    private TransferState transferStatus;
                }
                """);
        this.myFixture.addFileToProject("com/example/Merchant.java", """
                package com.example;
                import jakarta.persistence.Entity;
                import jakarta.persistence.Enumerated;
                import jakarta.persistence.Id;
                @Entity
                public class Merchant {
                    @Id
                    private String id;
                    @Enumerated
                    private TransferState transferStatus;
                }
                """);
        this.myFixture.enableInspections(new SpringHibernateMigrationInspection.EnumParameterBinding());
    }

    private List<HighlightInfo> enumProblems() {
        return this.myFixture.doHighlighting()
            .stream()
            .filter((HighlightInfo info) -> info.getDescription() != null
                    && info.getDescription().contains("to the enum path"))
            .toList();
    }

    public void testReportsStringBoundToEnumPathWithDynamicEntityName() {
        this.addFixtures();
        this.myFixture.configureByText("PreProcessing.java", """
                import jakarta.persistence.EntityManager;
                class PreProcessing {
                    int run(EntityManager em, String clazz, String id, String mandant) {
                        return em.createQuery("SELECT a FROM " + clazz
                                + " a WHERE a.id=:id AND a.mandant=:mandant AND a.transferStatus != :transferStatus")
                            .setParameter("id", id)
                            .setParameter("mandant", mandant)
                            .setParameter("transferStatus", "W")
                            .getResultList()
                            .size();
                    }
                }
                """);

        List<HighlightInfo> problems = this.enumProblems();

        Assertions.assertThat(problems).as("String bound to the enum path a.transferStatus").hasSize(1);
        Assertions.assertThat(problems.get(0).getSeverity().getName()).isEqualTo("ERROR");
        Assertions.assertThat(problems.get(0).getDescription())
            .contains("a.transferStatus")
            .contains("TransferState.W");
    }

    public void testReportsStringBoundToEnumPathWithResultClass() {
        this.addFixtures();
        this.myFixture.configureByText("Service.java", """
                import com.example.BusinessPartner;
                import jakarta.persistence.EntityManager;
                class Service {
                    Object run(EntityManager em, java.lang.String state) {
                        return em.createQuery("from BusinessPartner b where b.transferStatus = :state", BusinessPartner.class)
                            .setParameter("state", state)
                            .getResultList();
                    }
                }
                """);

        List<HighlightInfo> problems = this.enumProblems();

        Assertions.assertThat(problems).hasSize(1);
        Assertions.assertThat(problems.get(0).getDescription()).contains("BusinessPartner.transferStatus");
    }

    public void testQuickFixReplacesLiteralWithEnumConstant() {
        this.addFixtures();
        this.myFixture.configureByText("Service.java", """
                import com.example.BusinessPartner;
                import jakarta.persistence.EntityManager;
                class Service {
                    Object run(EntityManager em) {
                        return em.createQuery("from BusinessPartner b where b.transferStatus = :state", BusinessPartner.class)
                            .setParameter("state", "<caret>W")
                            .getResultList();
                    }
                }
                """);

        IntentionAction fix = this.myFixture.findSingleIntention("Bind enum constant 'TransferState.W'");
        this.myFixture.launchAction(fix);

        this.myFixture.checkResult("""
                import com.example.BusinessPartner;
                import com.example.TransferState;
                import jakarta.persistence.EntityManager;
                class Service {
                    Object run(EntityManager em) {
                        return em.createQuery("from BusinessPartner b where b.transferStatus = :state", BusinessPartner.class)
                            .setParameter("state", TransferState.W)
                            .getResultList();
                    }
                }
                """);
    }

    public void testNoWarningForEnumBindingStringFieldOrNativeQuery() {
        this.addFixtures();
        this.myFixture.configureByText("Fine.java", """
                import com.example.BusinessPartner;
                import com.example.TransferState;
                import jakarta.persistence.EntityManager;
                class Fine {
                    Object enumBinding(EntityManager em, String clazz) {
                        return em.createQuery("SELECT a FROM " + clazz + " a WHERE a.transferStatus != :transferStatus")
                            .setParameter("transferStatus", TransferState.W)
                            .getResultList();
                    }
                    Object stringField(EntityManager em, String mandant) {
                        return em.createQuery("from BusinessPartner b where b.mandant = :mandant", BusinessPartner.class)
                            .setParameter("mandant", mandant)
                            .getResultList();
                    }
                    int nativeQuery(EntityManager em, String id) {
                        return em.createNativeQuery(
                                "UPDATE BO_BUSINESSPARTNER B SET B.TRANSFER_STATUS = :transferStatus WHERE B.ID = :id")
                            .setParameter("transferStatus", "L")
                            .setParameter("id", id)
                            .executeUpdate();
                    }
                }
                """);

        Assertions.assertThat(this.enumProblems()).as("enum, String field and native bindings are valid").isEmpty();
    }

}
