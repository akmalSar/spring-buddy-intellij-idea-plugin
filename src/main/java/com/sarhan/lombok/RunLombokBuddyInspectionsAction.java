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

package com.sarhan.lombok;

import java.util.Arrays;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */
public class RunLombokBuddyInspectionsAction extends AnAction {

    private static final String[] LOMBOK_BUDDY_TOOLS = {
            "ConstructorToBuilder",
            "SetterChainToBuilder",
            "AllArgsConstructorRedundancy",
            "BuilderRedundancy",
            "BuilderHierarchy",
            "InheritanceShadow",
            "EqualsAndHashCode",
            "DataAnnotation",
            "ToString",
            "ConstructorAnnotation",
            "NonNull",
            "BuilderDefault",
            "SetterOnBuilderField",
            "ManualGetterShadow",
    };

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        AnalysisScope scope;
        if ((files != null) && (files.length > 0)) {
            scope = new AnalysisScope(project, Arrays.asList(files));
        } else {
            scope = new AnalysisScope(project);
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Lombok-Buddy: Running Inspections", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                ApplicationManager.getApplication().invokeAndWait(() -> {
                    FileDocumentManager.getInstance().saveAllDocuments();
                    PsiDocumentManager.getInstance(project).commitAllDocuments();
                });

                InspectionManagerEx managerEx = (InspectionManagerEx) InspectionManager.getInstance(project);
                GlobalInspectionContextBase context = (GlobalInspectionContextBase)
                        managerEx.createNewGlobalContext(false);

                InspectionProfileImpl profile = new InspectionProfileImpl("Lombok-Buddy");
                profile.initInspectionTools(project);
                profile.disableAllTools(project);
                for (String toolName : LOMBOK_BUDDY_TOOLS) {
                    profile.enableTool(toolName, project);
                }

                context.setExternalProfile(profile);
                context.doInspections(scope);
            }
        });
    }
}
