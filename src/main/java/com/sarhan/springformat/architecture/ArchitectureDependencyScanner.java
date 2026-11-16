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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds PSI-resolved references from a Java file to types forbidden by a set
 * of {@link ArchitectureRule}s.
 * <p>
 * Every {@link PsiJavaCodeReferenceElement} is considered: imports (single
 * type, static and on-demand), fully-qualified names in code, annotations,
 * field/parameter/return/generic/throws types, {@code extends}/{@code implements},
 * {@code new} expressions and static member qualifiers. Only references that
 * resolve to exactly one class (or, for on-demand imports, one package) are
 * checked; unresolved, ambiguous, local and type-parameter targets are never
 * reported. Javadoc references are ignored.
 * <p>
 * Each forbidden target is reported once per file: at its import when the
 * file imports it, otherwise at its first usage. Classes covered by an already
 * reported on-demand import of their package are not reported again.
 *
 * @author Akmal Sarhan
 */
public final class ArchitectureDependencyScanner {

    private ArchitectureDependencyScanner() {
    }

    /**
     * A single forbidden dependency or banned API use.
     *
     * @param anchor  the most specific reference element
     * @param finding the violated rule or banned API (first match wins)
     * @param target  fully-qualified class name, {@code Type#method}, or package name for on-demand imports
     */
    public record Violation(PsiElement anchor, ArchitectureFinding finding, String target) {
    }

    /**
     * Returns {@code true} when the file should be skipped because it lives in
     * test sources and the configuration does not include tests.
     */
    public static boolean isExcludedTestSource(@NotNull PsiFile file, boolean includeTests) {
        if (includeTests) {
            return false;
        }
        VirtualFile virtualFile = file.getVirtualFile();
        return virtualFile != null && TestSourcesFilter.isTestSources(virtualFile, file.getProject());
    }

    /**
     * Scans a Java file against the given rules.
     *
     * @param file  the file to scan
     * @param rules the rules; rules with severity OFF are ignored
     * @return violations in report order (imports first, then document order)
     */
    public static List<Violation> scan(@NotNull PsiJavaFile file, @NotNull List<ArchitectureRule> rules) {
        List<ArchitectureRule> active = new ArrayList<>();
        for (ArchitectureRule rule : rules) {
            if (!rule.severity().isOff()) {
                active.add(rule);
            }
        }
        if (active.isEmpty()) {
            return List.of();
        }
        return scan(file, new TargetMatcher() {
            @Override
            public ArchitectureFinding classViolation(String sourcePackage, @Nullable String sourceClassName,
                                                      String targetPackage, String targetClassName) {
                return firstViolatedRule(active, sourcePackage, sourceClassName, targetPackage, targetClassName);
            }

            @Override
            public ArchitectureFinding packageViolation(String sourcePackage, @Nullable String sourceClassName,
                                                        String targetPackage) {
                return firstViolatedRule(active, sourcePackage, sourceClassName, targetPackage, null);
            }
        }, null);
    }

    /**
     * Scans a Java file for uses of banned APIs outside their allowed packages.
     * Banned types and annotations are reported like forbidden dependencies
     * (once per file, at the import or the first use). Banned methods are
     * reported at every call and method reference that resolves to the
     * method or to an override of it.
     *
     * @param file       the file to scan
     * @param bannedApis the banned APIs; entries with severity OFF are ignored
     * @return violations in report order (imports first, then document order)
     */
    public static List<Violation> scanBannedApis(@NotNull PsiJavaFile file, @NotNull List<BannedApi> bannedApis) {
        String sourcePackage = file.getPackageName();
        PsiClass[] topLevelClasses = file.getClasses();
        String fileClassName = (topLevelClasses.length > 0) ? topLevelClasses[0].getQualifiedName() : null;
        List<BannedApi> active = new ArrayList<>();
        for (BannedApi api : bannedApis) {
            if (!api.severity().isOff() && !api.isAllowedIn(sourcePackage, fileClassName)) {
                active.add(api);
            }
        }
        if (active.isEmpty()) {
            return List.of();
        }
        return scan(file, new TargetMatcher() {
            @Override
            public ArchitectureFinding classViolation(String sourcePackage, @Nullable String sourceClassName,
                                                      String targetPackage, String targetClassName) {
                for (BannedApi api : active) {
                    if (api.matchesClass(targetClassName) && !api.isAllowedIn(sourcePackage, sourceClassName)) {
                        return api;
                    }
                }
                return null;
            }

            @Override
            public ArchitectureFinding packageViolation(String sourcePackage, @Nullable String sourceClassName,
                                                        String targetPackage) {
                return null;
            }

            @Override
            public MethodMatch methodViolation(String sourcePackage, @Nullable String sourceClassName,
                                               PsiMethod method) {
                return bannedMethod(active, sourcePackage, sourceClassName, method);
            }
        }, active);
    }

    private static List<Violation> scan(PsiJavaFile file, TargetMatcher matcher, @Nullable List<BannedApi> methods) {
        String sourcePackage = file.getPackageName();
        PsiClass[] topLevelClasses = file.getClasses();
        String fileClassName = (topLevelClasses.length > 0) ? topLevelClasses[0].getQualifiedName() : null;
        boolean checkMethods = methods != null && hasMethodSymbol(methods);

        List<Violation> violations = new ArrayList<>();
        Set<String> reportedTargets = new HashSet<>();
        Set<String> reportedPackages = new HashSet<>();

        PsiImportList importList = file.getImportList();
        if (importList != null) {
            for (PsiImportStatementBase statement : importList.getAllImportStatements()) {
                PsiJavaCodeReferenceElement reference = statement.getImportReference();
                if (reference == null) {
                    continue;
                }
                PsiElement resolved = reference.resolve();
                if (resolved instanceof PsiPackage psiPackage && statement.isOnDemand()) {
                    String packageName = psiPackage.getQualifiedName();
                    ArchitectureFinding finding = matcher.packageViolation(sourcePackage, fileClassName, packageName);
                    if (finding != null && reportedPackages.add(finding.id() + '|' + packageName)) {
                        violations.add(new Violation(reference, finding, packageName + ".*"));
                    }
                }
                else if (resolved instanceof PsiClass psiClass) {
                    check(reference, psiClass, matcher, sourcePackage, fileClassName, null, violations,
                            reportedTargets, reportedPackages);
                }
            }
        }

        for (PsiClass topLevel : topLevelClasses) {
            String sourceClassName = topLevel.getQualifiedName();
            topLevel.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitDocComment(@NotNull PsiDocComment comment) {
                    // Javadoc references are documentation, not dependencies.
                }

                @Override
                public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
                    super.visitReferenceElement(reference);
                    if (isCoveredByOuterClassReference(reference)) {
                        return;
                    }
                    PsiElement resolved = reference.advancedResolve(false).getElement();
                    if (resolved instanceof PsiClass psiClass) {
                        check(reference, psiClass, matcher, sourcePackage, sourceClassName, topLevel, violations,
                                reportedTargets, reportedPackages);
                    }
                }

                @Override
                public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                    super.visitMethodCallExpression(call);
                    if (checkMethods) {
                        checkMethod(call.getMethodExpression(), call.resolveMethod(), matcher, sourcePackage,
                                sourceClassName, violations);
                    }
                }

                @Override
                public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
                    super.visitMethodReferenceExpression(expression);
                    if (checkMethods && expression.resolve() instanceof PsiMethod method) {
                        checkMethod(expression, method, matcher, sourcePackage, sourceClassName, violations);
                    }
                }
            });
        }
        return violations;
    }

    private static boolean hasMethodSymbol(List<BannedApi> apis) {
        for (BannedApi api : apis) {
            if (api.memberName() != null) {
                return true;
            }
        }
        return false;
    }

    private static void checkMethod(PsiElement anchor, @Nullable PsiMethod method, TargetMatcher matcher,
                                    String sourcePackage, @Nullable String sourceClassName,
                                    List<Violation> violations) {
        if (method == null || method.isConstructor()) {
            return;
        }
        MethodMatch match = matcher.methodViolation(sourcePackage, sourceClassName, method);
        if (match == null) {
            return;
        }
        PsiElement name = (anchor instanceof PsiReferenceExpression reference)
                ? reference.getReferenceNameElement() : null;
        violations.add(new Violation((name != null) ? name : anchor, match.finding(), match.target()));
    }

    @Nullable
    private static MethodMatch bannedMethod(List<BannedApi> apis, String sourcePackage,
                                            @Nullable String sourceClassName, PsiMethod method) {
        List<PsiMethod> candidates = new ArrayList<>();
        candidates.add(method);
        candidates.addAll(Arrays.asList(method.findSuperMethods()));
        for (BannedApi api : apis) {
            if (!method.getName().equals(api.memberName()) || api.isAllowedIn(sourcePackage, sourceClassName)) {
                continue;
            }
            for (PsiMethod candidate : candidates) {
                PsiClass owner = candidate.getContainingClass();
                String ownerName = (owner != null) ? owner.getQualifiedName() : null;
                if (ownerName != null && api.matchesMethod(ownerName, candidate.getName())) {
                    return new MethodMatch(api, ownerName + "#" + candidate.getName());
                }
            }
        }
        return null;
    }

    /**
     * Registers a violation using the severity of its rule, through the
     * baseline-aware reporter.
     */
    public static void register(@NotNull ArchitectureReporter reporter, @NotNull Violation violation) {
        ArchitectureFinding finding = violation.finding();
        reporter.report(violation.anchor(), rangeWithoutTypeArguments(violation.anchor()), finding.code(),
                finding.id(), finding.severity(), finding.text(violation.target()));
    }

    /**
     * For {@code a.b.Type<X>} the problem covers {@code a.b.Type} only, so that
     * a violation inside the type arguments gets its own, non-overlapping range.
     */
    @Nullable
    private static TextRange rangeWithoutTypeArguments(PsiElement anchor) {
        if (!(anchor instanceof PsiJavaCodeReferenceElement reference)) {
            return null;
        }
        PsiElement name = reference.getReferenceNameElement();
        if (name == null) {
            return null;
        }
        int end = name.getTextRange().getEndOffset() - reference.getTextRange().getStartOffset();
        if (end <= 0 || end >= reference.getTextLength()) {
            return null;
        }
        return new TextRange(0, end);
    }

    /**
     * Builds the stable problem message for a violation.
     */
    public static String message(@NotNull Violation violation) {
        ArchitectureFinding finding = violation.finding();
        return ArchitectureProblem.format(finding.code(), finding.id(), finding.text(violation.target()));
    }

    private static void check(PsiJavaCodeReferenceElement reference, PsiClass target, TargetMatcher matcher,
                              String sourcePackage, @Nullable String sourceClassName, @Nullable PsiClass sourceTopLevel,
                              List<Violation> violations, Set<String> reportedTargets, Set<String> reportedPackages) {
        if (target instanceof PsiTypeParameter) {
            return;
        }
        String qualifiedName = target.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        if (sourceTopLevel != null && PsiTreeUtil.isAncestor(sourceTopLevel, target, false)) {
            return;
        }
        String targetPackage = PsiUtil.getPackageName(target);
        if (targetPackage == null) {
            return;
        }
        ArchitectureFinding finding = matcher.classViolation(sourcePackage, sourceClassName, targetPackage,
                qualifiedName);
        if (finding == null) {
            return;
        }
        if (reportedPackages.contains(finding.id() + '|' + targetPackage)) {
            return;
        }
        if (reportedTargets.add(finding.id() + '|' + qualifiedName)) {
            violations.add(new Violation(reference, finding, qualifiedName));
        }
    }

    @Nullable
    private static ArchitectureRule firstViolatedRule(List<ArchitectureRule> rules, String sourcePackage,
                                                      @Nullable String sourceClassName, String targetPackage,
                                                      @Nullable String targetClassName) {
        for (ArchitectureRule rule : rules) {
            if (rule.appliesTo(sourcePackage, sourceClassName) && rule.forbids(targetPackage, targetClassName)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * {@code a.b.Outer.Inner}: only the outermost class reference counts, its
     * qualifiers ({@code a.b.Outer}, {@code a.b}) are skipped. A qualifier of a
     * member access ({@code Outer.CONSTANT}) is still checked.
     */
    private static boolean isCoveredByOuterClassReference(PsiJavaCodeReferenceElement reference) {
        if (PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class, true) != null) {
            return true;
        }
        PsiElement parent = reference.getParent();
        if (parent instanceof PsiJavaCodeReferenceElement outer && outer.getQualifier() == reference) {
            return outer.advancedResolve(false).getElement() instanceof PsiClass;
        }
        return false;
    }

    /**
     * Decides which finding, if any, a resolved target violates.
     */
    private interface TargetMatcher {

        @Nullable
        ArchitectureFinding classViolation(String sourcePackage, @Nullable String sourceClassName,
                                           String targetPackage, String targetClassName);

        @Nullable
        ArchitectureFinding packageViolation(String sourcePackage, @Nullable String sourceClassName,
                                             String targetPackage);

        @Nullable
        default MethodMatch methodViolation(String sourcePackage, @Nullable String sourceClassName,
                                            PsiMethod method) {
            return null;
        }
    }

    private record MethodMatch(ArchitectureFinding finding, String target) {
    }
}
