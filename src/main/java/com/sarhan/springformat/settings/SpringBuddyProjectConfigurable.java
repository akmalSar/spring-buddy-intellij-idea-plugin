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

import java.util.EnumMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Child settings page of Spring Buddy: per-project target Spring Boot version.
 *
 * @author Akmal Sarhan
 */
public class SpringBuddyProjectConfigurable implements Configurable {

    private final Project project;

    private final Map<SpringBootTargetMode, String> labels = new EnumMap<>(SpringBootTargetMode.class);

    private ComboBox<SpringBootTargetMode> targetComboBox;

    private JBLabel detectedLabel;

    private JPanel panel;

    public SpringBuddyProjectConfigurable(Project project) {
        this.project = project;
        this.labels.put(SpringBootTargetMode.AUTO, "Auto-detect");
        this.labels.put(SpringBootTargetMode.BOOT_3, SpringBootTarget.BOOT_3.label());
        this.labels.put(SpringBootTargetMode.BOOT_4, SpringBootTarget.BOOT_4.label());
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Spring Boot Target";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (this.panel == null) {
            this.targetComboBox = new ComboBox<>(SpringBootTargetMode.values());
            this.targetComboBox.setRenderer(new SimpleListCellRenderer<>() {
                @Override
                public void customize(@NotNull JList<? extends SpringBootTargetMode> list,
                                      @Nullable SpringBootTargetMode value,
                                      int index,
                                      boolean selected,
                                      boolean hasFocus) {
                    setText((value != null) ? SpringBuddyProjectConfigurable.this.labels.get(value) : "");
                }
            });
            this.detectedLabel = new JBLabel();
            FormBuilder builder = FormBuilder.createFormBuilder();
            builder.addLabeledComponent("Target Spring Boot version:", this.targetComboBox, 1, false);
            builder.addComponent(this.detectedLabel, 1);
            this.panel = builder.addComponentFillVertically(new JPanel(), 0).getPanel();
        }
        reset();
        return this.panel;
    }

    @Override
    public boolean isModified() {
        SpringBootTargetMode current = SpringBuddyProjectSettingsState.getInstance(this.project).getTargetMode();
        return selectedMode() != current;
    }

    @Override
    public void apply() {
        SpringBuddyProjectSettingsState settings = SpringBuddyProjectSettingsState.getInstance(this.project);
        settings.setTargetMode(selectedMode());
        DaemonCodeAnalyzer.getInstance(this.project).settingsChanged();
    }

    @Override
    public void reset() {
        this.targetComboBox.setSelectedItem(SpringBuddyProjectSettingsState.getInstance(this.project).getTargetMode());
        updateDetectedLabel();
    }

    @Override
    public void disposeUIResources() {
        this.targetComboBox = null;
        this.detectedLabel = null;
        this.panel = null;
    }

    void setTargetMode(SpringBootTargetMode mode) {
        if (this.targetComboBox != null) {
            this.targetComboBox.setSelectedItem(mode);
        }
    }

    private SpringBootTargetMode selectedMode() {
        Object selected = this.targetComboBox.getSelectedItem();
        return (selected instanceof SpringBootTargetMode mode) ? mode : SpringBootTargetMode.AUTO;
    }

    private void updateDetectedLabel() {
        String detected = SpringBootVersionResolver.detectedVersion(this.project);
        this.detectedLabel.setText((detected != null)
                ? "Detected: Spring Boot " + detected
                : "Detected: none (defaults to Spring Boot 3.x)");
    }
}
