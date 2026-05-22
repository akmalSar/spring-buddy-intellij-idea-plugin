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

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.sarhan.dataFlow.base.SpringEntitySourceDetector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Traces a mutation sink back to its repository source, producing a
 * {@link GhostSaveFlow} that visualises the entity flow.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Identify the mutation sink — the qualifier expression is the tainted entity</li>
 *   <li>Resolve the qualifier to its variable declaration</li>
 *   <li>Walk the variable's initialiser / assignments looking for a repository source</li>
 *   <li>If the entity comes through a method parameter, walk one level up the call chain</li>
 * </ol>
 *
 * <p>Must be called inside a {@code ReadAction.compute { }} block.
 *
 * @author Akmal Sarhan
 */
public final class GhostSaveFlowBuilder {

    private final SpringEntitySourceDetector sourceDetector;

    public GhostSaveFlowBuilder(@NotNull Project project) {
        this.sourceDetector = new SpringEntitySourceDetector(project);
    }

    @NotNull
    public GhostSaveFlow buildFromSink(@NotNull PsiMethodCallExpression mutationCall) {
        PsiMethod enclosing = PsiTreeUtil.getParentOfType(mutationCall, PsiMethod.class);
        if (enclosing == null) {
            return emptyFlow(mutationCall);
        }

        List<GhostSaveFlow.Node> nodes = new ArrayList<>();
        List<GhostSaveFlow.Edge> edges = new ArrayList<>();

        // ── Entry method node ─────────────────────────────────────────────
        GhostSaveFlow.Node entryNode = new GhostSaveFlow.Node(
                "entry",
                enclosing.getName() + "()",
                "No @Transactional — Hibernate session inactive",
                GhostSaveFlow.NodeKind.ENTRY_METHOD,
                fileName(enclosing),
                lineOf(enclosing));
        nodes.add(entryNode);

        // ── Sink node (the mutation call) ─────────────────────────────────
        String sinkName = mutationCall.getMethodExpression().getReferenceName();
        GhostSaveFlow.Node sinkNode = new GhostSaveFlow.Node(
                "sink",
                ((sinkName != null) ? sinkName : "mutator") + "(…)",
                "⚠ Mutation silently dropped — entity is detached",
                GhostSaveFlow.NodeKind.SINK,
                fileName(mutationCall),
                lineOf(mutationCall));

        // ── Trace the qualifier back to its source ────────────────────────
        PsiExpression qualifier = mutationCall.getMethodExpression().getQualifierExpression();
        PsiVariable taintedVar = resolveVariable(qualifier);

        if (taintedVar != null) {
            GhostSaveFlow.Node varNode = new GhostSaveFlow.Node(
                    "var",
                    taintedVar.getName(),
                    typeOf(taintedVar) + " (tainted)",
                    GhostSaveFlow.NodeKind.VARIABLE,
                    fileName(taintedVar),
                    lineOf(taintedVar));

            // Find the source — initialiser or first assignment
            PsiMethodCallExpression source = findSourceForVariable(taintedVar, enclosing);
            if (source != null) {
                PsiMethod resolvedSource = source.resolveMethod();
                String sourceLabel = (resolvedSource != null)
                        ? formatSourceLabel(source, resolvedSource)
                        : source.getText();
                GhostSaveFlow.Node sourceNode = new GhostSaveFlow.Node(
                        "source",
                        sourceLabel,
                        "Loads detached entity",
                        GhostSaveFlow.NodeKind.SOURCE,
                        fileName(source),
                        lineOf(source));
                nodes.add(sourceNode);
                nodes.add(varNode);
                nodes.add(sinkNode);
                edges.add(new GhostSaveFlow.Edge("entry", "source", "executes"));
                edges.add(new GhostSaveFlow.Edge("source", "var", "returns"));
                edges.add(new GhostSaveFlow.Edge("var", "sink", "mutates"));
            } else if (taintedVar instanceof PsiParameter) {
                // Entity came in as a parameter — show as helper-call
                GhostSaveFlow.Node paramNode = new GhostSaveFlow.Node(
                        "param",
                        "param: " + taintedVar.getName(),
                        "Receives detached entity from caller",
                        GhostSaveFlow.NodeKind.HELPER_CALL,
                        fileName(taintedVar),
                        lineOf(taintedVar));
                nodes.add(paramNode);
                nodes.add(varNode);
                nodes.add(sinkNode);
                edges.add(new GhostSaveFlow.Edge("entry", "param", "called with"));
                edges.add(new GhostSaveFlow.Edge("param", "var", "binds"));
                edges.add(new GhostSaveFlow.Edge("var", "sink", "mutates"));
            } else {
                nodes.add(varNode);
                nodes.add(sinkNode);
                edges.add(new GhostSaveFlow.Edge("entry", "var", "declares"));
                edges.add(new GhostSaveFlow.Edge("var", "sink", "mutates"));
            }
        } else {
            nodes.add(sinkNode);
            edges.add(new GhostSaveFlow.Edge("entry", "sink", "executes"));
        }

        return GhostSaveFlow.builder(entryNode)
                .nodes(nodes)
                .edges(edges)
                .headerWarning("Ghost-Save: entity loaded outside @Transactional — "
                        + "Hibernate cannot flush changes")
                .build();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @Nullable
    private PsiVariable resolveVariable(@Nullable PsiExpression expr) {
        if (expr instanceof PsiReferenceExpression ref
                && ref.resolve() instanceof PsiVariable var) {
            return var;
        }
        // Unwrap Optional chain
        if (expr instanceof PsiMethodCallExpression call) {
            String name = call.getMethodExpression().getReferenceName();
            if (name != null && (name.equals("get") || name.startsWith("orElse"))) {
                return resolveVariable(call.getMethodExpression().getQualifierExpression());
            }
        }
        return null;
    }

    @Nullable
    private PsiMethodCallExpression findSourceForVariable(@NotNull PsiVariable var,
                                                            @NotNull PsiMethod scope) {
        // 1. Direct initialiser
        if (var.getInitializer() instanceof PsiMethodCallExpression call
                && isEntitySource(call)) {
            return call;
        }
        if (var.getInitializer() instanceof PsiMethodCallExpression call) {
            PsiMethodCallExpression nested = unwrapOptional(call);
            if (nested != null && isEntitySource(nested)) {
                return nested;
            }
        }
        // 2. Search for assignments to this variable within the method body
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
                if (assign.getLExpression() instanceof PsiReferenceExpression lref
                        && var.equals(lref.resolve())
                        && assign.getRExpression() instanceof PsiMethodCallExpression rcall) {
                    PsiMethodCallExpression candidate = isEntitySource(rcall)
                            ? rcall : unwrapOptional(rcall);
                    if (candidate != null && isEntitySource(candidate)) {
                        found[0] = candidate;
                    }
                }
            }
        });
        return found[0];
    }

    @Nullable
    private PsiMethodCallExpression unwrapOptional(@NotNull PsiMethodCallExpression call) {
        String name = call.getMethodExpression().getReferenceName();
        if (name != null && (name.equals("get") || name.startsWith("orElse"))) {
            PsiExpression q = call.getMethodExpression().getQualifierExpression();
            if (q instanceof PsiMethodCallExpression inner) {
                return inner;
            }
        }
        return null;
    }

    private boolean isEntitySource(@NotNull PsiMethodCallExpression call) {
        PsiMethod target = call.resolveMethod();
        return target != null && this.sourceDetector.isDetachedEntitySource(target);
    }

    @NotNull
    private static String formatSourceLabel(@NotNull PsiMethodCallExpression call,
                                              @NotNull PsiMethod target) {
        PsiClass owner = target.getContainingClass();
        String type = (owner != null && owner.getName() != null) ? owner.getName() : "?";
        return type + "." + target.getName() + "(…)";
    }

    @NotNull
    private static String typeOf(@NotNull PsiVariable var) {
        PsiType t = var.getType();
        return t.getPresentableText();
    }

    @NotNull
    private static String fileName(@Nullable PsiElement element) {
        if (element == null) {
            return "";
        }
        PsiFile file = element.getContainingFile();
        return (file != null) ? file.getName() : "";
    }

    private static int lineOf(@Nullable PsiElement element) {
        if (element == null) {
            return -1;
        }
        // For named elements (methods, variables, classes), prefer the name
        // identifier so navigation lands on the declaration line — not on the
        // preceding Javadoc or annotation block.
        PsiElement target = element;
        if (element instanceof PsiMethod m && m.getNameIdentifier() != null) {
            target = m.getNameIdentifier();
        } else if (element instanceof PsiVariable v && v.getNameIdentifier() != null) {
            target = v.getNameIdentifier();
        }
        PsiFile file = target.getContainingFile();
        if (file == null) {
            return -1;
        }
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) {
            return -1;
        }
        Document doc = FileDocumentManager.getInstance().getDocument(vf);
        if (doc == null) {
            return -1;
        }
        return doc.getLineNumber(target.getTextRange().getStartOffset()) + 1;
    }

    @NotNull
    private static GhostSaveFlow emptyFlow(@NotNull PsiElement element) {
        GhostSaveFlow.Node entry = new GhostSaveFlow.Node(
                "entry", "Unknown", "", GhostSaveFlow.NodeKind.ENTRY_METHOD,
                fileName(element), lineOf(element));
        return GhostSaveFlow.builder(entry)
                .nodes(List.of(entry))
                .edges(List.of())
                .build();
    }
}
