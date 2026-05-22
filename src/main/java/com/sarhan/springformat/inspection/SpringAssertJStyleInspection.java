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
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bans JUnit-style assertions in favour of AssertJ fluent assertions.
 * <p>
 * Flags usages of {@code org.junit.Assert} and Jupiter {@code Assertions}
 * (static imports or direct calls) and offers a quick-fix to rewrite them
 * as AssertJ {@code assertThat(...)} chains.
 *
 * @author Akmal Sarhan
 */
public class SpringAssertJStyleInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String JUNIT4_ASSERT = "org.junit.Assert";
    private static final String JUNIT5_ASSERTIONS = "org.junit.jupiter.api.Assertions";
    private static final String JUNIT5_ASSUMPTIONS = "org.junit.jupiter.api.Assumptions";
    private static final String ASSERTJ_ASSERTIONS = "org.assertj.core.api.Assertions";
    private static final String ASSERTJ_ASSUMPTIONS = "org.assertj.core.api.Assumptions";
    private static final String ASSERTJ_SOFT_ASSERTIONS = "org.assertj.core.api.SoftAssertions";

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitImportStatement(@NotNull PsiImportStatement statement) {
                // Assertion calls are the actionable locations. Flagging imports too duplicates results per file.
            }

            @Override
            public void visitImportStaticStatement(@NotNull PsiImportStaticStatement statement) {
                // Assertion calls are the actionable locations. Flagging imports too duplicates results per file.
            }

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                if (!SpringInspectionUtil.isInJavaFile(call)) {
                    return;
                }
                String methodName = methodName(call);
                if (!isConvertibleAssertion(methodName)) {
                    return;
                }
                if (isJunitAssertionCall(call)) {
                    holder.registerProblem(call,
                            "Use AssertJ assertions instead of 'Assertions' (Spring convention)",
                            ProblemHighlightType.WARNING, new ConvertJunitAssertionsFix());
                }
            }

            @Override
            public void visitTryStatement(@NotNull PsiTryStatement statement) {
                if (!SpringInspectionUtil.isInJavaFile(statement)) {
                    return;
                }
                if (canConvertTryFail(statement)) {
                    holder.registerProblem(statement,
                            "Use AssertJ assertions instead of 'Assertions' (Spring convention)",
                            ProblemHighlightType.WARNING, new ConvertJunitAssertionsFix());
                }
            }

            @Override
            public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
                if (!SpringInspectionUtil.isInJavaFile(expression)) {
                    return;
                }
                PsiExpression qualifier = expression.getQualifierExpression();
                if (qualifier != null && "Assertions".equals(qualifier.getText())
                        && expression.getText().endsWith("::fail")) {
                    holder.registerProblem(expression,
                            "Use AssertJ assertions instead of 'Assertions' (Spring convention)",
                            ProblemHighlightType.WARNING, new ConvertJunitAssertionsFix());
                }
            }
        };
    }

    private static boolean isJunitMethodReference(PsiMethodReferenceExpression expression) {
        PsiElement resolved = expression.resolve();
        return resolved instanceof com.intellij.psi.PsiMethod method
                && method.getContainingClass() != null
                && isJunitApiClass(method.getContainingClass().getQualifiedName());
    }

    private static boolean isJunitApiClass(String fqn) {
        return fqn.equals(JUNIT4_ASSERT) || fqn.equals(JUNIT5_ASSERTIONS) || fqn.equals(JUNIT5_ASSUMPTIONS);
    }

    private static boolean isConvertibleAssertion(@Nullable String methodName) {
        return "assertEquals".equals(methodName) || "assertNotEquals".equals(methodName)
                || "assertNull".equals(methodName) || "assertNotNull".equals(methodName)
                || "assertTrue".equals(methodName) || "assertFalse".equals(methodName)
                || "assertSame".equals(methodName) || "assertNotSame".equals(methodName)
                || "assertArrayEquals".equals(methodName) || "assertIterableEquals".equals(methodName)
                || "assertLinesMatch".equals(methodName) || "assertDoesNotThrow".equals(methodName)
                || "assertThrows".equals(methodName) || "assertInstanceOf".equals(methodName)
                || "assertTimeout".equals(methodName) || "assertTimeoutPreemptively".equals(methodName)
                || "assertAll".equals(methodName) || "assumeTrue".equals(methodName)
                || "assumeFalse".equals(methodName) || "assertThat".equals(methodName) || "fail".equals(methodName);
    }

    private static boolean isJunitAssertionCall(PsiMethodCallExpression call) {
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null) {
            return hasJunitStaticImport(call);
        }
        String qualifierText = qualifier.getText();
        if (isAssertJApiClass(qualifierText) || resolvesToAssertJApiClass(qualifier)) {
            return false;
        }
        if ("Assert".equals(qualifierText) || "Assertions".equals(qualifierText)
                || "Assumptions".equals(qualifierText) || qualifierText.endsWith(".Assertions")
                || qualifierText.endsWith(".Assumptions") || qualifierText.endsWith(".Assert")) {
            return true;
        }
        if (JUNIT4_ASSERT.equals(qualifierText) || JUNIT5_ASSERTIONS.equals(qualifierText)
                || JUNIT5_ASSUMPTIONS.equals(qualifierText)) {
            return true;
        }
        if (!(qualifier instanceof PsiReferenceExpression qRef)) {
            return false;
        }
        PsiElement resolved = qRef.resolve();
        return resolved instanceof PsiClass cls && isJunitApiClass(cls.getQualifiedName());
    }

    private static boolean isAssertJApiClass(String text) {
        return ASSERTJ_ASSERTIONS.equals(text) || ASSERTJ_ASSUMPTIONS.equals(text);
    }

    private static boolean resolvesToAssertJApiClass(PsiExpression qualifier) {
        if (!(qualifier instanceof PsiReferenceExpression reference)) {
            return false;
        }
        PsiElement resolved = reference.resolve();
        return resolved instanceof PsiClass cls && isAssertJApiClass(cls.getQualifiedName());
    }

    private static boolean hasJunitStaticImport(PsiMethodCallExpression call) {
        PsiFile file = call.getContainingFile();
        if (!(file instanceof PsiJavaFile javaFile)) {
            return false;
        }
        PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return false;
        }
        String methodName = methodName(call);
        if (methodName != null && call.getMethodExpression().getQualifierExpression() == null
                && call.getMethodExpression().resolve() == null) {
            return true;
        }
        for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
            PsiJavaCodeReferenceElement ref = statement.getImportReference();
            String text = (ref != null) ? ref.getCanonicalText() : statement.getText();
            if (text == null) {
                continue;
            }
            if (matchesStaticAssertionImport(statement, text, JUNIT4_ASSERT, methodName)
                    || matchesStaticAssertionImport(statement, text, JUNIT5_ASSERTIONS, methodName)
                    || matchesStaticAssertionImport(statement, text, JUNIT5_ASSUMPTIONS, methodName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStaticAssertionImport(PsiImportStaticStatement statement, String text,
                                                        String assertionClass, String methodName) {
        return (statement.isOnDemand() && assertionClass.equals(text))
                || text.equals(assertionClass + "." + methodName)
                || text.equals(assertionClass + ".*");
    }

    private static @Nullable String methodName(PsiMethodCallExpression call) {
        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        return (nameElement != null) ? nameElement.getText() : null;
    }

    private static boolean canConvertTryFail(PsiTryStatement statement) {
        PsiCodeBlock tryBlock = statement.getTryBlock();
        PsiCatchSection[] catchSections = statement.getCatchSections();
        if (tryBlock == null || tryBlock.getStatements().length != 1 || catchSections.length != 1
                || statement.getFinallyBlock() != null || statement.getResourceList() != null) {
            return false;
        }
        PsiCodeBlock catchBlock = catchSections[0].getCatchBlock();
        if (catchBlock == null || catchBlock.getStatements().length != 1) {
            return false;
        }
        PsiStatement catchStatement = catchBlock.getStatements()[0];
        if (!(catchStatement instanceof PsiExpressionStatement expressionStatement)
                || !(expressionStatement.getExpression() instanceof PsiMethodCallExpression failCall)) {
            return false;
        }
        return "fail".equals(methodName(failCall)) && isJunitAssertionCall(failCall);
    }

    private static final class ConvertJunitAssertionsFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Convert JUnit assertions to AssertJ";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PsiFile file = element.getContainingFile();
            if (!(file instanceof PsiJavaFile javaFile)) {
                return;
            }
            convertTryFail(project, javaFile);
            convertMethodReferences(project, javaFile);
            convertAssertions(project, javaFile);
            removeJunitAssertionImports(javaFile);
            ensureImportTextually(project, javaFile, ASSERTJ_ASSERTIONS);
            if (javaFile.getText().contains("Assumptions.")) {
                ensureImportTextually(project, javaFile, ASSERTJ_ASSUMPTIONS);
            }
            if (javaFile.getText().contains("SoftAssertions.")) {
                ensureImportTextually(project, javaFile, ASSERTJ_SOFT_ASSERTIONS);
            }
        }

        private static void convertTryFail(Project project, PsiJavaFile javaFile) {
            java.util.List<PsiTryStatement> targets = new java.util.ArrayList<>();
            javaFile.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitTryStatement(@NotNull PsiTryStatement statement) {
                    super.visitTryStatement(statement);
                    if (canConvertTryFail(statement)) {
                        targets.add(statement);
                    }
                }
            });
            for (PsiTryStatement statement : targets) {
                if (!statement.isValid() || !canConvertTryFail(statement)) {
                    continue;
                }
                PsiStatement tryStatement = statement.getTryBlock().getStatements()[0];
                String replacement = buildAssertThatCodeStatement(statement, tryStatement);
                statement.replace(JavaPsiFacade.getElementFactory(project).createStatementFromText(replacement, statement));
            }
        }

        private static String buildAssertThatCodeStatement(PsiTryStatement tryStatement, PsiStatement statement) {
            if (statement instanceof PsiExpressionStatement expressionStatement
                    && expressionStatement.getExpression() instanceof PsiAssignmentExpression assignment) {
                PsiExpression right = assignment.getRExpression();
                PsiExpression left = assignment.getLExpression();
                if (left instanceof PsiReferenceExpression reference && right instanceof PsiMethodCallExpression call) {
                    PsiVariable variable = reference.resolve() instanceof PsiVariable v ? v : null;
                    if (variable != null) {
                        String tmp = uniqueName(tryStatement, variable.getName() + "Value");
                        String type = variable.getType().getCanonicalText();
                        String erasedType = eraseGenerics(type);
                        String cast = type.equals(erasedType) ? "" : "(" + type + ") ";
                        return "{ " + erasedType + "[] " + tmp + " = new " + erasedType + "[1]; "
                                + "Assertions.assertThatCode(() -> " + tmp + "[0] = " + call.getText()
                                + ").doesNotThrowAnyException(); " + left.getText() + " = " + cast + tmp + "[0]; }";
                    }
                }
            }
            return "Assertions.assertThatCode(() -> { " + statement.getText()
                    + " }).doesNotThrowAnyException();";
        }

        private static String eraseGenerics(String type) {
            StringBuilder result = new StringBuilder();
            int depth = 0;
            for (int i = 0; i < type.length(); i++) {
                char c = type.charAt(i);
                if (c == '<') {
                    depth++;
                } else if (c == '>') {
                    if (depth > 0) {
                        depth--;
                    }
                } else if (depth == 0) {
                    result.append(c);
                }
            }
            return result.toString();
        }

        private static void convertAssertions(Project project, PsiJavaFile javaFile) {
            java.util.List<PsiDeclarationStatement> declarationTargets = new java.util.ArrayList<>();
            java.util.List<PsiMethodCallExpression> callTargets = new java.util.ArrayList<>();
            javaFile.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {
                    if (isUnwrappedExecutableDeclaration(statement)) {
                        declarationTargets.add(statement);
                        return;
                    }
                    super.visitDeclarationStatement(statement);
                }

                @Override
                public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                    super.visitMethodCallExpression(call);
                    if (convertCall(call) != null) {
                        callTargets.add(call);
                    }
                }
            });
            for (PsiDeclarationStatement statement : declarationTargets) {
                if (statement.isValid()) {
                    convertUnwrappedExecutableDeclaration(project, statement);
                }
            }
            // Replace innermost calls first so outer call texts still capture already-converted inner expressions consistently.
            for (PsiMethodCallExpression call : callTargets) {
                if (!call.isValid()) {
                    continue;
                }
                String replacement = convertCall(call);
                if (replacement != null) {
                    call.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacement, call));
                }
            }
        }

        private static void convertMethodReferences(Project project, PsiJavaFile javaFile) {
            java.util.List<PsiMethodReferenceExpression> targets = new java.util.ArrayList<>();
            javaFile.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
                    super.visitMethodReferenceExpression(expression);
                    PsiExpression qualifier = expression.getQualifierExpression();
                    if (qualifier != null && "Assertions".equals(qualifier.getText()) && expression.getText().endsWith("::fail")) {
                        targets.add(expression);
                    }
                }
            });
            for (PsiMethodReferenceExpression expression : targets) {
                if (expression.isValid()) {
                    expression.replace(JavaPsiFacade.getElementFactory(project)
                            .createExpressionFromText("() -> Assertions.fail(\"exception\")", expression));
                }
            }
        }

        private static boolean isUnwrappedExecutableDeclaration(PsiDeclarationStatement statement) {
            PsiElement[] declaredElements = statement.getDeclaredElements();
            if (declaredElements.length != 1 || !(declaredElements[0] instanceof PsiLocalVariable variable)) {
                return false;
            }
            PsiExpression initializer = variable.getInitializer();
            return initializer instanceof PsiMethodCallExpression call
                    && isReturnValueWrapper(methodName(call))
                    && isJunitAssertionCall(call);
        }

        private static boolean convertUnwrappedExecutableDeclaration(Project project, PsiDeclarationStatement statement) {
            PsiElement[] declaredElements = statement.getDeclaredElements();
            if (declaredElements.length != 1 || !(declaredElements[0] instanceof PsiLocalVariable variable)) {
                return false;
            }
            PsiExpression initializer = variable.getInitializer();
            if (!(initializer instanceof PsiMethodCallExpression call) || !isReturnValueWrapper(methodName(call))
                    || !isJunitAssertionCall(call)) {
                return false;
            }
            PsiExpression[] args = call.getArgumentList().getExpressions();
            int executableIndex = "assertDoesNotThrow".equals(methodName(call)) ? 0 : 1;
            if (args.length <= executableIndex) {
                return false;
            }
            String unwrapped = unwrapExecutableValue(args[executableIndex]);
            if (unwrapped == null) {
                return false;
            }
            String replacement = statement.getText().replace(initializer.getText(), unwrapped);
            statement.replace(JavaPsiFacade.getElementFactory(project).createStatementFromText(replacement, statement));
            return true;
        }

        private static boolean isReturnValueWrapper(@Nullable String methodName) {
            return "assertDoesNotThrow".equals(methodName) || "assertTimeout".equals(methodName)
                    || "assertTimeoutPreemptively".equals(methodName);
        }

        private static @Nullable String unwrapExecutableValue(PsiExpression executable) {
            if (executable instanceof PsiLambdaExpression lambda) {
                PsiElement body = lambda.getBody();
                if (body instanceof PsiExpression expression) {
                    return expression.getText();
                }
                if (body instanceof PsiCodeBlock codeBlock) {
                    PsiStatement[] statements = codeBlock.getStatements();
                    if (statements.length > 0 && statements[statements.length - 1] instanceof PsiReturnStatement returnStatement
                            && returnStatement.getReturnValue() != null) {
                        return returnStatement.getReturnValue().getText();
                    }
                }
            }
            return unwrapExecutable(executable.getText());
        }

        private static @Nullable String unwrapExecutable(String executable) {
            int arrow = executable.indexOf("->");
            if (arrow < 0) {
                return null;
            }
            String body = executable.substring(arrow + 2).trim();
            if (body.startsWith("{") && body.endsWith("}")) {
                body = body.substring(1, body.length() - 1).trim();
                if (body.startsWith("return ")) {
                    body = body.substring("return ".length()).trim();
                }
                if (body.endsWith(";")) {
                    body = body.substring(0, body.length() - 1).trim();
                }
            }
            return body.isEmpty() ? null : body;
        }

        private static @Nullable String convertCall(PsiMethodCallExpression call) {
            if (!isJunitAssertionCall(call)) {
                return null;
            }
            String methodName = methodName(call);
            PsiExpression[] args = call.getArgumentList().getExpressions();
            if ("assertEquals".equals(methodName) && args.length >= 2) {
                EqualityArguments arguments = equalityArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                if (isNullLiteral(arguments.expected())) {
                    return assertThat(arguments.actual()) + message(arguments.message()) + ".isNull()";
                }
                if (isBooleanLiteral(arguments.expected(), true)) {
                    return assertThat(arguments.actual()) + message(arguments.message()) + ".isTrue()";
                }
                if (isBooleanLiteral(arguments.expected(), false)) {
                    return assertThat(arguments.actual()) + message(arguments.message()) + ".isFalse()";
                }
                if (arguments.delta() != null) {
                    return assertThat(arguments.actual()) + message(arguments.message()) + ".isCloseTo("
                            + arguments.expected().getText() + ", Assertions.within(" + arguments.delta().getText() + "))";
                }
                return assertThat(arguments.actual()) + message(arguments.message()) + ".isEqualTo(" + arguments.expected().getText() + ")";
            }
            if ("assertNotEquals".equals(methodName) && args.length >= 2) {
                EqualityArguments arguments = equalityArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                return assertThat(arguments.actual()) + message(arguments.message()) + ".isNotEqualTo(" + arguments.expected().getText() + ")";
            }
            if ("assertArrayEquals".equals(methodName) && args.length >= 2) {
                EqualityArguments arguments = equalityArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                String matcher = canUseContainsExactly(arguments.expected(), arguments.actual())
                        ? ".containsExactly(" + arguments.expected().getText() + ")"
                        : ".isEqualTo(" + arguments.expected().getText() + ")";
                return assertThat(arguments.actual()) + message(arguments.message()) + matcher;
            }
            if ("assertIterableEquals".equals(methodName) && args.length >= 2) {
                EqualityArguments arguments = equalityArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                return assertThat(arguments.actual()) + message(arguments.message()) + ".containsExactlyElementsOf(" + arguments.expected().getText() + ")";
            }
            if ("assertLinesMatch".equals(methodName) && args.length >= 2) {
                EqualityArguments arguments = equalityArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                return assertThat(arguments.actual()) + message(arguments.message()) + ".containsExactlyElementsOf(" + arguments.expected().getText() + ")";
            }
            if ("assertSame".equals(methodName) && args.length >= 2) {
                EqualityArguments arguments = equalityArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                return assertThat(arguments.actual()) + message(arguments.message()) + ".isSameAs(" + arguments.expected().getText() + ")";
            }
            if ("assertNotSame".equals(methodName) && args.length >= 2) {
                EqualityArguments arguments = equalityArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                return assertThat(arguments.actual()) + message(arguments.message()) + ".isNotSameAs(" + arguments.expected().getText() + ")";
            }
            if ("assertNull".equals(methodName) && args.length >= 1) {
                UnaryArguments arguments = unaryArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                return assertThat(arguments.actual()) + message(arguments.message()) + ".isNull()";
            }
            if ("assertNotNull".equals(methodName) && args.length >= 1) {
                UnaryArguments arguments = unaryArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                return assertThat(arguments.actual()) + message(arguments.message()) + ".isNotNull()";
            }
            if ("assertTrue".equals(methodName) && args.length >= 1) {
                UnaryArguments arguments = unaryArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                return assertThatBoolean(arguments.actual()) + message(arguments.message()) + ".isTrue()";
            }
            if ("assertFalse".equals(methodName) && args.length >= 1) {
                UnaryArguments arguments = unaryArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                return assertThatBoolean(arguments.actual()) + message(arguments.message()) + ".isFalse()";
            }
            if ("assumeTrue".equals(methodName) && args.length >= 1) {
                UnaryArguments arguments = unaryArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                return assumeThatBoolean(arguments.actual()) + message(arguments.message()) + ".isTrue()";
            }
            if ("assumeFalse".equals(methodName) && args.length >= 1) {
                UnaryArguments arguments = unaryArguments(call, args);
                if (arguments == null) {
                    return null;
                }
                return assumeThatBoolean(arguments.actual()) + message(arguments.message()) + ".isFalse()";
            }
            if ("fail".equals(methodName)) {
                return convertFail(args);
            }
            if ("assertThrows".equals(methodName) && args.length >= 2) {
                PsiElement parent = call.getParent();
                if (parent instanceof PsiLocalVariable variable && variable.getInitializer() == call) {
                    return "Assertions.catchThrowableOfType(" + args[1].getText() + ", " + args[0].getText() + ")";
                }
                return "Assertions.assertThatThrownBy(" + args[1].getText()
                        + ").isInstanceOf(" + args[0].getText() + ")";
            }
            if ("assertInstanceOf".equals(methodName) && args.length >= 2) {
                return "Assertions.assertThat(" + args[1].getText() + ").isInstanceOf(" + args[0].getText() + ")";
            }
            if ("assertDoesNotThrow".equals(methodName) && args.length >= 1) {
                return "Assertions.assertThatCode(" + args[0].getText() + ").doesNotThrowAnyException()";
            }
            if (("assertTimeout".equals(methodName) || "assertTimeoutPreemptively".equals(methodName)) && args.length >= 2) {
                return "Assertions.assertThatCode(" + args[1].getText() + ").doesNotThrowAnyException()";
            }
            if ("assertAll".equals(methodName) && args.length >= 1) {
                return convertAssertAll(call, args);
            }
            if ("assertThat".equals(methodName)) {
                return convertHamcrestAssertThat(args);
            }
            return null;
        }

        private static @Nullable String convertAssertAll(PsiMethodCallExpression call, PsiExpression[] args) {
            int start = (args.length > 1 && isMessageArgument(args[0])) ? 1 : 0;
            if (start >= args.length) {
                return null;
            }
            String parameterName = uniqueName(call, "softly");
            StringBuilder result = new StringBuilder("SoftAssertions.assertSoftly((SoftAssertions " + parameterName + ") -> { ");
            for (int i = start; i < args.length; i++) {
                String statements = unwrapExecutableStatements(args[i]);
                if (statements == null || statements.isBlank()) {
                    return null;
                }
                result.append(statements);
                if (!statements.endsWith(";") && !statements.endsWith("}")) {
                    result.append(";");
                }
                result.append(" ");
            }
            result.append("})");
            return result.toString();
        }

        private static boolean canUseContainsExactly(PsiExpression expected, PsiExpression actual) {
            PsiType expectedType = expected.getType();
            PsiType actualType = actual.getType();
            if (!(expectedType instanceof PsiArrayType expectedArrayType)
                    || !(actualType instanceof PsiArrayType actualArrayType)) {
                return true;
            }
            return actualArrayType.getComponentType().isAssignableFrom(expectedArrayType.getComponentType());
        }

        private static @Nullable String unwrapExecutableStatements(PsiExpression executable) {
            if (executable instanceof PsiLambdaExpression lambda) {
                PsiElement body = lambda.getBody();
                if (body instanceof PsiCodeBlock codeBlock) {
                    String text = codeBlock.getText().trim();
                    return (text.length() >= 2) ? text.substring(1, text.length() - 1).trim() : "";
                }
                if (body instanceof PsiExpression expression) {
                    return expression.getText() + ";";
                }
            }
            return executable.getText() + ";";
        }

        private static @Nullable String convertHamcrestAssertThat(PsiExpression[] args) {
            PsiExpression message = null;
            PsiExpression actual;
            PsiExpression matcher;
            if (args.length == 2) {
                actual = args[0];
                matcher = args[1];
            } else if (args.length == 3) {
                message = args[0];
                actual = args[1];
                matcher = args[2];
            } else {
                return null;
            }
            if (!(matcher instanceof PsiMethodCallExpression matcherCall)) {
                return null;
            }
            String matcherName = methodName(matcherCall);
            PsiExpression[] matcherArgs = matcherCall.getArgumentList().getExpressions();
            if (matcherArgs.length == 0) {
                return null;
            }
            if ("is".equals(matcherName) || "equalTo".equals(matcherName)) {
                return assertThat(actual) + message(message) + ".isEqualTo(" + matcherArgs[0].getText() + ")";
            }
            if ("hasItems".equals(matcherName)) {
                return assertThat(actual) + message(message) + ".contains(" + joined(matcherArgs, 0) + ")";
            }
            return null;
        }

        private static String convertFail(PsiExpression[] args) {
            if (args.length == 0) {
                return "Assertions.fail(\"exception\")";
            }
            if (args.length == 1 && args[0] instanceof PsiLambdaExpression lambda) {
                String message = unwrapSupplierMessage(lambda);
                if (message != null) {
                    return "Assertions.fail(" + message + ")";
                }
            }
            if (args.length == 1 && isLikelyThrowable(args[0])) {
                return "Assertions.fail(\"Unexpected exception\", " + args[0].getText() + ")";
            }
            return "Assertions.fail(" + joined(args, 0) + ")";
        }

        private static @Nullable String unwrapSupplierMessage(PsiLambdaExpression lambda) {
            PsiElement body = lambda.getBody();
            if (body instanceof PsiExpression expression) {
                return expression.getText();
            }
            return null;
        }

        private static boolean isLikelyThrowable(PsiExpression expression) {
            String text = expression.getText().trim();
            if (!text.startsWith("\"") && !text.startsWith("'")) {
                return true;
            }
            String type = (expression.getType() != null) ? expression.getType().getCanonicalText() : null;
            if (type == null && expression instanceof PsiReferenceExpression reference
                    && reference.resolve() instanceof PsiVariable variable) {
                type = variable.getType().getCanonicalText();
                if (type == null) {
                    type = variable.getType().getPresentableText();
                }
            }
            return type != null && ("java.lang.Throwable".equals(type) || "java.lang.Exception".equals(type)
                    || type.endsWith("Exception") || type.endsWith("Error"));
        }

        private static String assertThat(PsiExpression expression) {
            String text = isNullLiteral(expression) ? "(Object) null" : expression.getText();
            return "Assertions.assertThat(" + text + ")";
        }

        private static String assertThatBoolean(PsiExpression expression) {
            return "Assertions.assertThat(" + booleanExpressionText(expression) + ")";
        }

        private static String assumeThatBoolean(PsiExpression expression) {
            return "Assumptions.assumeThat(" + booleanExpressionText(expression) + ")";
        }

        private static String message(@Nullable PsiExpression message) {
            if (message == null) {
                return "";
            }
            String text = supplierMessageText(message);
            return ".as(" + text + ")";
        }

        private static String booleanExpressionText(PsiExpression expression) {
            if (expression instanceof PsiLambdaExpression lambda) {
                PsiElement body = lambda.getBody();
                if (body instanceof PsiExpression bodyExpression) {
                    return bodyExpression.getText();
                }
            }
            if (expression instanceof PsiMethodReferenceExpression methodReference) {
                PsiExpression qualifier = methodReference.getQualifierExpression();
                String referenceName = methodReference.getReferenceName();
                if (qualifier != null && referenceName != null) {
                    return qualifier.getText() + "." + referenceName + "()";
                }
            }
            return expression.getText();
        }

        private static String supplierMessageText(PsiExpression expression) {
            if (expression instanceof PsiLambdaExpression lambda) {
                PsiElement body = lambda.getBody();
                if (body instanceof PsiExpression bodyExpression) {
                    return bodyExpression.getText();
                }
            }
            return expression.getText();
        }

        private static @Nullable EqualityArguments equalityArguments(PsiMethodCallExpression call, PsiExpression[] args) {
            JunitKind kind = junitKind(call);
            if (kind == JunitKind.JUNIT4 && args.length >= 3 && isMessageArgument(args[0])) {
                PsiExpression delta = (args.length >= 4) ? args[3] : null;
                return new EqualityArguments(args[1], args[2], args[0], delta);
            }
            if (kind == JunitKind.JUNIT5 && args.length >= 3 && isMessageArgument(args[args.length - 1])) {
                PsiExpression delta = (args.length >= 4) ? args[2] : null;
                return new EqualityArguments(args[0], args[1], args[args.length - 1], delta);
            }
            if (args.length >= 4 && isMessageArgument(args[0])) {
                return new EqualityArguments(args[1], args[2], args[0], args[3]);
            }
            if (args.length >= 3 && isMessageArgument(args[0])) {
                return new EqualityArguments(args[1], args[2], args[0], null);
            }
            if (args.length >= 3 && isMessageArgument(args[args.length - 1])) {
                PsiExpression delta = (args.length >= 4) ? args[2] : null;
                return new EqualityArguments(args[0], args[1], args[args.length - 1], delta);
            }
            PsiExpression delta = (args.length >= 3) ? args[2] : null;
            return new EqualityArguments(args[0], args[1], null, delta);
        }

        private static @Nullable UnaryArguments unaryArguments(PsiMethodCallExpression call, PsiExpression[] args) {
            JunitKind kind = junitKind(call);
            if (kind == JunitKind.JUNIT4 && args.length >= 2 && isMessageArgument(args[0])) {
                return new UnaryArguments(args[1], args[0]);
            }
            if (kind == JunitKind.JUNIT5 && args.length >= 2 && isMessageArgument(args[args.length - 1])) {
                return new UnaryArguments(args[0], args[args.length - 1]);
            }
            if (args.length >= 2 && isMessageArgument(args[0])) {
                return new UnaryArguments(args[1], args[0]);
            }
            return new UnaryArguments(args[0], (args.length >= 2) ? args[1] : null);
        }

        private static JunitKind junitKind(PsiMethodCallExpression call) {
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (qualifier != null) {
                String qualifierText = qualifier.getText();
                if ("Assert".equals(qualifierText) || JUNIT4_ASSERT.equals(qualifierText) || qualifierText.endsWith(".Assert")) {
                    return JunitKind.JUNIT4;
                }
                if ("Assertions".equals(qualifierText) || JUNIT5_ASSERTIONS.equals(qualifierText) || qualifierText.endsWith(".Assertions")) {
                    return JunitKind.JUNIT5;
                }
            }
            JunitKind importedKind = junitKindFromStaticImports(call);
            if (importedKind != JunitKind.UNKNOWN) {
                return importedKind;
            }
            PsiElement resolved = call.getMethodExpression().resolve();
            if (resolved instanceof PsiMethod method && method.getContainingClass() != null) {
                String qualifiedName = method.getContainingClass().getQualifiedName();
                if (JUNIT4_ASSERT.equals(qualifiedName)) {
                    return JunitKind.JUNIT4;
                }
                if (JUNIT5_ASSERTIONS.equals(qualifiedName)) {
                    return JunitKind.JUNIT5;
                }
            }
            return JunitKind.UNKNOWN;
        }

        private static JunitKind junitKindFromStaticImports(PsiMethodCallExpression call) {
            if (call.getMethodExpression().getQualifierExpression() != null || !(call.getContainingFile() instanceof PsiJavaFile javaFile)) {
                return JunitKind.UNKNOWN;
            }
            PsiImportList importList = javaFile.getImportList();
            String methodName = methodName(call);
            if (importList == null || methodName == null) {
                return JunitKind.UNKNOWN;
            }
            boolean junit4 = false;
            boolean junit5 = false;
            for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
                PsiJavaCodeReferenceElement ref = statement.getImportReference();
                String text = (ref != null) ? ref.getCanonicalText() : null;
                if (text == null) {
                    continue;
                }
                if (matchesStaticAssertionImport(statement, text, JUNIT4_ASSERT, methodName)) {
                    junit4 = true;
                }
                if (matchesStaticAssertionImport(statement, text, JUNIT5_ASSERTIONS, methodName)
                        || matchesStaticAssertionImport(statement, text, JUNIT5_ASSUMPTIONS, methodName)) {
                    junit5 = true;
                }
            }
            if (junit4 && !junit5) {
                return JunitKind.JUNIT4;
            }
            if (junit5 && !junit4) {
                return JunitKind.JUNIT5;
            }
            return JunitKind.UNKNOWN;
        }

        private static boolean isMessageArgument(PsiExpression expression) {
            if (expression instanceof PsiLambdaExpression) {
                return true;
            }
            String type = (expression.getType() != null) ? expression.getType().getCanonicalText() : null;
            return "java.lang.String".equals(type) || "java.util.function.Supplier<java.lang.String>".equals(type)
                    || isStringLiteral(expression);
        }

        private static boolean isStringLiteral(PsiExpression expression) {
            return expression instanceof PsiLiteralExpression literal && literal.getValue() instanceof String;
        }

        private static boolean isNullLiteral(PsiExpression expression) {
            return expression instanceof PsiLiteralExpression literal && literal.getValue() == null;
        }

        private static boolean isBooleanLiteral(PsiExpression expression, boolean value) {
            return expression instanceof PsiLiteralExpression literal && Boolean.valueOf(value).equals(literal.getValue());
        }

        private static String joined(PsiExpression[] args, int from) {
            StringBuilder result = new StringBuilder();
            for (int i = from; i < args.length; i++) {
                if (i > from) {
                    result.append(", ");
                }
                result.append(args[i].getText());
            }
            return result.toString();
        }

        private static String uniqueName(PsiElement scope, String base) {
            Set<String> names = new HashSet<>();
            scope.getContainingFile().accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
                    names.add(variable.getName());
                }

                @Override
                public void visitParameter(@NotNull PsiParameter parameter) {
                    names.add(parameter.getName());
                }
            });
            String candidate = base;
            int index = 1;
            while (names.contains(candidate)) {
                candidate = base + index++;
            }
            return candidate;
        }

        private static void removeJunitAssertionImports(PsiJavaFile javaFile) {
            PsiImportList importList = javaFile.getImportList();
            if (importList == null) {
                return;
            }
            for (PsiImportStatement statement : importList.getImportStatements()) {
                String fqn = statement.getQualifiedName();
                if (fqn != null && isJunitApiClass(fqn)) {
                    statement.delete();
                }
            }
            for (PsiImportStaticStatement statement : importList.getImportStaticStatements()) {
                PsiJavaCodeReferenceElement ref = statement.getImportReference();
                String text = (ref != null) ? ref.getCanonicalText() : null;
                if (text != null && (text.startsWith(JUNIT4_ASSERT) || text.startsWith(JUNIT5_ASSERTIONS)
                        || text.startsWith(JUNIT5_ASSUMPTIONS))) {
                    statement.delete();
                }
            }
        }

        private static void ensureImportTextually(Project project, PsiJavaFile javaFile, String qualifiedName) {
            PsiImportList importList = javaFile.getImportList();
            if (importList == null) {
                return;
            }
            for (PsiImportStatement statement : importList.getImportStatements()) {
                if (qualifiedName.equals(statement.getQualifiedName())) {
                    return;
                }
            }
            PsiClass assertions = JavaPsiFacade.getInstance(project)
                    .findClass(qualifiedName, GlobalSearchScope.allScope(project));
            if (assertions != null) {
                JavaCodeStyleManager.getInstance(project).addImport(javaFile, assertions);
                if (hasImport(javaFile, qualifiedName)) {
                    return;
                }
            }
            // Fallback: synthesize the import via a dummy file when the AssertJ class is not on the classpath.
            PsiJavaFile dummy = (PsiJavaFile) com.intellij.psi.PsiFileFactory.getInstance(project)
                    .createFileFromText("Dummy.java", com.intellij.lang.java.JavaLanguage.INSTANCE,
                            "import " + qualifiedName + ";");
            PsiImportList dummyList = dummy.getImportList();
            if (dummyList != null && dummyList.getImportStatements().length > 0) {
                importList.add(dummyList.getImportStatements()[0]);
            }
        }

        private static boolean hasImport(PsiJavaFile javaFile, String qualifiedName) {
            PsiImportList importList = javaFile.getImportList();
            if (importList == null) {
                return false;
            }
            for (PsiImportStatement statement : importList.getImportStatements()) {
                if (qualifiedName.equals(statement.getQualifiedName())) {
                    return true;
                }
            }
            return false;
        }

        private enum JunitKind {
            JUNIT4, JUNIT5, UNKNOWN
        }

        private record EqualityArguments(PsiExpression expected, PsiExpression actual,
                                         @Nullable PsiExpression message, @Nullable PsiExpression delta) {
        }

        private record UnaryArguments(PsiExpression actual, @Nullable PsiExpression message) {
        }
    }
}
