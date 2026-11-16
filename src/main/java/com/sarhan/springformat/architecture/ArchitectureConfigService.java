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
import java.util.function.Supplier;

import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.sarhan.springformat.settings.SpringBuddySettingsSynchronizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Loads and caches {@code .spring-buddy/architecture.json} from the project
 * directory. The cache is keyed on the file path and its modification stamp
 * (document stamp for unsaved edits), so edits, creation and deletion are
 * picked up on the next inspection run. A malformed file yields the built-in
 * presets and exactly one notification per file revision. Never throws.
 * <p>
 * Whether a valid file exists decides if architecture tools are exempt from
 * the minimum-level filter, so the inspection profile is re-synchronized
 * whenever that changes (file created, deleted, broken or fixed).
 *
 * @author Akmal Sarhan
 */
@Service(Service.Level.PROJECT)
public final class ArchitectureConfigService {

    public static final String CONFIG_PATH = ".spring-buddy/architecture.json";

    public static final String BASELINE_PATH = ".spring-buddy/architecture-baseline.json";

    public static final String NOTIFICATION_GROUP = "Spring Buddy";

    private String cachedBaselineKey;

    private ArchitectureBaseline cachedBaseline = ArchitectureBaseline.empty();

    private String notifiedBaselineKey;

    private volatile int baselineSuspensions;

    private final Project project;

    private String cachedKey;

    private ArchitectureConfig cachedConfig = ArchitectureConfig.presetsOnly();

    private String notifiedKey;

    private int notificationCount;

    private Boolean lastFromFile;

    private int resyncCount;

    public ArchitectureConfigService(@NotNull Project project) {
        this.project = project;
    }

    public static ArchitectureConfigService getInstance(@NotNull Project project) {
        return project.getService(ArchitectureConfigService.class);
    }

    /**
     * Returns the current configuration; built-in presets when the file is
     * missing, unreadable or malformed.
     */
    @NotNull
    public synchronized ArchitectureConfig getConfig() {
        try {
            VirtualFile file = findConfigFile();
            if (file == null) {
                this.cachedKey = null;
                this.cachedConfig = ArchitectureConfig.presetsOnly();
                resyncIfConfiguredStateChanged(false);
                return this.cachedConfig;
            }
            Document document = FileDocumentManager.getInstance().getCachedDocument(file);
            long stamp = (document != null) ? document.getModificationStamp() : file.getModificationStamp();
            String key = file.getPath() + '@' + stamp;
            if (key.equals(this.cachedKey)) {
                return this.cachedConfig;
            }
            String text = (document != null) ? document.getText() : VfsUtilCore.loadText(file);
            ArchitectureConfig config = ArchitectureConfigParser.parse(text);
            this.cachedKey = key;
            this.cachedConfig = config;
            if (config.error() != null) {
                notifyMalformed(key, config.error());
            }
            resyncIfConfiguredStateChanged(config.fromFile());
            return config;
        }
        catch (IOException | RuntimeException ex) {
            return ArchitectureConfig.presetsOnly();
        }
    }

    /**
     * Returns the baseline; empty when the file is missing. A malformed file
     * yields an empty baseline (every finding counts as new) and one
     * notification per file revision. Never throws.
     */
    @NotNull
    public synchronized ArchitectureBaseline getBaseline() {
        try {
            VirtualFile file = findFile(BASELINE_PATH);
            if (file == null) {
                this.cachedBaselineKey = null;
                this.cachedBaseline = ArchitectureBaseline.empty();
                return this.cachedBaseline;
            }
            Document document = FileDocumentManager.getInstance().getCachedDocument(file);
            long stamp = (document != null) ? document.getModificationStamp() : file.getModificationStamp();
            String key = file.getPath() + '@' + stamp;
            if (key.equals(this.cachedBaselineKey)) {
                return this.cachedBaseline;
            }
            String text = (document != null) ? document.getText() : VfsUtilCore.loadText(file);
            ArchitectureBaseline baseline = ArchitectureBaseline.parse(text);
            this.cachedBaselineKey = key;
            this.cachedBaseline = baseline;
            if (baseline.error() != null && !key.equals(this.notifiedBaselineKey)) {
                this.notifiedBaselineKey = key;
                notify("Spring Buddy: invalid " + BASELINE_PATH,
                        baseline.error() + ". All architecture violations are reported as new.");
            }
            return baseline;
        }
        catch (IOException | RuntimeException ex) {
            return ArchitectureBaseline.empty();
        }
    }

    /**
     * Runs the action with the baseline ignored, so every current finding is
     * reported with its configured severity; used to generate the baseline.
     */
    public <T> T withoutBaseline(@NotNull Supplier<T> action) {
        synchronized (this) {
            this.baselineSuspensions++;
        }
        try {
            return action.get();
        }
        finally {
            synchronized (this) {
                this.baselineSuspensions--;
            }
        }
    }

    public boolean isBaselineSuspended() {
        return this.baselineSuspensions > 0;
    }

    private void resyncIfConfiguredStateChanged(boolean fromFile) {
        if (this.lastFromFile != null && this.lastFromFile == fromFile) {
            return;
        }
        boolean firstCheckWithoutFile = this.lastFromFile == null && !fromFile;
        this.lastFromFile = fromFile;
        if (firstCheckWithoutFile) {
            return;
        }
        this.resyncCount++;
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            // The light test project's profile is shared by all tests; syncing it
            // would enable every bundled tool there.
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> SpringBuddySettingsSynchronizer.syncProject(this.project),
                this.project.getDisposed());
    }

    @Nullable
    private VirtualFile findConfigFile() {
        return findFile(CONFIG_PATH);
    }

    @Nullable
    private VirtualFile findFile(String relativePath) {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(this.project);
        if (baseDir == null || !baseDir.isValid()) {
            return null;
        }
        VirtualFile file = baseDir.findFileByRelativePath(relativePath);
        return (file != null && file.isValid() && !file.isDirectory()) ? file : null;
    }

    private void notifyMalformed(String key, String error) {
        if (key.equals(this.notifiedKey)) {
            return;
        }
        this.notifiedKey = key;
        notify("Spring Buddy: invalid " + CONFIG_PATH, error + ". Architecture rules fall back to the built-in presets.");
    }

    private void notify(String title, String content) {
        this.notificationCount++;
        try {
            NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP);
            if (group != null) {
                group.createNotification(title, content, NotificationType.WARNING).notify(this.project);
            }
        }
        catch (RuntimeException ignored) {
            // A notification failure must never break inspections.
        }
    }

    /**
     * Number of profile re-synchronizations requested so far; visible for tests.
     */
    synchronized int getResyncCount() {
        return this.resyncCount;
    }

    /**
     * Number of malformed-config notifications issued so far; visible for tests.
     */
    synchronized int getNotificationCount() {
        return this.notificationCount;
    }
}
