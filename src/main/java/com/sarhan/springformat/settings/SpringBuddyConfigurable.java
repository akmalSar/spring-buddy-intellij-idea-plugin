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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JPanel;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * @author Akmal Sarhan
 */
public class SpringBuddyConfigurable implements Configurable {

    private final Map<String, JBCheckBox> groupCheckBoxes = new LinkedHashMap<>();

    private ComboBox<String> levelComboBox;

    private JBCheckBox gitCommitCheckBox;

    private JPanel panel;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Spring Buddy";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (this.panel == null) {
            FormBuilder builder = FormBuilder.createFormBuilder();
            for (SpringBuddySettingsUtil.InspectionGroup group : SpringBuddySettingsUtil.groups()) {
                JBCheckBox checkBox = new JBCheckBox(group.label());
                this.groupCheckBoxes.put(group.id(), checkBox);
                builder.addComponent(checkBox, 1);
            }
            this.levelComboBox = new ComboBox<>(SpringBuddySettingsUtil.LEVELS.toArray(new String[0]));
            builder.addLabeledComponent("Minimum inspection level:", this.levelComboBox, 1, false);
            this.gitCommitCheckBox = new JBCheckBox("Run Spring Buddy inspections before Git commit");
            builder.addComponent(this.gitCommitCheckBox, 1);
            this.panel = builder.addComponentFillVertically(new JPanel(), 0).getPanel();
        }
        reset();
        return this.panel;
    }

    @Override
    public boolean isModified() {
        SpringBuddySettingsState settings = SpringBuddySettingsState.getInstance();
        Set<String> enabledGroups = settings.getEnabledGroups();
        for (Map.Entry<String, JBCheckBox> entry : this.groupCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected() != enabledGroups.contains(entry.getKey())) {
                return true;
            }
        }
        return !String.valueOf(this.levelComboBox.getSelectedItem()).equals(settings.getInspectionLevel())
                || this.gitCommitCheckBox.isSelected() != settings.isGitCommitCheckEnabled();
    }

    @Override
    public void apply() {
        Set<String> enabledGroups = this.groupCheckBoxes.entrySet().stream()
                .filter((Map.Entry<String, JBCheckBox> entry) -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        SpringBuddySettingsState settings = SpringBuddySettingsState.getInstance();
        settings.setEnabledGroups(enabledGroups);
        settings.setInspectionLevel(String.valueOf(this.levelComboBox.getSelectedItem()));
        settings.setGitCommitCheckEnabled(this.gitCommitCheckBox.isSelected());
        SpringBuddySettingsSynchronizer.syncOpenProjects();
    }

    @Override
    public void reset() {
        SpringBuddySettingsState settings = SpringBuddySettingsState.getInstance();
        Set<String> enabledGroups = settings.getEnabledGroups();
        for (Map.Entry<String, JBCheckBox> entry : this.groupCheckBoxes.entrySet()) {
            entry.getValue().setSelected(enabledGroups.contains(entry.getKey()));
        }
        this.levelComboBox.setSelectedItem(settings.getInspectionLevel());
        this.gitCommitCheckBox.setSelected(settings.isGitCommitCheckEnabled());
    }

    @Override
    public void disposeUIResources() {
        this.groupCheckBoxes.clear();
        this.levelComboBox = null;
        this.gitCommitCheckBox = null;
        this.panel = null;
    }
}
