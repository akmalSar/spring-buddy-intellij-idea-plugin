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

import java.util.*;

import org.jetbrains.annotations.NotNull;

/**
 * Generates a self-contained HTML page (string) containing a vertical
 * ghost-save flow SVG suitable for {@code JBCefBrowser}.
 *
 * <p>Ported from the fixed transaction-graph renderer:
 * <ul>
 *   <li>Large SVG with {@code overflow:visible} so dragged nodes never
 *       disappear.</li>
 *   <li>Per-edge {@code <linearGradient>} + {@code <marker orient="auto">}
 *       arrowheads so the flow direction is visually obvious.</li>
 *   <li>Edges update their geometry <b>and</b> gradient coordinates in
 *       {@code redrawEdges()} during node drag.</li>
 *   <li>Background pan, wheel zoom, and zoom buttons.</li>
 * </ul>
 *
 * @author Akmal Sarhan
 */
public final class GhostSaveFlowSvgRenderer {

    private static final int NODE_W = 300;
    private static final int NODE_H = 70;
    private static final int V_GAP  = 36;
    private static final int H_PAD  = 80;
    private static final int V_PAD  = 60;

    @NotNull
    public String renderHtml(@NotNull GhostSaveFlow flow, boolean darkTheme) {
        String svg = renderSvg(flow, darkTheme);
        return buildHtmlPage(svg, darkTheme);
    }

    @NotNull
    private String renderSvg(@NotNull GhostSaveFlow flow, boolean darkTheme) {
        Map<String, int[]> pos = layout(flow);

        // generous canvas so nothing clips ever
        int maxX = pos.values().stream().mapToInt((int[] p) -> p[0]).max().orElse(0) + NODE_W + H_PAD;
        int maxY = pos.values().stream().mapToInt((int[] p) -> p[1]).max().orElse(0) + NODE_H + V_PAD;
        int svgW = Math.max(3000, maxX + 1000);
        int svgH = Math.max(3000, maxY + 1000);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" "
                        + "style=\"font-family:monospace;background:transparent;overflow:visible;\">%n",
                svgW, svgH));

        // MARKER definitions (shared)
        sb.append("<defs>\n");
        sb.append(markerDef("m-gray", darkTheme ? "#AAAAAA" : "#8C8C8C"));
        sb.append(markerDef("m-red", darkTheme ? "#F56B6B" : "#D94A4A"));
        sb.append(markerDef("m-teal", darkTheme ? "#26C4B3" : "#1A9E8F"));
        sb.append(markerDef("m-amber", darkTheme ? "#F5C842" : "#E6A817"));
        sb.append("</defs>\n");

        // Edges first (under nodes)
        for (GhostSaveFlow.Edge e : flow.getEdges()) {
            sb.append(renderEdge(e, pos, darkTheme));
        }

        // Nodes
        for (GhostSaveFlow.Node n : flow.getNodes()) {
            int[] p = pos.get(n.id());
            if (p == null) {
                continue;
            }
            sb.append(renderNode(n, p[0], p[1], darkTheme));
        }

        sb.append("</svg>\n");
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Layout — single vertical column, centred
    // -----------------------------------------------------------------------

    @NotNull
    private Map<String, int[]> layout(@NotNull GhostSaveFlow flow) {
        Map<String, int[]> p = new LinkedHashMap<>();
        int cx = H_PAD + NODE_W / 2;   // horizontal centre of the node
        int x0 = cx - NODE_W / 2;      // top-left x of each node
        int y  = V_PAD;
        for (GhostSaveFlow.Node n : flow.getNodes()) {
            p.put(n.id(), new int[]{x0, y});
            y += NODE_H + V_GAP;
        }
        return p;
    }

    // -----------------------------------------------------------------------
    // Node
    // -----------------------------------------------------------------------

    @NotNull
    private String renderNode(@NotNull GhostSaveFlow.Node n, int x, int y, boolean dark) {
        String[] c = colorsFor(n.kind(), dark);
        String fill   = c[0];
        String stroke = c[1];
        String text   = c[2];
        String navId  = nid(n.id());

        return String.format(
                "<g id=\"%s\" class=\"flow-node\" data-x=\"%d\" data-y=\"%d\" "
                        + "data-from=\"%s\" data-to=\"%s\" "
                        + "transform=\"translate(%d,%d)\" "
                        + "style=\"cursor:grab;transition:opacity 0.2s;\">%n"
                        + "  <rect class=\"node-bg\" x=\"0\" y=\"0\" width=\"%d\" height=\"%d\" rx=\"10\" "
                        + "fill=\"%s\" stroke=\"%s\" stroke-width=\"2\"/>%n"
                        + "  <text x=\"%d\" y=\"%d\" font-size=\"12\" font-weight=\"600\" "
                        + "fill=\"%s\" pointer-events=\"none\">%s</text>%n"
                        + "%s"
                        + "%s"
                        + "</g>%n",
                navId, x, y,
                escapeXml(n.fileName()), String.valueOf(n.lineNumber()),
                x, y,
                NODE_W, NODE_H, fill, stroke,
                14, 22, text, escapeXml(kindBadge(n.kind()) + n.label()),
                detailLine(n.detail(), text),
                fileLine(n.fileName(), n.lineNumber(), text)
        );
    }

    @NotNull
    private static String detailLine(String detail, String text) {
        if (detail == null || detail.isEmpty()) {
            return "";
        }
        return String.format(
                "  <text x=\"14\" y=\"40\" font-size=\"10\" fill=\"%s\" opacity=\"0.85\" "
                        + "pointer-events=\"none\">%s</text>%n",
                text, escapeXml(detail));
    }

    @NotNull
    private static String fileLine(String fileName, int line, String text) {
        if (line <= 0) {
            return "";
        }
        return String.format(
                "  <text x=\"%d\" y=\"%d\" font-size=\"9\" fill=\"%s\" "
                        + "text-anchor=\"end\" opacity=\"0.7\" pointer-events=\"none\">%s:%d</text>%n",
                NODE_W - 12, 56, text,
                escapeXml(fileName), line);
    }

    // -----------------------------------------------------------------------
    // Edge — gradient stroke + marker arrowhead
    // -----------------------------------------------------------------------

    @NotNull
    private String renderEdge(@NotNull GhostSaveFlow.Edge e,
                              @NotNull Map<String, int[]> pos, boolean dark) {
        int[] from = pos.get(e.fromId());
        int[] to   = pos.get(e.toId());
        if (from == null || to == null) {
            return "";
        }

        int x1 = from[0] + NODE_W / 2;
        int y1 = from[1] + NODE_H;
        int x2 = to[0]   + NODE_W / 2;
        int y2 = to[1];

        String color    = edgeColor(e, dark);
        String marker   = edgeMarker(e);
        String gradId   = "eg-" + safeId(e.fromId()) + "-" + safeId(e.toId()) + "-r";
        String fromNid  = nid(e.fromId());
        String toNid    = nid(e.toId());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<g class=\"flow-edge\" style=\"transition:opacity 0.2s;\">%n"));
        // local gradient so redrawEdges() can update it via parentNode.querySelector('linearGradient')
        sb.append(String.format(
                "  <defs><linearGradient id=\"%s\" gradientUnits=\"userSpaceOnUse\" "
                        + "x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\">"
                        + "<stop offset=\"0%%\" stop-color=\"%s\" stop-opacity=\"0.35\"/>"
                        + "<stop offset=\"100%%\" stop-color=\"%s\" stop-opacity=\"1.0\"/>"
                        + "</linearGradient></defs>%n",
                gradId, x1, y1, x2, y2, color, color));
        // line + arrowhead
        sb.append(String.format(
                "  <line id=\"ep-%s-%s\" data-from=\"%s\" data-to=\"%s\" "
                        + "x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" "
                        + "stroke=\"url(#%s)\" stroke-width=\"2\" marker-end=\"%s\"/>%n",
                safeId(e.fromId()), safeId(e.toId()),
                fromNid, toNid,
                x1, y1, x2, y2, gradId, marker));
        // label near midpoint
        if (e.label() != null && !e.label().isEmpty()) {
            sb.append(String.format(
                    "  <text id=\"lb-%s-%s\" data-from=\"%s\" data-to=\"%s\" "
                            + "x=\"%d\" y=\"%d\" font-size=\"10\" fill=\"%s\" "
                            + "text-anchor=\"middle\" dominant-baseline=\"middle\" "
                            + "opacity=\"0.85\" pointer-events=\"none\">%s</text>%n",
                    safeId(e.fromId()), safeId(e.toId()),
                    fromNid, toNid,
                    (x1 + x2) / 2 + 14, (y1 + y2) / 2 + 4,
                    dark ? "#aaaaaa" : "#666666",
                    escapeXml(e.label())));
        }
        sb.append("</g>\n");
        return sb.toString();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @NotNull
    private static String markerDef(@NotNull String id, @NotNull String color) {
        return String.format(
                "  <marker id=\"%s\" markerWidth=\"8\" markerHeight=\"6\" "
                        + "refX=\"8\" refY=\"3\" orient=\"auto\" markerUnits=\"strokeWidth\">"
                        + "<polygon points=\"0,0 8,3 0,6\" fill=\"%s\"/>"
                        + "</marker>\n",
                id, color);
    }

    @NotNull
    private static String edgeColor(@NotNull GhostSaveFlow.Edge e, boolean dark) {
        // If label indicates transformation, use accent; otherwise neutral
        if (e.label() != null && (e.label().equals("modifying") || e.label().equals("save"))) {
            return dark ? "#F56B6B" : "#D94A4A";
        }
        return dark ? "#AAAAAA" : "#8C8C8C";
    }

    @NotNull
    private static String edgeMarker(@NotNull GhostSaveFlow.Edge e) {
        if (e.label() != null && (e.label().equals("modifying") || e.label().equals("save"))) {
            return "url(#m-red)";
        }
        return "url(#m-gray)";
    }

    @NotNull
    private static String nid(@NotNull String flowNodeId) {
        return "node-" + safeId(flowNodeId);
    }

    @NotNull
    private static String safeId(@NotNull String raw) {
        return raw.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    @NotNull
    private static String kindBadge(@NotNull GhostSaveFlow.NodeKind k) {
        return switch (k) {
            case ENTRY_METHOD -> "⚡ ";
            case SOURCE       -> "📦 ";
            case VARIABLE     -> "🔗 ";
            case HELPER_CALL  -> "🔧 ";
            case SINK         -> "🚨 ";
        };
    }

    @NotNull
    private static String[] colorsFor(@NotNull GhostSaveFlow.NodeKind k, boolean dark) {
        return switch (k) {
            case SOURCE -> dark
                    ? new String[]{"#2B3E50", "#4A90D9", "#A3CFF5"}
                    : new String[]{"#E3EDF7", "#4A90D9", "#2A5A9E"};
            case VARIABLE -> dark
                    ? new String[]{"#4D4420", "#E6A817", "#F5E0A3"}
                    : new String[]{"#F5F0D6", "#E6A817", "#7A5C00"};
            case SINK -> dark
                    ? new String[]{"#502B2B", "#D94A4A", "#F5A3A3"}
                    : new String[]{"#F7E3E3", "#D94A4A", "#9E2A2A"};
            case HELPER_CALL -> dark
                    ? new String[]{"#1F3D3D", "#1A9E8F", "#A3F5E8"}
                    : new String[]{"#E3F5F0", "#1A9E8F", "#0D5A50"};
            default -> dark
                    ? new String[]{"#3A3A3A", "#888888", "#DDDDDD"}
                    : new String[]{"#F5F5F5", "#888888", "#333333"};
        };
    }

    // -----------------------------------------------------------------------
    // Full HTML page
    // -----------------------------------------------------------------------

    @NotNull
    private String buildHtmlPage(@NotNull String svg, boolean dark) {
        String bodyBg = dark ? "#1e1e1e" : "#ffffff";
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<style>"
                + "  body { margin:0; padding:0; overflow:hidden; background:" + bodyBg + "; }"
                + "  #viewport { width:100vw; height:100vh; overflow:hidden; }"
                + "  #zoom-layer { transform-origin:0 0; }"
                + "  .flow-node:hover .node-bg { filter:brightness(1.12); }"
                + "  #zoom-bar { position:fixed; right:12px; top:12px; display:flex;"
                + "    flex-direction:column; gap:4px; z-index:10; }"
                + "  #zoom-bar button { width:28px; height:28px; border:1px solid #555;"
                + "    border-radius:5px; background:rgba(0,0,0,0.45); color:#ccc;"
                + "    font:bold 16px monospace; cursor:pointer; line-height:1; }"
                + "  #zoom-bar button:hover { background:rgba(0,0,0,0.7); color:#fff; }"
                + "  #zoom-pct { font-size:10px; font-family:monospace; color:"
                + (dark ? "#AAAAAA" : "#666666") + "; user-select:none; }"
                + "</style></head><body>"
                + "<div id=\"viewport\"><div id=\"zoom-layer\">" + svg + "</div></div>"
                + "<div id=\"zoom-bar\">"
                + "  <button onclick=\"zoomIn()\" title=\"Zoom in (+)\">+</button>"
                + "  <button onclick=\"zoomOut()\" title=\"Zoom out (−)\">−</button>"
                + "  <button onclick=\"fitAll()\" title=\"Fit all (F)\" style=\"font-size:11px\">⎚</button>"
                + "  <button onclick=\"resetView()\" title=\"Reset view (R)\" style=\"font-size:13px\">⌂</button>"
                + "  <span id=\"zoom-pct\">100%</span>"
                + "</div>"
                + "<script>"
                + buildInteractionJs()
                + "</script></body></html>";
    }

    @NotNull
    private String buildInteractionJs() {
        return ""
                + "var zl=document.getElementById('zoom-layer');"
                + "var vp=document.getElementById('viewport');"
                + "var W=" + NODE_W + ",H=" + NODE_H + ";"
                + "var s=1,zx=0,zy=0;"
                + "function apply(){zl.style.transform='translate('+zx+'px,'+zy+'px) scale('+s+')';"
                + "  var p=document.getElementById('zoom-pct');"
                + "  if(p)p.textContent=Math.round(s*100)+'%';}"
                + "function zoomIn(){var ns=Math.min(6,s+0.15);var r=ns/s;"
                + "  var vw=vp.clientWidth/2,vh=vp.clientHeight/2;"
                + "  zx=vw-r*(vw-zx);zy=vh-r*(vh-zy);s=ns;apply();}"
                + "function zoomOut(){var ns=Math.max(0.15,s-0.15);var r=ns/s;"
                + "  var vw=vp.clientWidth/2,vh=vp.clientHeight/2;"
                + "  zx=vw-r*(vw-zx);zy=vh-r*(vh-zy);s=ns;apply();}"
                + "function resetView(){s=1;zx=0;zy=0;apply();}"
                + "function fitAll(){var nodes=document.querySelectorAll('.flow-node');"
                + "  if(!nodes.length)return;"
                + "  var minX=1e9,minY=1e9,maxX=-1e9,maxY=-1e9,pad=80;"
                + "  for(var i=0;i<nodes.length;i++){var p2=pos(nodes[i]);"
                + "    minX=Math.min(minX,p2.x);minY=Math.min(minY,p2.y);"
                + "    maxX=Math.max(maxX,p2.x+W);maxY=Math.max(maxY,p2.y+H);}"
                + "  var bw=maxX-minX,bh=maxY-minY;"
                + "  var sc=Math.min(vp.clientWidth/(bw+pad*2),vp.clientHeight/(bh+pad*2),2);"
                + "  s=sc;zx=(vp.clientWidth-bw*sc)/2-minX*sc;"
                + "  zy=(vp.clientHeight-bh*sc)/2-minY*sc;apply();}"
                // Pan
                + "var pan=false,px=0,py=0,ox=0,oy=0;"
                + "vp.addEventListener('mousedown',function(e){"
                + "  if(e.target.closest('.flow-node'))return;"
                + "  if(e.button!==0)return;"
                + "  pan=true;px=e.clientX;py=e.clientY;ox=zx;oy=zy;});"
                + "window.addEventListener('mousemove',function(e){"
                + "  if(pan){zx=ox+e.clientX-px;zy=oy+e.clientY-py;apply();}});"
                + "window.addEventListener('mouseup',function(){pan=false;});"
                // Wheel zoom
                + "vp.addEventListener('wheel',function(e){e.preventDefault();"
                + "  var ns=Math.max(0.15,Math.min(6,s-(e.deltaY>0?0.1:-0.1)));"
                + "  var r=ns/s;zx=e.clientX-r*(e.clientX-zx);"
                + "  zy=e.clientY-r*(e.clientY-zy);s=ns;apply();}"
                + ",{passive:false});"
                // Node drag + click-to-navigate
                + "var dn=null,sx=0,sy=0,ex=0,ey=0,didDrag=false;"
                + "var lastClickTime=0,lastClickId='';"
                + "vp.addEventListener('mousedown',function(e){"
                + "  dn=e.target.closest('.flow-node');"
                + "  if(!dn||e.button!==0)return;"
                + "  e.preventDefault();e.stopPropagation();"
                + "  var t=dn.transform.baseVal.consolidate();"
                + "  ex=t?t.matrix.e:parseFloat(dn.getAttribute('data-x'));"
                + "  ey=t?t.matrix.f:parseFloat(dn.getAttribute('data-y'));"
                + "  sx=e.clientX;sy=e.clientY;didDrag=false;},true);"
                + "window.addEventListener('mousemove',function(e){"
                + "  if(!dn)return;"
                + "  var dx=e.clientX-sx,dy=e.clientY-sy;"
                + "  if(Math.abs(dx)>3||Math.abs(dy)>3)didDrag=true;"
                + "  if(didDrag){"
                + "    var nx=ex+dx/s,ny=ey+dy/s;"
                + "    dn.setAttribute('transform','translate('+nx+','+ny+')');"
                + "    dn.setAttribute('data-curx',nx);dn.setAttribute('data-cury',ny);"
                + "    redrawEdges(dn.id);}});"
                + "window.addEventListener('mouseup',function(e){"
                + "  if(!dn)return;"
                + "  if(!didDrag){"
                + "    var now=Date.now();"
                + "    if(now-lastClickTime<350&&lastClickId===dn.id){"
                + "      var f=dn.getAttribute('data-from'),l=dn.getAttribute('data-to');"
                + "      if(f&&l&&window.intellijNavigateLine)window.intellijNavigateLine(f+':'+l);"
                + "      lastClickTime=0;lastClickId='';"
                + "    }else{lastClickTime=now;lastClickId=dn.id;}"
                + "  }dn=null;});"
                // Keyboard shortcuts
                + "window.addEventListener('keydown',function(e){"
                + "  if(e.target.tagName==='INPUT'||e.target.tagName==='TEXTAREA')return;"
                + "  switch(e.key){case 'f':case 'F':fitAll();break;"
                + "  case 'r':case 'R':resetView();break;"
                + "  case '+':case '=':zoomIn();break;"
                + "  case '-':case '_':zoomOut();break;"
                + "  case 'ArrowLeft': zx+=50;apply();e.preventDefault();break;"
                + "  case 'ArrowRight':zx-=50;apply();e.preventDefault();break;"
                + "  case 'ArrowUp':   zy+=50;apply();e.preventDefault();break;"
                + "  case 'ArrowDown': zy-=50;apply();e.preventDefault();break;}"
                + "});"
                // Edge redraw during drag
                + "function redrawEdges(nidd){"
                + "  var svg=document.querySelector('svg');"
                + "  var lines=svg.querySelectorAll('line[data-from],line[data-to]');"
                + "  for(var i=0;i<lines.length;i++){"
                + "    var ln=lines[i];"
                + "    if(ln.getAttribute('data-from')!==nidd&&ln.getAttribute('data-to')!==nidd)continue;"
                + "    var fn=svg.getElementById(ln.getAttribute('data-from'));"
                + "    var tn=svg.getElementById(ln.getAttribute('data-to'));"
                + "    if(!fn||!tn)continue;"
                + "    var fp=pos(fn),tp=pos(tn);"
                + "    var x1=fp.x+W/2,y1=fp.y+H,x2=tp.x+W/2,y2=tp.y;"
                + "    ln.setAttribute('x1',x1);ln.setAttribute('y1',y1);"
                + "    ln.setAttribute('x2',x2);ln.setAttribute('y2',y2);"
                + "    var lg=ln.parentNode.querySelector('linearGradient');"
                + "    if(lg){lg.setAttribute('x1',x1);lg.setAttribute('y1',y1);"
                + "           lg.setAttribute('x2',x2);lg.setAttribute('y2',y2);}"
                + "    var lbl=ln.parentNode.querySelector('text');"
                + "    if(lbl){lbl.setAttribute('x',(x1+x2)/2+14);lbl.setAttribute('y',(y1+y2)/2+4);}"
                + "  }"
                + "}"
                // Position helper
                + "function pos(nd){"
                + "  var cx=parseFloat(nd.getAttribute('data-curx'));"
                + "  var cy=parseFloat(nd.getAttribute('data-cury'));"
                + "  if(!isNaN(cx))return{x:cx,y:cy};"
                + "  var t=nd.transform.baseVal.consolidate();"
                + "  return{x:t?t.matrix.e:parseFloat(nd.getAttribute('data-x'))||0,"
                + "         y:t?t.matrix.f:parseFloat(nd.getAttribute('data-y'))||0};}"
                // Auto-fit on first frame
                + "requestAnimationFrame(function(){fitAll();});";
    }

    @NotNull
    private static String escapeXml(@NotNull String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
