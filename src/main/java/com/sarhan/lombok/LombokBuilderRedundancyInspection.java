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
import java.util.Collection;
import java.util.List;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.annotations.NotNull;

/**
 * Detects redundant or unused {@code @Builder} / {@code @SuperBuilder} annotations.
 *
 * <p><b>Redundant</b> — the class already declares an explicit {@code builder()}
 * static method. Lombok would generate one, but the hand-written method shadows it.</p>
 *
 * <p><b>Unused</b> — the class has zero references anywhere in the project
 * <em>and</em> a textual scan finds no {@code ClassName.builder()} call sites.
 * Abstract classes are skipped because subclasses may rely on the parent builder.</p>
 *
 * @author Akmal Sarhan
 */
public class LombokBuilderRedundancyInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final List<String> BUILDER_ANNOTATIONS = Arrays.asList(
            "lombok.Builder",
            "Builder",
            "lombok.SuperBuilder",
            "lombok.experimental.SuperBuilder",
            "SuperBuilder"
    );

    /**
     * Checks whether this class is part of a Lombok builder hierarchy — either it
     * extends a class that has {@code @Builder}/{@code @SuperBuilder}, or one of
     * its subclasses does.  Removing {@code @Builder} from any member of such a
     * hierarchy can break compilation because Lombok generates static
     * {@code builder()} methods with incompatible return types.
     */
    private static boolean isInBuilderHierarchy(PsiClass aClass) {
        PsiClass[] supers = aClass.getSupers();
        for (PsiClass sup : supers) {
            if (hasBuilderAnnotation(sup)) {
                return true;
            }
        }

        Project project = aClass.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (PsiClass sub : com.intellij.psi.search.searches.ClassInheritorsSearch.search(aClass, scope, false)) {
            if (hasBuilderAnnotation(sub)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBuilderAnnotation(PsiClass aClass) {
        PsiModifierList modifiers = aClass.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation a : modifiers.getAnnotations()) {
            String fqn = a.getQualifiedName();
            if ("lombok.Builder".equals(fqn) || "Builder".equals(fqn)
                    || "lombok.SuperBuilder".equals(fqn)
                    || "lombok.experimental.SuperBuilder".equals(fqn)
                    || "SuperBuilder".equals(fqn)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnnotation(String qName, String candidate) {
        if (qName == null) {
            return false;
        }
        if (candidate.equals(qName)) {
            return true;
        }
        return qName.endsWith("." + candidate);
    }

    /**
     * Matches a qualified annotation name against a candidate.
     * Handles both short names ({@code @Builder}) and fully-qualified names
     * ({@code @lombok.Builder}, {@code @lombok.experimental.SuperBuilder}).
     */

    /**
     * Checks whether the class already declares an explicit no-arg static method
     * named {@code builder}.
     *
     * <p>Defensive checks ensure we ignore Lombok-plugin synthetic methods that are
     * injected via {@code PsiAugmentProvider} but do not exist in the source file.</p>
     */
    private static boolean hasExplicitBuilderMethod(PsiClass aClass) {
        for (PsiMethod method : aClass.getMethods()) {
            if (!"builder".equals(method.getName())) {
                continue;
            }
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            if (method.getParameterList().getParametersCount() != 0) {
                continue;
            }
            // A synthetic/augmented method is typically non-physical, has no
            // text range, or its navigation element points to the annotation.
            if (!method.isPhysical()) {
                continue;
            }
            if (method.getTextLength() <= 0) {
                continue;
            }
            if (method.getNavigationElement() != method) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Determines whether the builder pattern for this class is used anywhere in
     * the project.
     *
     * <p>Uses a two-tier strategy:</p>
     * <ol>
     *   <li><b>Primary</b> — {@link ReferencesSearch} finds resolved references to
     *       the class (constructor calls, field declarations, type casts, etc.).</li>
     *   <li><b>Secondary</b> — when {@code ReferencesSearch} finds nothing, we run a
     *       textual scan for {@code ClassName.builder(...)} call patterns. This
     *       catches the common case where the {@code builder()} method is synthetic
     *       and the method call is therefore unresolved in the PSI, causing
     *       {@code ReferencesSearch} to miss the qualifier reference.</li>
     * </ol>
     */
    private static boolean hasBuilderUsages(PsiClass aClass) {
        Project project = aClass.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        // Tier 1: resolved references (fast, covers most cases).
        Collection<PsiReference> refs = ReferencesSearch.search(aClass, scope, false).findAll();
        if (!refs.isEmpty()) {
            return true;
        }

        // Tier 2: textual scan for unresolved .builder() call sites.
        return hasTextualBuilderUsage(aClass, scope);
    }

    /**
     * Scans project files for textual evidence of {@code ClassName.builder()} usage.
     * Scans project source files in scope, then checks file text for common
     * builder call patterns.
     */
    private static boolean hasTextualBuilderUsage(PsiClass aClass, GlobalSearchScope scope) {
        String className = aClass.getName();
        String qualifiedName = aClass.getQualifiedName();
        if (className == null) {
            return true; // Cannot determine → assume used to be safe.
        }

        Collection<VirtualFile> candidateFiles = collectAllJavaKtFilesInScope(aClass.getProject(), scope);

        // Prepare search patterns.
        String directCall = className + ".builder";
        String directBuilder = className + ".Builder";
        String methodRef = className + "::builder";

        String fqnCall = (qualifiedName != null) ? (qualifiedName + ".builder") : null;
        String fqnBuilder = (qualifiedName != null) ? (qualifiedName + ".Builder") : null;
        String fqnMethodRef = (qualifiedName != null) ? (qualifiedName + "::builder") : null;

        for (VirtualFile file : candidateFiles) {
            // Skip non-Java files — builder calls only occur in Java/Kotlin source.
            String ext = file.getExtension();
            if (!("java".equals(ext) || "kt".equals(ext))) {
                continue;
            }

            PsiFile psiFile = PsiManager.getInstance(aClass.getProject()).findFile(file);
            if (!(psiFile instanceof PsiJavaFile)) {
                continue;
            }

            String text = psiFile.getText();

            // Direct calls / type references
            if (text.contains(directCall) || text.contains(directBuilder) || text.contains(methodRef)) {
                return true;
            }

            // Fully-qualified calls (covers static imports too)
            if ((fqnCall != null) && text.contains(fqnCall)) {
                return true;
            }
            if ((fqnBuilder != null) && text.contains(fqnBuilder)) {
                return true;
            }
            if ((fqnMethodRef != null) && text.contains(fqnMethodRef)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Collects all {@code .java} and {@code .kt} files that belong to the project
     * content and lie inside the given search scope.
     */
    private static Collection<VirtualFile> collectAllJavaKtFilesInScope(
            Project project, GlobalSearchScope scope) {

        List<VirtualFile> result = new java.util.ArrayList<>();
        com.intellij.openapi.roots.ProjectFileIndex fileIndex =
                com.intellij.openapi.roots.ProjectFileIndex.getInstance(project);

        fileIndex.iterateContent((@NotNull VirtualFile file) -> {
            if (scope.contains(file)) {
                String ext = file.getExtension();
                if ("java".equals(ext) || "kt".equals(ext)) {
                    result.add(file);
                }
            }
            return true;
        });

        return result;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return MyMessageBundle.message("inspection.builder.redundancy.display.name");
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
        return "Lombok builder";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(
            @NotNull ProblemsHolder holder,
            boolean isOnTheFly) {

                if (!LombokInspectionUtil.isInJavaFile(holder.getFile())) {
                    return PsiElementVisitor.EMPTY_VISITOR;
                }

        return new JavaElementVisitor() {

            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                super.visitClass(aClass);

                PsiModifierList modifierList = aClass.getModifierList();
                if (modifierList == null) {
                    return;
                }

                // Find the builder annotation on this class
                PsiAnnotation builderAnnotation = null;
                for (PsiAnnotation ann : modifierList.getAnnotations()) {
                    String qName = ann.getQualifiedName();
                    for (String candidate : BUILDER_ANNOTATIONS) {
                        if (matchesAnnotation(qName, candidate)) {
                            builderAnnotation = ann;
                            break;
                        }
                    }
                    if (builderAnnotation != null) {
                        break;
                    }
                }
                if (builderAnnotation == null) {
                    return;
                }

                // 1. Explicit builder() method already exists → annotation is redundant
                if (hasExplicitBuilderMethod(aClass)) {
                    holder.registerProblem(
                            builderAnnotation,
                            MyMessageBundle.message("inspection.builder.redundancy.problem.descriptor.explicit"),
                            ProblemHighlightType.WARNING,
                            new BuilderRedundancyFix()
                    );
                    return;
                }

                // 2. Skip classes that extend (or are extended by) another @Builder class.
                // Removing @Builder from a subclass of a @Builder parent breaks Lombok
                // builder hierarchies and can produce "builder() cannot hide builder()"
                // compilation errors when both generate static builder() methods.
                if (isInBuilderHierarchy(aClass)) {
                    return;
                }

                // 3. Unused builder pattern.
                // Skip abstract classes: their builder is typically consumed via subclasses.
                if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }

                if (!hasBuilderUsages(aClass)) {
                    holder.registerProblem(
                            builderAnnotation,
                            MyMessageBundle.message("inspection.builder.redundancy.problem.descriptor.unused"),
                            ProblemHighlightType.WEAK_WARNING,
                            new BuilderRedundancyFix()
                    );
                }
            }
        };
    }
}
