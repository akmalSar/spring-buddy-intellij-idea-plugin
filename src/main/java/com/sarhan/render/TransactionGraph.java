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

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of the transaction call graph rooted at a single PSI method.
 *
 * <p>Produced by {@code TransactionGraphBuilder} and cached by
 * {@code TransactionDiagramService}. Consumed by {@code TransactionSvgRenderer}.
 *
 * <p>Edges are directed: {@code Pair.first → Pair.second} (caller → callee).
 *
 * @author Akmal Sarhan
 */
public final class TransactionGraph {

    /** Maximum node count before the graph is truncated to depth 1. */
    public static final int MAX_NODES = 20;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /** The method at the caret — always the root of the graph. */
    private final TransactionNode root;

    /**
     * All nodes in the graph, including the root.
     * When {@link #truncated} is true this contains only the root plus its
     * direct callers and callees (depth = 1).
     */
    private final List<TransactionNode> nodes;

    /**
     * Directed edges represented as (callerId, calleeId) pairs.
     * The {@code isProxyBypass} flag is stored on the callee node, but the
     * SVG renderer reads it from here via the node map for edge styling.
     */
    private final List<Edge> edges;

    /**
     * True when the full call graph exceeded {@link #MAX_NODES} nodes and was
     * collapsed to depth 1. The renderer shows a yellow truncation banner.
     */
    private final boolean truncated;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    private TransactionGraph(Builder builder) {
        this.root      = Objects.requireNonNull(builder.root, "root");
        this.nodes     = List.copyOf(builder.nodes);
        this.edges     = List.copyOf(builder.edges);
        this.truncated = builder.truncated;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public TransactionNode getRoot()       { return this.root; }
    public List<TransactionNode> getNodes() { return this.nodes; }
    public List<Edge> getEdges()           { return this.edges; }
    public boolean isTruncated()           { return this.truncated; }

    /** Convenience: true if the graph has at least one @Transactional node. */
    public boolean hasTransactionalBoundary() {
        return this.nodes.stream().anyMatch((TransactionNode n) ->
                n.getStatus() != TransactionNode.TxStatus.NON_TX);
    }

    // -----------------------------------------------------------------------
    // Edge record
    // -----------------------------------------------------------------------

    public static Builder builder(TransactionNode root) {
        return new Builder(root);
    }

    /**
     * A directed call edge in the graph.
     *
     * <p>Edge-level metadata (proxy bypass, suspension) is duplicated here
     * for convenient SVG rendering without requiring a second node lookup.
     */
    public record Edge(
            String fromId,
            String toId,
            boolean proxyBypass,
            boolean suspension,
            boolean nested,
            String statusLabel
    ) {
        public Edge {
            Objects.requireNonNull(fromId, "fromId");
            Objects.requireNonNull(toId, "toId");
        }

public static Edge plain(String fromId, String toId) {
            return new Edge(fromId, toId, false, false, false, "");
        }
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static final class Builder {

        private final TransactionNode root;
        private List<TransactionNode> nodes = List.of();
        private List<Edge> edges = List.of();
        private boolean truncated = false;

        private Builder(TransactionNode root) {
            this.root = root;
        }

        public Builder nodes(List<TransactionNode> nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder edges(List<Edge> edges) {
            this.edges = edges;
            return this;
        }

        public Builder truncated(boolean t) {
            this.truncated = t;
            return this;
        }

        public TransactionGraph build() {
            return new TransactionGraph(this);
        }
    }
}
