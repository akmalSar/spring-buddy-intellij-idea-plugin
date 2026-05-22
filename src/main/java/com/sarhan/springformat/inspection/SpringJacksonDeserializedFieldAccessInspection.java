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
 * Detects controller methods that deserialize objects via {@code @RequestBody} or
 * {@code @Valid} and then access properties that are not available after Jackson
 * deserialization, including through chained getter navigation.
 *
 * <p>Two scenarios are covered:
 * <ol>
 *   <li><b>{@code @JsonIgnore} / {@code @JsonIgnoreProperties}</b> — fields excluded
 *       from deserialization yield {@code null} or default values at runtime.</li>
 *   <li><b>View-interface deserialization</b> — {@code @JsonSerialize(as = View.class)}
 *       means only the view-interface properties (plus {@code id}) are guaranteed to
 *       be available. Accessing any other property throws at runtime when a proxy
 *       guard is installed.</li>
 * </ol>
 *
 * <p>The inspection tracks the deserialized parameter and every getter call on it
 * recursively, so deep navigation such as
 * {@code user.getAddress().getStreet()} is also checked at each level.
 *
 * @author Akmal Sarhan
 */
public class SpringJacksonDeserializedFieldAccessInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        if (!SpringInspectionUtil.isInJavaFile(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new JavaElementVisitor() {
            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                if (!isControllerMethod(method)) {
                    return;
                }
                for (PsiParameter param : method.getParameterList().getParameters()) {
                    if (!isDeserializedParameter(param)) {
                        continue;
                    }
                    PsiClass paramClass = resolveClass(param.getType());
                    if (paramClass == null) {
                        continue;
                    }
                    Set<String> available = computeAvailableProperties(paramClass);
                    DeserializedTracker tracker = new DeserializedTracker();
                    tracker.track(param, available);
                    checkMethodBody(method, tracker, holder);
                }
            }
        };
    }

    // -----------------------------------------------------------------------
    // Controller detection
    // -----------------------------------------------------------------------

    private static boolean isControllerMethod(@NotNull PsiMethod method) {
        PsiClass cls = method.getContainingClass();
        if (cls == null) {
            return false;
        }
        PsiModifierList classMods = cls.getModifierList();
        if (classMods == null) {
            return false;
        }
        boolean isController = false;
        for (PsiAnnotation a : classMods.getAnnotations()) {
            if (isControllerAnnotation(a)) {
                isController = true;
                break;
            }
        }
        if (!isController) {
            return false;
        }
        PsiModifierList methodMods = method.getModifierList();
        for (PsiAnnotation a : methodMods.getAnnotations()) {
            if (isMappingAnnotation(a)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isControllerAnnotation(@NotNull PsiAnnotation annotation) {
        return SpringInspectionUtil.isAnyAnnotation(annotation,
                "org.springframework.web.bind.annotation.RestController",
                "org.springframework.stereotype.Controller");
    }

    private static boolean isMappingAnnotation(@NotNull PsiAnnotation annotation) {
        return SpringInspectionUtil.isAnyAnnotation(annotation,
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping",
                "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PatchMapping");
    }

    // -----------------------------------------------------------------------
    // Parameter detection
    // -----------------------------------------------------------------------

    private static boolean isDeserializedParameter(@NotNull PsiParameter param) {
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

    private static PsiClass resolveClass(PsiType type) {
        if (type instanceof PsiClassType classType) {
            return classType.resolve();
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Available-property computation
    // -----------------------------------------------------------------------

    /**
     * Returns a set of property names that are guaranteed to be available after
     * Jackson deserialization, or {@code null} if no restriction is detected
     * (inspection should be silent).
     */
    private static Set<String> computeAvailableProperties(@NotNull PsiClass psiClass) {
        Set<String> available = new HashSet<>();

        // 1. Check for @JsonSerialize(as = SomeView.class) on the class
        PsiAnnotation jsonSerialize = findAnnotation(psiClass, "JsonSerialize");
        if (jsonSerialize != null) {
            PsiAnnotationMemberValue asValue = jsonSerialize.findAttributeValue("as");
            if (asValue instanceof PsiClassObjectAccessExpression classObj) {
                PsiType type = classObj.getOperand().getType();
                PsiClass viewClass = resolveClass(type);
                if (viewClass != null && viewClass.isInterface()) {
                    for (PsiMethod m : viewClass.getMethods()) {
                        String prop = getterToProperty(m.getName());
                        if (prop != null) {
                            available.add(prop);
                        }
                    }
                    available.add("id");
                    return available;
                }
            }
        }

        // 2. Check for @JsonIgnoreProperties at class level
        Set<String> ignoredAtClassLevel = new HashSet<>();
        PsiAnnotation jsonIgnoreProps = findAnnotation(psiClass, "JsonIgnoreProperties");
        if (jsonIgnoreProps != null) {
            PsiAnnotationMemberValue value = jsonIgnoreProps.findAttributeValue("value");
            if (value instanceof PsiArrayInitializerMemberValue arr) {
                for (PsiAnnotationMemberValue v : arr.getInitializers()) {
                    if (v instanceof PsiLiteralExpression lit && lit.getValue() instanceof String s) {
                        ignoredAtClassLevel.add(s);
                    }
                }
            } else if (value instanceof PsiLiteralExpression lit && lit.getValue() instanceof String s) {
                ignoredAtClassLevel.add(s);
            }
        }

        // 3. Build available set from all non-ignored fields
        boolean hasRestriction = !ignoredAtClassLevel.isEmpty();
        for (PsiField field : psiClass.getAllFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            String fieldName = field.getName();
            if (ignoredAtClassLevel.contains(fieldName)) {
                hasRestriction = true;
                continue;
            }
            if (hasAnnotation(field, "JsonIgnore")) {
                hasRestriction = true;
                continue;
            }
            available.add(fieldName);
            String getterProp = getterToProperty("get" + capitalize(fieldName));
            if (getterProp != null) {
                available.add(getterProp);
            }
        }

        if (!hasRestriction) {
            return null;
        }
        return available;
    }

    // -----------------------------------------------------------------------
    // Body analysis with chained getter tracking
    // -----------------------------------------------------------------------

    private static void checkMethodBody(@NotNull PsiMethod method,
                                         @NotNull DeserializedTracker tracker,
                                         @NotNull ProblemsHolder holder) {
        PsiCodeBlock body = method.getBody();
        if (body == null) {
            return;
        }
        seedLocalVariableAliases(body, tracker);
        body.accept(new BodyChecker(tracker, holder));
    }

    private static void seedLocalVariableAliases(@NotNull PsiCodeBlock body,
                                                   @NotNull DeserializedTracker tracker) {
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
                            tracker.track(var, tracker.availableFor(var.getInitializer()));
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
                        tracker.track(lv, tracker.availableFor(rhs));
                        didAdd[0] = true;
                    }
                }
            });
            changed = didAdd[0];
        }
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

    @Nullable
    private static PsiField findFieldByPropertyName(@NotNull PsiClass psiClass, @NotNull String propertyName) {
        for (PsiField field : psiClass.getAllFields()) {
            if (propertyName.equals(field.getName())) {
                return field;
            }
        }
        return null;
    }

    /**
     * Returns the set of property names available on the value of a field that carries
     * {@code @JsonSerialize(as = SomeView.class)}, or {@code null} when the field has
     * no such annotation.
     */
    @Nullable
    private static Set<String> computeFieldRestrictedProperties(@NotNull PsiClass parentClass,
                                                                 @NotNull String propertyName) {
        PsiField field = findFieldByPropertyName(parentClass, propertyName);
        if (field == null) {
            return null;
        }
        PsiAnnotation jsonSerialize = findAnnotation(field, "JsonSerialize");
        if (jsonSerialize == null) {
            return null;
        }
        PsiAnnotationMemberValue asValue = jsonSerialize.findAttributeValue("as");
        if (asValue instanceof PsiClassObjectAccessExpression classObj) {
            PsiType viewType = classObj.getOperand().getType();
            PsiClass viewClass = resolveClass(viewType);
            if (viewClass != null && viewClass.isInterface()) {
                Set<String> available = new HashSet<>();
                for (PsiMethod m : viewClass.getMethods()) {
                    String prop = getterToProperty(m.getName());
                    if (prop != null) {
                        available.add(prop);
                    }
                }
                available.add("id");
                return available;
            }
        }
        return null;
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

    private static String capitalize(@NotNull String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Tracks expressions that represent deserialized values and the set of
     * properties guaranteed to be available on them.
     */
    private static final class DeserializedTracker {
        // Expressions that are tracked (derive from a deserialized parameter).
        // The mapped value is the set of available properties, or null when the
        // underlying type has no Jackson restriction (unrestricted).
        private final Map<PsiElement, Set<String>> tracked = new HashMap<>();

        void track(@NotNull PsiElement expr, @Nullable Set<String> available) {
            this.tracked.put(expr, available);
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
        Set<String> availableFor(@NotNull PsiElement expr) {
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
                                        Set<String> parentAvailable = availableFor(qualifier);
                                        if (parentAvailable != null && !parentAvailable.contains(prop)) {
                                            return null; // getter itself is unavailable
                                        }
                                        PsiClass returnClass = resolveClass(method.getReturnType());
                                        if (returnClass != null) {
                                            Set<String> returnAvailable = computeAvailableProperties(returnClass);
                                            // Field-level @JsonSerialize(as = ...) on qualifier's type
                                            PsiType qualifierType = qualifier.getType();
                                            if (qualifierType != null) {
                                                PsiClass parentClass = resolveClass(qualifierType);
                                                if (parentClass != null) {
                                                    Set<String> fieldRestricted = computeFieldRestrictedProperties(parentClass, prop);
                                                    if (fieldRestricted != null) {
                                                        returnAvailable = fieldRestricted;
                                                    }
                                                }
                                            }
                                            if (returnAvailable != null) {
                                                track(call, returnAvailable);
                                                return returnAvailable;
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
    }

    private static final class BodyChecker extends JavaRecursiveElementWalkingVisitor {
        private final DeserializedTracker tracker;
        private final ProblemsHolder holder;

        BodyChecker(@NotNull DeserializedTracker tracker, @NotNull ProblemsHolder holder) {
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
            Set<String> available = this.tracker.availableFor(qualifier);
            if (available == null) {
                return;
            }
            PsiElement resolved = expression.resolve();
            if (resolved instanceof PsiField field) {
                if (!available.contains(field.getName())) {
                    report(expression, field.getName());
                }
            } else if (resolved instanceof PsiMethod resolvedMethod) {
                String prop = getterToProperty(resolvedMethod.getName());
                if (prop != null && !available.contains(prop)) {
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
            Set<String> available = this.tracker.availableFor(qualifier);
            if (available != null && !available.contains(prop)) {
                report(call, prop);
                return; // don't also track the return value of a forbidden call
            }
            // Propagate tracking through getter chains even when the qualifier
            // has no Jackson restriction (e.g. user.getAddress().getSecretCode()).
            PsiType returnType = resolved.getReturnType();
            PsiClass returnClass = (returnType != null) ? resolveClass(returnType) : null;
            Set<String> returnAvailable = (returnClass != null)
                    ? computeAvailableProperties(returnClass) : null;
            // Field-level @JsonSerialize(as = ...) overrides class-level restrictions
            PsiType qualifierType = qualifier.getType();
            if (qualifierType != null) {
                PsiClass parentClass = resolveClass(qualifierType);
                if (parentClass != null) {
                    Set<String> fieldRestricted = computeFieldRestrictedProperties(parentClass, prop);
                    if (fieldRestricted != null) {
                        returnAvailable = fieldRestricted;
                    }
                }
            }
            if (returnAvailable != null) {
                this.tracker.track(call, returnAvailable);
            } else {
                this.tracker.track(call, null);
            }
        }

        private void report(@NotNull PsiElement element, @NotNull String property) {
            this.holder.registerProblem(element,
                    "Property '" + property + "' is not available after Jackson deserialization — "
                            + "it is either @JsonIgnore'd or not part of the serialized view",
                    ProblemHighlightType.ERROR);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
}
