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

package com.sarhan.springformat.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * SpringHideUtilityClassConstructor: a utility class (one where every method and field
 * is {@code static}) must not have a {@code public} or package-private (default)
 * constructor. It should have exactly one {@code private} no-arg constructor.
 *
 * <p>Mirrors {@code io.spring.javaformat.checkstyle.check.SpringHideUtilityClassConstructor}
 * which itself is a variant of Checkstyle's {@code HideUtilityClassConstructor}.
 *
 * <p>Quick-fix options:
 * <ol>
 *   <li>Make the existing constructor {@code private}</li>
 *   <li>Add a {@code private} no-arg constructor when none exists</li>
 * </ol>
 *
 * @author Akmal Sarhan
 */
public class SpringHideUtilityClassConstructorInspection extends AbstractBaseJavaLocalInspectionTool {

    /**
     * A class is a utility class when:
     * <ul>
     *   <li>It is a concrete top-level or static nested class (not interface/enum/annotation/record)</li>
     *   <li>It has at least one member (field or method)</li>
     *   <li>Every field and every method is {@code static}</li>
     *   <li>It does not extend anything (other than Object) or implement interfaces
     *       — inheriting from something implies it's not a pure utility</li>
     * </ul>
     */
    private static boolean isUtilityClass(PsiClass psiClass) {
        if (psiClass.isInterface() || psiClass.isEnum() || psiClass.isAnnotationType()) {
            return false;
        }
        if (psiClass instanceof PsiAnonymousClass) {
            return false;
        }

        // Records handle their constructors differently
        if (psiClass.isRecord()) {
            return false;
        }

        // Abstract classes are not utility classes
        PsiModifierList classMods = psiClass.getModifierList();
        if (classMods != null && classMods.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return false;
        }
        if (isBypassed(psiClass)) {
            return false;
        }

        // Must not extend a class other than Object
        PsiClassType[] superTypes = psiClass.getExtendsListTypes();
        if (superTypes.length > 0) {
            return false;
        }

        // Must not implement interfaces
        if (psiClass.getImplementsListTypes().length > 0) {
            return false;
        }

        // Inner (non-static) classes are not candidates
        if (psiClass.getContainingClass() != null && !isStatic(psiClass)) {
            return false;
        }

        // Collect instance members (non-static, non-constructor)
        PsiField[] fields = psiClass.getFields();
        PsiMethod[] methods = psiClass.getMethods();

        // Must have at least one member to be considered a utility class
        if (fields.length == 0 && methods.length == 0) {
            return false;
        }

        for (PsiField field : fields) {
            if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                return false;
            }
        }
        for (PsiMethod method : methods) {
            if (method.isConstructor()) {
                continue;
            }
            if (!method.hasModifierProperty(PsiModifier.STATIC)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isStatic(PsiClass psiClass) {
        PsiModifierList mods = psiClass.getModifierList();
        return mods != null && mods.hasModifierProperty(PsiModifier.STATIC);
    }

    private static boolean isBypassed(PsiClass psiClass) {
        PsiModifierList modifiers = psiClass.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if (name != null && (name.equals("SpringBootApplication") || name.endsWith(".SpringBootApplication")
                    || name.equals("Configuration") || name.endsWith(".Configuration")
                    || name.equals("EnableAutoConfiguration") || name.endsWith(".EnableAutoConfiguration"))) {
                return true;
            }
        }
        String text = modifiers.getText();
        return text.contains("@SpringBootApplication") || text.contains("@Configuration")
                || text.contains("@EnableAutoConfiguration");
    }

    private static boolean isPublicOrPackagePrivate(PsiMethod ctor) {
        PsiModifierList mods = ctor.getModifierList();
        return !mods.hasModifierProperty(PsiModifier.PRIVATE)
                && !mods.hasModifierProperty(PsiModifier.PROTECTED);
    }

    private static boolean canRegisterProblemOn(PsiElement element) {
        return element.isPhysical() && !element.getTextRange().isEmpty();
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                super.visitClass(psiClass);

                if (!isUtilityClass(psiClass)) {
                    return;
                }

                PsiMethod[] constructors = psiClass.getConstructors();

                if (constructors.length == 0) {
                    // Implicit public no-arg constructor — flag the class name itself
                    PsiIdentifier nameId = psiClass.getNameIdentifier();
                    holder.registerProblem(
                            (nameId != null) ? nameId : psiClass,
                            "Utility class '" + psiClass.getName() + "' has an implicit public constructor; add a private no-arg constructor (Spring convention)",
                            ProblemHighlightType.WARNING,
                            new AddPrivateConstructorFix()
                    );
                    return;
                }

                for (PsiMethod ctor : constructors) {
                    if (!canRegisterProblemOn(ctor)) {
                        continue;
                    }
                    if (isPublicOrPackagePrivate(ctor)) {
                        holder.registerProblem(
                                ctor,
                                "Utility class constructor should be private (Spring convention)",
                                ProblemHighlightType.WARNING,
                                new MakeConstructorPrivateFix()
                        );
                    }
                }
            }
        };
    }

    // ── Fix 1: make existing constructor private ──────────────────────────────────

    private static final class MakeConstructorPrivateFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Make constructor private";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement el = descriptor.getPsiElement();
            PsiMethod ctor = el instanceof PsiMethod m ? m
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(el, PsiMethod.class);
            if (ctor == null) {
                return;
            }
            PsiModifierList mods = ctor.getModifierList();
            mods.setModifierProperty(PsiModifier.PUBLIC, false);
            mods.setModifierProperty(PsiModifier.PROTECTED, false);
            mods.setModifierProperty(PsiModifier.PRIVATE, true);
        }
    }

    // ── Fix 2: add a private no-arg constructor ───────────────────────────────────

    private static final class AddPrivateConstructorFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Add private no-arg constructor";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement el = descriptor.getPsiElement();
            PsiClass psiClass = el instanceof PsiClass c ? c
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(el, PsiClass.class);
            if (psiClass == null) {
                return;
            }

            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiMethod privateCtor = factory.createMethodFromText(
                    "private " + psiClass.getName() + "() { }", psiClass);

            // Insert before the first method or at the top of the class body
            @Nullable PsiMethod firstMethod = this.firstMember(psiClass);
            if (firstMethod != null) {
                psiClass.addBefore(privateCtor, firstMethod);
            } else {
                psiClass.add(privateCtor);
            }
        }

        private @Nullable PsiMethod firstMember(PsiClass psiClass) {
            for (PsiElement member : psiClass.getChildren()) {
                if (member instanceof PsiMethod) {
                    return (PsiMethod) member;
                }
            }
            return null;
        }
    }
}
