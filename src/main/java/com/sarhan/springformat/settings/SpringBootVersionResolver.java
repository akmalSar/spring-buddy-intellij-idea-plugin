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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the Spring Boot version a project/module targets, either from the
 * explicit per-project setting or by auto-detecting it from the module classpath.
 * Detection uses the exact Maven coordinates of the {@code spring-boot} artifact
 * first (no jar-name heuristics) and falls back to a Boot-4-only marker class,
 * then to Boot 3.
 *
 * @author Akmal Sarhan
 */
public final class SpringBootVersionResolver {

    /**
     * Exact coordinates of the core Boot artifact, used to read the resolved
     * version from the module classpath.
     */
    public static final String SPRING_BOOT_COORDINATES = "org.springframework.boot:spring-boot";

    /**
     * Class that only exists since the Boot 4 modularisation; used as a fallback
     * marker when no explicit library version is found.
     */
    private static final String BOOT_4_MARKER_CLASS = "org.springframework.boot.persistence.autoconfigure.EntityScan";

    private static final Key<CachedValue<SpringBootTarget>> TARGET_CACHE_KEY =
            Key.create("spring.buddy.boot.target.cache");

    private SpringBootVersionResolver() {
    }

    /**
     * Gate helper for inspections: reports whether the target for the given
     * context is at least the given Boot major version.
     */
    public static boolean isAtLeast(@NotNull PsiElement context, @NotNull SpringBootTarget minimum) {
        return resolve(context).isAtLeast(minimum);
    }

    /**
     * Gate helper for inspections: reports whether the target for the given
     * context is exactly the given Boot major version.
     */
    public static boolean isTarget(@NotNull PsiElement context, @NotNull SpringBootTarget required) {
        return resolve(context) == required;
    }

    /**
     * Gate for the {@code spring-boot 3 -> 4} migration inspections. Migration
     * checks run before the upgrade, which is where they matter, so auto-detect
     * mode keeps them active regardless of the detected classpath. They stay
     * silent only when the project is explicitly pinned to Boot 3.x, or before
     * the project is initialised (no reliable target yet).
     */
    public static boolean migrationChecksActive(@NotNull Project project) {
        if (!project.isInitialized()) {
            return false;
        }
        return SpringBuddyProjectSettingsState.getInstance(project).getTargetMode() != SpringBootTargetMode.BOOT_3;
    }

    /**
     * Gate for the {@code spring-boot 3 -> 4} migration inspections, resolved from
     * the project of the given PSI context.
     */
    public static boolean migrationChecksActive(@NotNull PsiElement context) {
        return migrationChecksActive(context.getProject());
    }

    @NotNull
    public static SpringBootTarget resolve(@NotNull PsiElement context) {
        Project project = context.getProject();
        if (!project.isInitialized()) {
            return SpringBootTarget.BOOT_3;
        }
        Module module = ModuleUtilCore.findModuleForPsiElement(context);
        return resolve(project, module);
    }

    @NotNull
    public static SpringBootTarget resolve(@NotNull Project project, @Nullable Module module) {
        SpringBootTargetMode mode = SpringBuddyProjectSettingsState.getInstance(project).getTargetMode();
        if (mode == SpringBootTargetMode.BOOT_3) {
            return SpringBootTarget.BOOT_3;
        }
        if (mode == SpringBootTargetMode.BOOT_4) {
            return SpringBootTarget.BOOT_4;
        }
        if (module == null || !module.isLoaded()) {
            return SpringBootTarget.BOOT_3;
        }
        return cachedTarget(project, module);
    }

    @NotNull
    public static SpringBootTarget resolve(@NotNull Module module) {
        return resolve(module.getProject(), module);
    }

    /**
     * Detected Boot version string for the first module that carries the Boot
     * core artifact, for display in the settings page. Returns null when Boot
     * could not be detected.
     */
    @Nullable
    public static String detectedVersion(@NotNull Project project) {
        if (!project.isInitialized()) {
            return null;
        }
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            if (module.isLoaded()) {
                String version = libraryVersion(module);
                if (version != null) {
                    return version;
                }
            }
        }
        return null;
    }

    @NotNull
    private static SpringBootTarget cachedTarget(@NotNull Project project, @NotNull Module module) {
        CachedValueProvider<SpringBootTarget> provider = () -> CachedValueProvider.Result.create(
                SpringBootVersionResolver.detect(module),
                ProjectRootManager.getInstance(project),
                (ModificationTracker) () -> SpringBuddyProjectSettingsState
                        .getInstance(project).getModificationCount());
        return CachedValuesManager.getManager(project)
                .getCachedValue(module, TARGET_CACHE_KEY, provider, false);
    }

    @NotNull
    static SpringBootTarget detect(@NotNull Module module) {
        String version = libraryVersion(module);
        if (version != null) {
            return SpringBootTarget.fromVersionString(version);
        }
        return hasBoot4Marker(module) ? SpringBootTarget.BOOT_4 : SpringBootTarget.BOOT_3;
    }

    @Nullable
    static String libraryVersion(@NotNull Module module) {
        try {
            return com.intellij.java.library.JavaLibraryUtil
                    .getLibraryVersion(module, SPRING_BOOT_COORDINATES);
        }
        catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean hasBoot4Marker(@NotNull Module module) {
        try {
            return JavaPsiFacade.getInstance(module.getProject())
                    .findClass(BOOT_4_MARKER_CLASS, module.getModuleWithLibrariesScope()) != null;
        }
        catch (RuntimeException ex) {
            return false;
        }
    }
}
