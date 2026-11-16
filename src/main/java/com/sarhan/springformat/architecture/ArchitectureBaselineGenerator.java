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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.sarhan.springformat.inspection.BannedApiInspection;
import com.sarhan.springformat.inspection.DomainPurityInspection;
import com.sarhan.springformat.inspection.ForbiddenPackageDependencyInspection;
import com.sarhan.springformat.inspection.SpringConstructorInjectionInspection;
import com.sarhan.springformat.inspection.SpringControllerBoundaryInspection;
import com.sarhan.springformat.inspection.SuppressionPolicyInspection;
import org.jetbrains.annotations.NotNull;

/**
 * Collects every current architecture finding (baseline ignored) and turns it
 * into an {@link ArchitectureBaseline}. Must run inside a read action.
 *
 * @author Akmal Sarhan
 */
public final class ArchitectureBaselineGenerator {

    private ArchitectureBaselineGenerator() {
    }

    /**
     * Result of a baseline run.
     *
     * @param current     baseline built from the current findings
     * @param stale       entries of the previous baseline without a current finding
     * @param newFindings findings that were not in the previous baseline
     */
    public record Result(ArchitectureBaseline current, List<ArchitectureBaseline.Entry> stale, int newFindings) {
    }

    /**
     * Architecture inspections whose findings can be baselined.
     */
    public static List<LocalInspectionTool> tools() {
        return List.of(new ForbiddenPackageDependencyInspection(), new BannedApiInspection(),
                new DomainPurityInspection(), new SpringConstructorInjectionInspection(),
                new SpringControllerBoundaryInspection.RepositoryInjection(),
                new SpringControllerBoundaryInspection.EntityReturn(),
                new SpringControllerBoundaryInspection.EntityParameter(), new SuppressionPolicyInspection());
    }

    /**
     * Inspects all Java files in project content.
     */
    public static Result run(@NotNull Project project) {
        Collection<VirtualFile> files = FileTypeIndex.getFiles(JavaFileType.INSTANCE,
                GlobalSearchScope.projectScope(project));
        return run(project, files);
    }

    public static Result run(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
        ArchitectureConfigService service = ArchitectureConfigService.getInstance(project);
        ArchitectureBaseline previous = service.getBaseline();
        List<ArchitectureBaseline.Entry> findings = service.withoutBaseline(() -> collect(project, files));
        ArchitectureBaseline current = ArchitectureBaseline.of(findings);
        return new Result(current, previous.staleEntries(current), previous.newFindingCount(current));
    }

    private static List<ArchitectureBaseline.Entry> collect(Project project, Collection<VirtualFile> files) {
        InspectionManagerEx manager = (InspectionManagerEx) InspectionManager.getInstance(project);
        GlobalInspectionContextBase context = (GlobalInspectionContextBase) manager.createNewGlobalContext(false);
        PsiManager psiManager = PsiManager.getInstance(project);
        List<LocalInspectionToolWrapper> wrappers = new ArrayList<>();
        for (LocalInspectionTool tool : tools()) {
            wrappers.add(new LocalInspectionToolWrapper(tool));
        }
        List<ArchitectureBaseline.Entry> entries = new ArrayList<>();
        for (VirtualFile file : files) {
            PsiFile psiFile = (file != null && file.isValid()) ? psiManager.findFile(file) : null;
            if (psiFile == null) {
                continue;
            }
            for (LocalInspectionToolWrapper wrapper : wrappers) {
                for (ProblemDescriptor descriptor : inspect(psiFile, wrapper, context)) {
                    ArchitectureFingerprint fingerprint = ArchitectureFingerprint.of(descriptor);
                    if (fingerprint != null) {
                        entries.add(fingerprint.toEntry());
                    }
                }
            }
        }
        return entries;
    }

    private static List<ProblemDescriptor> inspect(PsiFile file, LocalInspectionToolWrapper wrapper,
                                                   GlobalInspectionContextBase context) {
        if (ProgressManager.getInstance().getProgressIndicator() == null) {
            return ProgressManager.getInstance().runProcess(
                    () -> InspectionEngine.runInspectionOnFile(file, wrapper, context), new EmptyProgressIndicator());
        }
        return InspectionEngine.runInspectionOnFile(file, wrapper, context);
    }
}
