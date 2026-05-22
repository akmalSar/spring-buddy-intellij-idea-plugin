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

package com.sarhan.render.flow;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a Ghost-Save data-flow graph.
 *
 * <p>Visualises how a JPA entity loaded outside a {@code @Transactional}
 * boundary flows through aliases, helper method calls, and finally to a
 * mutation sink whose effect is silently dropped by Hibernate.
 *
 * @author Akmal Sarhan
 */
public final class GhostSaveFlow {

    private final Node entry;
    private final List<Node> nodes;
    private final List<Edge> edges;
    private final String headerWarning;

    private GhostSaveFlow(Builder b) {
        this.entry         = Objects.requireNonNull(b.entry, "entry");
        this.nodes         = List.copyOf(b.nodes);
        this.edges         = List.copyOf(b.edges);
        this.headerWarning = b.headerWarning;
    }

    public Node getEntry()                  { return this.entry; }

    public List<Node> getNodes()            { return this.nodes; }

    public List<Edge> getEdges()            { return this.edges; }

    public String getHeaderWarning()        { return this.headerWarning; }

    public static Builder builder(Node entry) {
        return new Builder(entry);
    }

    public enum NodeKind {
        /** Entry method that owns the flow (typically not @Transactional). */
        ENTRY_METHOD,
        /** Repository / EntityManager call that loads the detached entity. */
        SOURCE,
        /** Local variable, field, or parameter carrying the taint. */
        VARIABLE,
        /** Intermediate helper method that receives the tainted entity. */
        HELPER_CALL,
        /** Setter / mutator that silently fails — the ghost-save sink. */
        SINK
    }

    /**
     * Single graph node.
     *
     * @param id           stable identifier (used as SVG element id)
     * @param label        primary text shown inside the node
     * @param detail       secondary text (e.g. file:line, type)
     * @param kind         category determines colour / shape
     * @param fileName     short file name for click-to-navigate
     * @param lineNumber   one-based line number (or -1 if unknown)
     */
    public record Node(
            String id,
            String label,
            String detail,
            NodeKind kind,
            String fileName,
            int lineNumber
    ) {
        public Node {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /**
     * Directed edge between two nodes, optionally labelled.
     */
    public record Edge(
            String fromId,
            String toId,
            String label
    ) {
        public Edge {
            Objects.requireNonNull(fromId, "fromId");
            Objects.requireNonNull(toId,   "toId");
        }
    }

    public static final class Builder {

        private final Node entry;
        private List<Node> nodes = List.of();
        private List<Edge> edges = List.of();
        private String headerWarning = "";

        private Builder(Node entry) { this.entry = entry; }

        public Builder nodes(List<Node> nodes)       { this.nodes = nodes; return this; }
        public Builder edges(List<Edge> edges)       { this.edges = edges; return this; }
        public Builder headerWarning(String warning) { this.headerWarning = warning; return this; }

        public GhostSaveFlow build() {
            return new GhostSaveFlow(this);
        }
    }
}
