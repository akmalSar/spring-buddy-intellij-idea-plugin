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

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */

public abstract class LombokEqualsAndHashCodeInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public String getDisplayName() {
        return MyMessageBundle.message("inspection.eqh.display.name");
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
        return "Lombok builder";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!LombokInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                super.visitClass(aClass);
                inspect(aClass, holder);
            }
        };
    }

    protected abstract void inspect(PsiClass aClass, ProblemsHolder holder);

    protected static boolean isSubclass(@NotNull PsiClass aClass) {
        PsiClass superClass = aClass.getSuperClass();
        return superClass != null && !"java.lang.Object".equals(superClass.getQualifiedName());
    }

    protected static boolean hasCallSuperTrue(@NotNull PsiAnnotation annotation) {
        PsiAnnotationParameterList paramList = annotation.getParameterList();
        for (PsiNameValuePair pair : paramList.getAttributes()) {
            if ("callSuper".equals(pair.getAttributeName()) && "true".equals(pair.getLiteralValue())) {
                return true;
            }
        }
        return false;
    }

    public static class EqualsAndHashCodeWithoutCallSuper extends LombokEqualsAndHashCodeInspection {
        @Override
        protected void inspect(PsiClass aClass, ProblemsHolder holder) {
            PsiAnnotation eqhAnn = LombokAnnotationUtil.findAnnotation(aClass, "lombok.EqualsAndHashCode", "EqualsAndHashCode");
            if (eqhAnn != null && isSubclass(aClass) && !hasCallSuperTrue(eqhAnn)) {
                holder.registerProblem(eqhAnn,
                        MyMessageBundle.message("inspection.eqh.001.problem.descriptor"),
                        ProblemHighlightType.WARNING,
                        new AddCallSuperToEqualsAndHashCodeFix());
            }
        }
    }

    public static class DataOnSubclassWithoutCallSuper extends LombokEqualsAndHashCodeInspection {
        @Override
        protected void inspect(PsiClass aClass, ProblemsHolder holder) {
            PsiAnnotation dataAnn = LombokAnnotationUtil.findAnnotation(aClass, "lombok.Data", "Data");
            if (dataAnn != null && isSubclass(aClass) && !hasExplicitCallSuperAnnotations(aClass)) {
                holder.registerProblem(dataAnn,
                        MyMessageBundle.message("inspection.eqh.002.problem.descriptor"),
                        ProblemHighlightType.WARNING,
                        new AddDataCallSuperAnnotationsFix());
            }
        }

        private static boolean hasExplicitCallSuperAnnotations(PsiClass aClass) {
            PsiAnnotation eqhAnn = LombokAnnotationUtil.findAnnotation(aClass, "lombok.EqualsAndHashCode", "EqualsAndHashCode");
            PsiAnnotation toStringAnn = LombokAnnotationUtil.findAnnotation(aClass, "lombok.ToString", "ToString");
            return eqhAnn != null && toStringAnn != null && hasCallSuperTrue(eqhAnn) && hasCallSuperTrue(toStringAnn);
        }
    }

    public static class EqualsAndHashCodeOnJpaEntity extends LombokEqualsAndHashCodeInspection {
        @Override
        protected void inspect(PsiClass aClass, ProblemsHolder holder) {
            PsiAnnotation eqhAnn = LombokAnnotationUtil.findAnnotation(aClass, "lombok.EqualsAndHashCode", "EqualsAndHashCode");
            if (eqhAnn != null && LombokAnnotationUtil.isJpaEntity(aClass)) {
                holder.registerProblem(eqhAnn,
                        MyMessageBundle.message("inspection.eqh.003.eqh.problem.descriptor"),
                        ProblemHighlightType.WARNING);
            }
        }
    }
}
