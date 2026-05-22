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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

/**
 * Detects usage of {@code jakarta.transaction.Transactional} (or {@code javax.transaction.Transactional})
 * and recommends migrating to {@code org.springframework.transaction.annotation.Transactional}.
 * <p>
 * Spring's transaction management (e.g. {@code @EnableTransactionManagement}) only recognises its own
 * {@code @Transactional} annotation. The Jakarta variant is silently ignored at runtime, so no proxy is
 * created and the method executes without a transaction boundary.
 * <p>
 * The quick fix also renames Jakarta-specific attributes:
 * <ul>
 *   <li>{@code rollbackOn}  → {@code rollbackFor}</li>
 *   <li>{@code dontRollbackOn} → {@code noRollbackFor}</li>
 * </ul>
 *
 * @author Akmal Sarhan
 */
public class SpringJakartaTransactionalMigrationInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final String JAKARTA_FQN = "jakarta.transaction.Transactional";
    private static final String JAVAX_FQN = "javax.transaction.Transactional";
    private static final String SPRING_FQN = "org.springframework.transaction.annotation.Transactional";

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
                PsiModifierList modifiers = method.getModifierList();
                if (modifiers == null) {
                    return;
                }
                PsiAnnotation annotation = findJakartaTransactional(modifiers);
                if (annotation != null) {
                    registerProblem(annotation, holder);
                }
            }

            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                super.visitClass(aClass);
                PsiModifierList modifiers = aClass.getModifierList();
                if (modifiers == null) {
                    return;
                }
                PsiAnnotation annotation = findJakartaTransactional(modifiers);
                if (annotation != null) {
                    registerProblem(annotation, holder);
                }
            }
        };
    }

    private static void registerProblem(PsiAnnotation annotation, ProblemsHolder holder) {
        PsiElement target = annotation.getNameReferenceElement();
        if (target == null) {
            target = annotation;
        }
        holder.registerProblem(target,
                "Jakarta @Transactional is ignored by Spring — use org.springframework.transaction.annotation.Transactional",
                ProblemHighlightType.ERROR,
                new MigrateToSpringTransactionalFix());
    }

    private static PsiAnnotation findJakartaTransactional(PsiModifierList modifiers) {
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String qName = annotation.getQualifiedName();
            if (JAKARTA_FQN.equals(qName) || JAVAX_FQN.equals(qName)) {
                return annotation;
            }
        }
        return null;
    }

    private static final class MigrateToSpringTransactionalFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return "Migrate to Spring @Transactional";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiAnnotation oldAnnotation = descriptor.getPsiElement() instanceof PsiAnnotation a
                    ? a
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiAnnotation.class);
            if (oldAnnotation == null) {
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("@org.springframework.transaction.annotation.Transactional");

            PsiAnnotationParameterList paramList = oldAnnotation.getParameterList();
            PsiNameValuePair[] attributes = paramList.getAttributes();
            if (attributes.length > 0) {
                sb.append("(");
                boolean first = true;
                for (PsiNameValuePair pair : attributes) {
                    String migrated = migratePair(pair);
                    if (migrated == null) {
                        continue; // skip default values that need no explicit attribute
                    }
                    if (!first) {
                        sb.append(", ");
                    }
                    first = false;
                    sb.append(migrated);
                }
                sb.append(")");
            }

            PsiAnnotation newAnnotation = JavaPsiFacade.getElementFactory(project)
                    .createAnnotationFromText(sb.toString(), oldAnnotation.getParent());

            PsiElement replaced = oldAnnotation.replace(newAnnotation);

            // Ensure import is present and shorten references
            PsiJavaFile javaFile = com.intellij.psi.util.PsiTreeUtil.getParentOfType(replaced, PsiJavaFile.class);
            if (javaFile != null) {
                SpringImportUtil.ensureImport(project, javaFile, SPRING_FQN);
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(javaFile);
            }
        }

        /**
         * Migrate a single name-value pair from Jakarta @Transactional to Spring.
         * Returns null when the pair represents a default that can be omitted.
         */
        private static String migratePair(PsiNameValuePair pair) {
            String name = pair.getAttributeName();
            PsiAnnotationMemberValue value = pair.getValue();
            String valueText = (value != null) ? value.getText() : "";

            // Jakarta's default unnamed attribute is TxType (value = TxType.REQUIRED, etc.)
            // Spring's value is a String qualifier, so TxType must become propagation.
            if (name == null) {
                if (valueText.contains("TxType.")) {
                    String propagation = mapTxTypeToPropagation(valueText);
                    if (propagation == null) {
                        return null; // REQUIRED is default, omit it
                    }
                    return "propagation = " + propagation;
                }
                return valueText;
            }

            return switch (name) {
                case "rollbackOn" -> "rollbackFor = " + valueText;
                case "dontRollbackOn" -> "noRollbackFor = " + valueText;
                case "value" -> {
                    if (valueText.contains("TxType.")) {
                        String propagation = mapTxTypeToPropagation(valueText);
                        yield (propagation != null) ? "propagation = " + propagation : null;
                    }
                    yield "value = " + valueText;
                }
                default -> name + " = " + valueText;
            };
        }

        private static String mapTxTypeToPropagation(String txTypeExpr) {
            String type = txTypeExpr.trim();
            return switch (type) {
                case "TxType.REQUIRED" -> null; // default in Spring, omit
                case "TxType.REQUIRES_NEW" -> "Propagation.REQUIRES_NEW";
                case "TxType.SUPPORTS" -> "Propagation.SUPPORTS";
                case "TxType.MANDATORY" -> "Propagation.MANDATORY";
                case "TxType.NOT_SUPPORTED" -> "Propagation.NOT_SUPPORTED";
                case "TxType.NEVER" -> "Propagation.NEVER";
                default -> type.replace("TxType.", "Propagation.");
            };
        }
    }
}
