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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PackagePattern}.
 *
 * @author Akmal Sarhan
 */
public class PackagePatternTest {

    @Test
    public void trailingDoubleDotMatchesPackageAndSubpackages() {
        PackagePattern pattern = PackagePattern.parse("com.acme.domain..");
        assertThat(pattern.matches("com.acme.domain", null)).isTrue();
        assertThat(pattern.matches("com.acme.domain.model", null)).isTrue();
        assertThat(pattern.matches("com.acme.domainx", null)).isFalse();
        assertThat(pattern.matches("com.acme", null)).isFalse();
    }

    @Test
    public void leadingAndInnerDoubleDotMatchAnySegments() {
        PackagePattern any = PackagePattern.parse("..domain..");
        assertThat(any.matches("domain", null)).isTrue();
        assertThat(any.matches("com.acme.domain.model", null)).isTrue();
        assertThat(any.matches("com.acme.subdomain", null)).isFalse();

        PackagePattern inner = PackagePattern.parse("com..web");
        assertThat(inner.matches("com.web", null)).isTrue();
        assertThat(inner.matches("com.acme.shop.web", null)).isTrue();
        assertThat(inner.matches("com.acme.web.api", null)).isFalse();
    }

    @Test
    public void starMatchesWithinOneSegment() {
        PackagePattern pattern = PackagePattern.parse("com.acme.*");
        assertThat(pattern.matches("com.acme.web", null)).isTrue();
        assertThat(pattern.matches("com.acme.web.api", null)).isFalse();
    }

    @Test
    public void literalMatchesExactPackageOrClassName() {
        PackagePattern pattern = PackagePattern.parse("jakarta.persistence.Entity");
        assertThat(pattern.matches("jakarta.persistence", "jakarta.persistence.Entity")).isTrue();
        assertThat(pattern.matches("jakarta.persistence", "jakarta.persistence.Table")).isFalse();
        assertThat(PackagePattern.parse("com.acme").matches("com.acme.sub", null)).isFalse();
    }

    @Test
    public void regexPrefixMatchesWholePackage() {
        PackagePattern pattern = PackagePattern.parse("regex:com\\.acme\\.(web|api)(\\..*)?");
        assertThat(pattern.matches("com.acme.api.v1", null)).isTrue();
        assertThat(pattern.matches("com.acme.service", null)).isFalse();
    }

    @Test
    public void rejectsInvalidPatterns() {
        assertThatThrownBy(() -> PackagePattern.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackagePattern.parse("com/acme")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackagePattern.parse("com...acme")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PackagePattern.parse("regex:(")).isInstanceOf(IllegalArgumentException.class);
    }
}
