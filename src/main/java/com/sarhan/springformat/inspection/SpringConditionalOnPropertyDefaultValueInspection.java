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
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects conflicts between {@code @ConditionalOnProperty(matchIfMissing = true)}
 * and {@code @Value} defaults that resolve to different boolean states.
 * The bean may be active while the injected value reflects a disabled state,
 * causing inconsistent runtime behavior.
 *
 * @author Akmal Sarhan
 */
public class SpringConditionalOnPropertyDefaultValueInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                super.visitClass(aClass);
                List<ConditionalOnPropertyInfo> conditionalProps = new ArrayList<>();
                for (PsiMethod method : aClass.getMethods()) {
                    PsiAnnotation annotation = findAnnotation(method.getModifierList(), "ConditionalOnProperty");
                    if (annotation == null) {
                        continue;
                    }
                    String matchIfMissing = extractAttributeValue(annotation, "matchIfMissing");
                    if (!"true".equals(matchIfMissing)) {
                        continue;
                    }
                    String name = extractAttributeValue(annotation, "name");
                    if (name == null) {
                        name = extractAttributeValue(annotation, "value");
                    }
                    if (name != null) {
                        conditionalProps.add(new ConditionalOnPropertyInfo(name, method));
                    }
                }
                if (conditionalProps.isEmpty()) {
                    return;
                }
                for (PsiField field : aClass.getFields()) {
                    PsiAnnotation valueAnnotation = findAnnotation(field.getModifierList(), "Value");
                    if (valueAnnotation == null) {
                        continue;
                    }
                    String valueExpr = extractAttributeValue(valueAnnotation, "value");
                    if (valueExpr == null) {
                        continue;
                    }
                    String propName = extractPropertyName(valueExpr);
                    String defaultValue = extractDefaultValue(valueExpr);
                    if (propName == null) {
                        continue;
                    }
                    for (ConditionalOnPropertyInfo info : conditionalProps) {
                        if (propName.equals(info.propertyName)) {
                            boolean defaultIsFalse = "false".equals(defaultValue);
                            if (defaultIsFalse || (defaultValue != null && !"true".equals(defaultValue))) {
                                PsiElement target = valueAnnotation;
                                holder.registerProblem(target,
                                        "@ConditionalOnProperty(matchIfMissing=true) conflicts with @Value default; the bean may be active while the injected value reflects a disabled state",
                                        ProblemHighlightType.WARNING);
                                break;
                            }
                        }
                    }
                }
            }
        };
    }

    @Nullable
    private static PsiAnnotation findAnnotation(PsiModifierList modifiers, String shortName) {
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String qName = annotation.getQualifiedName();
            if (qName == null && annotation.getNameReferenceElement() != null) {
                qName = annotation.getNameReferenceElement().getText();
            }
            if (shortName.equals(qName) || (qName != null && qName.endsWith("." + shortName))) {
                return annotation;
            }
        }
        return null;
    }

    @Nullable
    private static String extractAttributeValue(PsiAnnotation annotation, String attributeName) {
        PsiAnnotationMemberValue value = annotation.findAttributeValue(attributeName);
        if (value instanceof PsiLiteralExpression literal) {
            Object raw = literal.getValue();
            return (raw != null) ? raw.toString() : null;
        }
        if (value != null) {
            return value.getText();
        }
        return null;
    }

    @Nullable
    private static String extractPropertyName(String valueExpression) {
        if (valueExpression.startsWith("${")) {
            int colon = valueExpression.indexOf(':');
            int end = valueExpression.indexOf('}');
            if (end > 0) {
                int nameEnd = (colon > 0 && colon < end) ? colon : end;
                return valueExpression.substring(2, nameEnd);
            }
        }
        return null;
    }

    @Nullable
    private static String extractDefaultValue(String valueExpression) {
        if (valueExpression.startsWith("${")) {
            int colon = valueExpression.indexOf(':');
            int end = valueExpression.indexOf('}');
            if (colon > 0 && end > colon) {
                return valueExpression.substring(colon + 1, end);
            }
        }
        return null;
    }

    private static final class ConditionalOnPropertyInfo {
        final String propertyName;
        final PsiMethod method;

        ConditionalOnPropertyInfo(String propertyName, PsiMethod method) {
            this.propertyName = propertyName;
            this.method = method;
        }
    }
}
