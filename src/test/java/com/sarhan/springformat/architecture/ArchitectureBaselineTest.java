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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ArchitectureBaseline} parsing, serialization and diffing.
 *
 * @author Akmal Sarhan
 */
public class ArchitectureBaselineTest {

    private static final ArchitectureBaseline.Entry A = new ArchitectureBaseline.Entry("SB-ARCH-001", "r",
            "src/b/B.java", "aaaa", 1);

    private static final ArchitectureBaseline.Entry B = new ArchitectureBaseline.Entry("SB-ARCH-006", "x",
            "src/a/A.java", "bbbb", 1);

    @Test
    public void serializesDeterministicallyAndRoundTrips() {
        ArchitectureBaseline baseline = ArchitectureBaseline.of(List.of(A, B, A));
        String json = baseline.toJson();
        assertThat(json).isEqualTo("""
                {
                  "version": 1,
                  "violations": [
                    {
                      "code": "SB-ARCH-006",
                      "rule": "x",
                      "file": "src/a/A.java",
                      "fingerprint": "bbbb"
                    },
                    {
                      "code": "SB-ARCH-001",
                      "rule": "r",
                      "file": "src/b/B.java",
                      "fingerprint": "aaaa",
                      "count": 2
                    }
                  ]
                }
                """);
        assertThat(ArchitectureBaseline.of(List.of(B, A, A)).toJson()).isEqualTo(json);
        ArchitectureBaseline parsed = ArchitectureBaseline.parse(json);
        assertThat(parsed.error()).isNull();
        assertThat(parsed.count("aaaa")).isEqualTo(2);
        assertThat(parsed.count("bbbb")).isEqualTo(1);
        assertThat(parsed.count("cccc")).isZero();
        assertThat(parsed.toJson()).isEqualTo(json);
    }

    @Test
    public void reportsStaleEntriesAndNewFindings() {
        ArchitectureBaseline previous = ArchitectureBaseline.of(List.of(A, A, B));
        ArchitectureBaseline.Entry c = new ArchitectureBaseline.Entry("SB-ARCH-001", "r", "src/c/C.java", "cccc", 1);
        ArchitectureBaseline current = ArchitectureBaseline.of(List.of(A, c));

        List<ArchitectureBaseline.Entry> stale = previous.staleEntries(current);
        assertThat(stale).extracting(ArchitectureBaseline.Entry::fingerprint).containsExactly("bbbb", "aaaa");
        assertThat(stale).extracting(ArchitectureBaseline.Entry::count).containsExactly(1, 1);
        assertThat(previous.newFindingCount(current)).isEqualTo(1);
        assertThat(current.staleEntries(current)).isEmpty();
    }

    @Test
    public void malformedBaselineIsEmptyWithError() {
        assertThat(ArchitectureBaseline.parse("{ nope").error()).isNotNull();
        assertThat(ArchitectureBaseline.parse("{ \"version\": 2 }").error()).contains("'version' must be 1");
        assertThat(ArchitectureBaseline.parse("{ \"version\": 1, \"violations\": [ { \"code\": \"x\" } ] }").error())
                .contains("rule is required");
        assertThat(ArchitectureBaseline.parse("""
                { "version": 1, "violations": [ { "code": "c", "rule": "r", "file": "f", "fingerprint": "p",
                  "count": 0 } ] }
                """).error()).contains("count must be a positive number");
        ArchitectureBaseline broken = ArchitectureBaseline.parse("");
        assertThat(broken.entries()).isEmpty();
        assertThat(broken.count("aaaa")).isZero();
    }
}
