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

package com.sarhan.springformat.architecture;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stable, line-independent fingerprint of an architecture finding:
 * SHA-256 over code, rule id, project-relative file path, the enclosing
 * member ({@code a.b.Type#method}, {@code a.b.Type#field}, {@code a.b.Type}
 * or {@code <file>} for imports) and the highlighted source text. Moving code
 * within the file or editing other members keeps the fingerprint; renaming the
 * file, the member or the highlighted reference changes it.
 *
 * @param code        stable code
 * @param rule        rule id
 * @param file        project-relative file path
 * @param fingerprint 16 hex characters
 * @author Akmal Sarhan
 */
public record ArchitectureFingerprint(String code, String rule, String file, String fingerprint) {

    /**
     * Fingerprint of a finding that is about to be registered.
     *
     * @param anchor         element the problem is registered on
     * @param rangeInElement highlighted range relative to {@code anchor}, or {@code null} for the whole element
     */
    public static ArchitectureFingerprint of(@NotNull PsiElement anchor, @Nullable TextRange rangeInElement,
                                             @NotNull String code, @NotNull String rule) {
        String file = relativePath(anchor.getContainingFile());
        String text = anchor.getText();
        if (text == null) {
            text = "";
        }
        if (rangeInElement != null && rangeInElement.getEndOffset() <= text.length()) {
            text = rangeInElement.substring(text);
        }
        String key = code + '|' + rule + '|' + file + '|' + context(anchor) + '|' + text.trim().replaceAll("\\s+", " ");
        return new ArchitectureFingerprint(code, rule, file, hash(key));
    }

    /**
     * Fingerprint of a registered problem, or {@code null} when it is not an
     * architecture problem or has no element.
     */
    @Nullable
    public static ArchitectureFingerprint of(@NotNull ProblemDescriptor descriptor) {
        ArchitectureProblem problem = ArchitectureProblem.parse(descriptor.getDescriptionTemplate());
        PsiElement element = descriptor.getPsiElement();
        if (problem == null || element == null) {
            return null;
        }
        return of(element, descriptor.getTextRangeInElement(), problem.code(), problem.ruleId());
    }

    public ArchitectureBaseline.Entry toEntry() {
        return new ArchitectureBaseline.Entry(this.code, this.rule, this.file, this.fingerprint, 1);
    }

    /**
     * Project-relative path with {@code /} separators, or the file name when
     * the file is outside the project directory.
     */
    static String relativePath(@Nullable PsiFile file) {
        if (file == null) {
            return "<unknown>";
        }
        VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
        if (virtualFile == null) {
            return file.getName();
        }
        VirtualFile baseDir = ProjectUtil.guessProjectDir(file.getProject());
        String relative = (baseDir != null) ? VfsUtilCore.getRelativePath(virtualFile, baseDir, '/') : null;
        return (relative != null) ? relative : virtualFile.getName();
    }

    private static String context(PsiElement anchor) {
        PsiMember member = PsiTreeUtil.getParentOfType(anchor, PsiMember.class, false);
        while (member != null) {
            String name = describe(member);
            if (name != null) {
                return name;
            }
            member = PsiTreeUtil.getParentOfType(member, PsiMember.class, true);
        }
        return "<file>";
    }

    @Nullable
    private static String describe(PsiMember member) {
        if (member instanceof PsiClass psiClass) {
            return psiClass.getQualifiedName();
        }
        PsiClass owner = member.getContainingClass();
        String ownerName = (owner != null) ? owner.getQualifiedName() : null;
        String name = member.getName();
        if (ownerName == null || name == null) {
            return null;
        }
        if (member instanceof PsiMethod method) {
            return ownerName + '#' + name + '/' + method.getParameterList().getParametersCount();
        }
        return ownerName + '#' + name;
    }

    private static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
