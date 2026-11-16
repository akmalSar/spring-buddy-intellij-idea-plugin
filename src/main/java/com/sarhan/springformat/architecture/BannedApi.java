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

import java.util.List;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

/**
 * A banned class, annotation or method from the {@code bannedApis} section of
 * {@code .spring-buddy/architecture.json} ({@code SB-ARCH-006}).
 * <p>
 * {@code symbol} is a fully-qualified type name ({@code java.util.Date},
 * {@code javax.transaction.Transactional}) or a method written as
 * {@code Type#method} (all overloads). {@code Type.method} is accepted as
 * well. Matching compares the fully-qualified name of the PSI-resolved
 * target, so unresolved references and lookalike names never match.
 *
 * @param id        rule id from the configuration
 * @param symbol    banned type or method
 * @param allowedIn package patterns (or layers) where use is permitted
 * @param severity  severity of violations
 * @param message   human-readable reason, may be {@code null}
 * @param fixHint   root-cause fix, may be {@code null}
 * @author Akmal Sarhan
 */
public record BannedApi(String id, String symbol, List<PackagePattern> allowedIn, ArchitectureSeverity severity,
                        @Nullable String message, @Nullable String fixHint) implements ArchitectureFinding {

    public BannedApi {
        allowedIn = List.copyOf(allowedIn);
    }

    @Override
    public String code() {
        return ArchitecturePresets.CODE_BANNED_API;
    }

    /**
     * Returns {@code true} when a class in the given package may use this API.
     */
    public boolean isAllowedIn(@Nullable String packageName, @Nullable String qualifiedClassName) {
        return PackagePattern.anyMatches(this.allowedIn, packageName, qualifiedClassName);
    }

    public boolean matchesClass(String qualifiedClassName) {
        return this.symbol.equals(qualifiedClassName);
    }

    /**
     * Returns {@code true} when the symbol names the given method.
     *
     * @param qualifiedClassName fully-qualified name of the declaring class
     * @param methodName         method name
     */
    public boolean matchesMethod(String qualifiedClassName, String methodName) {
        int length = qualifiedClassName.length();
        return this.symbol.length() == length + 1 + methodName.length() && this.symbol.startsWith(qualifiedClassName)
                && (this.symbol.charAt(length) == '#' || this.symbol.charAt(length) == '.')
                && this.symbol.endsWith(methodName);
    }

    /**
     * Simple method name when the symbol can denote a method, otherwise {@code null}.
     */
    @Nullable
    public String memberName() {
        int separator = this.symbol.indexOf('#');
        if (separator < 0) {
            separator = this.symbol.lastIndexOf('.');
        }
        return (separator < 0) ? null : this.symbol.substring(separator + 1);
    }

    @Override
    public String what(String target) {
        if (this.allowedIn.isEmpty()) {
            return "Banned API " + target + " is used";
        }
        return "Banned API " + target + " is used outside " + allowedZone();
    }

    @Override
    public String defaultFix(String target) {
        if (this.allowedIn.isEmpty()) {
            return "Replace the use of " + target + " with the approved alternative";
        }
        return "Call the wrapper in " + allowedZone() + " instead of using " + target + " directly";
    }

    private String allowedZone() {
        return this.allowedIn.stream().map((PackagePattern pattern) -> "'" + pattern.raw() + "'")
                .collect(Collectors.joining(", "));
    }
}
