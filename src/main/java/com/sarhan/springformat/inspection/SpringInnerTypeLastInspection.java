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

import java.util.ArrayList;
import java.util.List;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects class members (fields, methods, initializers) declared after inner types,
 * violating the convention that inner types should be placed last in a class body.
 *
 * @author Akmal Sarhan
 */
public class SpringInnerTypeLastInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                if (psiClass instanceof PsiEnumConstantInitializer || !isTopLevel(psiClass)) {
                    return;
                }
                PsiClass firstInnerType = null;
                for (PsiMember member : directMembers(psiClass)) {
                    if (member instanceof PsiClass innerType) {
                        if (firstInnerType == null) {
                            firstInnerType = innerType;
                        }
                    }
                    else if (firstInnerType != null && isTypeMember(member)) {
                        holder.registerProblem(member,
                                "Class members should be declared before inner types",
                                ProblemHighlightType.WARNING,
                                new MoveMembersBeforeInnerTypesFix());
                        return;
                    }
                }
            }
        };
    }

    private static boolean isTopLevel(PsiClass psiClass) {
        return !(psiClass.getParent() instanceof PsiClass);
    }

    private static boolean isTypeMember(PsiMember member) {
        return member instanceof PsiField || member instanceof PsiMethod || member instanceof PsiClassInitializer;
    }

    private static List<PsiMember> directMembers(PsiClass psiClass) {
        List<PsiMember> result = new ArrayList<>();
        for (PsiElement child : psiClass.getChildren()) {
            if (child instanceof PsiMember member) {
                result.add(member);
            }
        }
        return result;
    }

    private static final class MoveMembersBeforeInnerTypesFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Move members before inner types";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiClass containingClass = descriptor.getPsiElement() instanceof PsiMember member
                    ? member.getContainingClass() : null;
            PsiClass firstInnerType = firstInnerType(containingClass);
            if (firstInnerType == null) {
                return;
            }
            List<PsiMember> membersToMove = new ArrayList<>();
            boolean seenInnerType = false;
            for (PsiMember candidate : directMembers(containingClass)) {
                if (candidate == firstInnerType) {
                    seenInnerType = true;
                }
                else if (seenInnerType && isTypeMember(candidate)) {
                    membersToMove.add(candidate);
                }
            }
            PsiElement anchor = firstInnerType;
            for (int i = membersToMove.size() - 1; i >= 0; i--) {
                PsiMember member = membersToMove.get(i);
                PsiElement added = containingClass.addBefore(member.copy(), anchor);
                anchor = added;
            }
            for (PsiMember member : membersToMove) {
                deleteAdjacentWhiteSpace(member);
                member.delete();
            }
        }

        @Nullable
        private static PsiClass firstInnerType(@Nullable PsiClass psiClass) {
            if (psiClass == null) {
                return null;
            }
            for (PsiMember member : directMembers(psiClass)) {
                if (member instanceof PsiClass innerType) {
                    return innerType;
                }
            }
            return null;
        }

        private static void deleteAdjacentWhiteSpace(PsiElement element) {
            PsiElement next = element.getNextSibling();
            if (next instanceof PsiWhiteSpace) {
                next.delete();
            }
            PsiElement previous = element.getPrevSibling();
            if (previous instanceof PsiWhiteSpace) {
                previous.delete();
            }
        }
    }
}
