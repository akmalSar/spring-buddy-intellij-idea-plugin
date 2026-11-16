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
import java.util.List;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

/**
 * Generates or refreshes {@code .spring-buddy/architecture-baseline.json}
 * from all current architecture findings, or only checks it, and reports
 * stale baseline entries (accepted violations that no longer exist) and new
 * violations.
 *
 * @author Akmal Sarhan
 */
public class ArchitectureBaselineAction extends AnAction {

    private final boolean write;

    /**
     * Generate / refresh action.
     */
    public ArchitectureBaselineAction() {
        this(true);
    }

    protected ArchitectureBaselineAction(boolean write) {
        this.write = write;
    }

    /**
     * Reports stale entries and new violations without changing the baseline.
     *
     * @author Akmal Sarhan
     */
    public static class Check extends ArchitectureBaselineAction {

        public Check() {
            super(false);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        String title = this.write ? "Spring Buddy: Updating Architecture Baseline"
                : "Spring Buddy: Checking Architecture Baseline";
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ApplicationManager.getApplication().invokeAndWait(() -> {
                    FileDocumentManager.getInstance().saveAllDocuments();
                    PsiDocumentManager.getInstance(project).commitAllDocuments();
                });
                ArchitectureBaselineGenerator.Result result = DumbService.getInstance(project)
                        .runReadActionInSmartMode(() -> ArchitectureBaselineGenerator.run(project));
                if (ArchitectureBaselineAction.this.write) {
                    ApplicationManager.getApplication().invokeLater(() -> writeAndNotify(project, result),
                            project.getDisposed());
                }
                else {
                    boolean clean = result.newFindings() == 0 && result.stale().isEmpty();
                    showNotification(project, checkMessage(result),
                            clean ? NotificationType.INFORMATION : NotificationType.WARNING);
                }
            }
        });
    }

    private static void writeAndNotify(Project project, ArchitectureBaselineGenerator.Result result) {
        try {
            writeBaseline(project, result.current());
            showNotification(project, writeMessage(result), NotificationType.INFORMATION);
        }
        catch (IOException | RuntimeException ex) {
            showNotification(project,
                    "Could not write " + ArchitectureConfigService.BASELINE_PATH + ": " + ex.getMessage(),
                    NotificationType.ERROR);
        }
    }

    /**
     * Writes the baseline file, creating {@code .spring-buddy} when needed.
     * Must run on the EDT.
     */
    public static VirtualFile writeBaseline(Project project, ArchitectureBaseline baseline) throws IOException {
        VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
        if (baseDir == null) {
            throw new IOException("project directory not found");
        }
        String path = ArchitectureConfigService.BASELINE_PATH;
        String dirName = path.substring(0, path.indexOf('/'));
        String fileName = path.substring(path.indexOf('/') + 1);
        byte[] content = baseline.toJson().getBytes(StandardCharsets.UTF_8);
        try {
            return WriteCommandAction.writeCommandAction(project).withName("Update Architecture Baseline")
                    .compute(() -> {
                        VirtualFile dir = VfsUtil.createDirectoryIfMissing(baseDir, dirName);
                        VirtualFile file = dir.findChild(fileName);
                        if (file == null) {
                            file = dir.createChildData(ArchitectureBaselineAction.class, fileName);
                        }
                        file.setBinaryContent(content);
                        return file;
                    });
        }
        catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException io) {
                throw io;
            }
            throw ex;
        }
    }

    static String writeMessage(ArchitectureBaselineGenerator.Result result) {
        int total = 0;
        for (ArchitectureBaseline.Entry entry : result.current().entries()) {
            total += entry.count();
        }
        StringBuilder message = new StringBuilder("Baseline written to " + ArchitectureConfigService.BASELINE_PATH
                + ": " + total + " known violation(s).");
        if (!result.stale().isEmpty()) {
            message.append(" Removed ").append(staleCount(result.stale()))
                    .append(" stale entr(y/ies) that were fixed.");
        }
        return message.toString();
    }

    static String checkMessage(ArchitectureBaselineGenerator.Result result) {
        StringBuilder message = new StringBuilder();
        message.append(result.newFindings()).append(" new architecture violation(s) not in the baseline");
        message.append("; ").append(staleCount(result.stale())).append(" stale baseline entr(y/ies)");
        if (!result.stale().isEmpty()) {
            message.append(":");
            int shown = 0;
            for (ArchitectureBaseline.Entry entry : result.stale()) {
                if (shown++ == 10) {
                    message.append("<br>…");
                    break;
                }
                message.append("<br>").append(entry.code()).append(' ').append(entry.rule()).append(" in ")
                        .append(entry.file());
            }
            message.append("<br>Refresh the baseline to drop fixed violations.");
        }
        return message.toString();
    }

    private static int staleCount(List<ArchitectureBaseline.Entry> stale) {
        int count = 0;
        for (ArchitectureBaseline.Entry entry : stale) {
            count += entry.count();
        }
        return count;
    }

    private static void showNotification(Project project, String content, NotificationType type) {
        NotificationGroup group = NotificationGroupManager.getInstance()
                .getNotificationGroup(ArchitectureConfigService.NOTIFICATION_GROUP);
        if (group != null) {
            group.createNotification("Spring Buddy architecture baseline", content, type).notify(project);
        }
    }
}
