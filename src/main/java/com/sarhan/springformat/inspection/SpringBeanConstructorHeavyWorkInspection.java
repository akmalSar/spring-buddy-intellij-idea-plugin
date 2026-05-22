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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Detects Spring bean constructors that perform work on injected dependencies.
 * Constructor initialization happens before dependency injection is complete,
 * so injected collaborators may be partially initialized.
 *
 * @author Akmal Sarhan
 */
public class SpringBeanConstructorHeavyWorkInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                super.visitMethod(method);
                if (!method.isConstructor() || method.getBody() == null || !isSpringBean(method.getContainingClass())) {
                    return;
                }
                for (PsiStatement statement : method.getBody().getStatements()) {
                    if (statement instanceof PsiExpressionStatement expressionStatement
                            && expressionStatement.getExpression() instanceof PsiMethodCallExpression call
                            && isCallOnConstructorParameter(call, method)
                            && !isSimpleObjectMethod(call)) {
                        holder.registerProblem(call,
                                "Constructor performs work on an injected dependency; use @PostConstruct after dependency injection is complete",
                                ProblemHighlightType.WARNING,
                                new MoveStatementToPostConstructFix());
                    }
                }
            }
        };
    }

    private static boolean isSpringBean(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
        PsiModifierList modifiers = psiClass.getModifierList();
        return hasAnnotation(modifiers, "Component") || hasAnnotation(modifiers, "Service")
                || hasAnnotation(modifiers, "Repository") || hasAnnotation(modifiers, "Controller")
                || hasAnnotation(modifiers, "RestController") || hasAnnotation(modifiers, "Configuration");
    }

    private static boolean hasAnnotation(PsiModifierList modifiers, String shortName) {
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if (shortName.equals(name) || (name != null && name.endsWith("." + shortName))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCallOnConstructorParameter(PsiMethodCallExpression call, PsiMethod constructor) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression reference) || reference.resolve() == null) {
            return false;
        }
        for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
            if (reference.resolve() == parameter) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSimpleObjectMethod(PsiMethodCallExpression call) {
        String name = call.getMethodExpression().getReferenceName();
        return "toString".equals(name) || "hashCode".equals(name) || "equals".equals(name);
    }

    private static final class MoveStatementToPostConstructFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Move call to @PostConstruct method";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiMethodCallExpression call = descriptor.getPsiElement() instanceof PsiMethodCallExpression c ? c
                    : PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethodCallExpression.class);
            PsiExpressionStatement statement = PsiTreeUtil.getParentOfType(call, PsiExpressionStatement.class);
            PsiClass psiClass = PsiTreeUtil.getParentOfType(call, PsiClass.class);
            if (call == null || statement == null || psiClass == null) {
                return;
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiMethod initMethod = findPostConstructMethod(psiClass);
            if (initMethod == null) {
                initMethod = factory.createMethodFromText("@PostConstruct\nprivate void init() {\n}", psiClass);
                initMethod.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);
                initMethod = (PsiMethod) psiClass.add(initMethod);
            }
            PsiCodeBlock body = initMethod.getBody();
            if (body == null) {
                return;
            }
            body.addBefore(statement.copy(), body.getRBrace());
            statement.delete();
        }

        private static PsiMethod findPostConstructMethod(PsiClass psiClass) {
            for (PsiMethod method : psiClass.getMethods()) {
                if (hasAnnotation(method.getModifierList(), "PostConstruct")) {
                    return method;
                }
            }
            return null;
        }
    }
}
