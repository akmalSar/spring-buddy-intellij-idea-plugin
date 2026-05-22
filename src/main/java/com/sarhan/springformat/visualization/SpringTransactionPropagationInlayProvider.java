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

package com.sarhan.springformat.visualization;

import javax.swing.*;

import com.intellij.codeInsight.hints.ChangeListener;
import com.intellij.codeInsight.hints.ImmediateConfigurable;
import com.intellij.codeInsight.hints.InlayGroup;
import com.intellij.codeInsight.hints.InlayHintsCollector;
import com.intellij.codeInsight.hints.InlayHintsProvider;
import com.intellij.codeInsight.hints.InlayHintsSink;
import com.intellij.codeInsight.hints.NoSettings;
import com.intellij.codeInsight.hints.SettingsKey;
import com.intellij.codeInsight.hints.presentation.InlayPresentation;
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Akmal Sarhan
 */
public class SpringTransactionPropagationInlayProvider implements InlayHintsProvider<NoSettings> {

    private static final SettingsKey<NoSettings> KEY = new SettingsKey<>("spring.transaction.propagation.visualizer");

    @Override
    public InlayHintsCollector getCollectorFor(@NotNull PsiFile file, @NotNull Editor editor, @NotNull NoSettings settings,
                                               @NotNull InlayHintsSink sink) {
        return (PsiElement element, Editor currentEditor, InlayHintsSink currentSink) -> {
            if (element instanceof PsiMethod method && method.getBody() != null) {
                Propagation current = propagation(method);
                PresentationFactory factory = new PresentationFactory(currentEditor);

                method.getBody().accept(new JavaRecursiveElementVisitor() {
                    @Override
                    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                        super.visitMethodCallExpression(call);
                        PsiMethod target = call.resolveMethod();
                        if (target == null) {
                            return;
                        }

                        Propagation targetPropagation = propagation(target);
                        if (targetPropagation == null) {
                            return;
                        }

                        Hint hint = hint(method, current, targetPropagation, target);
                        if (hint == null) {
                            return;
                        }

                        // 1. Scale icon to 12x12 to fit line height
                        Icon scaledIcon = IconUtil.toSize(hint.icon(), 12, 12);

                        InlayPresentation iconPart = factory.inset(factory.icon(scaledIcon), 2, 2, 1, 0);
                        InlayPresentation textPart = factory.smallText(hint.text());

                        // Sequence them: [Icon] [4px space] [Text]
                        InlayPresentation combined = factory.seq(
                                iconPart,
                                factory.inset(textPart, 4, 0, 0, 0)
                        );

                        InlayPresentation presentation = factory.withTooltip(
                                hint.tooltip(),
                                factory.roundWithBackgroundAndSmallInset(combined)
                        );

                        currentSink.addInlineElement(call.getTextRange().getEndOffset(), true, presentation, false);
                    }
                });
            }
            return true;
        };
    }

    private static @Nullable Hint hint(PsiMethod caller, @Nullable Propagation current, Propagation target, PsiMethod targetMethod) {
        if (targetMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
            return new Hint("Bypassed (Private)", "Private methods cannot be intercepted by Spring proxies.", AllIcons.General.Warning);
        }

        if (targetMethod.getContainingClass() == caller.getContainingClass()) {
            return new Hint("Self-Call Bypass", "Self-invocation bypasses the Spring Proxy.", AllIcons.General.Warning);
        }

        if (isAsync(targetMethod)) {
            return new Hint("Async Boundary", "Runs on separate thread; no shared transaction.", AllIcons.Actions.Lightning);
        }

        if (isReadOnly(caller) && !isReadOnly(targetMethod)) {
            return new Hint("Write in Read-Only", "Calling a write method from a read-only transaction.", AllIcons.General.Error);
        }

        if (isDeepRequiresNew(targetMethod, 0)) {
            return new Hint("Pool Risk: Nested", "Risk of Connection Pool Starvation.", AllIcons.General.Warning);
        }

        if (target == Propagation.REQUIRED && current == Propagation.REQUIRED) {
            return null;
        }

        return switch (target) {
            case REQUIRES_NEW ->
                    new Hint("New Tx", "Suspends current and borrows a new connection.", AllIcons.General.Add);
            case REQUIRED -> new Hint(
                    (current != null) ? "Joins Tx" : "Starts Tx",
                    (current != null) ? "Participates in current transaction." : "Starts a new transaction.",
                    (current != null) ? AllIcons.Hierarchy.Subtypes : AllIcons.Actions.Execute);
            case SUPPORTS -> new Hint("Supports", "Runs with Tx only if one exists.", AllIcons.Actions.Help);
            case MANDATORY -> new Hint("Mandatory", "Fails if no transaction exists.", AllIcons.Nodes.SecurityRole);
            case NOT_SUPPORTED ->
                    new Hint("No-Tx Block", "Suspends current Tx to run non-transactionally.", AllIcons.Actions.Pause);
            case NEVER -> new Hint("Never Tx", "Fails if a transaction exists.", AllIcons.Actions.Cancel);
            case NESTED ->
                    new Hint("Nested (Savepoint)", "Creates a savepoint within the current transaction.", AllIcons.Nodes.CopyOfFolder);
        };
    }

    private static boolean isReadOnly(PsiMethod method) {
        PsiAnnotation annotation = transactional(method);
        if (annotation == null) {
            return false;
        }
        PsiAnnotationMemberValue value = annotation.findAttributeValue("readOnly");
        return value != null && "true".equals(value.getText());
    }

    private static boolean isAsync(PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name != null && name.contains("Async")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDeepRequiresNew(PsiMethod method, int depth) {
        if (depth >= 2 && propagation(method) == Propagation.REQUIRES_NEW) {
            return true;
        }
        if (method.getBody() == null || depth > 4) {
            return false;
        }
        boolean[] found = {false};
        method.getBody().accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                PsiMethod target = call.resolveMethod();
                if (target != null && propagation(target) == Propagation.REQUIRES_NEW && isDeepRequiresNew(target, depth + 1)) {
                    found[0] = true;
                }
                super.visitMethodCallExpression(call);
            }
        });
        return found[0];
    }

    private static @Nullable Propagation propagation(PsiMethod method) {
        PsiAnnotation annotation = transactional(method);
        if (annotation == null) {
            return null;
        }
        PsiAnnotationMemberValue value = annotation.findAttributeValue("propagation");
        if (value == null) {
            return Propagation.REQUIRED;
        }

        String text = value.getText();
        for (Propagation p : Propagation.values()) {
            if (text.contains(p.name())) {
                return p;
            }
        }
        return Propagation.REQUIRED;
    }

    private static @Nullable PsiAnnotation transactional(PsiMethod method) {
        PsiModifierList modifiers = method.getModifierList();
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name != null && name.contains("Transactional")) {
                return annotation;
            }
        }
        return null;
    }

    @Override
    public NoSettings createSettings() {
        return new NoSettings();
    }

    @Override
    public String getName() {
        return "Transaction propagation visualizer";
    }

    @Override
    public InlayGroup getGroup() {
        return InlayGroup.OTHER_GROUP;
    }

    @Override
    public SettingsKey<NoSettings> getKey() {
        return KEY;
    }

    @Override
    public String getDescription() {
        return "Shows transaction boundaries.";
    }

    @Override
    public String getPreviewText() {
        return "class S { @Transactional void m() { s.save(); } }";
    }

    @Override
    public ImmediateConfigurable createConfigurable(NoSettings s) {
        return (ChangeListener l) -> null;
    }

    @Override
    public boolean isLanguageSupported(@NotNull Language l) {
        return l.isKindOf(JavaLanguage.INSTANCE);
    }

    private record Hint(String text, String tooltip, Icon icon) {
    }

    private enum Propagation {
        REQUIRED, REQUIRES_NEW, SUPPORTS, MANDATORY, NOT_SUPPORTED, NEVER, NESTED
    }
}
