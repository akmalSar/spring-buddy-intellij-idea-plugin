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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class ArchitectureConfigServiceTest extends BasePlatformTestCase {

    private static final String VALID = """
            { "version": 1, "presets": { "domainPurity": "error" },
              "rules": [ { "id": "r1", "from": "a..", "mustNotDependOn": "b.." } ] }
            """;

    public void testMissingFileYieldsPresetsOnly() {
        ArchitectureConfig config = service().getConfig();
        assertFalse(config.fromFile());
        assertNull(config.error());
        assertTrue(config.rules().isEmpty());
        assertEquals(ArchitectureSeverity.OFF, config.presetSeverity(ArchitectureConfig.PRESET_DOMAIN_PURITY));
        assertEquals(ArchitectureSeverity.WARNING,
                config.presetSeverity(ArchitectureConfig.PRESET_CONTROLLER_BOUNDARY));
    }

    public void testLoadsValidFileAndCachesByStamp() {
        this.myFixture.addFileToProject(ArchitectureConfigService.CONFIG_PATH, VALID);
        ArchitectureConfig first = service().getConfig();
        assertTrue(first.fromFile());
        assertEquals(1, first.rules().size());
        assertEquals(ArchitectureSeverity.ERROR, first.presetSeverity(ArchitectureConfig.PRESET_DOMAIN_PURITY));
        assertSame(first, service().getConfig());
    }

    public void testReloadsAfterChange() throws IOException {
        PsiFile file = this.myFixture.addFileToProject(ArchitectureConfigService.CONFIG_PATH, VALID);
        assertEquals(1, service().getConfig().rules().size());
        write(file.getVirtualFile(), "{ \"version\": 1 }");
        assertTrue(service().getConfig().rules().isEmpty());
    }

    public void testMalformedFileFallsBackAndNotifiesOncePerRevision() throws IOException {
        // The light project (and its service) is shared between tests, so count deltas.
        int before = service().getNotificationCount();
        PsiFile file = this.myFixture.addFileToProject(ArchitectureConfigService.CONFIG_PATH, "{ broken");
        ArchitectureConfig config = service().getConfig();
        assertFalse(config.fromFile());
        assertNotNull(config.error());
        service().getConfig();
        service().getConfig();
        assertEquals(before + 1, service().getNotificationCount());

        write(file.getVirtualFile(), "{ \"version\": 7 }");
        assertTrue(service().getConfig().error().contains("unsupported version"));
        assertEquals(before + 2, service().getNotificationCount());

        write(file.getVirtualFile(), VALID);
        assertTrue(service().getConfig().fromFile());
        assertEquals(before + 2, service().getNotificationCount());
    }

    public void testRequestsProfileResyncWhenConfiguredStateChanges() throws IOException {
        service().getConfig();
        int before = service().getResyncCount();
        PsiFile file = this.myFixture.addFileToProject(ArchitectureConfigService.CONFIG_PATH, VALID);
        service().getConfig();
        service().getConfig();
        assertEquals("valid file appeared", before + 1, service().getResyncCount());

        write(file.getVirtualFile(), "{ \"version\": 1, \"rules\": [] }");
        service().getConfig();
        assertEquals("still a valid file", before + 1, service().getResyncCount());

        write(file.getVirtualFile(), "{ broken");
        service().getConfig();
        assertEquals("file became invalid", before + 2, service().getResyncCount());
    }

    private ArchitectureConfigService service() {
        return ArchitectureConfigService.getInstance(getProject());
    }

    private static void write(VirtualFile file, String text) throws IOException {
        WriteAction.runAndWait(() -> file.setBinaryContent(text.getBytes(StandardCharsets.UTF_8)));
    }
}
