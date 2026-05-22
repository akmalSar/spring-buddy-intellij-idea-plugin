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

import java.util.Set;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects thread-safety issues in singleton Spring beans.
 * <p>
 * Covers three violation types:
 * <ol>
 *   <li>Mutable instance fields in singleton beans (race conditions).</li>
 *   <li>Non-concurrent shared caches (e.g. plain {@code HashMap}).</li>
 *   <li>{@code @Async} methods writing to unsynchronized shared bean state.</li>
 * </ol>
 *
 * @author Akmal Sarhan
 */
public class SpringBeanConcurrencyInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final Set<String> MUTABLE_CACHE_TYPES = Set.of(
            "java.util.Map", "java.util.HashMap", "java.util.LinkedHashMap", "java.util.TreeMap",
            "java.util.WeakHashMap", "java.util.IdentityHashMap", "java.util.List", "java.util.ArrayList",
            "java.util.LinkedList", "java.util.Set", "java.util.HashSet", "java.util.LinkedHashSet");
    private final Kind kind;

    protected SpringBeanConcurrencyInspection(Kind kind) {
        this.kind = kind;
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {

        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                super.visitClass(psiClass);
                if (!isSpringBean(psiClass)) {
                    return;
                }
                switch (SpringBeanConcurrencyInspection.this.kind) {
                    case STATEFUL_SINGLETON -> inspectStatefulSingleton(psiClass, holder);
                    case SHARED_MUTABLE_CACHE -> inspectSharedMutableCaches(psiClass, holder);
                    case ASYNC_SHARED_MUTABLE_STATE -> inspectAsyncSharedState(psiClass, holder);
                }
            }
        };
    }

    private static void inspectStatefulSingleton(PsiClass psiClass, ProblemsHolder holder) {
        if (hasAnnotation(psiClass.getModifierList(), "org.springframework.boot.context.properties.ConfigurationProperties", "ConfigurationProperties")) {
            return;
        }
        for (PsiField field : psiClass.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.FINAL)
                    || field.hasModifierProperty(PsiModifier.VOLATILE)
                    || hasAnnotation(field, "org.springframework.beans.factory.annotation.Autowired", "Autowired")
                    || hasAnnotation(field, "org.springframework.beans.factory.annotation.Value", "Value")
                    || hasAnnotation(field, "javax.persistence.PersistenceContext", "PersistenceContext")
                    || hasAnnotation(field, "jakarta.persistence.PersistenceContext", "PersistenceContext")
                    || isSpringBeanDependency(field) || isLombokConstructorInjectedDependency(psiClass, field)) {
                continue;
            }
            if (isThreadSafeOrInfrastructureType(field.getType())) {
                continue;
            }
            if (isOnlyWrittenDuringInitialization(psiClass, field)) {
                continue;
            }
            PsiElement target = (field.getNameIdentifier() != null) ? field.getNameIdentifier() : field;
            holder.registerProblem(target,
                    "Mutable instance field in singleton Spring bean can be shared across threads; prefer request/local state or a thread-safe type",
                    ProblemHighlightType.ERROR,
                    new MakeFieldFinalFix(),
                    new AddVolatileFix(),
                    new ConvertToAtomicReferenceFix());
        }
    }

    private static void inspectSharedMutableCaches(PsiClass psiClass, ProblemsHolder holder) {
        for (PsiField field : psiClass.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC) || isConcurrentOrSynchronizedType(field.getType())
                    || hasConcurrentInitializer(field)) {
                continue;
            }
            if (!isMutableCacheField(field)) {
                continue;
            }
            PsiElement target = (field.getNameIdentifier() != null) ? field.getNameIdentifier() : field;
            holder.registerProblem(target,
                    "Shared mutable cache inside singleton bean should use ConcurrentHashMap or explicit locking",
                    ProblemHighlightType.ERROR,
                    new ConvertMapToConcurrentHashMapFix());
        }
    }

    private static void inspectAsyncSharedState(PsiClass psiClass, ProblemsHolder holder) {
        for (PsiMethod method : psiClass.getMethods()) {
            if (!hasAnnotation(method, "org.springframework.scheduling.annotation.Async", "Async") || method.getBody() == null) {
                continue;
            }
            method.getBody().accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
                    super.visitAssignmentExpression(expression);
                    PsiField field = referencedField(expression.getLExpression());
                    if (field != null && isUnsafeSharedStateField(field)) {
                        holder.registerProblem(expression.getLExpression(),
                                "@Async method writes shared mutable bean state without synchronization",
                                ProblemHighlightType.WARNING);
                    }
                }

                @Override
                public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
                    super.visitMethodCallExpression(expression);
                    PsiField field = referencedField(expression.getMethodExpression().getQualifierExpression());
                    if (field != null && isUnsafeSharedStateField(field) && isMutatingMethod(expression)) {
                        holder.registerProblem(expression,
                                "@Async method mutates shared bean state without a thread-safe type or explicit locking",
                                ProblemHighlightType.WARNING);
                    }
                }
            });
        }
    }

    private static boolean isSpringBean(PsiClass psiClass) {
        PsiModifierList modifiers = psiClass.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if (name == null) {
                continue;
            }
            if (name.contains(".")) {
                if ("org.springframework.stereotype.Component".equals(name)
                        || "org.springframework.stereotype.Service".equals(name)
                        || "org.springframework.stereotype.Repository".equals(name)
                        || "org.springframework.stereotype.Controller".equals(name)
                        || "org.springframework.web.bind.annotation.RestController".equals(name)
                        || "org.springframework.context.annotation.Configuration".equals(name)
                        || "org.springframework.boot.autoconfigure.SpringBootApplication".equals(name)) {
                    return true;
                }
            } else {
                if ("Component".equals(name) || "Service".equals(name)
                        || "Repository".equals(name) || "Controller".equals(name)
                        || "RestController".equals(name) || "Configuration".equals(name)
                        || "SpringBootApplication".equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOnlyWrittenDuringInitialization(PsiClass psiClass, PsiField field) {
        boolean sawWrite = false;
        for (PsiReference reference : ReferencesSearch.search(field, GlobalSearchScope.projectScope(field.getProject())).findAll()) {
            PsiElement element = reference.getElement();
            if (!(element instanceof PsiReferenceExpression ref)) {
                continue;
            }
            if (!isWriteAccess(ref)) {
                continue;
            }
            sawWrite = true;
            PsiMethod method = PsiTreeUtil.getParentOfType(ref, PsiMethod.class);
            if (method == null || method.getContainingClass() != psiClass) {
                return false;
            }
            if (!method.isConstructor() && !hasAnnotation(method,
                    "jakarta.annotation.PostConstruct", "PostConstruct")
                    && !hasAnnotation(method, "javax.annotation.PostConstruct", "PostConstruct")) {
                return false;
            }
        }
        return sawWrite;
    }

    private static boolean isWriteAccess(PsiReferenceExpression ref) {
        PsiElement parent = ref.getParent();
        if (parent instanceof PsiAssignmentExpression assignment) {
            return assignment.getLExpression() == ref;
        }
        return parent instanceof com.intellij.psi.PsiPostfixExpression
                || parent instanceof com.intellij.psi.PsiPrefixExpression;
    }

    private static boolean isSpringBeanDependency(PsiField field) {
        PsiClass fieldClass = resolvedClass(field.getType());
        return fieldClass != null && isSpringBean(fieldClass);
    }

    private static boolean isLombokConstructorInjectedDependency(PsiClass psiClass, PsiField field) {
        if (!hasAnnotation(psiClass.getModifierList(), "lombok.AllArgsConstructor", "AllArgsConstructor")
                && !hasAnnotation(psiClass.getModifierList(), "lombok.RequiredArgsConstructor", "RequiredArgsConstructor")) {
            return false;
        }
        PsiClass fieldClass = resolvedClass(field.getType());
        return fieldClass != null && fieldClass.isPhysical() && !isSimpleValueType(field.getType());
    }

    private static @Nullable PsiClass resolvedClass(PsiType type) {
        if (type instanceof PsiClassType classType) {
            return classType.resolve();
        }
        return null;
    }

    private static boolean isSimpleValueType(PsiType type) {
        String canonical = rawCanonicalText(type);
        if (canonical == null) {
            return false;
        }
        return canonical.startsWith("java.lang.") || canonical.startsWith("java.time.")
                || canonical.startsWith("java.math.") || canonical.isEmpty();
    }

    private static boolean hasAnnotation(PsiModifierList modifierList, String fqn, String shortName) {
        if (modifierList == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifierList.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if (name == null) {
                continue;
            }
            if (name.contains(".")) {
                if (fqn.equals(name)) {
                    return true;
                }
            } else {
                if (shortName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAnnotation(PsiField field, String fqn, String shortName) {
        return hasAnnotation(field.getModifierList(), fqn, shortName);
    }

    private static boolean hasAnnotation(PsiMethod method, String fqn, String shortName) {
        return hasAnnotation(method.getModifierList(), fqn, shortName);
    }

    private static boolean isUnsafeSharedStateField(PsiField field) {
        return !field.hasModifierProperty(PsiModifier.STATIC) && !field.hasModifierProperty(PsiModifier.FINAL)
                && !field.hasModifierProperty(PsiModifier.VOLATILE)
                && !isThreadSafeOrInfrastructureType(field.getType())
                && !hasAnnotation(field, "org.springframework.beans.factory.annotation.Autowired", "Autowired")
                && !hasAnnotation(field, "org.springframework.beans.factory.annotation.Value", "Value");
    }

    private static boolean isThreadSafeOrInfrastructureType(PsiType type) {
        String canonical = rawCanonicalText(type);
        if (canonical != null) {
            if (canonical.startsWith("java.util.concurrent.")
                    || canonical.startsWith("java.util.concurrent.atomic.")
                    || "java.lang.String".equals(canonical)
                    || canonical.startsWith("org.slf4j.")) {
                return true;
            }
        }
        PsiClass resolved = resolvedClass(type);
        if (resolved != null) {
            String qn = resolved.getQualifiedName();
            if (qn != null && (qn.startsWith("java.util.concurrent.")
                    || qn.startsWith("java.util.concurrent.atomic."))) {
                return true;
            }
        }
        return isSpringInfrastructureType(type);
    }

    private static boolean isSpringInfrastructureType(PsiType type) {
        PsiClass resolved = resolvedClass(type);
        if (resolved == null) {
            return false;
        }
        if (SpringInspectionUtil.isSpringDataRepository(resolved)) {
            return true;
        }
        if (isSpringBean(resolved)) {
            return true;
        }
        String qualifiedName = resolved.getQualifiedName();
        return qualifiedName != null
                && (qualifiedName.startsWith("org.springframework.")
                || qualifiedName.startsWith("org.springframework.data."));
    }

    private static boolean isConcurrentOrSynchronizedType(PsiType type) {
        String canonical = rawCanonicalText(type);
        return canonical != null && canonical.startsWith("java.util.concurrent.");
    }

    private static boolean hasConcurrentInitializer(PsiField field) {
        if (!(field.getInitializer() instanceof PsiNewExpression newExpression)) {
            return false;
        }
        PsiType type = newExpression.getType();
        return isConcurrentOrSynchronizedType(type);
    }

    private static boolean isMutableCacheField(PsiField field) {
        String canonical = rawCanonicalText(field.getType());
        if (canonical == null || !MUTABLE_CACHE_TYPES.contains(canonical)) {
            return false;
        }
        return field.getInitializer() instanceof PsiNewExpression;
    }

    private static @Nullable String rawCanonicalText(PsiType type) {
        if (type instanceof PsiClassType classType) {
            return classType.rawType().getCanonicalText();
        }
        return (type != null) ? type.getCanonicalText() : null;
    }

    private static @Nullable PsiField referencedField(PsiExpression expression) {
        if (expression instanceof PsiReferenceExpression reference && reference.resolve() instanceof PsiField field) {
            return field;
        }
        return null;
    }

    private static boolean isMutatingMethod(PsiMethodCallExpression expression) {
        String name = expression.getMethodExpression().getReferenceName();
        return "add".equals(name) || "addAll".equals(name) || "put".equals(name) || "putAll".equals(name)
                || "remove".equals(name) || "clear".equals(name) || "set".equals(name) || "compute".equals(name)
                || "computeIfAbsent".equals(name) || "computeIfPresent".equals(name);
    }

    private enum Kind {
        STATEFUL_SINGLETON,
        SHARED_MUTABLE_CACHE,
        ASYNC_SHARED_MUTABLE_STATE
    }

    public static class StatefulSingleton extends SpringBeanConcurrencyInspection {
        public StatefulSingleton() {
            super(Kind.STATEFUL_SINGLETON);
        }
    }

    public static class SharedMutableCache extends SpringBeanConcurrencyInspection {
        public SharedMutableCache() {
            super(Kind.SHARED_MUTABLE_CACHE);
        }
    }

    public static class AsyncSharedMutableState extends SpringBeanConcurrencyInspection {
        public AsyncSharedMutableState() {
            super(Kind.ASYNC_SHARED_MUTABLE_STATE);
        }
    }

    private static final class MakeFieldFinalFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return getFamilyName();
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return "Make field final";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiField field = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
            if (field == null || field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            field.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        }
    }

    private static final class AddVolatileFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return getFamilyName();
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return "Mark field volatile";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiField field = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
            if (field == null || field.hasModifierProperty(PsiModifier.VOLATILE)) {
                return;
            }
            field.getModifierList().setModifierProperty(PsiModifier.VOLATILE, true);
        }
    }

    private static final class ConvertToAtomicReferenceFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return getFamilyName();
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return "Convert field to atomic type";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiField field = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
            if (field == null || field.getType() == null) {
                return;
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiTypeElement typeElement = field.getTypeElement();
            if (typeElement == null) {
                return;
            }
            AtomicMapping mapping = determineAtomicMapping(field.getType());
            String atomicType = mapping.atomicClass;
            String originalType = field.getType().getCanonicalText();
            String newTypeText;
            String newInitializerText;
            if (mapping.isReference) {
                newTypeText = "java.util.concurrent.atomic." + atomicType + "<" + originalType + ">";
                newInitializerText = (field.getInitializer() != null)
                        ? "new java.util.concurrent.atomic." + atomicType + "<>(" + field.getInitializer().getText() + ")"
                        : "new java.util.concurrent.atomic." + atomicType + "<>()";
            } else {
                newTypeText = "java.util.concurrent.atomic." + atomicType;
                newInitializerText = (field.getInitializer() != null)
                        ? "new java.util.concurrent.atomic." + atomicType + "(" + field.getInitializer().getText() + ")"
                        : "new java.util.concurrent.atomic." + atomicType + "()";
            }
            typeElement.replace(factory.createTypeElementFromText(newTypeText, field));
            PsiExpression initializer = field.getInitializer();
            rewriteReferences(project, field);
            PsiExpression replacement = factory.createExpressionFromText(newInitializerText, field);
            if (initializer != null) {
                initializer.replace(replacement);
            } else {
                field.setInitializer(replacement);
            }
            if (field.getContainingFile() instanceof PsiJavaFile javaFile) {
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(javaFile);
            }
        }

        private record AtomicMapping(String atomicClass, boolean isReference) { }

        private static AtomicMapping determineAtomicMapping(PsiType type) {
            String canonical = type.getCanonicalText();
            if (type instanceof com.intellij.psi.PsiPrimitiveType) {
                return switch (canonical) {
                    case "long" -> new AtomicMapping("AtomicLong", false);
                    case "int" -> new AtomicMapping("AtomicInteger", false);
                    case "boolean" -> new AtomicMapping("AtomicBoolean", false);
                    default -> new AtomicMapping("AtomicReference", true);
                };
            }
            return switch (canonical) {
                case "java.lang.Long" -> new AtomicMapping("AtomicLong", false);
                case "java.lang.Integer" -> new AtomicMapping("AtomicInteger", false);
                case "java.lang.Boolean" -> new AtomicMapping("AtomicBoolean", false);
                default -> new AtomicMapping("AtomicReference", true);
            };
        }

        private static void rewriteReferences(Project project, PsiField field) {
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            for (PsiReference reference : ReferencesSearch.search(field, GlobalSearchScope.projectScope(project)).findAll()) {
                PsiElement element = reference.getElement();
                if (!(element instanceof PsiReferenceExpression referenceExpression) || !referenceExpression.isValid()) {
                    continue;
                }
                PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(referenceExpression,
                        PsiAssignmentExpression.class, false);
                if (assignment != null && assignment.getLExpression() == referenceExpression && assignment.getRExpression() != null) {
                    assignment.replace(factory.createExpressionFromText(referenceExpression.getText() + ".set("
                            + assignment.getRExpression().getText() + ")", assignment));
                    continue;
                }
                PsiElement parent = referenceExpression.getParent();
                if (parent instanceof PsiAssignmentExpression parentAssignment && parentAssignment.getLExpression() == referenceExpression) {
                    continue;
                }
                if (!(parent instanceof PsiReferenceExpression)) {
                    referenceExpression.replace(factory.createExpressionFromText(referenceExpression.getText() + ".get()", referenceExpression));
                }
            }
        }
    }

    private static final class ConvertMapToConcurrentHashMapFix implements LocalQuickFix {

        @Override
        @NotNull
        public String getName() {
            return getFamilyName();
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return "Use ConcurrentHashMap";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiField field = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
            if (field == null || !(field.getType() instanceof PsiClassType classType)) {
                return;
            }
            String rawType = classType.rawType().getCanonicalText();
            if (!"java.util.Map".equals(rawType) && !rawType.endsWith("HashMap") && !rawType.endsWith("LinkedHashMap")) {
                return;
            }
            PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
            PsiTypeElement typeElement = field.getTypeElement();
            if (typeElement != null) {
                typeElement.replace(factory.createTypeElementFromText("java.util.concurrent.ConcurrentMap"
                        + genericText(field.getType()), field));
            }
            PsiExpression initializer = field.getInitializer();
            if (initializer != null) {
                initializer.replace(factory.createExpressionFromText("new java.util.concurrent.ConcurrentHashMap<>()", field));
            }
            if (field.getContainingFile() instanceof PsiJavaFile javaFile) {
                JavaCodeStyleManager.getInstance(project).shortenClassReferences(javaFile);
            }
        }

        private static String genericText(PsiType type) {
            String text = type.getCanonicalText();
            int index = text.indexOf('<');
            return (index >= 0) ? text.substring(index) : "";
        }
    }
}
