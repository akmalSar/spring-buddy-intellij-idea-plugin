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

import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class SpringAsyncReturnIgnoredInspectionTest extends BasePlatformTestCase {

    public void testDoesNotFlagAsyncVoid() {
        this.myFixture.enableInspections(new SpringAsyncReturnIgnoredInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Async {}
                class Sample {
                    @Async
                    void run() {}
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagNonAsyncReturningFuture() {
        this.myFixture.enableInspections(new SpringAsyncReturnIgnoredInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.concurrent.CompletableFuture;
                class Sample {
                    CompletableFuture<String> load() { return CompletableFuture.completedFuture("x"); }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFlagsDiscardedAsyncCallResult() {
        this.myFixture.enableInspections(new SpringAsyncReturnIgnoredInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.concurrent.CompletableFuture;
                @interface Async {}
                class Service {
                    @Async
                    public CompletableFuture<String> load() { return CompletableFuture.completedFuture("x"); }
                }
                class Caller {
                    void use(Service s) {
                        <warning descr="Result of @Async method is discarded; exceptions inside the async task will be swallowed">s.load()</warning>;
                    }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFixesDiscardedAsyncCallResult() {
        this.myFixture.configureByText("Sample.java", """
                import java.util.concurrent.CompletableFuture;
                @interface Async {}
                class Service {
                    @Async
                    public CompletableFuture<String> load() { return CompletableFuture.completedFuture("x"); }
                }
                class Caller {
                    void use(Service s) {
                        s.load();
                    }
                }
                """);
        this.myFixture.doHighlighting();
        com.intellij.psi.PsiClass callerClass = null;
        for (com.intellij.psi.PsiClass cls : ((com.intellij.psi.PsiJavaFile) this.myFixture.getFile()).getClasses()) {
            if ("Caller".equals(cls.getName())) {
                callerClass = cls;
                break;
            }
        }
        assertNotNull(callerClass);
        final com.intellij.psi.PsiMethodCallExpression[] targets = new com.intellij.psi.PsiMethodCallExpression[1];
        callerClass.accept(new com.intellij.psi.JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(com.intellij.psi.PsiMethodCallExpression expression) {
                super.visitMethodCallExpression(expression);
                if ("load".equals(expression.getMethodExpression().getReferenceName())) {
                    targets[0] = expression;
                }
            }
        });
        final PsiMethodCallExpression target = targets[0];
        assertNotNull(target);
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(
                getProject(),
                () -> {
                    com.intellij.codeInspection.ProblemDescriptor descriptor = com.intellij.codeInspection.InspectionManager.getInstance(getProject())
                            .createProblemDescriptor(target, "Result of @Async method is discarded", new SpringAsyncReturnIgnoredInspection.HandleDiscardedAsyncResultFix(),
                                    com.intellij.codeInspection.ProblemHighlightType.ERROR, true);
                    descriptor.getFixes()[0].applyFix(getProject(), descriptor);
                });
        String text = this.myFixture.getFile().getText();
        assertTrue(text.contains(".exceptionally((Throwable ex) -> {"));
        assertTrue(text.contains("log.error(\"An unexpected Exception occurred during Async [{}]\", ex.getMessage(), ex);"));
        assertTrue(text.contains("return null;"));
    }
}
