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

import java.util.Set;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.annotations.NotNull;

/**
 * SpringMockitoArgumentMatcherMix: when a Mockito stubbing or verification uses argument matchers
 * (any(), eq(), anyInt(), ...) for some arguments and raw values for others, the call fails at
 * runtime. Reports the raw arguments and suggests wrapping them in eq(...).
 *
 * @author Akmal Sarhan
 */
public class SpringMockitoArgumentMatcherMixInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final Set<String> MATCHER_NAMES = Set.of(
            "any", "anyInt", "anyLong", "anyShort", "anyByte", "anyChar", "anyFloat", "anyDouble",
            "anyBoolean", "anyString", "anyList", "anySet", "anyMap", "anyCollection", "anyIterable",
            "anyVararg", "eq", "same", "isA", "isNull", "isNotNull", "notNull", "nullable",
            "argThat", "longThat", "intThat", "booleanThat", "byteThat", "charThat", "doubleThat",
            "floatThat", "shortThat", "matches", "contains", "endsWith", "startsWith", "refEq");

    private static final Set<String> STUBBING_ROOT_NAMES = Set.of("when", "verify", "doReturn",
            "doThrow", "doNothing", "doAnswer", "given");

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                if (!isInsideMockitoStubbing(call)) {
                    return;
                }
                PsiExpression[] arguments = call.getArgumentList().getExpressions();
                if (arguments.length < 2) {
                    return;
                }
                int matcherCount = 0;
                int rawCount = 0;
                for (PsiExpression argument : arguments) {
                    if (isMatcherCall(argument)) {
                        matcherCount++;
                    } else {
                        rawCount++;
                    }
                }
                if (matcherCount > 0 && rawCount > 0) {
                    for (PsiExpression argument : arguments) {
                        if (!isMatcherCall(argument)) {
                            holder.registerProblem(argument,
                                    "Mixing matchers and raw values in a Mockito call — wrap with eq(...)",
                                    ProblemHighlightType.WARNING,
                                    new WrapRawValueWithEqFix());
                        }
                    }
                }
            }
        };
    }

    private static boolean isMatcherCall(PsiExpression expression) {
        if (!(expression instanceof PsiMethodCallExpression call)) {
            return false;
        }
        String name = call.getMethodExpression().getReferenceName();
        return name != null && MATCHER_NAMES.contains(name);
    }

    private static String matcherQualifier(PsiMethodCallExpression call) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        return (qualifier != null) ? qualifier.getText() + "." : "";
    }

    private static boolean isInsideMockitoStubbing(PsiMethodCallExpression call) {
        // The reported call is the inner method invoked on a mock, which itself is the argument
        // of a Mockito stubbing root like `when(...)`, `verify(mock)....`, `given(...)`.
        PsiElement parent = call.getParent();
        // when(mock.method(...)) -> argument of when(...)
        if (parent instanceof com.intellij.psi.PsiExpressionList list
                && list.getParent() instanceof PsiMethodCallExpression outer) {
            String outerName = outer.getMethodExpression().getReferenceName();
            if (outerName != null && STUBBING_ROOT_NAMES.contains(outerName)) {
                return true;
            }
        }
        // verify(mock).method(...) -> qualifier of `call` is a verify(...) invocation
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier instanceof PsiMethodCallExpression qualifierCall) {
            String qualifierName = qualifierCall.getMethodExpression().getReferenceName();
            if (qualifierName != null && STUBBING_ROOT_NAMES.contains(qualifierName)) {
                return true;
            }
            // doReturn(x).when(mock).method(...)
            if ("when".equals(qualifierName)) {
                PsiExpression deeper = qualifierCall.getMethodExpression().getQualifierExpression();
                if (deeper instanceof PsiMethodCallExpression deeperCall) {
                    String deeperName = deeperCall.getMethodExpression().getReferenceName();
                    if (deeperName != null && STUBBING_ROOT_NAMES.contains(deeperName)) {
                        return true;
                    }
                }
            }
            // BDDMockito: then(mock).should().method(...) — `should()` chained from `then(mock)`
            if ("should".equals(qualifierName)) {
                PsiExpression deeper = qualifierCall.getMethodExpression().getQualifierExpression();
                if (deeper instanceof PsiMethodCallExpression deeperCall
                        && "then".equals(deeperCall.getMethodExpression().getReferenceName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class WrapRawValueWithEqFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Wrap raw value with eq(...)";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PsiExpression rawArgument)
                    || !(rawArgument.getParent() instanceof PsiExpressionList argumentList)
                    || !(argumentList.getParent() instanceof PsiMethodCallExpression)) {
                return;
            }
            String qualifier = "";
            for (PsiExpression argument : argumentList.getExpressions()) {
                if (argument instanceof PsiMethodCallExpression call && isMatcherCall(argument)) {
                    qualifier = matcherQualifier(call);
                    break;
                }
            }
            PsiExpression replacement = JavaPsiFacade.getElementFactory(project)
                    .createExpressionFromText(qualifier + "eq(" + rawArgument.getText() + ")", rawArgument);
            rawArgument.replace(replacement);
        }
    }
}
