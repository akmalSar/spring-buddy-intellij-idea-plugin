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

import java.util.HashSet;
import java.util.Set;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * SpringTestNoAssertions: a {@code @Test} method whose body contains no assertion-like call
 * (AssertJ {@code assertThat}, JUnit {@code assertX}, Mockito {@code verify}, AssertJ
 * {@code assertThatThrownBy} / {@code assertThatCode}, or Hamcrest {@code assertThat}).
 *
 * @author Akmal Sarhan
 */
public class SpringTestNoAssertionsInspection extends AbstractBaseJavaLocalInspectionTool {

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
                if (!isTestMethod(method)) {
                    return;
                }
                PsiCodeBlock body = method.getBody();
                if (body == null || body.getStatements().length == 0) {
                    return;
                }
                if (containsAssertionLikeCall(body, method.getContainingClass(), new HashSet<>())) {
                    return;
                }
                PsiElement nameId = method.getNameIdentifier();
                if (nameId == null) {
                    return;
                }
                holder.registerProblem(nameId,
                        "Test method '" + method.getName() + "' has no assertion or verify() call",
                        ProblemHighlightType.WARNING);
            }
        };
    }

    private static boolean isTestMethod(PsiMethod method) {
        for (PsiAnnotation annotation : method.getModifierList().getAnnotations()) {
            String fqn = annotation.getQualifiedName();
            if (fqn == null) {
                continue;
            }
            if (fqn.equals("org.junit.jupiter.api.Test")
                    || fqn.equals("org.junit.Test")
                    || fqn.equals("org.junit.jupiter.api.RepeatedTest")
                    || fqn.equals("org.junit.jupiter.params.ParameterizedTest")
                    || fqn.equals("Test")
                    || fqn.equals("RepeatedTest")
                    || fqn.equals("ParameterizedTest")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAssertionLikeCall(PsiCodeBlock body, com.intellij.psi.PsiClass owner,
                                                     Set<PsiMethod> visitedMethods) {
        boolean[] found = {false};
        body.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (found[0]) {
                    return;
                }
                if (element instanceof PsiMethodCallExpression call) {
                    String name = call.getMethodExpression().getReferenceName();
                    if (name != null && (isAssertionLike(name) || isArchUnitRuleCheck(call))) {
                        found[0] = true;
                        return;
                    }
                    PsiMethod resolved = call.resolveMethod();
                    if (resolved != null && owner != null && isSameOrSuperClass(owner, resolved.getContainingClass())
                            && resolved.getBody() != null && visitedMethods.add(resolved)
                            && containsAssertionLikeCall(resolved.getBody(), owner, visitedMethods)) {
                        found[0] = true;
                        return;
                    }
                }
                super.visitElement(element);
            }
        });
        return found[0];
    }

    private static boolean isSameOrSuperClass(com.intellij.psi.PsiClass owner, com.intellij.psi.PsiClass candidate) {
        for (com.intellij.psi.PsiClass current = owner; current != null; current = current.getSuperClass()) {
            if (current == candidate) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAssertionLike(String name) {
        return name.startsWith("assert")
                || name.equals("verify")
                || name.equals("andExpect")
                || name.equals("expectStatus")
                || name.equals("expectBody")
                || name.equals("expectHeader")
                || name.equals("jsonPath")
                || name.equals("verifyNoInteractions")
                || name.equals("verifyNoMoreInteractions")
                || name.equals("then") // BDDMockito.then(mock).should(...)
                || name.equals("fail")
                || name.equals("expectThrows");
    }

    private static boolean isArchUnitRuleCheck(PsiMethodCallExpression call) {
        if (!"check".equals(call.getMethodExpression().getReferenceName())) {
            return false;
        }
        return isArchUnitAssertionChain(call.getMethodExpression().getQualifierExpression());
    }

    private static boolean isArchUnitAssertionChain(PsiExpression expression) {
        PsiExpression current = expression;
        boolean hasShould = false;
        while (current instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            hasShould = hasShould || isArchUnitShouldMethod(name);
            if (isArchUnitRuleDefinitionRoot(name)
                    && "com.tngtech.archunit.lang.syntax.ArchRuleDefinition"
                    .equals(call.getMethodExpression().getQualifierExpression().getText()) && hasShould) {
                return true;
            }
            current = call.getMethodExpression().getQualifierExpression();
        }
        return false;
    }

    private static boolean isArchUnitShouldMethod(String name) {
        return "should".equals(name) || "orShould".equals(name) || "andShould".equals(name);
    }

    private static boolean isArchUnitRuleDefinitionRoot(String name) {
        return "classes".equals(name) || "methods".equals(name) || "fields".equals(name);
    }
}
