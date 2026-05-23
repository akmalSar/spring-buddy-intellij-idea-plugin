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

import java.util.*;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

/**
 * Generates a self-contained HTML page (string) containing an SVG transaction
 * graph suitable for loading into a {@code JBCefBrowser} via
 * {@code loadHTML(htmlString, "about:blank")}.
 *
 * <h3>UX enhancements over v1</h3>
 * <ul>
 *   <li><b>Bug fix</b>: {@code s}, {@code zx}, {@code zy} are now properly initialised
 *       to {@code 1/0/0} so dragging works from the very first interaction.</li>
 *   <li><b>Auto-fit on load</b>: {@code fitAll()} is called in the first
 *       {@code requestAnimationFrame} so the graph is always centred and scaled
 *       to fill the viewport on open.</li>
 *   <li><b>Node selection</b>: single-click selects a node, highlights its direct
 *       neighbours and the connecting edges, and dims everything else.
 *       A second click on the same node (or a click on the background) deselects.</li>
 *   <li><b>Glow filter</b>: the selected node gets a soft SVG glow via
 *       {@code <filter id="f-glow">}.</li>
 *   <li><b>Double-click to navigate</b>: two clicks within 300 ms on the same
 *       node fires {@code window.intellijNavigateNode(id)} — more reliable than
 *       the previous mouseup-distance heuristic.</li>
 *   <li><b>Smooth zoom on buttons</b>: zoom-bar clicks and keyboard shortcuts use
 *       a 160 ms CSS transition; wheel and drag zoom remain instant for
 *       responsiveness.</li>
 *   <li><b>Keyboard shortcuts</b>: F=fit, R=reset, +/−=zoom, arrows=pan,
 *       Escape=deselect.</li>
 *   <li><b>Zoom-percentage indicator</b>: shown below the zoom-bar buttons.</li>
 *   <li><b>Minimap</b>: a canvas thumbnail in the bottom-right corner shows all
 *       nodes (selected node highlighted) and a yellow viewport rectangle.
 *       Click anywhere on the minimap to pan there instantly.</li>
 *   <li><b>Touch support</b>: one-finger pan and two-finger pinch-to-zoom.</li>
 *   <li><b>Cursor polish</b>: {@code grab} on the background, {@code grabbing}
 *       while panning, {@code pointer} on nodes.</li>
 *   <li><b>Node hover brightness</b>: CSS {@code filter:brightness(1.18)} on
 *       {@code :hover} via the {@code .node-bg} class on the background rect.</li>
 *   <li><b>Keyboard hint strip</b>: a translucent one-liner in the bottom-left
 *       corner lists the available shortcuts.</li>
 *   <li><b>Dark-theme tooltip</b>: tooltip background/foreground now respects
 *       the {@code darkTheme} parameter.</li>
 * </ul>
 *
 * @author Akmal Sarhan
 */
public final class TransactionSvgRenderer {

    // -----------------------------------------------------------------------
    // Color palette (light / dark)
    // -----------------------------------------------------------------------

    private static final Map<TransactionNode.Propagation, String[]> PROP_COLORS = Map.of(
            TransactionNode.Propagation.REQUIRED,       new String[]{"#4A90D9", "#5BA3F5"},
            TransactionNode.Propagation.REQUIRES_NEW,   new String[]{"#E6A817", "#F5C842"},
            TransactionNode.Propagation.NESTED,         new String[]{"#1A9E8F", "#26C4B3"},
            TransactionNode.Propagation.SUPPORTS,       new String[]{"#8C8C8C", "#AAAAAA"},
            TransactionNode.Propagation.NOT_SUPPORTED,  new String[]{"#8C8C8C", "#AAAAAA"},
            TransactionNode.Propagation.NEVER,          new String[]{"#8C8C8C", "#AAAAAA"},
            TransactionNode.Propagation.MANDATORY,      new String[]{"#4A90D9", "#5BA3F5"}
    );

    private static final String[] ERROR_COLOR  = {"#D94A4A", "#F56B6B"};
    private static final String[] NON_TX_COLOR = {"#8C8C8C", "#AAAAAA"};

    // -----------------------------------------------------------------------
    // Layout constants
    // -----------------------------------------------------------------------

    private static final int NODE_W     = 220;
    private static final int NODE_H     = 60;
    private static final int H_GAP      = 40;
    private static final int V_GAP      = 80;
    private static final int PADDING    = 40;
    private static final int LEGEND_ROW = 22;

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    @NotNull
    public String renderHtml(@NotNull TransactionGraph graph, boolean darkTheme) {
        String svgContent = renderSvg(graph, darkTheme);
        return buildHtmlPage(svgContent, graph, darkTheme);
    }

    // -----------------------------------------------------------------------
    // SVG generation
    // -----------------------------------------------------------------------

    @NotNull
    private String renderSvg(@NotNull TransactionGraph graph, boolean darkTheme) {
        List<TransactionNode> nodes = graph.getNodes();
        List<TransactionGraph.Edge> edges = graph.getEdges();

        Map<String, int[]> positions = computeLayout(graph);

        int maxX = positions.values().stream().mapToInt((int[] p) -> p[0]).max().orElse(0) + NODE_W + PADDING;
        int maxY = positions.values().stream().mapToInt((int[] p) -> p[1]).max().orElse(0) + NODE_H + PADDING;
        int svgW = Math.max(3000, maxX + 1000);
        int svgH = Math.max(2000, maxY + 1000);

        Set<TransactionNode.Propagation> presentProps = nodes.stream()
                .map(TransactionNode::getPropagation)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        StringBuilder svg = new StringBuilder();
        svg.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" " +
                        "style=\"font-family:monospace;background:transparent;overflow:visible;\">%n",
                svgW, svgH));

        // ── Defs ──────────────────────────────────────────────────────────
        svg.append("<defs>\n");
        svg.append(buildMarkerDef("m-gray",  darkTheme ? "#AAAAAA" : "#8C8C8C"));
        svg.append(buildMarkerDef("m-red",   errorColor(darkTheme)));
        svg.append(buildMarkerDef("m-amber", darkTheme ? "#F5C842" : "#E6A817"));
        svg.append(buildMarkerDef("m-teal",  darkTheme ? "#26C4B3" : "#1A9E8F"));
        for (TransactionGraph.Edge edge : edges) {
            svg.append(buildEdgeGradientDef(edge, darkTheme));
        }
        // Glow filter — applied to the selected node's background rect via JS
        svg.append(
                "  <filter id=\"f-glow\" x=\"-30%\" y=\"-30%\" width=\"160%\" height=\"160%\">\n" +
                        "    <feGaussianBlur in=\"SourceGraphic\" stdDeviation=\"4\" result=\"blur\"/>\n" +
                        "    <feMerge>" +
                        "<feMergeNode in=\"blur\"/>" +
                        "<feMergeNode in=\"SourceGraphic\"/>" +
                        "</feMerge>\n" +
                        "  </filter>\n");
        svg.append("</defs>\n");

        // Edges first so nodes render on top
        for (TransactionGraph.Edge edge : edges) {
            int[] from = positions.get(edge.fromId());
            int[] to   = positions.get(edge.toId());
            if (from == null || to == null) {
                continue;
            }
            svg.append(buildEdge(edge, from, to, darkTheme));
        }

        // Nodes
        for (TransactionNode node : nodes) {
            int[] pos = positions.get(node.getId());
            if (pos == null) {
                continue;
            }
            boolean isRoot = node.getId().equals(graph.getRoot().getId());
            svg.append(buildNode(node, pos[0], pos[1], darkTheme, isRoot));
        }

        svg.append("</svg>");
        return svg.toString();
    }

    // -----------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------

    @NotNull
    private Map<String, int[]> computeLayout(@NotNull TransactionGraph graph) {
        String rootId = graph.getRoot().getId();
        Set<String> allIds = graph.getNodes().stream()
                .map(TransactionNode::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> calleeIds = graph.getEdges().stream()
                .filter((TransactionGraph.Edge e) -> e.fromId().equals(rootId))
                .map(TransactionGraph.Edge::toId)
                .collect(Collectors.toSet());

        Set<String> callerIds = new LinkedHashSet<>(allIds);
        callerIds.remove(rootId);
        callerIds.removeAll(calleeIds);

        Map<String, int[]> pos = new LinkedHashMap<>();
        layoutRow(new ArrayList<>(callerIds), 0, pos);
        layoutRow(List.of(rootId), 1, pos);
        layoutRow(new ArrayList<>(calleeIds), 2, pos);
        return pos;
    }

    private void layoutRow(@NotNull List<String> ids, int row,
                           @NotNull Map<String, int[]> pos) {
        int y = PADDING + row * (NODE_H + V_GAP);
        int totalW = ids.size() * NODE_W + (ids.size() - 1) * H_GAP;
        int startX = PADDING + Math.max(0, (600 - totalW) / 2);
        for (int i = 0; i < ids.size(); i++) {
            pos.put(ids.get(i), new int[]{startX + i * (NODE_W + H_GAP), y});
        }
    }

    // -----------------------------------------------------------------------
    // SVG node
    // -----------------------------------------------------------------------

    @NotNull
    private String buildNode(@NotNull TransactionNode node, int x, int y,
                             boolean dark, boolean isRoot) {
        String[] colors    = colorFor(node, dark);
        String fillColor   = colors[0];
        String strokeColor = colors[1];
        String strokeWidth = node.isWriteInReadOnly() ? "2.5" : isRoot ? "2.5" : "1.5";
        String actualStroke = node.isWriteInReadOnly() ? errorColor(dark)
                : isRoot ? (dark ? "#F5C842" : "#E6A817") : strokeColor;
        String nodeFill    = nodeFill(fillColor, dark);

        String[] parts     = node.getDisplayName().split("#", 2);
        String className   = (parts.length > 1) ? parts[0] : node.getDisplayName();
        String methodName  = (parts.length > 1) ? parts[1] : "";
        String badgeBg     = dark ? "#1E1E1E" : "#FFFFFF";

        StringBuilder sb = new StringBuilder();
        // cursor:pointer + opacity transition for selection dimming
        sb.append(String.format(
                "<g id=\"%s\" class=\"tx-node\" data-x=\"%d\" data-y=\"%d\" " +
                        "transform=\"translate(%d,%d)\" style=\"cursor:pointer;transition:opacity 0.2s;\">%n",
                escapeXml(node.getId()), x, y, x, y));

        // Main background rect — class="node-bg" lets CSS target it for hover/glow
        sb.append(String.format(
                "  <rect class=\"node-bg\" x=\"0\" y=\"0\" width=\"%d\" height=\"%d\" rx=\"8\" ry=\"8\" " +
                        "fill=\"%s\" stroke=\"%s\" stroke-width=\"%s\" style=\"transition:filter 0.15s;\"/>%n",
                NODE_W, NODE_H, nodeFill, actualStroke, strokeWidth));

        // Class name
        sb.append(String.format(
                "  <text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"%s\" " +
                        "text-anchor=\"middle\" font-weight=\"600\">%s</text>%n",
                NODE_W / 2, 20, strokeColor, escapeXml(truncate(className, 22))));

        // Method name
        sb.append(String.format(
                "  <text x=\"%d\" y=\"%d\" font-size=\"12\" fill=\"%s\" " +
                        "text-anchor=\"middle\">%s</text>%n",
                NODE_W / 2, 36, strokeColor, escapeXml(truncate("#" + methodName, 24))));

        // Root star
        if (isRoot) {
            sb.append(String.format(
                    "  <text x=\"%d\" y=\"%d\" font-size=\"9\" fill=\"%s\" " +
                            "text-anchor=\"middle\" opacity=\"0.85\">★</text>%n",
                    NODE_W / 2, 50, dark ? "#F5C842" : "#E6A817"));
        }

        // Hidden propagation text (screen readers / copy-paste)
        sb.append(String.format(
                "  <text x=\"%d\" y=\"%d\" font-size=\"10\" fill=\"%s\" " +
                        "text-anchor=\"middle\" opacity=\"0\">%s</text>%n",
                NODE_W / 2, 52, strokeColor,
                (node.getStatus() == TransactionNode.TxStatus.NON_TX)
                        ? "NON_TX"
                        : node.getPropagation().name() + " · " + node.getStatus().name()));

        // ── Propagation badge ─────────────────────────────────────────────
        String propSymbol = propagationSymbol(node);
        String propLabel = propagationShortLabel(node);
        String propTooltip = escapeXml(propagationTooltip(node));
        String propColor = strokeColor;
        int pillH = 16;
        int pillPad = 5;
        int textW = 8 + 3 + propLabel.length() * 6;
        int pillW = textW + pillPad * 2;
        int pillX = -pillW / 2 + 18;
        int pillY = -(pillH / 2);
        sb.append(String.format(
                "  <g class=\"prop-badge\" " +
                        "onmouseenter=\"showTip(evt,'%s')\" onmouseleave=\"hideTip()\">%n",
                propTooltip));
        sb.append(String.format(
                "    <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"%d\" rx=\"8\" " +
                        "fill=\"%s\" stroke=\"%s\" stroke-width=\"1\" opacity=\"0.92\"/>%n",
                pillX, pillY, pillW, pillH, badgeBg, propColor));
        sb.append(String.format(
                "    <text x=\"%d\" y=\"%d\" font-size=\"9\" fill=\"%s\" " +
                        "font-weight=\"700\" text-anchor=\"start\">%s</text>%n",
                pillX + pillPad, pillY + 11, propColor, escapeXml(propSymbol)));
        sb.append(String.format(
                "    <text x=\"%d\" y=\"%d\" font-size=\"9\" fill=\"%s\" " +
                        "text-anchor=\"start\">%s</text>%n",
                pillX + pillPad + 11, pillY + 11, propColor, escapeXml(propLabel)));
        sb.append("  </g>\n");

        // ── readOnly lock badge ───────────────────────────────────────────
        if (node.isReadOnly()) {
            int bx = NODE_W - 1;
            int by = 1;
            int br = 9;
            int lx = bx - 4;
            int ly = by - 1;
            String lockTooltip = escapeXml(
                    "readOnly = true — this transaction must not write to the database. " +
                            "Spring will throw an exception if a flush is attempted.");

            sb.append(String.format(
                    "  <g class=\"lock-badge\" " +
                            "onmouseenter=\"showTip(evt,'%s')\" onmouseleave=\"hideTip()\">%n",
                    lockTooltip));
            sb.append(String.format(
                    "    <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\" stroke=\"%s\" stroke-width=\"1.2\"/>%n",
                    bx, by, br, badgeBg, strokeColor));
            sb.append(String.format(
                    "    <path d=\"M %d %d a4,3.5 0 0,1 8,0\" fill=\"none\" stroke=\"%s\" stroke-width=\"1.3\" stroke-linecap=\"round\"/>%n",
                    lx, ly, strokeColor));
            sb.append(String.format(
                    "    <rect x=\"%d\" y=\"%d\" width=\"8\" height=\"6\" rx=\"1.2\" fill=\"%s\"/>%n",
                    lx, ly, strokeColor));
            sb.append(String.format(
                    "    <circle cx=\"%d\" cy=\"%d\" r=\"1.3\" fill=\"%s\"/>%n",
                    bx, by + 3, badgeBg));
            sb.append("  </g>\n");
        }

        // ── writeInReadOnly error banner ──────────────────────────────────
        if (node.isWriteInReadOnly()) {
            sb.append(String.format(
                    "  <rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"16\" rx=\"3\" " +
                            "fill=\"%s\" opacity=\"0.85\"/>%n",
                    4, NODE_H - 20, NODE_W - 8, errorColor(dark)));
            sb.append(String.format(
                    "  <text x=\"%d\" y=\"%d\" font-size=\"9\" fill=\"white\" " +
                            "text-anchor=\"middle\">⚠ write in readOnly tx</text>%n",
                    NODE_W / 2, NODE_H - 9));
        }

        sb.append("</g>\n");
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Propagation badge helpers
    // -----------------------------------------------------------------------

    @NotNull
    private static String propagationSymbol(@NotNull TransactionNode node) {
        if (node.getStatus() == TransactionNode.TxStatus.NON_TX) {
            return "∅";
        }
        return switch (node.getPropagation()) {
            case REQUIRED      -> "⟳";
            case REQUIRES_NEW  -> "★";
            case NESTED        -> "⤵";
            case SUPPORTS      -> "≈";
            case NOT_SUPPORTED -> "⊘";
            case NEVER         -> "✕";
            case MANDATORY     -> "!";
        };
    }

    @NotNull
    private static String propagationShortLabel(@NotNull TransactionNode node) {
        if (node.getStatus() == TransactionNode.TxStatus.NON_TX) {
            return "NO TX";
        }
        return switch (node.getPropagation()) {
            case REQUIRED      -> "REQUIRED";
            case REQUIRES_NEW  -> "NEW TX";
            case NESTED        -> "NESTED";
            case SUPPORTS      -> "SUPPORTS";
            case NOT_SUPPORTED -> "NO SUPP";
            case NEVER         -> "NEVER";
            case MANDATORY     -> "MANDATORY";
        };
    }

    @NotNull
    private static String propagationTooltip(@NotNull TransactionNode node) {
        if (node.getStatus() == TransactionNode.TxStatus.NON_TX) {
            return "NON_TX — this method has no @Transactional annotation and runs outside any transaction boundary.";
        }
        String status = " [" + node.getStatus().name() + "]";
        return switch (node.getPropagation()) {
            case REQUIRED ->
                    "REQUIRED" + status + " — joins the caller's transaction if one exists, otherwise starts a new one. The Spring default.";
            case REQUIRES_NEW ->
                    "REQUIRES_NEW" + status + " — always starts a fresh, independent transaction. The caller's transaction is suspended until this one completes.";
            case NESTED ->
                    "NESTED" + status + " — runs inside the caller's transaction as a savepoint. Can roll back independently without affecting the outer transaction.";
            case SUPPORTS ->
                    "SUPPORTS" + status + " — participates in the caller's transaction if present; runs non-transactionally if there is none.";
            case NOT_SUPPORTED ->
                    "NOT_SUPPORTED" + status + " — always runs non-transactionally. Suspends the caller's transaction if one exists.";
            case NEVER ->
                    "NEVER" + status + " — must not be called inside a transaction. Throws an exception if a transaction is active.";
            case MANDATORY ->
                    "MANDATORY" + status + " — requires an existing transaction. Throws an exception if no transaction is active.";
        };
    }

    // -----------------------------------------------------------------------
    // SVG marker definition
    // -----------------------------------------------------------------------

    @NotNull
    private String buildMarkerDef(@NotNull String id, @NotNull String color) {
        return String.format(
                "  <marker id=\"%s\" markerWidth=\"8\" markerHeight=\"6\" " +
                        "refX=\"8\" refY=\"3\" orient=\"auto\" markerUnits=\"strokeWidth\">\n" +
                        "    <polygon points=\"0,0 8,3 0,6\" fill=\"%s\"/>\n" +
                        "  </marker>\n", id, color);
    }

    // -----------------------------------------------------------------------
    // SVG edges
    // -----------------------------------------------------------------------

    @NotNull
    private String buildEdgeGradientDef(@NotNull TransactionGraph.Edge edge, boolean dark) {
        String gradId = "eg-" + safeId(edge.fromId()) + "-" + safeId(edge.toId());
        String color;
        if (edge.proxyBypass()) {
            color = errorColor(dark);
        } else if (edge.suspension()) {
            color = dark ? "#F5C842" : "#E6A817";
        } else if (edge.nested()) {
            color = dark ? "#26C4B3" : "#1A9E8F";
        } else {
            color = dark ? "#AAAAAA" : "#8C8C8C";
        }

        return String.format(
                "  <linearGradient id=\"%s\" gradientUnits=\"userSpaceOnUse\" " +
                        "x1=\"0\" y1=\"0\" x2=\"1\" y2=\"0\">\n" +
                        "    <stop offset=\"0%%\"   stop-color=\"%s\" stop-opacity=\"0.35\"/>\n" +
                        "    <stop offset=\"100%%\" stop-color=\"%s\" stop-opacity=\"1.0\"/>\n" +
                        "  </linearGradient>\n",
                gradId, color, color);
    }

    @NotNull
    private static String safeId(@NotNull String raw) {
        return raw.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    @NotNull
    private String buildEdge(@NotNull TransactionGraph.Edge edge,
                             int[] from, int[] to, boolean dark) {
        int sx = from[0] + NODE_W / 2;
        int sy = from[1] + NODE_H / 2;
        int tx = to[0]   + NODE_W / 2;
        int ty = to[1]   + NODE_H / 2;
        int[] conn = edgeConnection(sx, sy, tx, ty, from[0], from[1], to[0], to[1]);
        int x1 = conn[0];
        int y1 = conn[1];
        int x2 = conn[2];
        int y2 = conn[3];
        int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;

        String color;
        String dashArray;
        String tooltip;
        String markerEnd;
        if (edge.proxyBypass()) {
            color     = errorColor(dark);
            dashArray = "stroke-dasharray=\"6 4\"";
            tooltip   = "⚠ Proxy bypass: this.method() skips the Spring proxy — @Transactional is ignored";
            markerEnd = "url(#m-red)";
        } else if (edge.suspension()) {
            color     = dark ? "#F5C842" : "#E6A817";
            dashArray = "stroke-dasharray=\"8 2\"";
            tooltip   = "Suspends caller's transaction (REQUIRES_NEW)";
            markerEnd = "url(#m-amber)";
        } else if (edge.nested()) {
            color     = dark ? "#26C4B3" : "#1A9E8F";
            dashArray = "stroke-dasharray=\"3 3\"";
            tooltip   = "Nested savepoint inside caller's transaction (NESTED)";
            markerEnd = "url(#m-teal)";
        } else {
            color     = dark ? "#AAAAAA" : "#8C8C8C";
            dashArray = "";
            tooltip   = "Joins or starts transaction (REQUIRED)";
            markerEnd = "url(#m-gray)";
        }

        String gradId     = "eg-" + safeId(edge.fromId()) + "-" + safeId(edge.toId());
        String gradCoords = String.format("x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\"", x1, y1, x2, y2);

        StringBuilder sb = new StringBuilder();
        // Give the edge group an id so selection JS can set opacity on it directly
        sb.append(String.format("<g id=\"eg-grp-%s-%s\" style=\"transition:opacity 0.2s;\">%n",
                safeId(edge.fromId()), safeId(edge.toId())));
        sb.append(String.format(
                "  <defs><linearGradient id=\"%s-r\" gradientUnits=\"userSpaceOnUse\" %s>" +
                        "<stop offset=\"0%%\" stop-color=\"%s\" stop-opacity=\"0.3\"/>" +
                        "<stop offset=\"100%%\" stop-color=\"%s\" stop-opacity=\"1\"/>" +
                        "</linearGradient></defs>%n",
                gradId, gradCoords, color, color));
        sb.append(String.format("  <title>%s</title>%n", escapeXml(tooltip)));
        sb.append(String.format(
                "  <path id=\"ep-%s-%s\" data-from=\"%s\" data-to=\"%s\"" +
                        " d=\"M %d %d Q %d %d %d %d\" fill=\"none\" stroke=\"url(#%s-r)\" " +
                        "stroke-width=\"2\" %s marker-end=\"%s\"/>%n",
                edge.fromId(), edge.toId(), edge.fromId(), edge.toId(),
                x1, y1, cx, cy, x2, y2, gradId, dashArray, markerEnd));

        String label = (edge.statusLabel() != null) ? edge.statusLabel() : "";
        if (!label.isEmpty()) {
            sb.append(String.format(
                    "  <text id=\"lb-%s-%s\" x=\"%d\" y=\"%d\" font-size=\"9\" fill=\"%s\"" +
                            " text-anchor=\"middle\" dominant-baseline=\"middle\" opacity=\"0.85\">%s</text>%n",
                    escapeXml(edge.fromId()), escapeXml(edge.toId()),
                    cx, cy - 4, dark ? "#BBBBBB" : "#666666",
                    escapeXml(label)));
        }
        sb.append("</g>\n");
        return sb.toString();
    }

    @NotNull
    private static int[] edgeConnection(int sx, int sy, int tx, int ty,
                                        int fx, int fy, int tox, int toy) {
        double dx = tx - sx;
        double dy = ty - sy;
        if (Math.abs(dy) > Math.abs(dx)) {
            if (dy > 0) {
                return new int[]{sx, fy + NODE_H, tx, toy};
            }
            return new int[]{sx, fy, tx, toy + NODE_H};
        } else {
            if (dx > 0) {
                return new int[]{fx + NODE_W, sy, tox, ty};
            }
            return new int[]{fx, sy, tox + NODE_W, ty};
        }
    }

    // -----------------------------------------------------------------------
    // Full HTML page
    // -----------------------------------------------------------------------

    @NotNull
    private String buildHtmlPage(@NotNull String svgContent,
                                 @NotNull TransactionGraph graph,
                                 boolean dark) {
        // Theme-sensitive CSS values
        String ttBg     = dark ? "#3C3C3C"               : "#FFFFCC";
        String ttFg     = dark ? "#EEEEEE"               : "#333333";
        String ttBorder = dark ? "#666666"               : "#AAAAAA";
        String mmBg     = dark ? "rgba(22,24,32,0.88)"   : "rgba(245,245,250,0.88)";
        String mmBorder = dark ? "#444"                  : "#CCC";
        String hintFg   = dark ? "rgba(180,180,180,0.5)" : "rgba(80,80,80,0.5)";
        String zPctFg   = dark ? "#AAAAAA"               : "#666666";
        String bodyBg   = dark ? "#1e1e1e"               : "#ffffff";
        String bodyFg   = dark ? "#eeeeee"               : "#333333";

        String bannerHtml = "";
        if (graph.isTruncated()) {
            bannerHtml = "<div id=\"truncation-banner\" style=\"" +
                    "background:#F5C842;color:#333;padding:6px 12px;font-size:12px;" +
                    "font-family:monospace;border-bottom:1px solid #E6A817;\">" +
                    "⚠ Graph truncated — showing direct callers and callees only " +
                    "(50-node limit)</div>";
        }

        String emptyStateHtml = "";
        if (!graph.hasTransactionalBoundary()) {
            emptyStateHtml = "<div id=\"empty-state\" style=\"" +
                    "position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);" +
                    "text-align:center;font-size:13px;font-family:monospace;color:#888;\">" +
                    "No transaction boundaries found in the reachable call graph.</div>";
        }

        return "<!DOCTYPE html><html><head>" +
                "<meta charset=\"UTF-8\">" +
                "<style>" +

                // ── Base ───────────────────────────────────────────────────
                "body{margin:0;padding:0;overflow:hidden;background:" + bodyBg + ";color:" + bodyFg + ";}" +
                "#refresh-banner{display:none;background:#4A90D9;color:white;" +
                "  padding:6px 12px;font-size:12px;font-family:monospace;" +
                "  cursor:pointer;border-bottom:1px solid #357ABD;}" +
                "#spinner{display:none;position:absolute;top:50%;left:50%;" +
                "  transform:translate(-50%,-50%);font-size:14px;font-family:monospace;}" +
                "@keyframes spin{to{transform:rotate(360deg);}}" +
                ".spinner-icon{display:inline-block;animation:spin 1s linear infinite;}" +

                // ── Viewport & zoom layer ──────────────────────────────────
                "#viewport{width:100%;height:100vh;cursor:grab;}" +
                "#viewport.panning{cursor:grabbing!important;}" +
                // 'sm' class is added momentarily on button/keyboard zooms for a
                // smooth animation; wheel and drag zoom omit it to stay responsive.
                "#zoom-layer{transform-origin:0 0;}" +
                "#zoom-layer.sm{transition:transform 0.16s cubic-bezier(.25,.46,.45,.94);}" +

                // ── Zoom bar ───────────────────────────────────────────────
                "#zoom-bar{position:absolute;right:12px;top:12px;" +
                "  display:flex;flex-direction:column;align-items:center;gap:4px;z-index:10;}" +
                "#zoom-bar button{width:28px;height:28px;border:1px solid #555;" +
                "  border-radius:5px;background:rgba(0,0,0,0.45);color:#ccc;" +
                "  font:bold 16px monospace;cursor:pointer;line-height:1;}" +
                "#zoom-bar button:hover{background:rgba(0,0,0,0.7);color:#fff;}" +
                "#zoom-pct{font-size:10px;font-family:monospace;color:" + zPctFg + ";user-select:none;}" +

                // ── Tooltip ────────────────────────────────────────────────
                "#svg-tooltip{display:none;position:fixed;z-index:100;" +
                "  max-width:320px;padding:6px 10px;" +
                "  background:" + ttBg + ";color:" + ttFg + ";border:1px solid " + ttBorder + ";" +
                "  border-radius:5px;font-size:11px;font-family:monospace;" +
                "  line-height:1.5;pointer-events:none;" +
                "  box-shadow:0 2px 8px rgba(0,0,0,0.28);}" +

                // ── Node styles ────────────────────────────────────────────
                ".tx-node{cursor:pointer;}" +
                ".tx-node text{pointer-events:none;}" +
                // brightness hover on the background rect only
                ".tx-node:hover .node-bg{filter:brightness(1.18) saturate(1.12);}" +
                ".prop-badge,.lock-badge{cursor:default;}" +

                // ── Minimap ────────────────────────────────────────────────
                "#minimap{position:absolute;bottom:12px;right:12px;z-index:10;" +
                "  border:1px solid " + mmBorder + ";border-radius:6px;overflow:hidden;" +
                "  cursor:crosshair;box-shadow:0 2px 10px rgba(0,0,0,0.3);" +
                "  background:" + mmBg + ";}" +
                "#mm-canvas{display:block;}" +

                // ── Keyboard hint ──────────────────────────────────────────
                "#key-hint{position:absolute;bottom:12px;left:12px;" +
                "  font-size:10px;font-family:monospace;color:" + hintFg + ";" +
                "  pointer-events:none;line-height:1.9;user-select:none;}" +

                "</style>" +
                "</head><body>" +

                "<div id=\"svg-tooltip\"></div>" +
                "<div id=\"refresh-banner\" onclick=\"location.reload()\">" +
                "  ⟳ Code changed — click to refresh</div>" +
                "<div id=\"viewport\">" +
                bannerHtml +
                "<div id=\"zoom-layer\">" +
                svgContent +
                "</div>" +
                "</div>" +
                "<div id=\"spinner\"><span class=\"spinner-icon\">⟳</span> Loading graph…</div>" +

                // Zoom bar — includes percentage label
                "<div id=\"zoom-bar\">" +
                "  <button onclick=\"zoomIn()\"  title=\"Zoom in (+)\">+</button>" +
                "  <button onclick=\"zoomOut()\" title=\"Zoom out (−)\">−</button>" +
                "  <button onclick=\"fitAll()\"  title=\"Fit all (F)\" style=\"font-size:11px\">⎚</button>" +
                "  <button onclick=\"resetView()\" title=\"Reset view (R)\" style=\"font-size:13px\">⌂</button>" +
                "  <span id=\"zoom-pct\">100%</span>" +
                "</div>" +

                // Minimap canvas
                "<div id=\"minimap\"><canvas id=\"mm-canvas\" width=\"150\" height=\"95\"></canvas></div>" +

                // Keyboard hint strip
                "<div id=\"key-hint\">" +
                "F fit · R reset · ± zoom<br>" +
                "↑↓←→ pan · Esc deselect<br>" +
                "dbl-click navigate" +
                "</div>" +

                emptyStateHtml +

                "<script>" +

                // ── GLOBALS ────────────────────────────────────────────────
                "var zl=document.getElementById('zoom-layer');" +
                "var vp=document.getElementById('viewport');" +
                "var W=" + NODE_W + ",H=" + NODE_H + ";" +
                // FIX: initialise so dragging and zoom work before fitAll() fires
                "var s=1,zx=0,zy=0;" +
                "var selId=null;" +
                "var mmOffX=0,mmOffY=0,mmSc=1;" +

                // ── APPLY ──────────────────────────────────────────────────
                "function apply(){" +
                "  zl.style.transform='translate('+zx+'px,'+zy+'px) scale('+s+')';" +
                "  var pctEl=document.getElementById('zoom-pct');" +
                "  if(pctEl)pctEl.textContent=Math.round(s*100)+'%';" +
                "  updateMinimap();" +
                "}" +
                // smoothApply wraps apply with a short CSS transition; used only for
                // button clicks and keyboard shortcuts — NOT for wheel or drag events
                // so those remain perfectly responsive.
                "function smoothApply(){" +
                "  zl.classList.add('sm');" +
                "  apply();" +
                "  setTimeout(function(){zl.classList.remove('sm');},160);" +
                "}" +

                // ── ZOOM CONTROLS ──────────────────────────────────────────
                "function zoomIn(){" +
                "  var ns=Math.min(6,s+0.15),r=ns/s;" +
                "  var vw=vp.clientWidth/2,vh=vp.clientHeight/2;" +
                "  zx=vw-r*(vw-zx);zy=vh-r*(vh-zy);s=ns;smoothApply();" +
                "}" +
                "function zoomOut(){" +
                "  var ns=Math.max(0.15,s-0.15),r=ns/s;" +
                "  var vw=vp.clientWidth/2,vh=vp.clientHeight/2;" +
                "  zx=vw-r*(vw-zx);zy=vh-r*(vh-zy);s=ns;smoothApply();" +
                "}" +
                "function resetView(){s=1;zx=0;zy=0;smoothApply();}" +
                "function fitAll(){" +
                "  var nodes=document.querySelectorAll('.tx-node');" +
                "  if(!nodes.length)return;" +
                "  var minX=1e9,minY=1e9,maxX=-1e9,maxY=-1e9,pad=60;" +
                "  for(var i=0;i<nodes.length;i++){" +
                "    var p=pos(nodes[i]);" +
                "    minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);" +
                "    maxX=Math.max(maxX,p.x+W);maxY=Math.max(maxY,p.y+H);" +
                "  }" +
                "  var bw=maxX-minX,bh=maxY-minY;" +
                "  var sc=Math.min(vp.clientWidth/(bw+pad*2),vp.clientHeight/(bh+pad*2),2);" +
                "  s=sc;" +
                "  zx=(vp.clientWidth -bw*sc)/2-minX*sc;" +
                "  zy=(vp.clientHeight-bh*sc)/2-minY*sc;" +
                "  smoothApply();" +
                "}" +

                // ── NODE SELECTION ─────────────────────────────────────────
                // Highlights the clicked node (glow filter) and its direct
                // neighbours; dims all other nodes and edges.
                "function selectNode(nid){" +
                "  selId=nid;" +
                "  var allNodes=document.querySelectorAll('.tx-node');" +
                "  var allPaths=document.querySelectorAll('path[data-from]');" +
                "  if(!nid){" +
                // Clear everything
                "    for(var i=0;i<allNodes.length;i++){allNodes[i].style.opacity='';}" +
                "    for(var i=0;i<allPaths.length;i++){allPaths[i].parentNode.style.opacity='';}" +
                "    var bgs=document.querySelectorAll('.node-bg');" +
                "    for(var i=0;i<bgs.length;i++){bgs[i].style.filter='';}" +
                "    updateMinimap();return;" +
                "  }" +
                // Collect the selected node + its direct neighbours
                "  var conn=new Set([nid]);" +
                "  for(var i=0;i<allPaths.length;i++){" +
                "    var fr=allPaths[i].getAttribute('data-from');" +
                "    var to=allPaths[i].getAttribute('data-to');" +
                "    if(fr===nid||to===nid){conn.add(fr);conn.add(to);}" +
                "  }" +
                // Dim non-connected nodes; glow the selected one
                "  for(var i=0;i<allNodes.length;i++){" +
                "    var n=allNodes[i];" +
                "    n.style.opacity=conn.has(n.id)?'1':'0.15';" +
                "    var bg=n.querySelector('.node-bg');" +
                "    if(bg)bg.style.filter=(n.id===nid)?'url(#f-glow)':'';" +
                "  }" +
                // Dim non-adjacent edges
                "  for(var i=0;i<allPaths.length;i++){" +
                "    var fr=allPaths[i].getAttribute('data-from');" +
                "    var to=allPaths[i].getAttribute('data-to');" +
                "    var grp=allPaths[i].parentNode;" +
                "    if(grp)grp.style.opacity=((fr===nid||to===nid)?'1':'0.07');" +
                "  }" +
                "  updateMinimap();" +
                "}" +

                // ── TOOLTIP ────────────────────────────────────────────────
                "var _tip=document.getElementById('svg-tooltip');" +
                "function showTip(evt,text){" +
                "  _tip.textContent=text;" +
                "  _tip.style.display='block';" +
                "  _moveTip(evt);" +
                "  evt.currentTarget.addEventListener('mousemove',_moveTip);" +
                "}" +
                "function hideTip(){_tip.style.display='none';}" +
                "function _moveTip(evt){" +
                "  var x=evt.clientX,y=evt.clientY;" +
                "  var tw=_tip.offsetWidth,th=_tip.offsetHeight;" +
                "  var vw=window.innerWidth,vh=window.innerHeight;" +
                "  _tip.style.left=((x+14+tw>vw)?(x-14-tw):(x+14))+'px';" +
                "  _tip.style.top= ((y+14+th>vh)?(y-14-th):(y+14))+'px';" +
                "}" +

                // ── WHEEL ZOOM ─────────────────────────────────────────────
                "vp.addEventListener('wheel',function(e){" +
                "  e.preventDefault();" +
                "  var ns=Math.max(0.15,Math.min(6,s-(e.deltaY>0?0.1:-0.1)));" +
                "  var r=ns/s;" +
                "  zx=e.clientX-r*(e.clientX-zx);zy=e.clientY-r*(e.clientY-zy);" +
                "  s=ns;apply();" +   // no smoothApply — wheel must stay instant
                "},{passive:false});" +

                // ── BACKGROUND PAN ─────────────────────────────────────────
                "var pan=false,ppx=0,ppy=0,pox=0,poy=0;" +
                "vp.addEventListener('mousedown',function(e){" +
                "  if(e.target.closest('.tx-node'))return;" +
                "  if(e.button!==0)return;" +
                "  selectNode(null);" +   // click on empty background = deselect
                "  pan=true;ppx=e.clientX;ppy=e.clientY;pox=zx;poy=zy;" +
                "  vp.classList.add('panning');" +
                "});" +
                "window.addEventListener('mousemove',function(e){" +
                "  if(pan){zx=pox+e.clientX-ppx;zy=poy+e.clientY-ppy;apply();}" +
                "});" +
                "window.addEventListener('mouseup',function(){" +
                "  if(pan){pan=false;vp.classList.remove('panning');}" +
                "});" +

                // ── NODE DRAG ──────────────────────────────────────────────
                "var dn=null,dsx=0,dsy=0,dex=0,dey=0,didDrag=false;" +
                "var lastClickTime=0,lastClickId='';" +
                "vp.addEventListener('mousedown',function(e){" +
                "  dn=e.target.closest('.tx-node');" +
                "  if(!dn||e.button!==0)return;" +
                "  e.preventDefault();e.stopPropagation();" +
                "  var t=dn.transform.baseVal.consolidate();" +
                "  dex=t?t.matrix.e:parseFloat(dn.getAttribute('data-x'));" +
                "  dey=t?t.matrix.f:parseFloat(dn.getAttribute('data-y'));" +
                "  dsx=e.clientX;dsy=e.clientY;didDrag=false;" +
                "},true);" +
                "window.addEventListener('mousemove',function(e){" +
                "  if(!dn)return;" +
                "  var dx=e.clientX-dsx,dy=e.clientY-dsy;" +
                "  if(Math.abs(dx)>3||Math.abs(dy)>3)didDrag=true;" +
                "  if(didDrag){" +
                "    var nx=dex+dx/s,ny=dey+dy/s;" +
                "    dn.setAttribute('transform','translate('+nx+','+ny+')');" +
                "    dn.setAttribute('data-curx',nx);dn.setAttribute('data-cury',ny);" +
                "    redrawEdges(dn.id);" +
                "  }" +
                "});" +
                "window.addEventListener('mouseup',function(e){" +
                "  if(!dn)return;" +
                "  if(!didDrag){" +
                // Use a 300 ms double-click window for navigate; single-click = select toggle
                "    var now=Date.now();" +
                "    if(now-lastClickTime<300&&lastClickId===dn.id){" +
                "      window.intellijNavigateNode&&window.intellijNavigateNode(dn.id);" +
                "      lastClickTime=0;lastClickId='';" +
                "    }else{" +
                "      selectNode(selId===dn.id?null:dn.id);" +
                "      lastClickTime=now;lastClickId=dn.id;" +
                "    }" +
                "  }" +
                "  dn=null;" +
                "});" +

                // ── KEYBOARD ──────────────────────────────────────────────
                "window.addEventListener('keydown',function(e){" +
                "  if(e.target.tagName==='INPUT'||e.target.tagName==='TEXTAREA')return;" +
                "  switch(e.key){" +
                "    case 'f':case 'F':fitAll();break;" +
                "    case 'r':case 'R':resetView();break;" +
                "    case '+':case '=':zoomIn();break;" +
                "    case '-':case '_':zoomOut();break;" +
                "    case 'Escape':selectNode(null);break;" +
                "    case 'ArrowLeft': zx+=50;apply();e.preventDefault();break;" +
                "    case 'ArrowRight':zx-=50;apply();e.preventDefault();break;" +
                "    case 'ArrowUp':   zy+=50;apply();e.preventDefault();break;" +
                "    case 'ArrowDown': zy-=50;apply();e.preventDefault();break;" +
                "  }" +
                "});" +

                // ── TOUCH ──────────────────────────────────────────────────
                // Single finger → pan; two fingers → pinch-to-zoom.
                "var lastTouches=null;" +
                "vp.addEventListener('touchstart',function(e){" +
                "  if(e.touches.length===1){" +
                "    pan=true;" +
                "    ppx=e.touches[0].clientX;ppy=e.touches[0].clientY;" +
                "    pox=zx;poy=zy;" +
                "  }" +
                "  lastTouches=Array.from(e.touches).map(function(t){" +
                "    return{clientX:t.clientX,clientY:t.clientY};" +
                "  });" +
                "},{passive:false});" +
                "vp.addEventListener('touchmove',function(e){" +
                "  e.preventDefault();" +
                "  if(e.touches.length===1&&pan){" +
                "    zx=pox+e.touches[0].clientX-ppx;" +
                "    zy=poy+e.touches[0].clientY-ppy;" +
                "    apply();" +
                "  }else if(e.touches.length===2&&lastTouches&&lastTouches.length>=2){" +
                "    var d1=Math.hypot(e.touches[0].clientX-e.touches[1].clientX," +
                "                      e.touches[0].clientY-e.touches[1].clientY);" +
                "    var d0=Math.hypot(lastTouches[0].clientX-lastTouches[1].clientX," +
                "                      lastTouches[0].clientY-lastTouches[1].clientY);" +
                "    if(d0>0){" +
                "      var ratio=d1/d0;" +
                "      var cx=(e.touches[0].clientX+e.touches[1].clientX)/2;" +
                "      var cy=(e.touches[0].clientY+e.touches[1].clientY)/2;" +
                "      var ns=Math.max(0.15,Math.min(6,s*ratio));" +
                "      var r=ns/s;zx=cx-r*(cx-zx);zy=cy-r*(cy-zy);s=ns;apply();" +
                "    }" +
                "  }" +
                "  lastTouches=Array.from(e.touches).map(function(t){" +
                "    return{clientX:t.clientX,clientY:t.clientY};" +
                "  });" +
                "},{passive:false});" +
                "vp.addEventListener('touchend',function(e){" +
                "  pan=false;" +
                "  lastTouches=Array.from(e.touches).map(function(t){" +
                "    return{clientX:t.clientX,clientY:t.clientY};" +
                "  });" +
                "});" +

                // ── MINIMAP ────────────────────────────────────────────────
                // Redrawn on every apply() call: shows scaled node dots, edges
                // as grey lines, selected node in blue, and a yellow viewport rect.
                // Click on the canvas to pan the main viewport to that location.
                "var mmCv=document.getElementById('mm-canvas');" +
                "var MMW=150,MMH=95;" +
                "function updateMinimap(){" +
                "  if(!mmCv)return;" +
                "  var ctx=mmCv.getContext('2d');" +
                "  ctx.clearRect(0,0,MMW,MMH);" +
                "  var nodes=document.querySelectorAll('.tx-node');" +
                "  if(!nodes.length)return;" +
                "  var minX=1e9,minY=1e9,maxX=-1e9,maxY=-1e9,pad=8;" +
                "  for(var i=0;i<nodes.length;i++){" +
                "    var p=pos(nodes[i]);" +
                "    minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);" +
                "    maxX=Math.max(maxX,p.x+W);maxY=Math.max(maxY,p.y+H);" +
                "  }" +
                "  var gw=maxX-minX,gh=maxY-minY;" +
                "  if(!gw||!gh)return;" +
                "  mmSc=Math.min((MMW-pad*2)/gw,(MMH-pad*2)/gh);" +
                "  mmOffX=pad+(MMW-pad*2-gw*mmSc)/2-minX*mmSc;" +
                "  mmOffY=pad+(MMH-pad*2-gh*mmSc)/2-minY*mmSc;" +
                // Edges
                "  ctx.strokeStyle='rgba(140,140,140,0.35)';ctx.lineWidth=0.7;" +
                "  var paths=document.querySelectorAll('path[data-from]');" +
                "  for(var i=0;i<paths.length;i++){" +
                "    var fn2=document.getElementById(paths[i].getAttribute('data-from'));" +
                "    var tn2=document.getElementById(paths[i].getAttribute('data-to'));" +
                "    if(!fn2||!tn2)continue;" +
                "    var fp=pos(fn2),tp=pos(tn2);" +
                "    ctx.beginPath();" +
                "    ctx.moveTo(fp.x*mmSc+mmOffX+(W/2)*mmSc,fp.y*mmSc+mmOffY+(H/2)*mmSc);" +
                "    ctx.lineTo(tp.x*mmSc+mmOffX+(W/2)*mmSc,tp.y*mmSc+mmOffY+(H/2)*mmSc);" +
                "    ctx.stroke();" +
                "  }" +
                // Nodes
                "  for(var i=0;i<nodes.length;i++){" +
                "    var nd=nodes[i],p2=pos(nd);" +
                "    ctx.fillStyle=(nd.id===selId)" +
                "      ?'rgba(90,163,245,0.95)':'rgba(110,125,155,0.65)';" +
                "    ctx.fillRect(p2.x*mmSc+mmOffX,p2.y*mmSc+mmOffY," +
                "      Math.max(W*mmSc,4),Math.max(H*mmSc,3));" +
                "  }" +
                // Viewport rect
                "  var vx0=(-zx)/s,vy0=(-zy)/s;" +
                "  var vx1=vx0+vp.clientWidth/s,vy1=vy0+vp.clientHeight/s;" +
                "  ctx.strokeStyle='rgba(255,200,50,0.9)';ctx.lineWidth=1.5;" +
                "  ctx.strokeRect(vx0*mmSc+mmOffX,vy0*mmSc+mmOffY," +
                "    (vx1-vx0)*mmSc,(vy1-vy0)*mmSc);" +
                "}" +
                // Minimap click → pan to that graph location
                "if(mmCv){" +
                "  mmCv.addEventListener('click',function(e){" +
                "    var r=mmCv.getBoundingClientRect();" +
                "    var gx=(e.clientX-r.left-mmOffX)/mmSc;" +
                "    var gy=(e.clientY-r.top -mmOffY)/mmSc;" +
                "    zx=vp.clientWidth /2-gx*s;" +
                "    zy=vp.clientHeight/2-gy*s;" +
                "    apply();" +
                "  });" +
                "}" +

                // ── EDGE REDRAW ON NODE DRAG ───────────────────────────────
                // orient="auto" handles arrowhead rotation; we only update geometry.
                "function redrawEdges(nid){" +
                "  var svg=document.querySelector('svg');" +
                "  var paths=svg.querySelectorAll('path[data-from],path[data-to]');" +
                "  for(var i=0;i<paths.length;i++){" +
                "    var p=paths[i];" +
                "    if(p.getAttribute('data-from')!==nid&&p.getAttribute('data-to')!==nid)continue;" +
                "    var fn=svg.getElementById(p.getAttribute('data-from'));" +
                "    var tn=svg.getElementById(p.getAttribute('data-to'));" +
                "    if(!fn||!tn)continue;" +
                "    var fp=pos(fn),tp=pos(tn);" +
                "    var sx=fp.x+W/2,sy=fp.y+H/2,tx=tp.x+W/2,ty=tp.y+H/2;" +
                "    var dx=tx-sx,dy=ty-sy,x1,y1,x2,y2;" +
                "    if(Math.abs(dy)>Math.abs(dx)){" +
                "      if(dy>0){x1=sx;y1=fp.y+H;x2=tx;y2=tp.y;}" +
                "      else    {x1=sx;y1=fp.y;  x2=tx;y2=tp.y+H;}" +
                "    }else{" +
                "      if(dx>0){x1=fp.x+W;y1=sy;x2=tp.x;  y2=ty;}" +
                "      else    {x1=fp.x;  y1=sy;x2=tp.x+W;y2=ty;}" +
                "    }" +
                "    var cx=(x1+x2)/2,cy=(y1+y2)/2;" +
                "    p.setAttribute('d','M'+x1+' '+y1+' Q'+cx+' '+cy+' '+x2+' '+y2);" +
                "    var lbl=p.parentNode.querySelector('text');" +
                "    if(lbl){lbl.setAttribute('x',cx);lbl.setAttribute('y',cy-4);}" +
                "    var lg=p.parentNode.querySelector('linearGradient');" +
                "    if(lg){lg.setAttribute('x1',x1);lg.setAttribute('y1',y1);" +
                "           lg.setAttribute('x2',x2);lg.setAttribute('y2',y2);}" +
                "  }" +
                "}" +

                // ── POS HELPER ─────────────────────────────────────────────
                "function pos(nd){" +
                "  var cx=parseFloat(nd.dataset.curx),cy=parseFloat(nd.dataset.cury);" +
                "  if(!isNaN(cx))return{x:cx,y:cy};" +
                "  var t=nd.transform.baseVal.consolidate();" +
                "  return{x:t?t.matrix.e:parseFloat(nd.dataset.x)," +
                "          y:t?t.matrix.f:parseFloat(nd.dataset.y)};" +
                "}" +

                // ── INIT ───────────────────────────────────────────────────
                // Auto-fit the graph the moment the first frame is ready.
                "requestAnimationFrame(function(){fitAll();});" +

                // ── REFRESH BANNER (callable from IDE side) ────────────────
                "function showRefreshBanner(){" +
                "  var b=document.getElementById('refresh-banner');" +
                "  if(b)b.style.display='block';" +
                "}" +

                "</script>" +
                "</body></html>";
    }

    // -----------------------------------------------------------------------
    // Color helpers
    // -----------------------------------------------------------------------

    @NotNull
    private String[] colorFor(@NotNull TransactionNode node, boolean dark) {
        if (node.getStatus() == TransactionNode.TxStatus.NON_TX) {
            int idx = dark ? 1 : 0;
            return new String[]{NON_TX_COLOR[idx], NON_TX_COLOR[idx]};
        }
        String[] pair  = PROP_COLORS.getOrDefault(node.getPropagation(), NON_TX_COLOR);
        String color   = dark ? pair[1] : pair[0];
        return new String[]{color, color};
    }

    @NotNull
    private String colorPair(@NotNull TransactionNode.Propagation p, boolean dark) {
        String[] pair = PROP_COLORS.getOrDefault(p, NON_TX_COLOR);
        return dark ? pair[1] : pair[0];
    }

    @NotNull
    private String errorColor(boolean dark) {
        return dark ? ERROR_COLOR[1] : ERROR_COLOR[0];
    }

    @NotNull
    private static String nodeFill(@NotNull String hex, boolean dark) {
        return mix(hex, dark ? "#1e1e1e" : "#ffffff", dark ? 0.28d : 0.16d);
    }

    @NotNull
    private static String mix(@NotNull String foreground, @NotNull String background, double foregroundWeight) {
        int[] fg = rgb(foreground);
        int[] bg = rgb(background);
        int r = (int) Math.round(fg[0] * foregroundWeight + bg[0] * (1d - foregroundWeight));
        int g = (int) Math.round(fg[1] * foregroundWeight + bg[1] * (1d - foregroundWeight));
        int b = (int) Math.round(fg[2] * foregroundWeight + bg[2] * (1d - foregroundWeight));
        return String.format("#%02X%02X%02X", r, g, b);
    }

    @NotNull
    private static int[] rgb(@NotNull String hex) {
        hex = hex.startsWith("#") ? hex.substring(1) : hex;
        return new int[] {
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
        };
    }

    @NotNull
    private static String escapeXml(@NotNull String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    @NotNull
    private static String truncate(@NotNull String s, int maxLen) {
        return (s.length() <= maxLen) ? s : s.substring(0, maxLen - 1) + "…";
    }
}
