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

package com.sarhan.springformat.settings;

import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.assertj.core.api.Assertions;

/**
 * Tests for {@link SpringBootVersionResolver}.
 *
 * @author Akmal Sarhan
 */
public class SpringBootVersionResolverTests extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        SpringBuddyProjectSettingsState.getInstance(getProject()).setTargetMode(SpringBootTargetMode.AUTO);
        super.tearDown();
    }

    public void testResolve_explicitBoot3Mode_returnsBoot3() {
        SpringBuddyProjectSettingsState.getInstance(getProject()).setTargetMode(SpringBootTargetMode.BOOT_3);

        SpringBootTarget target = SpringBootVersionResolver.resolve(getProject(), null);

        Assertions.assertThat(target)
                .as("explicit BOOT_3 mode wins without a module")
                .isEqualTo(SpringBootTarget.BOOT_3);
    }

    public void testResolve_explicitBoot4Mode_returnsBoot4() {
        SpringBuddyProjectSettingsState.getInstance(getProject()).setTargetMode(SpringBootTargetMode.BOOT_4);

        SpringBootTarget target = SpringBootVersionResolver.resolve(getProject(), null);

        Assertions.assertThat(target)
                .as("explicit BOOT_4 mode wins without a module")
                .isEqualTo(SpringBootTarget.BOOT_4);
    }

    public void testResolve_autoModeWithoutModuleLibraries_fallsBackToBoot3() {
        SpringBuddyProjectSettingsState.getInstance(getProject()).setTargetMode(SpringBootTargetMode.AUTO);

        SpringBootTarget target = SpringBootVersionResolver.resolve(getProject(), null);

        Assertions.assertThat(target)
                .as("auto mode without classpath falls back to Boot 3")
                .isEqualTo(SpringBootTarget.BOOT_3);
    }

    public void testResolve_autoModeWithBoot4Marker_returnsBoot4() {
        SpringBuddyProjectSettingsState.getInstance(getProject()).setTargetMode(SpringBootTargetMode.AUTO);
        myFixture.addFileToProject("org/springframework/boot/persistence/autoconfigure/EntityScan.java",
                "package org.springframework.boot.persistence.autoconfigure;\n"
                        + "public @interface EntityScan { }\n");

        SpringBootTarget target = SpringBootVersionResolver.resolve(getProject(), getModule());

        Assertions.assertThat(target)
                .as("Boot 4 marker class on module classpath flips auto-detection to Boot 4")
                .isEqualTo(SpringBootTarget.BOOT_4);
    }

    public void testResolve_autoModeMarkerResolvedPerPsiElement_usesModuleOfClass() {
        SpringBuddyProjectSettingsState.getInstance(getProject()).setTargetMode(SpringBootTargetMode.AUTO);
        myFixture.addFileToProject("org/springframework/boot/persistence/autoconfigure/EntityScan.java",
                "package org.springframework.boot.persistence.autoconfigure;\n"
                        + "public @interface EntityScan { }\n");

        SpringBootTarget target = SpringBootVersionResolver
                .resolve(myFixture.addFileToProject("src/Sample.java", "class Sample { }\n"));

        Assertions.assertThat(target)
                .as("resolver resolves per PSI element module")
                .isEqualTo(SpringBootTarget.BOOT_4);
    }

    public void testFromMajor_mapsUnknownMajorsToNearestSupported() {
        Assertions.assertThat(SpringBootTarget.fromMajor(2).major())
                .as("pre-3 majors clamp to Boot 3")
                .isEqualTo(3);
        Assertions.assertThat(SpringBootTarget.fromMajor(3).major())
                .as("Boot 3 stays Boot 3")
                .isEqualTo(3);
        Assertions.assertThat(SpringBootTarget.fromMajor(4).major())
                .as("Boot 4 stays Boot 4")
                .isEqualTo(4);
        Assertions.assertThat(SpringBootTarget.fromMajor(5).major())
                .as("future majors clamp to Boot 4")
                .isEqualTo(4);
    }

    public void testFromVersionString_parsesLeadingMajor() {
        Assertions.assertThat(SpringBootTarget.fromVersionString("4.0.1"))
                .as("4.0.1 maps to Boot 4")
                .isEqualTo(SpringBootTarget.BOOT_4);
        Assertions.assertThat(SpringBootTarget.fromVersionString("3.5.3"))
                .as("3.5.3 maps to Boot 3")
                .isEqualTo(SpringBootTarget.BOOT_3);
        Assertions.assertThat(SpringBootTarget.fromVersionString(null))
                .as("null version falls back to Boot 3")
                .isEqualTo(SpringBootTarget.BOOT_3);
    }

    public void testIsAtLeast_gateMatchesTarget() {
        SpringBuddyProjectSettingsState.getInstance(getProject()).setTargetMode(SpringBootTargetMode.BOOT_4);
        com.intellij.psi.PsiElement context = myFixture.addFileToProject("Sample.java", "class Sample { }\n");

        Assertions.assertThat(SpringBootVersionResolver.isAtLeast(context, SpringBootTarget.BOOT_4))
                .as("Boot 4 context satisfies Boot 4 gate")
                .isTrue();
        Assertions.assertThat(SpringBootVersionResolver.isAtLeast(context, SpringBootTarget.BOOT_3))
                .as("Boot 4 context satisfies Boot 3 gate")
                .isTrue();
    }

    public void testMigrationChecksAuto_autoMode_isActiveWithoutClasspath() {
        SpringBuddyProjectSettingsState.getInstance(getProject()).setTargetMode(SpringBootTargetMode.AUTO);
        PsiElement context = myFixture.addFileToProject("Sample.java", "class Sample { }\n");

        Assertions.assertThat(SpringBootVersionResolver.migrationChecksActive(context))
                .as("auto-detect keeps the migration pre-flight active on a Boot 3 classpath")
                .isTrue();
    }

    public void testMigrationChecksAuto_explicitBoot3_isInactive() {
        SpringBuddyProjectSettingsState.getInstance(getProject()).setTargetMode(SpringBootTargetMode.BOOT_3);
        PsiElement context = myFixture.addFileToProject("Sample.java", "class Sample { }\n");

        Assertions.assertThat(SpringBootVersionResolver.migrationChecksActive(context))
                .as("explicit Boot 3 target silences the migration checks")
                .isFalse();
    }

    public void testMigrationChecksAuto_explicitBoot4_isActive() {
        SpringBuddyProjectSettingsState.getInstance(getProject()).setTargetMode(SpringBootTargetMode.BOOT_4);
        PsiElement context = myFixture.addFileToProject("Sample.java", "class Sample { }\n");

        Assertions.assertThat(SpringBootVersionResolver.migrationChecksActive(context))
                .as("explicit Boot 4 target keeps the migration checks active")
                .isTrue();
    }
}
