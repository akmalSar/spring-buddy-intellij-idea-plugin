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

import org.jetbrains.annotations.Nullable;

/**
 * A resolved dependency rule: classes in {@code from} must not reference types
 * in {@code mustNotDependOn}. Layer names are already expanded to their
 * package patterns. {@code except} patterns exempt a dependency when they match
 * either the referenced type or the referencing class.
 *
 * @param code            stable Spring Buddy code, e.g. {@code SB-ARCH-001}
 * @param id              rule id from the configuration (or preset id)
 * @param from            source package patterns
 * @param mustNotDependOn forbidden target package patterns
 * @param severity        severity of violations
 * @param message         human-readable reason, may be {@code null}
 * @param except          exemption patterns
 * @param fixHint         root-cause fix shown in the message, may be {@code null}
 * @author Akmal Sarhan
 */
public record ArchitectureRule(String code, String id, List<PackagePattern> from, List<PackagePattern> mustNotDependOn,
                               ArchitectureSeverity severity, @Nullable String message, List<PackagePattern> except,
                               @Nullable String fixHint) implements ArchitectureFinding {

    public ArchitectureRule {
        from = List.copyOf(from);
        mustNotDependOn = List.copyOf(mustNotDependOn);
        except = List.copyOf(except);
    }

    public ArchitectureRule(String code, String id, List<PackagePattern> from, List<PackagePattern> mustNotDependOn,
                            ArchitectureSeverity severity, @Nullable String message, List<PackagePattern> except) {
        this(code, id, from, mustNotDependOn, severity, message, except, null);
    }

    /**
     * Returns {@code true} when a class in the given package is constrained by this rule.
     */
    public boolean appliesTo(@Nullable String packageName, @Nullable String qualifiedClassName) {
        return !this.severity.isOff() && PackagePattern.anyMatches(this.from, packageName, qualifiedClassName)
                && !PackagePattern.anyMatches(this.except, packageName, qualifiedClassName);
    }

    /**
     * Returns {@code true} when referencing the given target violates this rule.
     */
    public boolean forbids(@Nullable String targetPackage, @Nullable String targetQualifiedName) {
        return PackagePattern.anyMatches(this.mustNotDependOn, targetPackage, targetQualifiedName)
                && !PackagePattern.anyMatches(this.except, targetPackage, targetQualifiedName);
    }

    public ArchitectureRule withSeverity(ArchitectureSeverity newSeverity) {
        return new ArchitectureRule(this.code, this.id, this.from, this.mustNotDependOn, newSeverity, this.message,
                this.except, this.fixHint);
    }
}
