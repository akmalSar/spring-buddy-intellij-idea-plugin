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

package com.sarhan.render;

import java.awt.*;
import java.awt.event.MouseEvent;

import javax.swing.*;

import com.intellij.codeInsight.hints.*;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.sarhan.springformat.inspection.SpringInspectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Akmal Sarhan
 */

public final class TransactionInlayHintsProvider
        implements InlayHintsProvider<TransactionInlayHintsProvider.Settings> {

    // -----------------------------------------------------------------------
    // InlayHintsProvider identity
    // -----------------------------------------------------------------------

    private static final SettingsKey<Settings> KEY =
            new SettingsKey<>("sarhan.transactionDiagram");

    @NotNull
    @Override
    public SettingsKey<Settings> getKey() {
        return KEY;
    }

    @NotNull
    @Override
    public String getName() {
        return "Transaction Flow Diagram";
    }

    @Nullable
    @Override
    public String getPreviewText() {
        return """
                @Transactional
                public void saveOrder(Order o) {
                    orderRepository.save(o);
                }
                """;
    }

    // -----------------------------------------------------------------------
    // Settings
    // -----------------------------------------------------------------------

    @NotNull
    @Override
    public Settings createSettings() {
        return new Settings();
    }

    @NotNull
    @Override
    public ImmediateConfigurable createConfigurable(@NotNull Settings settings) {
        return (ChangeListener changeListener) -> new JPanel(); // no UI in v1
    }

    // -----------------------------------------------------------------------
    // Collector
    // -----------------------------------------------------------------------

    @Nullable
    @Override
    public InlayHintsCollector getCollectorFor(
            @NotNull PsiFile file,
            @NotNull Editor editor,
            @NotNull Settings settings,
            @NotNull InlayHintsSink sink) {

        if (!(file instanceof PsiJavaFile)) {
            return null;
        }

        return new FactoryInlayHintsCollector(editor) {
            @Override
            public boolean collect(@NotNull PsiElement element,
                                   @NotNull Editor editor,
                                   @NotNull InlayHintsSink sink) {

                if (!(element instanceof PsiMethod method)) {
                    return true;
                }
                if (!shouldShowHint(method)) {
                    return true;
                }

                int offset = findTransactionalAnnotationEnd(method);

// ── Measure the indentation in pixels ──────────────────────────────────
                com.intellij.openapi.editor.Document doc = editor.getDocument();
                int lineStart = doc.getLineStartOffset(doc.getLineNumber(offset));
                CharSequence seq = doc.getCharsSequence();

                int indentEnd = lineStart;
                while (indentEnd < offset
                        && indentEnd < seq.length()
                        && Character.isWhitespace(seq.charAt(indentEnd))) {
                    indentEnd++;
                }
                String indentChars = seq.subSequence(lineStart, indentEnd).toString();

                Font editorFont = editor.getColorsScheme()
                        .getFont(com.intellij.openapi.editor.colors.EditorFontType.PLAIN);
                FontMetrics fm = editor.getContentComponent().getFontMetrics(editorFont);
                int leftPx = fm.stringWidth(indentChars);   // ← actual pixel width

// ── Build the presentation with pixel-level left inset ─────────────────
                PresentationFactory factory = this.getFactory();
                InlayPresentation text      = factory.smallText("⟳ Tx Flow");
                InlayPresentation indented  = factory.inset(text, leftPx, 0, 0, 0);
                InlayPresentation clickable = factory.referenceOnHover(
                        indented,
                        (MouseEvent mouseEvent, Point point) ->
                                TransactionDiagramToolWindowFactory.showGraph(
                                        method.getProject(), method));

                sink.addBlockElement(offset, true, true, 100, clickable);
                return true;
            }
        };
    }

    // -----------------------------------------------------------------------
    // Hint eligibility — show only on transaction boundaries or pitfalls
    // -----------------------------------------------------------------------

    private static boolean shouldShowHint(@NotNull PsiMethod method) {
        if (method.isConstructor()) {
            return false;
        }

        // 1. Direct @Transactional annotation
        if (isTransactional(method)) {
            return true;
        }

        // 2. Public method in @Transactional class (inherited boundary)
        PsiClass clazz = method.getContainingClass();
        if (clazz != null && isTransactional(clazz) && isPublic(method)) {
            return true;
        }

        // 3. Same-class self-invocation of @Transactional method (proxy bypass)
        if (hasSelfInvokedTransactionalMethod(method)) {
            return true;
        }

        // 4. Programmatic transaction via PlatformTransactionManager
        if (TransactionGraphBuilder.hasProgrammaticTransaction(method)) {
            return true;
        }

        // 5. Spring Data JPA repository method (implicitly transactional)
        if (TransactionGraphBuilder.isSpringDataRepositoryClass(method.getContainingClass())) {
            return true;
        }

        return false;
    }

    /** True if the owner carries one of the known @Transactional annotations. */
    private static boolean isTransactional(@NotNull PsiModifierListOwner owner) {
        for (String fqn : new String[]{
                "org.springframework.transaction.annotation.Transactional",
                "jakarta.transaction.Transactional",
                "javax.transaction.Transactional"}) {
            if (owner.hasAnnotation(fqn)) {
                return true;
            }
        }
        // Simple-name fallback for unresolved stubs
        PsiModifierList ml = owner.getModifierList();
        if (ml != null) {
            for (PsiAnnotation ann : ml.getAnnotations()) {
                PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
                if (ref != null && "Transactional".equals(ref.getReferenceName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPublic(@NotNull PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers == null) {
            return false;
        }
        if (modifiers.hasModifierProperty(PsiModifier.PUBLIC)) {
            return true;
        }
        // Override of an interface method is implicitly public even without the keyword
        for (PsiMethod superMethod : method.findSuperMethods()) {
            if (superMethod.getContainingClass() != null
                    && superMethod.getContainingClass().isInterface()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code method} contains a call to another
     * {@code @Transactional} method in the <b>same</b> class via an
     * unqualified expression or {@code this.} — i.e. a proxy bypass.
     */
    private static boolean hasSelfInvokedTransactionalMethod(@NotNull PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }

        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return false;
        }

        boolean[] found = {false};
        body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitMethodCallExpression(
                    @NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                if (found[0]) {
                    return;
                }
                PsiMethod resolved = call.resolveMethod();
                if (resolved == null) {
                    return;
                }
                // same-class call only
                if (!containingClass.equals(resolved.getContainingClass())) {
                    return;
                }
                // target method must be @Transactional
                if (!isTransactional(resolved)) {
                    return;
                }

                PsiExpression qualifier =
                        call.getMethodExpression().getQualifierExpression();
                // No qualifier or explicit "this." → proxy bypass
                if (qualifier == null || qualifier instanceof PsiThisExpression) {
                    found[0] = true;
                }
            }
        });
        return found[0];
    }

    // -----------------------------------------------------------------------
    // Annotation offset computation
    // -----------------------------------------------------------------------

    private static int findTransactionalAnnotationEnd(@NotNull PsiMethod method) {
        PsiModifierList ml = method.getModifierList();
        if (ml != null) {
            for (PsiAnnotation ann : ml.getAnnotations()) {
                if (isTransactionalAnnotation(ann)) {
                    return ann.getTextRange().getStartOffset();
                }
            }
        }
        // Use the method's text start so the block hint always appears above the
        // first line of the method declaration (above annotations, modifiers, return type).
        return method.getTextRange().getStartOffset();
    }

    private static boolean isTransactionalAnnotation(@NotNull PsiAnnotation ann) {
        return SpringInspectionUtil.isAnyAnnotation(ann,
                "org.springframework.transaction.annotation.Transactional",
                "jakarta.transaction.Transactional",
                "javax.transaction.Transactional");
    }

    // -----------------------------------------------------------------------
    // Settings placeholder
    // -----------------------------------------------------------------------

    public static final class Settings {
    }
}
