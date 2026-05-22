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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * Detects AssertJ assertions that use suboptimal or verbose matchers.
 * Suggests idiomatic alternatives such as {@code isEmpty()} instead of {@code isEqualTo("")},
 * {@code hasSize(n)} instead of {@code size().isEqualTo(n)}, and direct Optional/collection
 * matchers instead of chaining through {@code isTrue()} / {@code isFalse()}.
 *
 * @author Akmal Sarhan
 */
public abstract class SpringAssertJWrongMatcherInspection extends AbstractBaseJavaLocalInspectionTool {

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
                inspect(call, holder);
            }
        };
    }

    protected abstract void inspect(PsiMethodCallExpression call, ProblemsHolder holder);

    protected static Match match(PsiMethodCallExpression call) {
        String terminal = call.getMethodExpression().getReferenceName();
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (terminal == null || !(qualifier instanceof PsiMethodCallExpression assertThatCall)
                || !"assertThat".equals(assertThatCall.getMethodExpression().getReferenceName())) {
            return null;
        }
        PsiExpression[] subjectArgs = assertThatCall.getArgumentList().getExpressions();
        if (subjectArgs.length != 1) {
            return null;
        }
        PsiExpression assertThatQualifier = assertThatCall.getMethodExpression().getQualifierExpression();
        String prefix = (assertThatQualifier != null) ? assertThatQualifier.getText() + "." : "";
        return new Match(terminal, subjectArgs[0], call.getArgumentList().getExpressions(), prefix);
    }

    protected static void register(ProblemsHolder holder, PsiMethodCallExpression call, String message, String replacement) {
        holder.registerProblem(call, message, ProblemHighlightType.WARNING, new RewriteFix(replacement));
    }

    protected static boolean isEmptyStringLiteral(PsiExpression expression) {
        return expression instanceof PsiLiteralExpression literal && "".equals(literal.getValue());
    }

    protected static boolean isZeroLiteral(PsiExpression expression) {
        return expression instanceof PsiLiteralExpression literal && Integer.valueOf(0).equals(literal.getValue());
    }

    protected static boolean isLongExpression(PsiExpression expression) {
        PsiType type = expression.getType();
        return type != null && "long".equals(type.getCanonicalText());
    }

    protected record Match(String terminal, PsiExpression subject, PsiExpression[] terminalArgs, String prefix) {
    }

    public static class EmptyStringEquality extends SpringAssertJWrongMatcherInspection {
        @Override
        protected void inspect(PsiMethodCallExpression call, ProblemsHolder holder) {
            Match m = match(call);
            if (m != null && "isEqualTo".equals(m.terminal()) && m.terminalArgs().length == 1
                    && isEmptyStringLiteral(m.terminalArgs()[0])) {
                register(holder, call, "Use isEmpty() instead of isEqualTo(\"\")",
                        m.prefix() + "assertThat(" + m.subject().getText() + ").isEmpty()");
            }
        }
    }

    public static class BooleanMethodMatcher extends SpringAssertJWrongMatcherInspection {
        @Override
        protected void inspect(PsiMethodCallExpression call, ProblemsHolder holder) {
            Match m = match(call);
            if (m == null || m.terminalArgs().length != 0 || !("isTrue".equals(m.terminal()) || "isFalse".equals(m.terminal()))
                    || !(m.subject() instanceof PsiMethodCallExpression subjectCall)) {
                return;
            }
            PsiExpression subjectQualifier = subjectCall.getMethodExpression().getQualifierExpression();
            if (subjectQualifier == null) {
                return;
            }
            String method = subjectCall.getMethodExpression().getReferenceName();
            PsiExpression[] args = subjectCall.getArgumentList().getExpressions();
            boolean isTrue = "isTrue".equals(m.terminal());
            String q = subjectQualifier.getText();
            if ("equals".equals(method) && args.length == 1 && isTrue) {
                register(holder, call, "Use isEqualTo(...) instead of equals(...).isTrue()",
                        m.prefix() + "assertThat(" + q + ").isEqualTo(" + args[0].getText() + ")");
            } else if ("isPresent".equals(method) && args.length == 0) {
                register(holder, call, "Use AssertJ Optional matcher instead of isPresent().isTrue()/isFalse()",
                        m.prefix() + "assertThat(" + q + ")." + (isTrue ? "isPresent" : "isNotPresent") + "()");
            } else if ("isEmpty".equals(method) && args.length == 0) {
                register(holder, call, "Use isEmpty()/isNotEmpty() on the subject directly",
                        m.prefix() + "assertThat(" + q + ")." + (isTrue ? "isEmpty" : "isNotEmpty") + "()");
            } else if ("contains".equals(method) && args.length == 1) {
                register(holder, call, "Use contains()/doesNotContain() on the subject directly",
                        m.prefix() + "assertThat(" + q + ")." + (isTrue ? "contains" : "doesNotContain")
                                + "(" + args[0].getText() + ")");
            }
        }
    }

    public static class SizeEqualsMatcher extends SpringAssertJWrongMatcherInspection {
        @Override
        protected void inspect(PsiMethodCallExpression call, ProblemsHolder holder) {
            Match m = match(call);
            if (m == null || !"isEqualTo".equals(m.terminal()) || m.terminalArgs().length != 1
                    || !(m.subject() instanceof PsiMethodCallExpression sizeCall)
                    || !"size".equals(sizeCall.getMethodExpression().getReferenceName())
                    || sizeCall.getArgumentList().getExpressions().length != 0
                    || sizeCall.getMethodExpression().getQualifierExpression() == null) {
                return;
            }
            PsiExpression sizeQualifier = sizeCall.getMethodExpression().getQualifierExpression();
            if (isZeroLiteral(m.terminalArgs()[0])) {
                register(holder, call, "Use isEmpty() instead of size().isEqualTo(0)",
                        m.prefix() + "assertThat(" + sizeQualifier.getText() + ").isEmpty()");
            } else {
                String sizeArg = m.terminalArgs()[0].getText();
                if (isLongExpression(m.terminalArgs()[0])) {
                    sizeArg = "(int)(" + sizeArg + ")";
                }
                register(holder, call, "Use hasSize(n) instead of size().isEqualTo(n)",
                        m.prefix() + "assertThat(" + sizeQualifier.getText() + ").hasSize(" + sizeArg + ")");
            }
        }
    }

    private static class RewriteFix implements LocalQuickFix {
        private final String replacement;

        RewriteFix(String replacement) {
            this.replacement = replacement;
        }

        @Override
        @NotNull
        public String getName() {
            return "Replace with " + this.replacement;
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return "Use idiomatic AssertJ matcher";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element instanceof PsiMethodCallExpression call) {
                PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                call.replace(factory.createExpressionFromText(this.replacement, call));
            }
        }
    }
}
