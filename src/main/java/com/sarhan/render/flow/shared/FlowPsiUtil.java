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

package com.sarhan.render.flow.shared;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tiny PSI helpers shared by all flow builders. Keeps file:line bookkeeping
 * consistent across the four data-flow visualisations.
 *
 * @author Akmal Sarhan
 */
public final class FlowPsiUtil {

    private FlowPsiUtil() {
    }

    /** Returns the short file name of {@code element}, or empty if unknown. */
    @NotNull
    public static String fileName(@Nullable PsiElement element) {
        if (element == null) {
            return "";
        }
        PsiFile file = element.getContainingFile();
        return (file != null) ? file.getName() : "";
    }

    /**
     * Returns the one-based line number of {@code element}'s declaration, or
     * {@code -1} if unavailable. For named elements (methods, variables) the
     * line of the name identifier is preferred so navigation lands on the
     * declaration rather than on a preceding Javadoc or annotation block.
     */
    public static int lineOf(@Nullable PsiElement element) {
        if (element == null) {
            return -1;
        }
        PsiElement target = element;
        if (element instanceof PsiMethod m && m.getNameIdentifier() != null) {
            target = m.getNameIdentifier();
        } else if (element instanceof PsiVariable v && v.getNameIdentifier() != null) {
            target = v.getNameIdentifier();
        }
        PsiFile file = target.getContainingFile();
        if (file == null) {
            return -1;
        }
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) {
            return -1;
        }
        Document doc = FileDocumentManager.getInstance().getDocument(vf);
        if (doc == null) {
            return -1;
        }
        return doc.getLineNumber(target.getTextRange().getStartOffset()) + 1;
    }
}
