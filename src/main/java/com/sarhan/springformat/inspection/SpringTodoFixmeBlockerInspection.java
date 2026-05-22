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

import java.util.regex.Pattern;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * SpringTodoFixmeBlocker: flags {@code TODO} / {@code FIXME} comments inside Spring components
 * ({@code @Service}, {@code @Component}, {@code @Repository}, {@code @Controller},
 * {@code @RestController}, {@code @Configuration}) that don't reference a ticket id
 * (e.g. {@code ABC-123} or {@code #123}).
 *
 * @author Akmal Sarhan
 */
public class SpringTodoFixmeBlockerInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final Pattern TODO_PATTERN = Pattern.compile("(?i)\\b(TODO|FIXME)\\b");
    private static final Pattern TICKET_PATTERN = Pattern.compile("([A-Z][A-Z0-9_]+-\\d+|#\\d+)");

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitComment(@NotNull PsiComment comment) {
                super.visitComment(comment);
                String text = comment.getText();
                if (text == null) {
                    return;
                }
                if (!TODO_PATTERN.matcher(text).find()) {
                    return;
                }
                if (TICKET_PATTERN.matcher(text).find()) {
                    return;
                }
                PsiClass enclosing = PsiTreeUtil.getParentOfType(comment, PsiClass.class);
                if (enclosing == null || !isSpringComponent(enclosing)) {
                    return;
                }
                holder.registerProblem((PsiElement) comment,
                        "TODO/FIXME in Spring component without a ticket reference (e.g. ABC-123 or #123)",
                        ProblemHighlightType.WARNING);
            }
        };
    }

    private static boolean isSpringComponent(PsiClass cls) {
        PsiModifierList ml = cls.getModifierList();
        if (ml == null) {
            return false;
        }
        for (PsiAnnotation annotation : ml.getAnnotations()) {
            String fqn = annotation.getQualifiedName();
            if (fqn == null) {
                continue;
            }
            if (fqn.startsWith("org.springframework.stereotype.")
                    || "org.springframework.web.bind.annotation.RestController".equals(fqn)
                    || "org.springframework.context.annotation.Configuration".equals(fqn)
                    || "Service".equals(fqn) || "Component".equals(fqn) || "Repository".equals(fqn)
                    || "Controller".equals(fqn) || "RestController".equals(fqn)
                    || "Configuration".equals(fqn)) {
                return true;
            }
        }
        return false;
    }
}
