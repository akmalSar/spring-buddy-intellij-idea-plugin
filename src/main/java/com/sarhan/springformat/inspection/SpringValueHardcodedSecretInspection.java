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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Detects @Value annotations on fields named like secrets/passwords/tokens/apiKeys
 * that contain a hardcoded string literal instead of a property placeholder.
 * Hardcoded credentials in source code are a security vulnerability.
 *
 * @author Akmal Sarhan
 */
public class SpringValueHardcodedSecretInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitField(@NotNull PsiField field) {
                PsiAnnotation valueAnnotation = findValueAnnotation(field);
                if (valueAnnotation == null) {
                    return;
                }
                if (!isSensitiveFieldName(field.getName())) {
                    return;
                }
                PsiAnnotationMemberValue valueAttr = valueAnnotation.findAttributeValue("value");
                if (valueAttr == null) {
                    valueAttr = valueAnnotation.findAttributeValue(null);
                }
                if (!(valueAttr instanceof PsiLiteralExpression literal)) {
                    return;
                }
                Object value = literal.getValue();
                if (!(value instanceof String str)) {
                    return;
                }
                // Allow property placeholders like ${db.password} or #{expression}
                if (str.startsWith("${") || str.startsWith("#{")) {
                    return;
                }
                // Flag anything else as a hardcoded secret
                holder.registerProblem(
                        literal,
                        "Hardcoded secret in @Value field — use a property placeholder (${...}) or externalized config instead",
                        ProblemHighlightType.ERROR
                );
            }
        };
    }

    private static PsiAnnotation findValueAnnotation(PsiField field) {
        for (PsiAnnotation a : field.getAnnotations()) {
            String name = a.getQualifiedName();
            if (name == null && a.getNameReferenceElement() != null) {
                name = a.getNameReferenceElement().getText();
            }
            if ("Value".equals(name) || (name != null && name.endsWith(".Value"))) {
                return a;
            }
        }
        return null;
    }

    private static boolean isSensitiveFieldName(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        return lower.contains("password") || lower.contains("secret")
                || lower.contains("token") || lower.contains("apikey")
                || lower.contains("credential")
                || lower.equals("auth") || lower.contains("authkey") || lower.contains("authtoken")
                || lower.contains("privatekey") || lower.contains("accesskey")
                || lower.contains("refreshToken");
    }
}
