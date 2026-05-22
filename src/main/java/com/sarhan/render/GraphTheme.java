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

import java.awt.Color;

import javax.swing.UIManager;

import com.intellij.ide.ui.LafManager;
import org.jetbrains.annotations.NotNull;

/**
 * Detects graph theme from the user's active IDE UI colors.
 *
 * @author Akmal Sarhan
 */
public final class GraphTheme {

    private GraphTheme() {
    }

    public static boolean isDark() {
        Color background = UIManager.getColor("Panel.background");
        if (background == null) {
            return LafManager.getInstance().getCurrentUIThemeLookAndFeel().isDark();
        }
        return luminance(background) < 0.5d;
    }

    private static double luminance(@NotNull Color color) {
        return (0.2126d * color.getRed() + 0.7152d * color.getGreen() + 0.0722d * color.getBlue()) / 255d;
    }
}
