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

package com.sarhan.render.flow.nplusone;

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.sarhan.dataFlow.base.SpringEntitySourceDetector;
import com.sarhan.render.flow.shared.FlowGraph;
import com.sarhan.render.flow.shared.FlowPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds a flow graph for a predicted JPA N+1 query: repository collection
 * source, loop/stream element alias, then lazy association sink.
 *
 * @author Akmal Sarhan
 */
public final class NPlusOneFlowBuilder {

    private final SpringEntitySourceDetector sourceDetector;

    public NPlusOneFlowBuilder(@NotNull Project project) {
        this.sourceDetector = new SpringEntitySourceDetector(project);
    }

    @NotNull
    public FlowGraph buildFromSink(@NotNull PsiElement sinkElement) {
        PsiMethod enclosing = PsiTreeUtil.getParentOfType(sinkElement, PsiMethod.class);
        if (enclosing == null) {
            return emptyFlow(sinkElement);
        }

        FlowGraph.Node entryNode = new FlowGraph.Node(
                "entry",
                enclosing.getName() + "()",
                "Lazy loading is active in this context",
                FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(enclosing),
                FlowPsiUtil.lineOf(enclosing));

        PsiVariable elementVar = resolveElementVariable(sinkElement);
        FlowGraph.Node sinkNode = new FlowGraph.Node(
                "sink",
                sinkLabel(sinkElement),
                "Lazy association loaded once per row",
                FlowGraph.NodeKind.LAZY_GETTER,
                FlowPsiUtil.fileName(sinkElement),
                FlowPsiUtil.lineOf(sinkElement));

        List<FlowGraph.Node> nodes = new ArrayList<>();
        List<FlowGraph.Edge> edges = new ArrayList<>();
        nodes.add(entryNode);

        if (elementVar == null) {
            nodes.add(sinkNode);
            edges.add(new FlowGraph.Edge("entry", "sink", "N+1"));
            return graph(entryNode, nodes, edges, sinkNode.label());
        }

        FlowGraph.Node elementNode = new FlowGraph.Node(
                "element",
                elementVar.getName(),
                "Loop / stream element",
                FlowGraph.NodeKind.VARIABLE,
                FlowPsiUtil.fileName(elementVar),
                FlowPsiUtil.lineOf(elementVar));

        PsiExpression iterated = iteratedExpression(elementVar);
        PsiVariable collectionVar = resolveVariable(iterated);
        PsiMethodCallExpression source = (collectionVar != null)
                ? findCollectionSource(collectionVar, enclosing) : null;

        if (source != null) {
            FlowGraph.Node sourceNode = new FlowGraph.Node(
                    "source",
                    sourceLabel(source),
                    "Repository / EntityManager collection result",
                    FlowGraph.NodeKind.SOURCE,
                    FlowPsiUtil.fileName(source),
                    FlowPsiUtil.lineOf(source));
            nodes.add(sourceNode);
            if (collectionVar != null) {
                FlowGraph.Node collectionNode = new FlowGraph.Node(
                        "collection",
                        collectionVar.getName(),
                        typeOf(collectionVar) + " (query result)",
                        FlowGraph.NodeKind.VARIABLE,
                        FlowPsiUtil.fileName(collectionVar),
                        FlowPsiUtil.lineOf(collectionVar));
                nodes.add(collectionNode);
                edges.add(new FlowGraph.Edge("entry", "source", "executes"));
                edges.add(new FlowGraph.Edge("source", "collection", "returns"));
                edges.add(new FlowGraph.Edge("collection", "element", loopEdgeLabel(elementVar)));
            } else {
                edges.add(new FlowGraph.Edge("entry", "source", "executes"));
                edges.add(new FlowGraph.Edge("source", "element", loopEdgeLabel(elementVar)));
            }
        } else if (collectionVar != null) {
            FlowGraph.Node collectionNode = new FlowGraph.Node(
                    "collection",
                    collectionVar.getName(),
                    typeOf(collectionVar) + " (tainted collection)",
                    FlowGraph.NodeKind.VARIABLE,
                    FlowPsiUtil.fileName(collectionVar),
                    FlowPsiUtil.lineOf(collectionVar));
            nodes.add(collectionNode);
            edges.add(new FlowGraph.Edge("entry", "collection", "uses"));
            edges.add(new FlowGraph.Edge("collection", "element", loopEdgeLabel(elementVar)));
        } else {
            edges.add(new FlowGraph.Edge("entry", "element", loopEdgeLabel(elementVar)));
        }

        nodes.add(elementNode);
        nodes.add(sinkNode);
        edges.add(new FlowGraph.Edge("element", "sink", "N+1"));
        return graph(entryNode, nodes, edges, sinkNode.label());
    }

    @NotNull
    private static FlowGraph graph(@NotNull FlowGraph.Node entryNode,
                                   @NotNull List<FlowGraph.Node> nodes,
                                   @NotNull List<FlowGraph.Edge> edges,
                                   @NotNull String title) {
        return FlowGraph.builder(entryNode)
                .nodes(nodes)
                .edges(edges)
                .title(title)
                .headerWarning("N+1 query: each loop / stream element triggers lazy loading. "
                        + "Use fetch join, EntityGraph, DTO projection, or batch loading.")
                .build();
    }

    @Nullable
    private static PsiVariable resolveElementVariable(@NotNull PsiElement sinkElement) {
        PsiExpression qualifier = sinkQualifier(sinkElement);
        return resolveVariable(qualifier);
    }

    @Nullable
    private static PsiExpression sinkQualifier(@NotNull PsiElement sinkElement) {
        if (sinkElement instanceof PsiMethodCallExpression call) {
            return call.getMethodExpression().getQualifierExpression();
        }
        if (sinkElement instanceof PsiReferenceExpression ref) {
            return ref.getQualifierExpression();
        }
        PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(sinkElement, PsiMethodCallExpression.class);
        if (call != null && call.getTextRange().contains(sinkElement.getTextRange())) {
            return call.getMethodExpression().getQualifierExpression();
        }
        PsiReferenceExpression ref = PsiTreeUtil.getParentOfType(sinkElement, PsiReferenceExpression.class);
        return (ref != null) ? ref.getQualifierExpression() : null;
    }

    @Nullable
    private static PsiVariable resolveVariable(@Nullable PsiExpression expr) {
        if (expr instanceof PsiReferenceExpression ref
                && ref.resolve() instanceof PsiVariable var) {
            return var;
        }
        if (expr instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            if (name != null && (name.equals("stream") || name.equals("parallelStream")
                    || name.equals("filter") || name.equals("map") || name.equals("flatMap")
                    || name.equals("peek") || name.equals("distinct") || name.equals("sorted")
                    || name.equals("limit") || name.equals("skip"))) {
                return resolveVariable(call.getMethodExpression().getQualifierExpression());
            }
        }
        return null;
    }

    @Nullable
    private static PsiExpression iteratedExpression(@NotNull PsiVariable elementVar) {
        if (elementVar instanceof PsiParameter param) {
            PsiElement parent = param.getDeclarationScope();
            if (parent instanceof PsiForeachStatement foreach) {
                return foreach.getIteratedValue();
            }
            if (parent instanceof PsiLambdaExpression lambda) {
                PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
                return (call != null) ? call.getMethodExpression().getQualifierExpression() : null;
            }
        }
        return null;
    }

    @Nullable
    private PsiMethodCallExpression findCollectionSource(@NotNull PsiVariable var,
                                                         @NotNull PsiMethod scope) {
        if (var.getInitializer() instanceof PsiMethodCallExpression call) {
            PsiMethodCallExpression candidate = collectionSourceCall(call);
            if (candidate != null) {
                return candidate;
            }
        }
        PsiCodeBlock body = scope.getBody();
        if (body == null) {
            return null;
        }
        PsiMethodCallExpression[] found = {null};
        body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assign) {
                super.visitAssignmentExpression(assign);
                if (found[0] != null) {
                    return;
                }
                if (assign.getLExpression() instanceof PsiReferenceExpression ref
                        && var.equals(ref.resolve())
                        && assign.getRExpression() instanceof PsiMethodCallExpression call) {
                    found[0] = collectionSourceCall(call);
                }
            }
        });
        return found[0];
    }

    @Nullable
    private PsiMethodCallExpression collectionSourceCall(@NotNull PsiMethodCallExpression call) {
        PsiMethod method = call.resolveMethod();
        if (method != null && this.sourceDetector.isDetachedEntitySource(method)) {
            return call;
        }
        String name = call.getMethodExpression().getReferenceName();
        if (name != null && (name.equals("get") || name.startsWith("orElse"))) {
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (qualifier instanceof PsiMethodCallExpression inner) {
                return collectionSourceCall(inner);
            }
        }
        return null;
    }

    @NotNull
    private static String sinkLabel(@NotNull PsiElement sinkElement) {
        if (sinkElement instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            return ((name != null) ? name : "lazy") + "(...)";
        }
        if (sinkElement instanceof PsiReferenceExpression ref) {
            String name = ref.getReferenceName();
            return (name != null) ? name : "lazy association";
        }
        return "lazy association";
    }

    @NotNull
    private static String sourceLabel(@NotNull PsiMethodCallExpression source) {
        PsiMethod method = source.resolveMethod();
        if (method == null) {
            return source.getText();
        }
        return method.getName() + "(...)";
    }

    @NotNull
    private static String loopEdgeLabel(@NotNull PsiVariable elementVar) {
        PsiExpression iterated = iteratedExpression(elementVar);
        if (iterated instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            if (name != null && (name.equals("forEach") || name.equals("map") || name.equals("filter")
                    || name.equals("flatMap") || name.equals("peek") || name.equals("anyMatch")
                    || name.equals("allMatch") || name.equals("noneMatch"))) {
                return name;
            }
            return "stream";
        }
        return "iterates";
    }

    @NotNull
    private static String typeOf(@NotNull PsiVariable var) {
        return var.getType().getPresentableText();
    }

    @NotNull
    private static FlowGraph emptyFlow(@NotNull PsiElement element) {
        FlowGraph.Node entry = new FlowGraph.Node(
                "entry", "Unknown", "", FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(element), FlowPsiUtil.lineOf(element));
        return FlowGraph.builder(entry).nodes(List.of(entry)).edges(List.of()).build();
    }
}
