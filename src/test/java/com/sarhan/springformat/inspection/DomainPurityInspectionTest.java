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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class DomainPurityInspectionTest extends BasePlatformTestCase {

    private static final String NOT_A_FIX = " Suppressing or working around this check is not a fix.";

    private static final String WEB = "[SB-ARCH-004 domain-no-web] Forbidden dependency on ";

    private static final String WEB_END = ". Why: domain code must stay independent of HTTP and the Servlet API. Fix: "
            + "Move request/response handling into the web adapter and pass plain domain values into the domain."
            + NOT_A_FIX;

    private static final String JPA = "[SB-ARCH-004 domain-no-persistence] Forbidden dependency on ";

    private static final String JPA_END = ". Why: domain code must stay independent of JPA and persistence frameworks. "
            + "Fix: Keep persistence mapping in the persistence adapter (separate entity or mapping class) and expose "
            + "a repository port owned by the domain. If this project keeps JPA entities in the domain package, set "
            + "style to layered in .spring-buddy/architecture.json." + NOT_A_FIX;

    private static final String LAYERED_WEB_END = ". Why: domain code must stay independent of HTTP and the Servlet "
            + "API. Fix: Move request/response handling into the controller and pass plain values into domain classes, "
            + "or set presets.domainPurity to off in .spring-buddy/architecture.json." + NOT_A_FIX;

    private static final String INFRA = "[SB-ARCH-004 domain-no-infrastructure] Forbidden dependency on ";

    private static final String INFRA_END = ". Why: the domain must not know infrastructure adapters. Fix: Define a "
            + "port interface in the domain and implement it in the infrastructure layer." + NOT_A_FIX;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new DomainPurityInspection());
        this.myFixture.addFileToProject("org/springframework/web/bind/annotation/RestController.java", """
                package org.springframework.web.bind.annotation;
                public @interface RestController { }
                """);
        this.myFixture.addFileToProject("org/springframework/http/ResponseEntity.java", """
                package org.springframework.http;
                public class ResponseEntity<T> { }
                """);
        this.myFixture.addFileToProject("jakarta/persistence/Entity.java", """
                package jakarta.persistence;
                public @interface Entity { }
                """);
        this.myFixture.addFileToProject("jakarta/persistence/Embeddable.java", """
                package jakarta.persistence;
                public @interface Embeddable { }
                """);
        this.myFixture.addFileToProject("com/acme/infra/MailClient.java", """
                package com.acme.infra;
                public class MailClient { }
                """);
    }

    public void testReportsWebAndJpaTypesInDomainWhenEnabled() {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", """
                { "version": 1, "style": "hexagonal" }
                """);
        check("com/acme/domain/Order.java", """
                package com.acme.domain;
                import <warning descr="%sjakarta.persistence.Entity%J">jakarta.persistence.Entity</warning>;
                @Entity
                public class Order {
                    <warning descr="%sorg.springframework.http.ResponseEntity%W">org.springframework.http.ResponseEntity</warning><String> response;
                }
                """.replace("%sjakarta", JPA + "jakarta").replace("%sorg", WEB + "org").replace("%J", JPA_END)
                .replace("%W", WEB_END));
    }

    public void testDoesNotReportOutsideDomain() {
        check("com/acme/web/OrderController.java", """
                package com.acme.web;
                import org.springframework.web.bind.annotation.RestController;
                import jakarta.persistence.Entity;
                @RestController
                public class OrderController { }
                """);
    }

    public void testDoesNotReportPlainJavaInDomain() {
        check("com/acme/domain/Money.java", """
                package com.acme.domain;
                public record Money(long cents) { }
                """);
    }

    public void testAllowListAndCustomDomainLayerAndInfrastructure() {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", """
                {
                  "version": 1,
                  "style": "hexagonal",
                  "presets": { "domainPurity": "error" },
                  "layers": {
                    "domain": ["com.acme.core.."],
                    "infrastructure": ["com.acme.infra.."]
                  },
                  "domainPurityAllow": ["jakarta.persistence.Embeddable"]
                }
                """);
        check("com/acme/core/Order.java", """
                package com.acme.core;
                import jakarta.persistence.Embeddable;
                import <error descr="%sjakarta.persistence.Entity%J">jakarta.persistence.Entity</error>;
                import <error descr="%scom.acme.infra.MailClient%I">com.acme.infra.MailClient</error>;
                @Embeddable
                @Entity
                public class Order {
                    MailClient client;
                }
                """.replace("%sjakarta", JPA + "jakarta").replace("%scom", INFRA + "com").replace("%J", JPA_END)
                .replace("%I", INFRA_END));
    }

    public void testPresetOff() {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", """
                { "version": 1, "presets": { "domainPurity": "off" } }
                """);
        check("com/acme/domain/Order.java", """
                package com.acme.domain;
                import jakarta.persistence.Entity;
                @Entity
                public class Order { }
                """);
    }

    public void testOffWithoutConfig() {
        check("com/acme/domain/Order.java", """
                package com.acme.domain;
                import jakarta.persistence.Entity;
                @Entity
                public class Order { }
                """);
        assertNoArchitectureError();
    }

    public void testNoConfigFileNeverReportsError() {
        check("com/acme/domain/Money.java", """
                package com.acme.domain;
                import jakarta.persistence.Entity;
                import org.springframework.http.ResponseEntity;
                public class Money {
                    ResponseEntity<String> response;
                }
                """);
        assertNoArchitectureError();
    }

    public void testLayeredAllowsJpaEntityInDomain() {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", """
                { "version": 1, "style": "layered", "presets": { "domainPurity": "error" } }
                """);
        check("com/acme/domain/AuthConfiguration.java", """
                package com.acme.domain;
                import jakarta.persistence.Entity;
                import org.springframework.http.ResponseEntity;
                @Entity
                public class AuthConfiguration {
                    ResponseEntity<String> response;
                }
                """);
    }

    public void testLayeredAllowsJpaTypesInNonEntityDomainClassButReportsWeb() {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", """
                { "version": 1, "presets": { "domainPurity": "warning" } }
                """);
        check("com/acme/domain/Money.java", """
                package com.acme.domain;
                import jakarta.persistence.Embeddable;
                import <warning descr="%sorg.springframework.http.ResponseEntity%W">org.springframework.http.ResponseEntity</warning>;
                public class Money {
                    ResponseEntity<String> response;
                }
                """.replace("%s", WEB).replace("%W", LAYERED_WEB_END));
    }

    public void testHexagonalReportsJpaEntityInDomain() {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", """
                { "version": 1, "style": "hexagonal" }
                """);
        check("com/acme/domain/AuthConfiguration.java", """
                package com.acme.domain;
                import <warning descr="%sjakarta.persistence.Entity%J">jakarta.persistence.Entity</warning>;
                @Entity
                public class AuthConfiguration { }
                """.replace("%s", JPA).replace("%J", JPA_END));
    }

    private void assertNoArchitectureError() {
        assertTrue(this.myFixture.doHighlighting().stream()
                .filter((HighlightInfo info) -> info.getDescription() != null
                        && info.getDescription().startsWith("[SB-ARCH-"))
                .noneMatch((HighlightInfo info) -> info.getSeverity().getName().equals("ERROR")));
    }

    public void testOffWhenConfigOmitsPreset() {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", """
                { "version": 1, "presets": { "controllerBoundary": "error" } }
                """);
        check("com/acme/domain/Order.java", """
                package com.acme.domain;
                import jakarta.persistence.Entity;
                @Entity
                public class Order { }
                """);
    }

    private void check(String path, String content) {
        PsiFile file = this.myFixture.addFileToProject(path, content);
        this.myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        this.myFixture.checkHighlighting(true, false, true, true);
        long expected = content.split("descr=\"\\[SB-ARCH-", -1).length - 1;
        long actual = this.myFixture.doHighlighting().stream()
                .filter((HighlightInfo info) -> info.getDescription() != null
                        && info.getDescription().startsWith("[SB-ARCH-"))
                .count();
        assertEquals(expected, actual);
    }
}
