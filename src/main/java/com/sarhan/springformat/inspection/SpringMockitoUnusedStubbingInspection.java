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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * SpringMockitoUnusedStubbing: a {@code when(mock.method(...))} stubbing inside a {@code @Test}
 * method whose stubbed {@code method} is never invoked elsewhere in the same test method
 * (under strict-stubbing this would already fail, but is often missed in lenient setups).
 *
 * @author Akmal Sarhan
 */
public class SpringMockitoUnusedStubbingInspection extends AbstractBaseJavaLocalInspectionTool {

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
                if (body == null) {
                    return;
                }
                analyse(body, holder);
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
                    || fqn.equals("Test")
                    || fqn.equals("org.junit.jupiter.params.ParameterizedTest")
                    || fqn.equals("ParameterizedTest")) {
                return true;
            }
        }
        return false;
    }

    private static void analyse(PsiCodeBlock body, ProblemsHolder holder) {
        // Locate `when(mock.method(...))` and check whether `method` is referenced anywhere else
        // in the test body. The stubbed call itself counts as 1 occurrence; we expect >= 2 if the
        // stubbing is actually exercised.
        body.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiMethodCallExpression call) {
                    String outerName = call.getMethodExpression().getReferenceName();
                    if (("when".equals(outerName) || "given".equals(outerName))
                            && call.getArgumentList().getExpressions().length == 1
                            && call.getArgumentList().getExpressions()[0]
                            instanceof PsiMethodCallExpression stubbedCall) {
                        String stubbedName = stubbedCall.getMethodExpression().getReferenceName();
                        if (stubbedName != null && !isLenientStubbing(call) && !isFieldBasedStub(stubbedCall)
                                && countOccurrences(body, stubbedName) <= 1) {
                            LocalQuickFix[] fixes = "toString".equals(stubbedName)
                                    ? new LocalQuickFix[] {new RemoveUnusedStubbingFix(), new MakeStubbingLenientFix()}
                                    : new LocalQuickFix[] {new AddVerifyForStubbingFix(),
                                            new RemoveUnusedStubbingFix(), new MakeStubbingLenientFix()};
                            holder.registerProblem(stubbedCall,
                                    "Stubbed method '" + stubbedName + "' is never invoked in this test",
                                    ProblemHighlightType.WARNING,
                                    fixes);
                        }
                    }
                }
                super.visitElement(element);
            }
        });
    }

    private static boolean isStubbingFraming(String name) {
        return name.equals("when") || name.equals("given") || name.equals("thenReturn")
                || name.equals("thenThrow") || name.equals("thenAnswer") || name.equals("thenCallRealMethod")
                || name.equals("willReturn") || name.equals("willThrow") || name.equals("willAnswer");
    }

    private static int countOccurrences(PsiCodeBlock body, String methodName) {
        int[] count = {0};
        body.accept(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                if (element instanceof PsiMethodCallExpression call
                        && methodName.equals(call.getMethodExpression().getReferenceName())) {
                    count[0]++;
                }
                super.visitElement(element);
            }
        });
        return count[0];
    }

    private static boolean isFieldBasedStub(PsiMethodCallExpression stubbedCall) {
        PsiExpression qualifier = stubbedCall.getMethodExpression().getQualifierExpression();
        if (qualifier instanceof PsiReferenceExpression reference) {
            return reference.resolve() instanceof PsiField;
        }
        return qualifier instanceof PsiThisExpression thisExpression
                && thisExpression.getQualifier() instanceof PsiReferenceExpression reference
                && reference.resolve() instanceof PsiField;
    }

    private static boolean isLenientStubbing(PsiMethodCallExpression stubbingCall) {
        PsiExpression qualifier = stubbingCall.getMethodExpression().getQualifierExpression();
        return qualifier instanceof PsiMethodCallExpression qualifierCall
                && "lenient".equals(qualifierCall.getMethodExpression().getReferenceName());
    }

    private static boolean isStubbingRoot(PsiMethodCallExpression call) {
        String name = call.getMethodExpression().getReferenceName();
        return "when".equals(name) || "given".equals(name);
    }

    private static final class RemoveUnusedStubbingFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Remove unused Mockito stubbing";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiExpressionStatement statement = PsiTreeUtil.getParentOfType(
                    descriptor.getPsiElement(), PsiExpressionStatement.class, false);
            if (statement != null) {
                statement.delete();
            }
        }
    }

    private static final class MakeStubbingLenientFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Mark stubbing as lenient";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiExpressionStatement statement = PsiTreeUtil.getParentOfType(
                    descriptor.getPsiElement(), PsiExpressionStatement.class, false);
            if (statement == null) {
                return;
            }
            String text = statement.getText();
            int index = text.indexOf("when(");
            if (index < 0 || text.contains("lenient().when(")) {
                return;
            }
            Document document = PsiDocumentManager.getInstance(project).getDocument(statement.getContainingFile());
            if (document == null) {
                return;
            }
            document.insertString(statement.getTextRange().getStartOffset() + index, "lenient().");
            PsiDocumentManager.getInstance(project).commitDocument(document);
        }
    }

    private static final class AddVerifyForStubbingFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Verify stubbed method is invoked";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PsiMethodCallExpression stubbedCall)) {
                return;
            }
            PsiCodeBlock body = PsiTreeUtil.getParentOfType(stubbedCall, PsiCodeBlock.class);
            if (body == null) {
                return;
            }
            PsiExpression qualifier = stubbedCall.getMethodExpression().getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            String methodName = stubbedCall.getMethodExpression().getReferenceName();
            if (methodName == null) {
                return;
            }
            PsiExpressionList argumentList = stubbedCall.getArgumentList();
            String verifyStatement = "Mockito.verify(" + qualifier.getText() + ")." + methodName
                    + argumentList.getText() + ";";
            body.addBefore(JavaElementFactoryHolder.statementFromText(project, verifyStatement, body), body.getRBrace());
        }
    }

    private static final class JavaElementFactoryHolder {

        private JavaElementFactoryHolder() {
        }

        private static PsiElement statementFromText(Project project, String text, PsiElement context) {
            return com.intellij.psi.JavaPsiFacade.getElementFactory(project).createStatementFromText(text, context);
        }
    }
}
