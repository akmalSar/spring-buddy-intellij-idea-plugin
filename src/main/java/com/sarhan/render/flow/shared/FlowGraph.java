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

import java.util.List;
import java.util.Objects;

/**
 * Generic flow graph used by all Spring Buddy data-flow visualisations
 * (ghost-save, lazy-init prediction, transaction-boundary escape,
 * self-invocation indirection, ...).
 *
 * <p>A flow is a vertical sequence of {@link Node}s connected by directed
 * {@link Edge}s. Each node carries a {@link NodeKind} that drives colour,
 * an emoji badge and a textual semantic label for the renderer.
 *
 * <p>The model is intentionally small — per-flow specifics (header warning
 * wording, edge labels) are decided by the flow builder for that inspection.
 *
 * @author Akmal Sarhan
 */
public final class FlowGraph {

    private final Node entry;
    private final List<Node> nodes;
    private final List<Edge> edges;
    private final String headerWarning;
    private final String title;

    private FlowGraph(Builder b) {
        this.entry         = Objects.requireNonNull(b.entry, "entry");
        this.nodes         = List.copyOf(b.nodes);
        this.edges         = List.copyOf(b.edges);
        this.headerWarning = b.headerWarning;
        this.title         = b.title;
    }

    public Node getEntry()           { return this.entry; }

    public List<Node> getNodes()     { return this.nodes; }

    public List<Edge> getEdges()     { return this.edges; }

    public String getHeaderWarning() { return this.headerWarning; }

    public String getTitle()         { return this.title; }

    public static Builder builder(Node entry) {
        return new Builder(entry);
    }

    /**
     * Visual category for a node.  The renderer maps the kind to a colour
     * palette and an emoji badge.
     */
    public enum NodeKind {
        /** Method that owns the flow (typically the analysed entry-point). */
        ENTRY_METHOD,
        /** Repository / EntityManager call producing a tainted value. */
        SOURCE,
        /** Local variable, field or parameter carrying the taint. */
        VARIABLE,
        /** Intermediate helper method receiving the tainted value. */
        HELPER_CALL,
        /**
         * A {@code @Transactional} method whose proxy is bypassed —
         * used by the self-invocation flow.
         */
        PROXY_BYPASS,
        /**
         * A {@code @Transactional} method whose return value escapes the
         * transaction boundary — used by the boundary-escape flow.
         */
        TRANSACTIONAL_RETURN,
        /**
         * A lazy-loading getter on a detached entity — used by the
         * lazy-init prediction flow.
         */
        LAZY_GETTER,
        /** Mutator / sink whose effect is silently dropped or fails. */
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
     * Directed edge between two nodes.  The label, when non-empty, is
     * rendered near the midpoint and also drives the edge colour
     * (label values {@code "modifying"}, {@code "save"}, {@code "bypass"},
     * {@code "lazy"}, {@code "escape"} all paint the edge red).
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
        private String title = "";

        private Builder(Node entry) { this.entry = entry; }

        public Builder nodes(List<Node> nodes)       { this.nodes = nodes; return this; }
        public Builder edges(List<Edge> edges)       { this.edges = edges; return this; }
        public Builder headerWarning(String warning) { this.headerWarning = warning; return this; }
        public Builder title(String title)           { this.title = title; return this; }

        public FlowGraph build() {
            return new FlowGraph(this);
        }
    }
}
