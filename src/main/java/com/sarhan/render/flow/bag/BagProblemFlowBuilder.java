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

package com.sarhan.render.flow.bag;

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.sarhan.render.flow.shared.FlowGraph;
import com.sarhan.render.flow.shared.FlowPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds a {@link FlowGraph} that visualises the Hibernate "bag" problem on a
 * JPA entity class: all {@code @OneToMany} / {@code @ManyToMany} fields typed
 * as {@code List} are shown as SINK nodes hanging off the entity class node.
 *
 * <p>Topology (one bag association):
 * <pre>
 *   ENTRY_METHOD  (the @Entity class)
 *       │
 *       ▼
 *   SINK  (@OneToMany List&lt;X&gt; field)  ← delete-all + re-insert on every change
 * </pre>
 *
 * <p>When multiple bag fields exist on the entity they all appear as parallel
 * SINK nodes under the same entity entry node, making the Cartesian-product
 * risk visually obvious.
 *
 * <p>Must be called inside a {@code ReadAction.compute { }} block.
 *
 * @author Akmal Sarhan
 */
public final class BagProblemFlowBuilder {

    private static final String[] MANY_ANNOTATIONS = {
            "jakarta.persistence.OneToMany",
            "jakarta.persistence.ManyToMany",
            "javax.persistence.OneToMany",
            "javax.persistence.ManyToMany",
            "OneToMany",
            "ManyToMany"
    };

    public BagProblemFlowBuilder(@NotNull Project project) {
    }

    /**
     * Builds the graph rooted at the class that contains the given bag field.
     * All other bag fields on the same class are also included as SINK nodes.
     */
    @NotNull
    public FlowGraph buildFromField(@NotNull PsiField bagField) {
        PsiClass entityClass = bagField.getContainingClass();
        if (entityClass == null) {
            return emptyFlow(bagField);
        }

        String className = (entityClass.getName() != null) ? entityClass.getName() : "Entity";
        FlowGraph.Node entryNode = new FlowGraph.Node(
                "entity",
                className,
                "@Entity — Hibernate-managed class",
                FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(entityClass),
                FlowPsiUtil.lineOf(entityClass));

        List<FlowGraph.Node> nodes = new ArrayList<>();
        List<FlowGraph.Edge> edges = new ArrayList<>();
        nodes.add(entryNode);

        List<PsiField> bagFields = collectBagFields(entityClass);
        if (bagFields.isEmpty()) {
            bagFields = List.of(bagField);
        }

        int index = 0;
        for (PsiField field : bagFields) {
            String nodeId = "bag_" + index;
            String annotationShort = shortAnnotationName(field);
            String typeText = field.getType().getPresentableText();
            boolean isThisField = field.equals(bagField);
            FlowGraph.Node sinkNode = new FlowGraph.Node(
                    nodeId,
                    "@" + annotationShort + " " + field.getName(),
                    typeText + " — delete-all + re-insert on every change"
                            + (isThisField ? " ← flagged" : ""),
                    FlowGraph.NodeKind.SINK,
                    FlowPsiUtil.fileName(field),
                    FlowPsiUtil.lineOf(field));
            nodes.add(sinkNode);
            edges.add(new FlowGraph.Edge("entity", nodeId, "bag"));
            index++;
        }

        int bagCount = bagFields.size();
        String warning = (bagCount == 1)
                ? "Bag semantics: @" + shortAnnotationName(bagField) + " List on "
                        + className + " — Hibernate deletes all rows and re-inserts on every change. "
                        + "Use Set instead."
                : bagCount + " bag associations on " + className
                        + " — multiple bags cause Cartesian product queries. "
                        + "Change all List fields to Set.";

        return FlowGraph.builder(entryNode)
                .nodes(nodes)
                .edges(edges)
                .title(className + " bags")
                .headerWarning(warning)
                .build();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @NotNull
    private static List<PsiField> collectBagFields(@NotNull PsiClass entityClass) {
        List<PsiField> bags = new ArrayList<>();
        for (PsiField field : entityClass.getFields()) {
            if (isBagField(field)) {
                bags.add(field);
            }
        }
        return bags;
    }

    private static boolean isBagField(@NotNull PsiField field) {
        PsiAnnotation ann = findManyAnnotation(field);
        if (ann == null) {
            return false;
        }
        String typeText = field.getType().getCanonicalText();
        return isList(typeText);
    }

    @Nullable
    private static PsiAnnotation findManyAnnotation(@NotNull PsiField field) {
        PsiModifierList modifiers = field.getModifierList();
        if (modifiers == null) {
            return null;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String fqn = annotation.getQualifiedName();
            if (fqn == null) {
                PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
                if (ref != null) {
                    fqn = ref.getText();
                }
            }
            for (String many : MANY_ANNOTATIONS) {
                if (many.equals(fqn)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private static boolean isList(@Nullable String typeText) {
        if (typeText == null) {
            return false;
        }
        return typeText.equals("java.util.List")
                || typeText.startsWith("java.util.List<")
                || typeText.equals("List")
                || typeText.startsWith("List<");
    }

    @NotNull
    private static String shortAnnotationName(@NotNull PsiField field) {
        PsiAnnotation ann = findManyAnnotation(field);
        if (ann == null) {
            return "OneToMany";
        }
        String fqn = ann.getQualifiedName();
        if (fqn == null) {
            PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
            fqn = (ref != null) ? ref.getText() : "OneToMany";
        }
        int dot = fqn.lastIndexOf('.');
        return (dot >= 0) ? fqn.substring(dot + 1) : fqn;
    }

    @NotNull
    private static FlowGraph emptyFlow(@NotNull PsiElement element) {
        FlowGraph.Node entry = new FlowGraph.Node(
                "entity", "Unknown", "", FlowGraph.NodeKind.ENTRY_METHOD,
                FlowPsiUtil.fileName(element), FlowPsiUtil.lineOf(element));
        return FlowGraph.builder(entry)
                .nodes(List.of(entry))
                .edges(List.of())
                .build();
    }
}
