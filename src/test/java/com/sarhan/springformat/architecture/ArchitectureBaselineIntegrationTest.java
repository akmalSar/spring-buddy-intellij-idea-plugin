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

package com.sarhan.springformat.architecture;

import java.util.List;
import java.util.Map;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sarhan.springformat.RunSpringFormatInspectionsActionTestAccess;
import com.sarhan.springformat.inspection.ForbiddenPackageDependencyInspection;

/**
 * Baseline / ratchet behaviour: generation, fingerprint stability, baselined
 * findings as WEAK WARNING or hidden, new findings keep their severity and
 * block the pre-commit gate, stale entries.
 *
 * @author Akmal Sarhan
 */
public class ArchitectureBaselineIntegrationTest extends BasePlatformTestCase {

    private static final String CONFIG = """
            { "version": 1, "rules": [ { "id": "domain-no-web", "from": "com.acme.domain..",
              "mustNotDependOn": "com.acme.web..", "severity": "error" } ] }
            """;

    private static final String ORDER = """
            package com.acme.domain;
            import com.acme.web.Api;
            public class Order { Api api; }
            """;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new ForbiddenPackageDependencyInspection());
        this.myFixture.addFileToProject("com/acme/web/Api.java", "package com.acme.web; public class Api { }");
        this.myFixture.addFileToProject("com/acme/web/Other.java", "package com.acme.web; public class Other { }");
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", CONFIG);
    }

    public void testGeneratedBaselineDowngradesKnownViolationToWeakWarning() {
        PsiFile order = this.myFixture.addFileToProject("com/acme/domain/Order.java", ORDER);
        writeBaseline(order);

        List<HighlightInfo> infos = architectureInfos(order);
        assertEquals(1, infos.size());
        assertEquals(HighlightSeverity.WEAK_WARNING, infos.get(0).getSeverity());
        assertTrue(infos.get(0).getDescription(), infos.get(0).getDescription().endsWith("[baselined]"));
        assertTrue(collectGate(order).isEmpty());
    }

    public void testNewViolationKeepsSeverityAndBlocksGate() {
        PsiFile order = this.myFixture.addFileToProject("com/acme/domain/Order.java", ORDER);
        writeBaseline(order);
        put("com/acme/domain/Order.java", """
                package com.acme.domain;
                import com.acme.web.Api;
                import com.acme.web.Other;
                public class Order { Api api; Other other; }
                """);

        List<HighlightInfo> infos = architectureInfos(order);
        assertEquals(2, infos.size());
        long errors = infos.stream().filter((HighlightInfo info) -> info.getSeverity() == HighlightSeverity.ERROR)
                .count();
        assertEquals(1, errors);
        List<String> gate = collectGate(order).get("Order.java");
        assertEquals(1, gate.size());
        assertTrue(gate.get(0), gate.get(0).contains("com.acme.web.Other"));
    }

    public void testFingerprintSurvivesLineShifts() {
        PsiFile order = this.myFixture.addFileToProject("com/acme/domain/Order.java", ORDER);
        writeBaseline(order);
        put("com/acme/domain/Order.java", """
                package com.acme.domain;


                // moved down by a comment and blank lines
                import com.acme.web.Api;
                public class Order {
                    int unrelated;
                    Api api;
                }
                """);
        List<HighlightInfo> infos = architectureInfos(order);
        assertEquals(1, infos.size());
        assertEquals(HighlightSeverity.WEAK_WARNING, infos.get(0).getSeverity());
    }

    public void testBaselinedSeverityOffHidesKnownViolations() {
        put(".spring-buddy/architecture.json",
                CONFIG.replace("\"version\": 1,", "\"version\": 1, \"baselinedSeverity\": \"off\","));
        PsiFile order = this.myFixture.addFileToProject("com/acme/domain/Order.java", ORDER);
        writeBaseline(order);
        assertTrue(architectureInfos(order).isEmpty());
    }

    public void testStaleEntriesAreReportedAfterFix() {
        PsiFile order = this.myFixture.addFileToProject("com/acme/domain/Order.java", ORDER);
        writeBaseline(order);
        put("com/acme/domain/Order.java", """
                package com.acme.domain;
                public class Order { }
                """);
        ArchitectureBaselineGenerator.Result result = ArchitectureBaselineGenerator.run(getProject(),
                List.of(order.getVirtualFile()));
        assertEquals(1, result.stale().size());
        assertEquals("domain-no-web", result.stale().get(0).rule());
        assertEquals("com/acme/domain/Order.java", result.stale().get(0).file());
        assertEquals(0, result.newFindings());
        assertTrue(result.current().entries().isEmpty());
    }

    public void testMalformedBaselineReportsEverythingAsNew() {
        PsiFile order = this.myFixture.addFileToProject("com/acme/domain/Order.java", ORDER);
        this.myFixture.addFileToProject(ArchitectureConfigService.BASELINE_PATH, "{ broken");
        List<HighlightInfo> infos = architectureInfos(order);
        assertEquals(1, infos.size());
        assertEquals(HighlightSeverity.ERROR, infos.get(0).getSeverity());
    }

    private void writeBaseline(PsiFile file) {
        ArchitectureBaselineGenerator.Result result = ArchitectureBaselineGenerator.run(getProject(),
                List.of(file.getVirtualFile()));
        assertEquals(1, result.current().entries().size());
        assertEquals("com/acme/domain/Order.java", result.current().entries().get(0).file());
        put(ArchitectureConfigService.BASELINE_PATH, result.current().toJson());
    }

    private void put(String path, String text) {
        VirtualFile existing = this.myFixture.getTempDirFixture().getFile(path);
        if (existing == null) {
            this.myFixture.addFileToProject(path, text);
            return;
        }
        try {
            WriteAction.runAndWait(() -> VfsUtil.saveText(existing, text));
        }
        catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    }

    private List<HighlightInfo> architectureInfos(PsiFile file) {
        this.myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        return this.myFixture.doHighlighting().stream()
                .filter((HighlightInfo info) -> info.getDescription() != null
                        && info.getDescription().startsWith("[SB-ARCH-"))
                .toList();
    }

    private Map<String, List<String>> collectGate(PsiFile file) {
        ScopeToolState state = new ScopeToolState("Test",
                new LocalInspectionToolWrapper(new ForbiddenPackageDependencyInspection()), true,
                HighlightDisplayLevel.WARNING);
        return RunSpringFormatInspectionsActionTestAccess.collect(getProject(), file.getVirtualFile(), state);
    }
}
