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

package com.sarhan.springformat.git;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.PairConsumer;
import com.sarhan.springformat.RunSpringFormatInspectionsAction;
import com.sarhan.springformat.settings.SpringBuddySettingsState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Akmal Sarhan
 */
public class SpringBuddyCheckinHandlerFactory extends CheckinHandlerFactory {

    @Override
    public @NotNull CheckinHandler createHandler(@NotNull CheckinProjectPanel panel,
                                                  @NotNull com.intellij.openapi.vcs.changes.CommitContext commitContext) {
        return new SpringBuddyCheckinHandler(panel);
    }

    private static final class SpringBuddyCheckinHandler extends CheckinHandler {

        private final CheckinProjectPanel panel;
        private JCheckBox checkBox;

        private SpringBuddyCheckinHandler(CheckinProjectPanel panel) {
            this.panel = panel;
        }

        private VirtualFile[] getCommitFiles() {
            Collection<Change> changes = this.panel.getSelectedChanges();
            return changes.stream()
                    .map(Change::getVirtualFile)
                    .filter(Objects::nonNull)
                    .toArray(VirtualFile[]::new);
        }

        @Override
        @Nullable
        public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
            this.checkBox = new NonFocusableCheckBox("Run Spring Buddy inspections");
            return new MyRefreshableOnComponent(this.checkBox);
        }

        @Override
        public ReturnResult beforeCheckin(@Nullable CommitExecutor executor,
                                          PairConsumer<Object, Object> additionalDataConsumer) {
            if (this.checkBox != null && !this.checkBox.isSelected()) {
                return ReturnResult.COMMIT;
            }
            if (!SpringBuddySettingsState.getInstance().isGitCommitCheckEnabled()) {
                return ReturnResult.COMMIT;
            }

            VirtualFile[] files = getCommitFiles();

            Map<String, List<String>> errorsByFile = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> RunSpringFormatInspectionsAction.collectErrorInspections(this.panel.getProject(), files),
                "Running Spring Buddy inspections...", true, this.panel.getProject());

            if (errorsByFile.isEmpty()) {
                return ReturnResult.COMMIT;
            }

            StringBuilder message = new StringBuilder("Spring Buddy found ERROR-level issues:\n\n");
            for (Map.Entry<String, List<String>> entry : errorsByFile.entrySet()) {
                message.append(entry.getKey()).append(":\n");
                for (String error : entry.getValue()) {
                    message.append("  • ").append(error).append("\n");
                }
                message.append("\n");
            }

            int answer = Messages.showYesNoCancelDialog(
                    this.panel.getProject(),
                    message.toString(),
                    "Spring Buddy Pre-Commit Check",
                    "&Review", "&Commit Anyway", "Cancel",
                    Messages.getWarningIcon());
            if (answer == Messages.CANCEL) {
                return ReturnResult.CANCEL;
            }
            if (answer == Messages.YES) {
                ApplicationManager.getApplication().invokeLater(() ->
                        RunSpringFormatInspectionsAction.runInspections(this.panel.getProject(), files));
                return ReturnResult.CANCEL;
            }
            return ReturnResult.COMMIT;
        }

        private static final class MyRefreshableOnComponent implements RefreshableOnComponent, UnnamedConfigurable {

            private final JCheckBox checkBox;

            private MyRefreshableOnComponent(JCheckBox checkBox) {
                this.checkBox = checkBox;
            }

            @Override
            public JComponent getComponent() {
                JPanel panel = new JPanel(new java.awt.BorderLayout());
                panel.add(this.checkBox);
                return panel;
            }

            @Override
            public void saveState() {
                SpringBuddySettingsState.getInstance().setGitCommitCheckEnabled(this.checkBox.isSelected());
            }

            @Override
            public void restoreState() {
                this.checkBox.setSelected(SpringBuddySettingsState.getInstance().isGitCommitCheckEnabled());
            }

            @Override
            public @Nullable JComponent createComponent() {
                return getComponent();
            }

            @Override
            public boolean isModified() {
                return this.checkBox.isSelected() != SpringBuddySettingsState.getInstance().isGitCommitCheckEnabled();
            }

            @Override
            public void apply() {
                saveState();
            }

            @Override
            public void reset() {
                restoreState();
            }
        }
    }
}
