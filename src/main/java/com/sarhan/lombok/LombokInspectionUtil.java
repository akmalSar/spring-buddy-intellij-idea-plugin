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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

/**
 * @author Akmal Sarhan
 */

final class LombokInspectionUtil {

    private LombokInspectionUtil() {
    }

    static boolean isInJavaFile(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (!(file instanceof PsiJavaFile)) {
            return false;
        }
        VirtualFile virtualFile = file.getVirtualFile();
        return virtualFile != null && "java".equalsIgnoreCase(virtualFile.getExtension());
    }
}
