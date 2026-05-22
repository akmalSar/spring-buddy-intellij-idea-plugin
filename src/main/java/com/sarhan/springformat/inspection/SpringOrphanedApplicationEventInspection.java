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

package com.sarhan.springformat.inspection;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Detects orphaned Spring application events: events published via
 * {@code ApplicationEventPublisher.publishEvent()} that have zero matching
 * {@code @EventListener} or {@code @TransactionalEventListener} listeners,
 * and listeners whose parameter types are never published anywhere
 * in the project.
 *
 * <p>Spring events are weakly typed at the invocation site, so refactoring
 * can silently break the publisher-listener contract. This inspection
 * cross-references all {@code publishEvent} call sites against all
 * listener parameter types to flag dead ends.
 *
 * @author Spring Buddy
 * @author Akmal Sarhan
 */
public class SpringOrphanedApplicationEventInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final ConcurrentMap<Project, EventIndex> projectCache = new ConcurrentHashMap<>();

    /**
     * Framework lifecycle events that are published by Spring itself, not via
     * explicit ApplicationEventPublisher.publishEvent() in user code.
     */
    private static final Set<String> FRAMEWORK_LIFECYCLE_EVENTS = Set.of(
            "org.springframework.context.event.ContextRefreshedEvent",
            "org.springframework.context.event.ContextStartedEvent",
            "org.springframework.context.event.ContextStoppedEvent",
            "org.springframework.context.event.ContextClosedEvent",
            "org.springframework.boot.context.event.ApplicationStartingEvent",
            "org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent",
            "org.springframework.boot.context.event.ApplicationContextInitializedEvent",
            "org.springframework.boot.context.event.ApplicationPreparedEvent",
            "org.springframework.boot.context.event.ApplicationStartedEvent",
            "org.springframework.boot.context.event.ApplicationReadyEvent",
            "org.springframework.boot.context.event.ApplicationFailedEvent"
    );

    static void clearProjectCache(Project project) {
        projectCache.remove(project);
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        Project project = holder.getProject();
        EventIndex index = projectCache.computeIfAbsent(project, EventIndex::new);
        PsiFile currentFile = holder.getFile();

        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                for (PsiMethod method : psiClass.getMethods()) {
                    checkEventListenerParameters(method, index, currentFile, holder);
                }
                for (PsiMethod method : psiClass.getMethods()) {
                    checkPublishEventCalls(method, index, currentFile, holder);
                }
                for (PsiField field : psiClass.getFields()) {
                    checkPublishEventOnField(field, index, currentFile, holder);
                }
            }
        };
    }

    private static void checkEventListenerParameters(PsiMethod method, EventIndex index,
            PsiFile currentFile, ProblemsHolder holder) {
        if (!isEventListener(method)) {
            return;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != 1) {
            return;
        }
        PsiType type = parameters[0].getType();
        PsiClass resolved = SpringInspectionUtil.resolvedClass(type);
        if (resolved == null) {
            return;
        }
        String fqn = resolved.getQualifiedName();
        if (fqn == null) {
            fqn = type.getCanonicalText();
        }
        if (fqn == null || index.publishedTypes.contains(fqn) || FRAMEWORK_LIFECYCLE_EVENTS.contains(fqn)) {
            return;
        }
        PsiElement target = parameters[0].getTypeElement();
        if (target == null) {
            target = parameters[0];
        }
        holder.registerProblem(target,
                "The event type '" + toSimpleName(fqn) + "' is never published via ApplicationEventPublisher.publishEvent() in this project",
                ProblemHighlightType.WARNING);
    }

    private static void checkPublishEventCalls(PsiMethod method, EventIndex index,
            PsiFile currentFile, ProblemsHolder holder) {
        if (method.getBody() == null) {
            return;
        }
        method.getBody().accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                if (!"publishEvent".equals(call.getMethodExpression().getReferenceName())) {
                    return;
                }
                PsiExpression[] args = call.getArgumentList().getExpressions();
                if (args.length == 0) {
                    return;
                }
                PsiExpression arg = args[0];
                PsiType type = arg.getType();
                if (type == null) {
                    type = inferNewInstanceType(arg);
                }
                PsiClass resolved = SpringInspectionUtil.resolvedClass(type);
                if (resolved == null) {
                    return;
                }
                String fqn = resolved.getQualifiedName();
                if (fqn == null) {
                    fqn = type.getCanonicalText();
                }
                if (fqn == null || index.listenerTypes.contains(fqn)) {
                    return;
                }
                holder.registerProblem(call,
                        "The published event '" + toSimpleName(fqn) + "' has no matching @EventListener or @TransactionalEventListener in this project",
                        ProblemHighlightType.WARNING);
            }
        });
    }

    private static void checkPublishEventOnField(PsiField field, EventIndex index,
            PsiFile currentFile, ProblemsHolder holder) {
        if (field.getInitializer() == null) {
            return;
        }
        field.getInitializer().accept(new JavaRecursiveElementVisitor() {
            private boolean inPublisherChain;

            @Override
            public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                super.visitMethodCallExpression(call);
                if ("publishEvent".equals(call.getMethodExpression().getReferenceName())) {
                    this.inPublisherChain = true;
                }
                if (this.inPublisherChain && "publishEvent".equals(call.getMethodExpression().getReferenceName())) {
                    PsiExpression[] args = call.getArgumentList().getExpressions();
                    if (args.length == 0) {
                        return;
                    }
                    PsiExpression arg = args[0];
                    PsiType type = arg.getType();
                    if (type == null) {
                        type = inferNewInstanceType(arg);
                    }
                    PsiClass resolved = SpringInspectionUtil.resolvedClass(type);
                    if (resolved == null) {
                        return;
                    }
                    String fqn = resolved.getQualifiedName();
                    if (fqn == null) {
                        fqn = type.getCanonicalText();
                    }
                    if (fqn == null || index.listenerTypes.contains(fqn)) {
                        return;
                    }
                    holder.registerProblem(call,
                            "The published event '" + toSimpleName(fqn) + "' has no matching @EventListener or @TransactionalEventListener in this project",
                            ProblemHighlightType.WARNING);
                }
                if (!this.inPublisherChain) {
                    checkPublishEventCallRecursive(call, index, currentFile, holder);
                }
            }
        });
    }

    private static void checkPublishEventCallRecursive(PsiMethodCallExpression call, EventIndex index,
            PsiFile currentFile, ProblemsHolder holder) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length == 0) {
            return;
        }
        for (PsiExpression arg : args) {
            if (!call.getMethodExpression().getText().contains("publishEvent")) {
                continue;
            }
            arg.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitReferenceExpression(@NotNull PsiReferenceExpression ref) {
                    super.visitReferenceExpression(ref);
                    PsiType type = ref.getType();
                    if (type == null) {
                        return;
                    }
                    PsiClass resolved = SpringInspectionUtil.resolvedClass(type);
                    if (resolved == null) {
                        return;
                    }
                    String fqn = resolved.getQualifiedName();
                    if (fqn == null) {
                        fqn = type.getCanonicalText();
                    }
                    if (fqn != null && !index.listenerTypes.contains(fqn)) {
                        holder.registerProblem(call,
                                "The published event '" + toSimpleName(fqn) + "' has no matching @EventListener or @TransactionalEventListener in this project",
                                ProblemHighlightType.WARNING);
                    }
                }
            });
        }
    }

    private static PsiType inferNewInstanceType(PsiExpression expression) {
        String text = expression.getText();
        if (text.startsWith("new ")) {
            String typeName = text.substring(4);
            int parenIndex = typeName.indexOf('(');
            if (parenIndex > 0) {
                typeName = typeName.substring(0, parenIndex).trim();
            }
            PsiJavaFile file = (PsiJavaFile) expression.getContainingFile();
            for (PsiClass cls : file.getClasses()) {
                String simpleName = typeName.contains(".") ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;
                if (simpleName.equals(cls.getName()) || typeName.equals(cls.getQualifiedName())) {
                    return JavaPsiFacade.getElementFactory(expression.getProject())
                            .createType(cls);
                }
            }
            for (PsiClass innerCls : findDeepInnerClasses(file)) {
                String simpleName = typeName.contains(".") ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;
                if (simpleName.equals(innerCls.getName()) || typeName.equals(innerCls.getQualifiedName())) {
                    return JavaPsiFacade.getElementFactory(expression.getProject())
                            .createType(innerCls);
                }
            }
        }
        return expression.getType();
    }

    private static java.util.List<PsiClass> findDeepInnerClasses(PsiClass psiClass) {
        java.util.List<PsiClass> result = new java.util.ArrayList<>();
        for (PsiClass inner : psiClass.getInnerClasses()) {
            result.add(inner);
            result.addAll(findDeepInnerClasses(inner));
        }
        return result;
    }

    private static java.util.List<PsiClass> findDeepInnerClasses(PsiJavaFile file) {
        java.util.List<PsiClass> result = new java.util.ArrayList<>();
        for (PsiClass cls : file.getClasses()) {
            result.addAll(findDeepInnerClasses(cls));
        }
        return result;
    }

    private static boolean isEventListener(PsiMethod method) {
        for (PsiAnnotation annotation : method.getAnnotations()) {
            String fqn = annotation.getQualifiedName();
            if (fqn == null && annotation.getNameReferenceElement() != null) {
                fqn = annotation.getNameReferenceElement().getText();
            }
            if ("org.springframework.context.event.EventListener".equals(fqn)
                    || "org.springframework.transaction.event.TransactionalEventListener".equals(fqn)
                    || "EventListener".equals(fqn)
                    || "TransactionalEventListener".equals(fqn)) {
                return true;
            }
        }
        return false;
    }

    static String toSimpleName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return (lastDot >= 0) ? fqn.substring(lastDot + 1) : fqn;
    }

    static final class EventIndex {
        final Set<String> publishedTypes = new HashSet<>();
        final Set<String> listenerTypes = new HashSet<>();

        EventIndex(Project project) {
            Collection<VirtualFile> files = FilenameIndex.getAllFilesByExt(project, "java",
                    GlobalSearchScope.projectScope(project));
            PsiManager psiManager = PsiManager.getInstance(project);
            for (VirtualFile vf : files) {
                PsiFile psiFile = psiManager.findFile(vf);
                if (!(psiFile instanceof PsiJavaFile javaFile)) {
                    continue;
                }
                for (PsiClass psiClass : javaFile.getClasses()) {
                    for (PsiMethod method : psiClass.getMethods()) {
                        collectEventListenerTypes(method, this.listenerTypes);
                        if (method.getBody() != null) {
                            collectPublishEventTypes(method.getBody(), this.publishedTypes, psiFile);
                        }
                    }
                    for (PsiField field : psiClass.getFields()) {
                        if (field.getInitializer() != null) {
                            collectPublishEventTypes(field.getInitializer(), this.publishedTypes, psiFile);
                        }
                    }
                }
            }
        }

        private static void collectEventListenerTypes(PsiMethod method, Set<String> types) {
            if (!isEventListener(method)) {
                return;
            }
            PsiParameter[] params = method.getParameterList().getParameters();
            if (params.length != 1) {
                return;
            }
            PsiType type = params[0].getType();
            PsiClass resolved = SpringInspectionUtil.resolvedClass(type);
            if (resolved != null && resolved.getQualifiedName() != null) {
                types.add(resolved.getQualifiedName());
            } else {
                String canonical = type.getCanonicalText();
                if (canonical != null) {
                    types.add(canonical);
                }
            }
        }

        private static void collectPublishEventTypes(PsiElement scope, Set<String> types, PsiFile file) {
            scope.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
                    super.visitMethodCallExpression(call);
                    if (!"publishEvent".equals(call.getMethodExpression().getReferenceName())) {
                        return;
                    }
                    PsiExpression[] args = call.getArgumentList().getExpressions();
                    if (args.length == 0) {
                        return;
                    }
                    PsiExpression arg = args[0];
                    PsiType type = arg.getType();
                    if (type == null) {
                        String text = arg.getText();
                        if (text.startsWith("new ")) {
                            String typeName = text.substring(4);
                            int paren = typeName.indexOf('(');
                            if (paren > 0) {
                                typeName = typeName.substring(0, paren).trim();
                            }
                            String simpleName = typeName.contains(".")
                                    ? typeName.substring(typeName.lastIndexOf('.') + 1)
                                    : typeName;
                            for (PsiClass cls : ((PsiJavaFile) file).getClasses()) {
                                if (simpleName.equals(cls.getName())) {
                                    String clsFqn = cls.getQualifiedName();
                                    if (clsFqn != null) {
                                        types.add(clsFqn);
                                    }
                                    return;
                                }
                                for (PsiClass inner : findDeepInnerClasses(cls)) {
                                    if (simpleName.equals(inner.getName())) {
                                        String clsFqn = inner.getQualifiedName();
                                        if (clsFqn != null) {
                                            types.add(clsFqn);
                                        }
                                        return;
                                    }
                                }
                            }
                        }
                        return;
                    }
                    PsiClass resolved = SpringInspectionUtil.resolvedClass(type);
                    if (resolved != null && resolved.getQualifiedName() != null) {
                        types.add(resolved.getQualifiedName());
                    } else {
                        String canonical = type.getCanonicalText();
                        if (canonical != null) {
                            types.add(canonical);
                        }
                    }
                }
            });
        }
    }
}
