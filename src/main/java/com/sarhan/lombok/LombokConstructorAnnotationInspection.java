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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;

/**
 * Detects dangerous or incorrect usage of Lombok constructor annotations.
 *
 * <ul>
 *   <li><b>CONSTRUCTOR-001</b> — {@code @AllArgsConstructor} without {@code @NoArgsConstructor}
 *       on JPA / Jackson classes.</li>
 *   <li><b>CONSTRUCTOR-002</b> — {@code @RequiredArgsConstructor} with no final or {@code @NonNull} fields.</li>
 *   <li><b>BUILDER-JPA</b> — {@code @Builder} on a JPA entity without {@code @NoArgsConstructor}.</li>
 *   <li><b>CONSTRUCTOR-003</b> — {@code @AllArgsConstructor} together with {@code @RequiredArgsConstructor}
 *       (potentially conflicting constructor generation).</li>
 *   <li><b>PROJECTION-001</b> — {@code @AllArgsConstructor} on a likely JPQL projection class
 *       (fragile — field reordering silently breaks {@code @Query} expressions).</li>
 * </ul>
 *
 * @author Akmal Sarhan
 */
public class LombokConstructorAnnotationInspection extends AbstractBaseJavaLocalInspectionTool {

    /**
     * Heuristic: a class is likely a JPQL/DTO projection if it implements an
     * interface annotated with {@code @JsonDeserialize} (common in Spring Data
     * JPA projection patterns) AND the class is not a JPA entity itself.
     * {@code @AllArgsConstructor} on such a class is fragile — reordering fields
     * silently breaks {@code select new ClassName(...)} constructor expressions.
     */
    private static boolean isLikelyJpqlProjection(PsiClass aClass) {
        if (LombokAnnotationUtil.isJpaEntity(aClass)) {
            return false;
        }
        for (PsiClass iface : aClass.getInterfaces()) {
            if (LombokAnnotationUtil.hasJsonDeserialize(iface)) {
                return true;
            }
            for (PsiMethod method : iface.getAllMethods()) {
                for (PsiAnnotation ann : method.getModifierList().getAnnotations()) {
                    String fqn = ann.getQualifiedName();
                    if ("org.springframework.beans.factory.annotation.Value".equals(fqn)
                            || "Value".equals(fqn)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return MyMessageBundle.message("inspection.constructor.display.name");
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
            public void visitClass(@NotNull PsiClass aClass) {
                super.visitClass(aClass);

                // CONSTRUCTOR-001: @AllArgsConstructor without @NoArgsConstructor on JPA/Jackson
                PsiAnnotation allArgsAnn = LombokAnnotationUtil.findAnnotation(
                        aClass, "lombok.AllArgsConstructor", "AllArgsConstructor");
                if ((allArgsAnn != null)
                        && !LombokAnnotationUtil.hasNoArgsConstructor(aClass)
                        && (LombokAnnotationUtil.isJpaEntity(aClass)
                        || LombokAnnotationUtil.hasJsonDeserialize(aClass))) {
                    holder.registerProblem(
                            allArgsAnn,
                            MyMessageBundle.message("inspection.constructor.001.problem.descriptor"),
                            ProblemHighlightType.WARNING,
                            new AddNoArgsConstructorFix()
                    );
                }

                // CONSTRUCTOR-002: @RequiredArgsConstructor with no qualifying fields
                PsiAnnotation reqArgsAnn = LombokAnnotationUtil.findAnnotation(
                        aClass, "lombok.RequiredArgsConstructor", "RequiredArgsConstructor");
                if (reqArgsAnn != null) {
                    boolean hasQualifyingField = false;
                    for (PsiField field : aClass.getFields()) {
                        if (field.hasModifierProperty(PsiModifier.STATIC)) {
                            continue;
                        }
                        if (field.hasModifierProperty(PsiModifier.FINAL)
                                || LombokAnnotationUtil.hasNonNull(field)) {
                            hasQualifyingField = true;
                            break;
                        }
                    }
                    if (!hasQualifyingField) {
                        holder.registerProblem(
                                reqArgsAnn,
                                MyMessageBundle.message("inspection.constructor.002.problem.descriptor"),
                                ProblemHighlightType.WARNING,
                                new ReplaceRequiredArgsConstructorWithNoArgsFix()
                        );
                    }
                }

                // BUILDER-JPA: @Builder on JPA entity without @NoArgsConstructor
                if (LombokAnnotationUtil.isJpaEntity(aClass)
                        && !LombokAnnotationUtil.hasNoArgsConstructor(aClass)) {
                    PsiAnnotation builderAnn = LombokAnnotationUtil.findBuilderAnnotation(aClass);
                    if (builderAnn != null) {
                        holder.registerProblem(
                                builderAnn,
                                MyMessageBundle.message("inspection.builder.jpa.problem.descriptor"),
                                ProblemHighlightType.ERROR,
                                new AddNoArgsConstructorToBuilderJPAFix()
                        );
                    }
                }

                // CONSTRUCTOR-003: @AllArgsConstructor + @RequiredArgsConstructor together
                if ((allArgsAnn != null) && (reqArgsAnn != null)) {
                    holder.registerProblem(
                            reqArgsAnn,
                            MyMessageBundle.message("inspection.constructor.003.problem.descriptor"),
                            ProblemHighlightType.WEAK_WARNING,
                            new RemoveConflictingConstructorAnnotationFix()
                    );
                }

                // PROJECTION-001: @AllArgsConstructor on likely JPQL projection class
                if ((allArgsAnn != null) && isLikelyJpqlProjection(aClass)) {
                    holder.registerProblem(
                            allArgsAnn,
                            MyMessageBundle.message("inspection.projection.001.problem.descriptor"),
                            ProblemHighlightType.WARNING
                    );
                }
            }
        };
    }
}
