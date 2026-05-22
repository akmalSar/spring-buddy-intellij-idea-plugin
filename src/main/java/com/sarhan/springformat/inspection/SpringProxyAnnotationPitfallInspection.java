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
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

/**
 * Detects proxy-blocking modifiers (private, static, final) on Spring proxy-bound
 * annotations such as Security, Cache, and Async, and flags self-invocations that
 * bypass the Spring proxy.
 *
 * @author Akmal Sarhan
 */
public class SpringProxyAnnotationPitfallInspection extends AbstractBaseJavaLocalInspectionTool {

    private final Family family;

    protected SpringProxyAnnotationPitfallInspection(Family family) {
        this.family = family;
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                if (!SpringProxyAnnotationPitfallInspection.this.hasFamilyAnnotation(method)) {
                    SpringProxyAnnotationPitfallInspection.this.reportSelfInvocations(method, holder);
                    return;
                }
                if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
                    register(method, holder, SpringProxyAnnotationPitfallInspection.this.label() + " annotation on private method is not proxied", new MakeMethodPublicFix());
                }
                if (method.hasModifierProperty(PsiModifier.STATIC)) {
                    register(method, holder, SpringProxyAnnotationPitfallInspection.this.label() + " annotation on static method is not proxied", new RemoveModifierFix(PsiModifier.STATIC, "Remove static modifier"));
                }
                if (method.hasModifierProperty(PsiModifier.FINAL)) {
                    register(method, holder, SpringProxyAnnotationPitfallInspection.this.label() + " annotation on final method cannot be subclass-proxied", new RemoveModifierFix(PsiModifier.FINAL, "Remove final modifier"));
                }
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null && containingClass.hasModifierProperty(PsiModifier.FINAL)) {
                    register(method, holder, SpringProxyAnnotationPitfallInspection.this.label() + " annotation in final class cannot be subclass-proxied", null);
                }
                if (SpringProxyAnnotationPitfallInspection.this.thisFamily(Family.ASYNC) && method.getReturnType() != null
                        && "void".equals(method.getReturnType().getCanonicalText())) {
                    register(method, holder, "@Async void method exceptions are only handled by AsyncUncaughtExceptionHandler", new ChangeReturnTypeToCompletableFutureFix());
                }
            }
        };
    }

    private boolean hasFamilyAnnotation(PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            if (this.matchesFamily(annotation)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesFamily(PsiAnnotation annotation) {
        return switch (this.family) {
            case SECURITY -> isAnnotation(annotation, "PreAuthorize") || isAnnotation(annotation, "PostAuthorize")
                    || isAnnotation(annotation, "Secured") || isAnnotation(annotation, "RolesAllowed");
            case CACHE -> isAnnotation(annotation, "Cacheable") || isAnnotation(annotation, "CachePut")
                    || isAnnotation(annotation, "CacheEvict");
            case ASYNC -> isAnnotation(annotation, "Async");
        };
    }

    private static boolean isAnnotation(PsiAnnotation annotation, String shortName) {
        String name = annotation.getQualifiedName();
        if (name == null && annotation.getNameReferenceElement() != null) {
            name = annotation.getNameReferenceElement().getText();
        }
        return shortName.equals(name) || (name != null && name.endsWith("." + shortName));
    }

    private void reportSelfInvocations(PsiMethod caller, ProblemsHolder holder) {
        PsiClass containingClass = caller.getContainingClass();
        if (containingClass == null || caller.getBody() == null) {
            return;
        }
        caller.getBody().accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                PsiMethod target = expression.resolveMethod();
                PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
                if (target != null && target.getContainingClass() == containingClass
                        && (qualifier == null || qualifier instanceof PsiThisExpression)
                        && SpringProxyAnnotationPitfallInspection.this.hasFamilyAnnotation(target)) {
                    holder.registerProblem(
                            expression,
                            SpringProxyAnnotationPitfallInspection.this.label() + " self-invocation bypasses the Spring proxy",
                            ProblemHighlightType.ERROR);
                }
            }
        });
    }

    private String label() {
        return switch (this.family) {
            case SECURITY -> "Spring Security";
            case CACHE -> "Spring Cache";
            case ASYNC -> "@Async";
        };
    }

    private boolean thisFamily(Family family) {
        return this.family == family;
    }

    private static void register(PsiMethod method, ProblemsHolder holder, String message, LocalQuickFix fix) {
        PsiIdentifier nameIdentifier = method.getNameIdentifier();
        PsiElement element = (nameIdentifier != null) ? nameIdentifier : method;
        if (fix != null) {
            holder.registerProblem(element, message, ProblemHighlightType.ERROR, fix);
        } else {
            holder.registerProblem(element, message, ProblemHighlightType.ERROR);
        }
    }

    private enum Family {SECURITY, CACHE, ASYNC}

    public static class Security extends SpringProxyAnnotationPitfallInspection {
        public Security() {
            super(Family.SECURITY);
        }
    }

    public static class Cache extends SpringProxyAnnotationPitfallInspection {
        public Cache() {
            super(Family.CACHE);
        }
    }

    public static class Async extends SpringProxyAnnotationPitfallInspection {
        public Async() {
            super(Family.ASYNC);
        }
    }

    private static final class MakeMethodPublicFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Make method public";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethod method = descriptor.getPsiElement() instanceof PsiMethod m ? m
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
            if (method != null) {
                method.getModifierList().setModifierProperty(PsiModifier.PRIVATE, false);
                method.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
            }
        }
    }

    private static class RemoveModifierFix implements LocalQuickFix {
        private final String modifier;
        private final String name;

        RemoveModifierFix(String modifier, String name) {
            this.modifier = modifier;
            this.name = name;
        }

        @Override
        @NotNull
        public String getName() {
            return this.name;
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethod method = descriptor.getPsiElement() instanceof PsiMethod m ? m
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
            if (method != null) {
                method.getModifierList().setModifierProperty(this.modifier, false);
            }
        }
    }

    private static final class ChangeReturnTypeToCompletableFutureFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Change return type to CompletableFuture<Void>";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethod method = descriptor.getPsiElement() instanceof PsiMethod m ? m
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethod.class);
            if (method == null) {
                return;
            }
            PsiTypeElement returnType = method.getReturnTypeElement();
            if (returnType != null) {
                PsiTypeElement newReturnType = JavaPsiFacade.getElementFactory(project)
                        .createTypeElementFromText("java.util.concurrent.CompletableFuture<Void>", method);
                returnType.replace(newReturnType);
            }

            PsiCodeBlock body = method.getBody();
            if (body != null) {
                PsiStatement[] statements = body.getStatements();
                boolean hasTerminal = false;
                for (PsiStatement statement : statements) {
                    if (statement instanceof PsiReturnStatement ret) {
                        if (ret.getReturnValue() == null) {
                            statement.replace(JavaPsiFacade.getElementFactory(project)
                                    .createStatementFromText(
                                            "return java.util.concurrent.CompletableFuture.completedFuture(null);",
                                            method));
                        }
                        hasTerminal = true;
                    }
                    else if (statement instanceof com.intellij.psi.PsiThrowStatement) {
                        hasTerminal = true;
                    }
                }
                if (!hasTerminal) {
                    body.addBefore(JavaPsiFacade.getElementFactory(project)
                                    .createStatementFromText(
                                            "return java.util.concurrent.CompletableFuture.completedFuture(null);",
                                            method),
                            body.getRBrace());
                }
            }

            if (method.getContainingFile() instanceof PsiJavaFile javaFile) {
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(javaFile);
            }
        }
    }
}
