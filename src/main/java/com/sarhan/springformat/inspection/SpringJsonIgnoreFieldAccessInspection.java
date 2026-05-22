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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Detects accesses to {@code @JsonIgnore}'d fields on DTO parameters inside
 * Spring-managed components (services, validators, event listeners, etc.).
 *
 * <p>When a DTO arrives in a Spring component through Jackson deserialization
 * (e.g. from a controller {@code @RequestBody} that is then passed to a service
 * or validator), fields annotated with {@code @JsonIgnore} or listed in
 * {@code @JsonIgnoreProperties} are {@code null} or default values.
 * Accessing them silently produces incorrect data.
 *
 * <p>This inspection scans any Spring component method and flags every read
 * access (field reference or getter call) on a parameter whose declared type
 * carries Jackson-excluded properties.
 *
 * <p><b>False-positive mitigation:</b> the inspection only fires when the
 * parameter type actually has {@code @JsonIgnore} annotations or a
 * {@code @JsonSerialize(as = View.class)} restriction. Fully-populated
 * JPA entities that happen to carry {@code @JsonIgnore} for serialization
 * purposes but are loaded from the database may trigger a warning; in that
 * case the developer should suppress the inspection for that method.
 *
 * @author Akmal Sarhan
 */
public class SpringJsonIgnoreFieldAccessInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitClass(@NotNull PsiClass psiClass) {
                if (!isSpringComponent(psiClass)) {
                    return;
                }
                for (PsiMethod method : psiClass.getMethods()) {
                    analyseMethod(method, holder);
                }
            }
        };
    }

    // -----------------------------------------------------------------------
    // Component detection
    // -----------------------------------------------------------------------

    private static boolean isSpringComponent(@NotNull PsiClass psiClass) {
        PsiModifierList modifiers = psiClass.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if (name != null) {
                if (name.startsWith("org.springframework.stereotype.")
                        || name.startsWith("org.springframework.web.bind.annotation.")
                        || name.startsWith("org.springframework.context.annotation.")
                        || name.startsWith("org.springframework.boot.autoconfigure.")
                        || "Component".equals(name)
                        || "Service".equals(name)
                        || "Repository".equals(name)
                        || "Controller".equals(name)
                        || "RestController".equals(name)
                        || "Configuration".equals(name)
                        || "SpringBootApplication".equals(name)) {
                    return true;
                }
            }
            // Fallback: raw text check for unresolved annotations
            String text = annotation.getText();
            if (text != null && (text.equals("@Service") || text.equals("@Component")
                    || text.equals("@Repository") || text.equals("@Controller")
                    || text.equals("@RestController") || text.equals("@Configuration"))) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Method analysis
    // -----------------------------------------------------------------------

    private static void analyseMethod(@NotNull PsiMethod method, @NotNull ProblemsHolder holder) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return;
        }

        // Build map: parameter → unavailable properties
        Map<PsiParameter, Set<String>> paramUnavailable = new HashMap<>();
        boolean hasClassParam = false;
        boolean privateHelperMethod = method.hasModifierProperty(PsiModifier.PRIVATE);
        for (PsiParameter param : method.getParameterList().getParameters()) {
            PsiClass paramClass = resolveClass(param.getType());
            if (paramClass == null) {
                continue;
            }
            boolean likelyFromJackson = isLikelyJacksonDeserialized(param);
            boolean jpaEntity = SpringInspectionUtil.isJpaEntityOrModel(paramClass);
            if (privateHelperMethod && !likelyFromJackson) {
                continue;
            }
            // Include the parameter when:
            //   - it is explicitly marked as Jackson input (@Valid / @RequestBody)
            //   - OR it is a plain DTO (no JPA indicators) — likely passed from a controller
            // Skip when it is a JPA entity WITHOUT Jackson markers — then it almost
            // certainly arrived from a repository and all fields are populated.
            if (jpaEntity && !likelyFromJackson) {
                continue;
            }
            hasClassParam = true;
            Set<String> unavailable = computeUnavailableProperties(paramClass);
            paramUnavailable.put(param, unavailable);
        }
        if (!hasClassParam) {
            return;
        }

        ParameterTracker tracker = new ParameterTracker(paramUnavailable);
        seedLocalVariableAliases(body, tracker);
        body.accept(new AccessChecker(tracker, holder));
    }

    private static void seedLocalVariableAliases(@NotNull PsiCodeBlock body,
                                                   @NotNull ParameterTracker tracker) {
        boolean changed = true;
        while (changed) {
            boolean[] didAdd = {false};
            body.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitDeclarationStatement(@NotNull PsiDeclarationStatement stmt) {
                    super.visitDeclarationStatement(stmt);
                    for (PsiElement e : stmt.getDeclaredElements()) {
                        if (e instanceof PsiLocalVariable var && var.getInitializer() != null
                                && !tracker.isTracked(var)
                                && tracker.isTracked(var.getInitializer())) {
                            tracker.track(var, tracker.unavailableFor(var.getInitializer()));
                            didAdd[0] = true;
                        }
                    }
                }

                @Override
                public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expr) {
                    super.visitAssignmentExpression(expr);
                    PsiExpression rhs = expr.getRExpression();
                    if (rhs == null || !tracker.isTracked(rhs)) {
                        return;
                    }
                    if (expr.getLExpression() instanceof PsiReferenceExpression lref
                            && lref.resolve() instanceof PsiLocalVariable lv
                            && !tracker.isTracked(lv)) {
                        tracker.track(lv, tracker.unavailableFor(rhs));
                        didAdd[0] = true;
                    }
                }
            });
            changed = didAdd[0];
        }
    }

    /**
     * Returns the set of property names that are known to be UNAVAILABLE on
     * instances of this class after Jackson deserialization, or {@code null}
     * if the class has no Jackson restriction.
     */
    private static Set<String> computeUnavailableProperties(@NotNull PsiClass psiClass) {
        Set<String> unavailable = new HashSet<>();
        boolean hasRestriction = false;

        // 1. @JsonIgnoreProperties at class level
        PsiAnnotation jsonIgnoreProps = findAnnotation(psiClass, "JsonIgnoreProperties");
        if (jsonIgnoreProps != null) {
            hasRestriction = true;
            PsiAnnotationMemberValue value = jsonIgnoreProps.findAttributeValue("value");
            if (value instanceof PsiArrayInitializerMemberValue arr) {
                for (PsiAnnotationMemberValue v : arr.getInitializers()) {
                    if (v instanceof PsiLiteralExpression lit && lit.getValue() instanceof String s) {
                        unavailable.add(s);
                    }
                }
            } else if (value instanceof PsiLiteralExpression lit && lit.getValue() instanceof String s) {
                unavailable.add(s);
            }
        }

        // 2. @JsonSerialize(as = View) restriction
        PsiAnnotation jsonSerialize = findAnnotation(psiClass, "JsonSerialize");
        if (jsonSerialize != null) {
            PsiAnnotationMemberValue asValue = jsonSerialize.findAttributeValue("as");
            if (asValue instanceof PsiClassObjectAccessExpression classObj) {
                PsiType type = classObj.getOperand().getType();
                PsiClass viewClass = resolveClass(type);
                if (viewClass != null && viewClass.isInterface()) {
                    hasRestriction = true;
                    Set<String> viewProps = new HashSet<>();
                    for (PsiMethod m : viewClass.getMethods()) {
                        String prop = getterToProperty(m.getName());
                        if (prop != null) {
                            viewProps.add(prop);
                        }
                    }
                    viewProps.add("id");
                    // Any field not in the view is unavailable
                    for (PsiField field : psiClass.getAllFields()) {
                        if (field.hasModifierProperty(PsiModifier.STATIC)) {
                            continue;
                        }
                        if (!viewProps.contains(field.getName())) {
                            unavailable.add(field.getName());
                        }
                    }
                    return unavailable.isEmpty() ? null : unavailable;
                }
            }
        }

        // 3. @JsonIgnore on individual fields
        for (PsiField field : psiClass.getAllFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            if (hasAnnotation(field, "JsonIgnore")) {
                hasRestriction = true;
                unavailable.add(field.getName());
            }
        }

        return hasRestriction ? unavailable : null;
    }

    // -----------------------------------------------------------------------
    // Tracking and checking
    // -----------------------------------------------------------------------

    private static PsiClass resolveClass(PsiType type) {
        if (type instanceof PsiClassType classType) {
            return classType.resolve();
        }
        return null;
    }

    private static PsiAnnotation findAnnotation(@NotNull PsiModifierListOwner owner, @NotNull String shortName) {
        PsiModifierList mods = owner.getModifierList();
        if (mods == null) {
            return null;
        }
        for (PsiAnnotation a : mods.getAnnotations()) {
            String name = a.getQualifiedName();
            if (name == null && a.getNameReferenceElement() != null) {
                name = a.getNameReferenceElement().getText();
            }
            if (name != null && (shortName.equals(name) || name.endsWith("." + shortName))) {
                return a;
            }
        }
        return null;
    }

    private static boolean hasAnnotation(@NotNull PsiModifierListOwner owner, @NotNull String shortName) {
        return findAnnotation(owner, shortName) != null;
    }

    /**
     * Converts a getter method name to a property name.
     * {@code getFoo} → {@code foo}, {@code isActive} → {@code active}.
     * Returns {@code null} if the name does not follow the getter convention.
     */
    private static String getterToProperty(@NotNull String methodName) {
        int prefixLen = 0;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            prefixLen = 3;
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            prefixLen = 2;
        }
        if (prefixLen == 0) {
            return null;
        }
        return Character.toLowerCase(methodName.charAt(prefixLen))
                + methodName.substring(prefixLen + 1);
    }

    /**
     * Returns {@code true} when the parameter carries an annotation that marks it
     * as arriving from Jackson deserialization ({@code @RequestBody},
     * {@code @Valid}, {@code @Validated}).
     */
    private static boolean isLikelyJacksonDeserialized(@NotNull PsiParameter param) {
        PsiModifierList mods = param.getModifierList();
        if (mods == null) {
            return false;
        }
        for (PsiAnnotation a : mods.getAnnotations()) {
            if (SpringInspectionUtil.isAnyAnnotation(a,
                    "org.springframework.web.bind.annotation.RequestBody",
                    "jakarta.validation.Valid",
                    "javax.validation.Valid",
                    "org.springframework.validation.annotation.Validated")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tracks which expressions derive from restricted DTO parameters.
     */
    private static final class ParameterTracker {
        // Expressions tracked as deriving from a restricted DTO parameter.
        // Value is the set of unavailable properties, or null when the underlying
        // type has no Jackson restriction (still tracked for getter chaining).
        private final Map<PsiElement, Set<String>> tracked = new HashMap<>();

        ParameterTracker(@NotNull Map<PsiParameter, Set<String>> paramUnavailable) {
            for (Map.Entry<PsiParameter, Set<String>> e : paramUnavailable.entrySet()) {
                this.tracked.put(e.getKey(), e.getValue());
            }
        }

        boolean isTracked(@NotNull PsiElement expr) {
            if (this.tracked.containsKey(expr)) {
                return true;
            }
            if (expr instanceof PsiReferenceExpression ref) {
                PsiElement resolved = ref.resolve();
                if (resolved != null && this.tracked.containsKey(resolved)) {
                    return true;
                }
            }
            if (expr instanceof PsiMethodCallExpression call) {
                PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
                if (qualifier != null && isTracked(qualifier)) {
                    PsiMethod method = call.resolveMethod();
                    if (method != null && getterToProperty(method.getName()) != null) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Nullable
        Set<String> unavailableFor(@NotNull PsiElement expr) {
            Set<String> direct = this.tracked.get(expr);
            if (direct != null) {
                return direct;
            }
            if (this.tracked.containsKey(expr)) {
                return null; // tracked but unrestricted
            }
            if (expr instanceof PsiReferenceExpression ref) {
                PsiElement resolved = ref.resolve();
                if (resolved != null) {
                    return this.tracked.get(resolved);
                }
            }
            if (expr instanceof PsiMethodCallExpression call) {
                PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
                if (qualifier != null && isTracked(qualifier)) {
                    PsiMethod method = call.resolveMethod();
                    if (method != null) {
                        String prop = getterToProperty(method.getName());
                        if (prop != null) {
                            Set<String> parentUnavailable = unavailableFor(qualifier);
                            if (parentUnavailable != null && parentUnavailable.contains(prop)) {
                                return null; // getter itself is unavailable
                            }
                            PsiClass returnClass = resolveClass(method.getReturnType());
                            if (returnClass != null) {
                                Set<String> returnUnavailable = computeUnavailableProperties(returnClass);
                                if (returnUnavailable != null && !returnUnavailable.isEmpty()) {
                                    track(call, returnUnavailable);
                                    return returnUnavailable;
                                }
                            }
                            // Return type is unrestricted, but call is tracked
                            track(call, null);
                            return null;
                        }
                    }
                }
            }
            return null;
        }

        void track(@NotNull PsiElement expr, @Nullable Set<String> unavailable) {
            this.tracked.put(expr, unavailable);
        }
    }

    private static final class AccessChecker extends JavaRecursiveElementVisitor {
        private final ParameterTracker tracker;
        private final ProblemsHolder holder;

        AccessChecker(@NotNull ParameterTracker tracker, @NotNull ProblemsHolder holder) {
            this.tracker = tracker;
            this.holder = holder;
        }

        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            // Skip method-reference expressions — the enclosing PsiMethodCallExpression
            // is handled by visitMethodCallExpression to avoid duplicate reports.
            if (expression.getParent() instanceof PsiMethodCallExpression) {
                return;
            }
            PsiExpression qualifier = expression.getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (!this.tracker.isTracked(qualifier)) {
                return;
            }
            Set<String> unavailable = this.tracker.unavailableFor(qualifier);
            if (unavailable == null) {
                return;
            }
            PsiElement resolved = expression.resolve();
            if (resolved instanceof PsiField field) {
                if (unavailable.contains(field.getName())) {
                    report(expression, field.getName());
                }
            } else if (resolved instanceof PsiMethod method) {
                String prop = getterToProperty(method.getName());
                if (prop != null && unavailable.contains(prop)) {
                    report(expression, prop);
                }
            }
        }

        @Override
        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
            super.visitMethodCallExpression(call);
            PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
            if (qualifier == null) {
                return;
            }
            if (!this.tracker.isTracked(qualifier)) {
                return;
            }
            PsiMethod resolved = call.resolveMethod();
            if (resolved == null) {
                return;
            }
            String prop = getterToProperty(resolved.getName());
            if (prop == null) {
                return;
            }
            Set<String> unavailable = this.tracker.unavailableFor(qualifier);
            if (unavailable != null && unavailable.contains(prop)) {
                report(call, prop);
                return;
            }
            // Propagate tracking through getter chains even when the qualifier
            // has no Jackson restriction (e.g. user.getAddress().getSecretCode()).
            PsiType returnType = resolved.getReturnType();
            if (returnType == null) {
                return;
            }
            PsiClass returnClass = resolveClass(returnType);
            if (returnClass == null) {
                return;
            }
            Set<String> returnUnavailable = computeUnavailableProperties(returnClass);
            if (returnUnavailable != null && !returnUnavailable.isEmpty()) {
                this.tracker.track(call, returnUnavailable);
            } else {
                this.tracker.track(call, null);
            }
        }

        private void report(@NotNull PsiElement element, @NotNull String property) {
            this.holder.registerProblem(element,
                    "Property '" + property + "' is @JsonIgnore'd and will be null "
                            + "when this object comes from Jackson deserialization",
                    ProblemHighlightType.ERROR);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
}
