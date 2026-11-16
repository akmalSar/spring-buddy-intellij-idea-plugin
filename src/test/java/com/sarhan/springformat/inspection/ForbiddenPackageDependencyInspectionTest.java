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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class ForbiddenPackageDependencyInspectionTest extends BasePlatformTestCase {

    private static final String CONFIG = """
            {
              "version": 1,
              "layers": {
                "domain": ["com.acme.domain.."],
                "web": ["com.acme.web.."]
              },
              "rules": [
                {
                  "id": "domain-no-web",
                  "from": "domain",
                  "mustNotDependOn": "web",
                  "severity": "error",
                  "message": "Domain must not depend on web",
                  "except": ["com.acme.web.shared.."]
                }
              ]
            }
            """;

    private static final String MSG = "[SB-ARCH-001 domain-no-web] Forbidden dependency on ";

    private static final String DEFAULT_FIX = "Move this code into a layer that may use the dependency, or depend "
            + "on an interface owned by this layer and implement it in the layer that may. Suppressing or working "
            + "around this check is not a fix.";

    private static final String END = ". Why: Domain must not depend on web. Fix: Move this code into a layer that "
            + "may use the dependency, or depend on an interface owned by this layer and implement it in the layer "
            + "that may. Suppressing or working around this check is not a fix.";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new ForbiddenPackageDependencyInspection());
        this.myFixture.addFileToProject("com/acme/web/OrderController.java", """
                package com.acme.web;
                public class OrderController {
                    public static final String PATH = "/orders";
                    public static class Nested { }
                }
                """);
        this.myFixture.addFileToProject("com/acme/web/WebAnnotation.java", """
                package com.acme.web;
                public @interface WebAnnotation { }
                """);
        this.myFixture.addFileToProject("com/acme/web/Response.java", """
                package com.acme.web;
                public interface Response<T> { }
                """);
        this.myFixture.addFileToProject("com/acme/web/shared/WebDto.java", """
                package com.acme.web.shared;
                public class WebDto { }
                """);
    }

    public void testReportsSingleTypeImportOnceAtImport() {
        addConfig(CONFIG);
        checkDomainFile("""
                package com.acme.domain;
                import <error descr="%sweb.OrderController%e">com.acme.web.OrderController</error>;
                public class Order {
                    OrderController first;
                    OrderController second() { return null; }
                }
                """);
    }

    public void testReportsWildcardImportOnceAndNotItsUsages() {
        addConfig(CONFIG);
        checkDomainFile("""
                package com.acme.domain;
                import <error descr="%sweb.*%e">com.acme.web</error>.*;
                public class Order {
                    OrderController controller;
                    Response<Order> response;
                }
                """);
    }

    public void testReportsFullyQualifiedNamesAnnotationsGenericsAndInheritance() {
        addConfig(CONFIG);
        checkDomainFile("""
                package com.acme.domain;
                @<error descr="%sweb.WebAnnotation%e">com.acme.web.WebAnnotation</error>
                public class Order implements <error descr="%sweb.Response%e">com.acme.web.Response</error><Box<<error descr="%sweb.OrderController.Nested%e">com.acme.web.OrderController.Nested</error>>> {
                    com.acme.web.OrderController.Nested nested(<error descr="%sweb.OrderController%e">com.acme.web.OrderController</error> controller) { return null; }
                }
                class Box<T> { }
                """);
    }

    public void testReportsStaticMemberQualifier() {
        addConfig(CONFIG);
        checkDomainFile("""
                package com.acme.domain;
                public class Order {
                    String path = <error descr="%sweb.OrderController%e">com.acme.web.OrderController</error>.PATH;
                }
                """);
    }

    public void testExceptPatternExemptsTarget() {
        addConfig(CONFIG);
        checkDomainFile("""
                package com.acme.domain;
                import com.acme.web.shared.WebDto;
                public class Order {
                    WebDto dto;
                }
                """);
    }

    public void testExceptPatternExemptsSourceClass() {
        addConfig("""
                { "version": 1, "rules": [ { "id": "domain-no-web", "from": "com.acme.domain..",
                  "mustNotDependOn": "com.acme.web..", "except": ["com.acme.domain.Order"] } ] }
                """);
        checkDomainFile("""
                package com.acme.domain;
                import com.acme.web.OrderController;
                public class Order {
                    OrderController controller;
                }
                """);
    }

    public void testDoesNotReportUnresolvedTypes() {
        addConfig(CONFIG);
        checkDomainFile("""
                package com.acme.domain;
                import com.acme.web.Missing;
                import com.acme.missing.*;
                public class Order {
                    Missing missing;
                    com.acme.web.Unknown unknown;
                }
                """);
    }

    public void testDoesNotReportClassesOutsideFromPackages() {
        addConfig(CONFIG);
        String content = """
                package com.acme.service;
                import com.acme.web.OrderController;
                public class OrderService {
                    OrderController controller;
                }
                """;
        checkFile(this.myFixture.addFileToProject("com/acme/service/OrderService.java", content), content);
    }

    public void testReportsWarningSeverityFromRule() {
        addConfig("""
                { "version": 1, "rules": [ { "id": "domain-no-web", "from": "com.acme.domain..",
                  "mustNotDependOn": "com.acme.web..", "severity": "warning" } ] }
                """);
        checkDomainFile("""
                package com.acme.domain;
                public class Order {
                    <warning descr="[SB-ARCH-001 domain-no-web] Forbidden dependency on com.acme.web.OrderController. Fix: %FIX%">com.acme.web.OrderController</warning> controller;
                }
                """);
    }

    public void testOffRuleReportsNothing() {
        addConfig("""
                { "version": 1, "rules": [ { "id": "domain-no-web", "from": "com.acme.domain..",
                  "mustNotDependOn": "com.acme.web..", "severity": "off" } ] }
                """);
        checkDomainFile("""
                package com.acme.domain;
                import com.acme.web.OrderController;
                public class Order { OrderController controller; }
                """);
    }

    public void testMissingConfigReportsNothing() {
        checkDomainFile("""
                package com.acme.domain;
                import com.acme.web.OrderController;
                public class Order { OrderController controller; }
                """);
    }

    public void testMalformedConfigReportsNothing() {
        addConfig("{ \"version\": 1, \"rules\": [ { \"id\": \"broken\" } ] }");
        checkDomainFile("""
                package com.acme.domain;
                import com.acme.web.OrderController;
                public class Order { OrderController controller; }
                """);
    }

    public void testSkipsTestSourcesByDefault() throws java.io.IOException {
        addConfig(CONFIG);
        VirtualFile testRoot = createTestRoot();
        try {
            String content = """
                    package com.acme.domain;
                    import com.acme.web.OrderController;
                    public class OrderTest { OrderController controller; }
                    """;
            checkFile(this.myFixture.addFileToProject("testsrc/com/acme/domain/OrderTest.java", content), content);
        }
        finally {
            PsiTestUtil.removeSourceRoot(getModule(), testRoot);
        }
    }

    public void testChecksTestSourcesWhenIncluded() throws java.io.IOException {
        addConfig(CONFIG.replace("\"version\": 1,", "\"version\": 1, \"includeTests\": true,"));
        VirtualFile testRoot = createTestRoot();
        try {
            String content = """
                    package com.acme.domain;
                    import <error descr="%sweb.OrderController%e">com.acme.web.OrderController</error>;
                    public class OrderTest { OrderController controller; }
                    """.replace("%s", MSG + "com.acme.").replace("%e", END);
            checkFile(this.myFixture.addFileToProject("testsrc/com/acme/domain/OrderTest.java", content), content);
        }
        finally {
            PsiTestUtil.removeSourceRoot(getModule(), testRoot);
        }
    }

    private VirtualFile createTestRoot() throws java.io.IOException {
        VirtualFile root = this.myFixture.getTempDirFixture().findOrCreateDir("testsrc");
        PsiTestUtil.addSourceRoot(getModule(), root, true);
        return root;
    }

    private void addConfig(String json) {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", json);
    }

    /**
     * Checks the markup and additionally that the number of architecture
     * problems equals the number of expected ones, because
     * {@code ignoreExtraHighlighting} would otherwise hide extra reports.
     */
    private void checkDomainFile(String text) {
        String content = text.replace("%s", MSG + "com.acme.").replace("%e", END).replace("%FIX%", DEFAULT_FIX);
        PsiFile file = this.myFixture.addFileToProject("com/acme/domain/Order.java", content);
        this.myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        this.myFixture.checkHighlighting(true, false, true, true);
        assertArchitectureProblemCount(countMarkers(content));
    }

    private void checkFile(PsiFile file, String content) {
        this.myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        this.myFixture.checkHighlighting(true, false, true, true);
        assertArchitectureProblemCount(countMarkers(content));
    }

    private static int countMarkers(String content) {
        int count = 0;
        int index = content.indexOf("descr=\"[SB-ARCH-");
        while (index >= 0) {
            count++;
            index = content.indexOf("descr=\"[SB-ARCH-", index + 1);
        }
        return count;
    }

    private void assertArchitectureProblemCount(int expected) {
        List<HighlightInfo> infos = this.myFixture.doHighlighting();
        long actual = infos.stream()
                .filter((HighlightInfo info) -> info.getDescription() != null
                        && info.getDescription().startsWith("[SB-ARCH-"))
                .count();
        assertEquals("architecture problems: " + infos, expected, actual);
    }
}
