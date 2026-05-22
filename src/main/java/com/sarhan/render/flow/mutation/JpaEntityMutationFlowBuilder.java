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

package com.sarhan.render.flow.mutation;

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.sarhan.dataFlow.base.SpringEntitySourceDetector;
import com.sarhan.render.flow.shared.FlowGraph;
import com.sarhan.render.flow.shared.FlowPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds flow graphs for managed JPA entity mutations inside transactions.
 *
 * @author Akmal Sarhan
 */
public final class JpaEntityMutationFlowBuilder {

    private final SpringEntitySourceDetector sourceDetector;

    public JpaEntityMutationFlowBuilder(@NotNull Project project) {
        this.sourceDetector = new SpringEntitySourceDetector(project);
    }

    @NotNull
    public FlowGraph build(@NotNull PsiElement element, @NotNull MutationFlowMode mode) {
        PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
        if (method == null && element instanceof PsiMethod psiMethod) {
            method = psiMethod;
        }
        if (method == null) {
            return emptyFlow(element, mode);
        }

        PsiMethodCallExpression mutationCall = element instanceof PsiMethodCallExpression call
                ? call : firstMutationCall(method);
        FlowGraph.Node entryNode = new FlowGraph.Node(
                "entry",
                method.getName() + "()",
                entryDetail(mode),
                FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(method),
                FlowPsiUtil.lineOf(method));

        List<FlowGraph.Node> nodes = new ArrayList<>();
        List<FlowGraph.Edge> edges = new ArrayList<>();
        nodes.add(entryNode);

        if (mutationCall == null) {
            return graph(entryNode, nodes, edges, mode, method.getName());
        }

        String sinkName = mutationCall.getMethodExpression().getReferenceName();
        FlowGraph.Node sinkNode = new FlowGraph.Node(
                "sink",
                ((sinkName != null) ? sinkName : "mutate") + "(...)" ,
                sinkDetail(mode),
                FlowGraph.NodeKind.SINK,
                FlowPsiUtil.fileName(mutationCall),
                FlowPsiUtil.lineOf(mutationCall));

        PsiVariable entityVar = resolveVariable(mutationCall.getMethodExpression().getQualifierExpression());
        if (entityVar == null) {
            nodes.add(sinkNode);
            edges.add(new FlowGraph.Edge("entry", "sink", "mutates"));
            return graph(entryNode, nodes, edges, mode, sinkNode.label());
        }

        FlowGraph.Node variableNode = new FlowGraph.Node(
                "entity",
                entityVar.getName(),
                entityVar.getType().getPresentableText() + " (managed entity)",
                FlowGraph.NodeKind.VARIABLE,
                FlowPsiUtil.fileName(entityVar),
                FlowPsiUtil.lineOf(entityVar));

        PsiMethodCallExpression source = findSource(entityVar, method);
        if (source != null) {
            FlowGraph.Node sourceNode = new FlowGraph.Node(
                    "source",
                    sourceLabel(source),
                    "Repository / EntityManager loads a managed entity",
                    FlowGraph.NodeKind.SOURCE,
                    FlowPsiUtil.fileName(source),
                    FlowPsiUtil.lineOf(source));
            nodes.add(sourceNode);
            nodes.add(variableNode);
            nodes.add(sinkNode);
            edges.add(new FlowGraph.Edge("entry", "source", "loads"));
            edges.add(new FlowGraph.Edge("source", "entity", "returns"));
            edges.add(new FlowGraph.Edge("entity", "sink", "mutates"));
        } else {
            nodes.add(variableNode);
            nodes.add(sinkNode);
            edges.add(new FlowGraph.Edge("entry", "entity", "uses"));
            edges.add(new FlowGraph.Edge("entity", "sink", "mutates"));
        }
        return graph(entryNode, nodes, edges, mode, sinkNode.label());
    }

    @Nullable
    private PsiMethodCallExpression firstMutationCall(@NotNull PsiMethod method) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return null;
        }
        PsiMethodCallExpression[] found = {null};
        body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                if (found[0] != null) {
                    return;
                }
                String name = call.getMethodExpression().getReferenceName();
                if (name != null && isLikelyMutation(name)) {
                    found[0] = call;
                }
            }
        });
        return found[0];
    }

    @Nullable
    private PsiMethodCallExpression findSource(@NotNull PsiVariable variable, @NotNull PsiMethod method) {
        PsiMethodCallExpression source = sourceFromExpression(variable.getInitializer());
        if (source != null) {
            return source;
        }
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return null;
        }
        PsiMethodCallExpression[] found = {null};
        body.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
                super.visitAssignmentExpression(assignment);
                if (found[0] != null) {
                    return;
                }
                if (assignment.getLExpression() instanceof PsiReferenceExpression reference
                        && variable.equals(reference.resolve())) {
                    found[0] = sourceFromExpression(assignment.getRExpression());
                }
            }
        });
        return found[0];
    }

    @Nullable
    private PsiMethodCallExpression sourceFromExpression(@Nullable PsiExpression expression) {
        if (!(expression instanceof PsiMethodCallExpression call)) {
            return null;
        }
        PsiMethod target = call.resolveMethod();
        if (target != null && this.sourceDetector.isDetachedEntitySource(target)) {
            return call;
        }
        String name = call.getMethodExpression().getReferenceName();
        if (name != null && (name.equals("get") || name.startsWith("orElse"))) {
            return sourceFromExpression(call.getMethodExpression().getQualifierExpression());
        }
        return null;
    }

    @Nullable
    private static PsiVariable resolveVariable(@Nullable PsiExpression expression) {
        if (expression instanceof PsiReferenceExpression reference
                && reference.resolve() instanceof PsiVariable variable) {
            return variable;
        }
        return null;
    }

    private static boolean isLikelyMutation(@NotNull String name) {
        return name.startsWith("set") || name.startsWith("add")
                || name.startsWith("remove") || name.equals("clear")
                || name.equals("put") || name.equals("putAll");
    }

    @NotNull
    private static FlowGraph graph(@NotNull FlowGraph.Node entryNode,
                                   @NotNull List<FlowGraph.Node> nodes,
                                   @NotNull List<FlowGraph.Edge> edges,
                                   @NotNull MutationFlowMode mode,
                                   @NotNull String title) {
        return FlowGraph.builder(entryNode)
                .nodes(nodes)
                .edges(edges)
                .title(title)
                .headerWarning(header(mode))
                .build();
    }

    @NotNull
    private static String sourceLabel(@NotNull PsiMethodCallExpression source) {
        PsiMethod target = source.resolveMethod();
        if (target == null || target.getContainingClass() == null || target.getContainingClass().getName() == null) {
            return source.getText();
        }
        return target.getContainingClass().getName() + "." + target.getName() + "(...)";
    }

    @NotNull
    private static String entryDetail(@NotNull MutationFlowMode mode) {
        return switch (mode) {
            case READ_ONLY -> "@Transactional(readOnly = true) keeps entity managed but forbids writes";
            case DIRTY_CHECKING -> "@Transactional keeps entity managed until commit";
        };
    }

    @NotNull
    private static String sinkDetail(@NotNull MutationFlowMode mode) {
        return switch (mode) {
            case READ_ONLY -> "Mutation in read-only transaction may fail during flush / commit";
            case DIRTY_CHECKING -> "Mutation can be flushed automatically by dirty checking";
        };
    }

    @NotNull
    private static String header(@NotNull MutationFlowMode mode) {
        return switch (mode) {
            case READ_ONLY -> "Read-only transaction mutation: managed entity is changed inside readOnly=true; flush can fail or mark rollback-only.";
            case DIRTY_CHECKING -> "Dirty checking: managed entity mutation can persist at commit without an explicit save call.";
        };
    }

    @NotNull
    private static FlowGraph emptyFlow(@NotNull PsiElement element, @NotNull MutationFlowMode mode) {
        FlowGraph.Node entry = new FlowGraph.Node("entry", "Unknown", "", FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(element), FlowPsiUtil.lineOf(element));
        return graph(entry, List.of(entry), List.of(), mode, "entity-mutation");
    }
}
