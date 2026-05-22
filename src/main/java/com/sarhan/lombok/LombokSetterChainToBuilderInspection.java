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

package com.sarhan.lombok;

import java.util.ArrayList;
import java.util.List;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */
public class LombokSetterChainToBuilderInspection extends AbstractBaseJavaLocalInspectionTool {

    private static boolean isSetterOnVariable(PsiMethodCallExpression call,
                                              PsiLocalVariable variable) {
        PsiReferenceExpression methodRef = call.getMethodExpression();
        PsiExpression qualifier = methodRef.getQualifierExpression();
        String methodName = methodRef.getReferenceName();

        return (qualifier instanceof PsiReferenceExpression ref)
                && variable.getName().equals(ref.getReferenceName())
                && (methodName != null)
                && methodName.startsWith("set")
                && (methodName.length() > 3)
                && (call.getArgumentList().getExpressions().length == 1);
    }

    private static boolean isVariableReassignedOrReadMeaningfully(PsiStatement stmt,
                                                                  PsiLocalVariable variable) {
        // Allow setter calls on this variable — these are expected
        if (stmt instanceof PsiExpressionStatement exprStmt) {
            PsiExpression expr = exprStmt.getExpression();
            if ((expr instanceof PsiMethodCallExpression call)
                    && isSetterOnVariable(call, variable)) {
                return false;
            }
        }

        // Stop if the statement contains ANY reference to the variable.
        // This covers: reassignment, method arguments, return statements,
        // declarations whose initializer uses the variable, etc.
        for (PsiReferenceExpression ref : PsiTreeUtil.findChildrenOfType(stmt, PsiReferenceExpression.class)) {
            if (variable.getName().equals(ref.getReferenceName())) {
                PsiElement resolved = ref.resolve();
                if (resolved == variable) {
                    return true;
                }
            }
        }

        return false;
    }

    // Shared utility — also used by the fix
    static List<PsiExpressionStatement> collectSetterStatements(PsiLocalVariable variable) {
        List<PsiExpressionStatement> result = new ArrayList<>();

        PsiElement declStatement = variable.getParent(); // DeclarationStatement
        PsiElement block = declStatement.getParent();
        if (!(block instanceof PsiCodeBlock codeBlock)) {
            return result;
        }

        boolean foundDeclaration = false;
        boolean foundFirstSetter = false;
        boolean stoppedCollecting = false;

        for (PsiStatement stmt : codeBlock.getStatements()) {

            if (!foundDeclaration) {
                if (stmt.equals(declStatement)) {
                    foundDeclaration = true;
                }
                continue;
            }

            // Hard stop: variable is reassigned or used in a non-setter way
            if (isVariableReassignedOrReadMeaningfully(stmt, variable)) {
                break;
            }

            // After declaration: collect setter calls on this variable.
            // If we already found the first setter and then hit a non-setter,
            // mark that we stopped.  If a later setter appears after that gap,
            // the whole chain is discontinuous — return empty so that the
            // inspection does not trigger at all.
            if (stmt instanceof PsiExpressionStatement exprStmt) {
                PsiExpression expr = exprStmt.getExpression();
                if ((expr instanceof PsiMethodCallExpression call)
                        && isSetterOnVariable(call, variable)) {
                    if (stoppedCollecting) {
                        // setter chain is broken by an interleaved statement
                        return new ArrayList<>();
                    }
                    result.add(exprStmt);
                    foundFirstSetter = true;
                    continue;
                }
            }

            // Soft stop: non-setter statement found after the first setter
            if (foundFirstSetter) {
                stoppedCollecting = true;
            }
        }

        return result;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return MyMessageBundle.message("inspection.setter.chain.to.builder.display.name");
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
        return "Lombok builder";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(
            @NotNull ProblemsHolder holder,
            boolean isOnTheFly) {

                if (!LombokInspectionUtil.isInJavaFile(holder.getFile())) {
                    return PsiElementVisitor.EMPTY_VISITOR;
                }

        return new JavaElementVisitor() {

            @Override
            public void visitLocalVariable(@NotNull PsiLocalVariable variable) {
                super.visitLocalVariable(variable);

                // Must be initialized with a no-arg constructor
                PsiExpression initializer = variable.getInitializer();
                if (!(initializer instanceof PsiNewExpression newExpr)) {
                    return;
                }

                PsiExpressionList args = newExpr.getArgumentList();
                if ((args == null) || (args.getExpressions().length != 0)) {
                    return;
                }

                // Resolve the class and check for @Builder
                PsiJavaCodeReferenceElement classRef = newExpr.getClassReference();
                if (classRef == null) {
                    return;
                }
                PsiElement resolved = classRef.resolve();
                if (!(resolved instanceof PsiClass psiClass)) {
                    return;
                }
                if (!hasBuilderAnnotation(psiClass)) {
                    return;
                }

                // Find at least one setter call on this variable after declaration
                List<PsiExpressionStatement> setters = collectSetterStatements(variable);
                if (setters.isEmpty()) {
                    return;
                }

                // Safety: @Builder (not @SuperBuilder) does not generate builder methods
                // for inherited fields. Converting setters for inherited fields would
                // produce non-compiling code like .createdAt(...) when the field is
                // declared in a superclass.
                if (!hasSuperBuilderAnnotation(psiClass)
                        && hasSetterForInheritedField(psiClass, setters)) {
                    return;
                }

                // Safety: if the block between the declaration and the last setter
                // contains a variable declaration referenced by the setter
                // arguments, conversion would reorder code and break the
                // forward reference (variable used before definition).
                if (hasForwardVariableReference(variable, setters)) {
                    return;
                }

                holder.registerProblem(
                        variable,
                        MyMessageBundle.message("inspection.setter.chain.to.builder.problem.descriptor"),
                        ProblemHighlightType.WEAK_WARNING,
                        new SetterChainToBuilderFix()
                );
            }

            private boolean hasBuilderAnnotation(PsiClass psiClass) {
                PsiModifierList modifiers = psiClass.getModifierList();
                if (modifiers == null) {
                    return false;
                }
                for (PsiAnnotation a : modifiers.getAnnotations()) {
                    String fqn = a.getQualifiedName();
                    if (isBuilderAnnotation(fqn)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean isBuilderAnnotation(String fqn) {
                return "lombok.Builder".equals(fqn)
                        || "Builder".equals(fqn)
                        || "lombok.SuperBuilder".equals(fqn)
                        || "lombok.experimental.SuperBuilder".equals(fqn)
                        || "SuperBuilder".equals(fqn);
            }

            private boolean hasSuperBuilderAnnotation(PsiClass psiClass) {
                PsiModifierList modifiers = psiClass.getModifierList();
                if (modifiers == null) {
                    return false;
                }
                for (PsiAnnotation a : modifiers.getAnnotations()) {
                    String fqn = a.getQualifiedName();
                    if ("lombok.SuperBuilder".equals(fqn)
                            || "lombok.experimental.SuperBuilder".equals(fqn)
                            || "SuperBuilder".equals(fqn)) {
                        return true;
                    }
                }
                return false;
            }

            private boolean hasSetterForInheritedField(PsiClass psiClass,
                                                       List<PsiExpressionStatement> setters) {
                for (PsiExpressionStatement stmt : setters) {
                    PsiMethodCallExpression call =
                            (PsiMethodCallExpression) stmt.getExpression();
                    String methodName = call.getMethodExpression().getReferenceName();
                    if ((methodName == null) || (methodName.length() <= 3)
                            || !methodName.startsWith("set")) {
                        continue;
                    }
                    // setCreatedAt -> createdAt
                    String fieldName = Character.toLowerCase(methodName.charAt(3))
                            + methodName.substring(4);
                    PsiField field = psiClass.findFieldByName(fieldName, true);
                    if ((field == null) || (field.getContainingClass() != psiClass)) {
                        // Field is inherited or does not exist in the class hierarchy.
                        // Either way a plain @Builder will not expose it.
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private boolean hasForwardVariableReference(PsiLocalVariable variable,
                                                List<PsiExpressionStatement> setters) {
        PsiElement declStmt = variable.getParent();
        PsiElement lastSetter = setters.isEmpty() ? null : setters.getLast().getParent();
        if (lastSetter == null) {
            return true;
        }
        PsiCodeBlock block = (PsiCodeBlock) declStmt.getParent();
        if (block == null) {
            return true;
        }

        java.util.Set<String> declaredInRange = new java.util.HashSet<>();
        boolean inRange = false;
        for (PsiStatement stmt : block.getStatements()) {
            if (stmt == declStmt) {
                inRange = true;
                continue;
            }
            if (!inRange) {
                continue;
            }
            if (stmt instanceof com.intellij.psi.PsiDeclarationStatement decl) {
                for (PsiElement el : decl.getDeclaredElements()) {
                    if (el instanceof PsiLocalVariable local) {
                        declaredInRange.add(local.getName());
                    }
                }
            }
            if (stmt == lastSetter) {
                break;
            }
        }
        if (declaredInRange.isEmpty()) {
            return false;
        }

        for (PsiExpressionStatement setter : setters) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) setter.getExpression();
            PsiExpression[] args = call.getArgumentList().getExpressions();
            if (args.length > 0) {
                String argText = args[0].getText();
                for (String name : declaredInRange) {
                    if (java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(name) + "\\b").matcher(argText).find()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
