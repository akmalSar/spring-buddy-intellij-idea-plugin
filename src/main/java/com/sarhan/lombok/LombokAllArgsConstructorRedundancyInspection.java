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

package com.sarhan.lombok;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import org.jetbrains.annotations.NotNull;

/**
 * @author Akmal Sarhan
 */
public class LombokAllArgsConstructorRedundancyInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final List<String> SPRING_STEREOTYPES = Arrays.asList(
            "org.springframework.stereotype.Component",
            "org.springframework.stereotype.Service",
            "org.springframework.stereotype.Repository",
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.context.annotation.Configuration"
    );

    private static final String CONFIGURATION_PROPERTIES = "org.springframework.boot.context.properties.ConfigurationProperties";

    private static final List<String> NO_ARGS_REQUIRED_ANNOTATIONS = Arrays.asList(
            "javax.persistence.Entity",
            "jakarta.persistence.Entity",
            "javax.persistence.MappedSuperclass",
            "jakarta.persistence.MappedSuperclass",
            "javax.persistence.Embeddable",
            "jakarta.persistence.Embeddable"
    );

    private static boolean hasBuilderAnnotation(PsiClass aClass) {
        PsiModifierList modifiers = aClass.getModifierList();
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation a : modifiers.getAnnotations()) {
            String fqn = a.getQualifiedName();
            if ("lombok.Builder".equals(fqn) || "Builder".equals(fqn)
                    || "lombok.SuperBuilder".equals(fqn)
                    || "lombok.experimental.SuperBuilder".equals(fqn)
                    || "SuperBuilder".equals(fqn)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLikelyJpqlProjection(PsiClass aClass) {
        if (LombokAnnotationUtil.isJpaEntity(aClass)) {
            return false;
        }
        for (PsiClass iface : aClass.getInterfaces()) {
            if (LombokAnnotationUtil.hasJsonDeserialize(iface)) {
                return true;
            }
            for (PsiMethod method : iface.getAllMethods()) {
                for (PsiAnnotation ann : method.getModifierList().getAnnotations()) {
                    String fqn = ann.getQualifiedName();
                    if ("org.springframework.beans.factory.annotation.Value".equals(fqn)
                            || "Value".equals(fqn)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isBuilderAnnotation(String qName) {
        return "lombok.Builder".equals(qName)
                || "Builder".equals(qName)
                || "lombok.SuperBuilder".equals(qName)
                || "lombok.experimental.SuperBuilder".equals(qName)
                || "SuperBuilder".equals(qName);
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return MyMessageBundle.message("inspection.allargs.constructor.redundancy.display.name");
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
        return "Lombok builder";
    }

    @Override
    @NotNull
    public PsiElementVisitor buildVisitor(
            @NotNull ProblemsHolder holder,
            boolean isOnTheFly) {

                if (!LombokInspectionUtil.isInJavaFile(holder.getFile())) {
                    return PsiElementVisitor.EMPTY_VISITOR;
                }

        return new JavaElementVisitor() {

            @Override
            public void visitClass(@NotNull PsiClass aClass) {
                super.visitClass(aClass);
                if (!LombokInspectionUtil.isInJavaFile(aClass)) {
                    return;
                }

                PsiModifierList modifierList = aClass.getModifierList();
                if (modifierList == null) {
                    return;
                }

                // 1. Must have @AllArgsConstructor
                PsiAnnotation allArgsConstructorAnn = null;
                for (PsiAnnotation ann : modifierList.getAnnotations()) {
                    String qName = ann.getQualifiedName();
                    if ("lombok.AllArgsConstructor".equals(qName) || "AllArgsConstructor".equals(qName)) {
                        allArgsConstructorAnn = ann;
                        break;
                    }
                }
                if (allArgsConstructorAnn == null) {
                    return;
                }

                // Skip if @AllArgsConstructor has non-default attributes — removing
                // it would change behavior or break code (e.g., staticConstructor,
                // access level, onConstructor).
                PsiAnnotationParameterList annParams = allArgsConstructorAnn.getParameterList();
                for (PsiNameValuePair pair : annParams.getAttributes()) {
                    String attrName = pair.getAttributeName();
                    String attrValue = pair.getLiteralValue();
                    if ("staticConstructor".equals(attrName) && (attrValue != null)) {
                        return; // e.g. @AllArgsConstructor(staticConstructor = "of")
                    }
                    if ("access".equals(attrName)
                            && !"PUBLIC".equals(attrValue)
                            && !"public".equals(attrValue)) {
                        return; // e.g. @AllArgsConstructor(access = PRIVATE)
                    }
                    if ("onConstructor".equals(attrName) || "onConstructor_".equals(attrName)) {
                        return; // adds annotations to generated constructor
                    }
                    if ("onParam".equals(attrName) || "onParam_".equals(attrName)) {
                        return; // adds annotations to generated params
                    }
                }

                // 2. Manual override: explicit constructor must exist that initializes
                //    every instance field declared in THIS class (not parent fields).
                PsiMethod[] constructors = aClass.getConstructors();
                if (constructors.length == 0) {
                    return; // no manual constructor → annotation is not redundant
                }

                // Collect instance field names declared in the current class only.
                java.util.Set<String> instanceFieldNames = new java.util.HashSet<>();
                for (PsiField f : aClass.getFields()) {
                    if (!f.hasModifierProperty(PsiModifier.STATIC)) {
                        instanceFieldNames.add(f.getName());
                    }
                }

                // Find a constructor whose parameters correspond to the current class fields.
                // A true all-args constructor for this class must have at least one parameter
                // for every instance field, and each parameter name must match a field.
                PsiMethod matchingConstructor = null;
                for (PsiMethod ctor : constructors) {
                    PsiParameter[] params = ctor.getParameterList().getParameters();
                    // Parameter count must match instance field count (no more, no less)
                    if (params.length != instanceFieldNames.size()) {
                        continue;
                    }
                    boolean allParamsMatchFields = true;
                    for (PsiParameter param : params) {
                        if (!instanceFieldNames.contains(param.getName())) {
                            allParamsMatchFields = false;
                            break;
                        }
                    }
                    if (allParamsMatchFields) {
                        matchingConstructor = ctor;
                        break;
                    }
                }
                if (matchingConstructor == null) {
                    return;
                }

                // 3. Builder trifecta: @Builder + @NoArgsConstructor → @AllArgsConstructor is mandatory
                if (hasBuilderAndNoArgsConstructor(aClass)) {
                    return;
                }

                // 4. Builder annotations present — never flag @AllArgsConstructor
                // on a class that has @Builder or @SuperBuilder.  The user may still
                // be in the process of migrating constructor calls, and removing
                // @AllArgsConstructor prematurely breaks the remaining ones.
                if (LombokAnnotationUtil.hasBuilderAnnotation(aClass)) {
                    return;
                }

                // 4. Builder-hierarchy safety: removing @AllArgsConstructor from a class
                // that participates in a Lombok builder hierarchy can break subclasses or
                // nested classes that rely on the generated all-args constructor.
                if (isInBuilderHierarchy(aClass)) {
                    return;
                }

                // 5. JPQL projection safety: if the class looks like a Spring Data
                // JPA projection (implements a @JsonDeserialize/@Value-annotated
                // interface), the all-args constructor may be used in @Query
                // "select new ClassName(...)" expressions — which are invisible
                // to the PSI reference search.
                if (isLikelyJpqlProjection(aClass)) {
                    return;
                }

                // 5. Spring DI safety: @AllArgsConstructor can be the constructor Spring uses for
                // dependency injection. Removing it may make the bean impossible to instantiate.
                if (isSpringManaged(aClass)) {
                    return;
                }

                // 6. Configuration properties safety
                if (hasAnnotation(aClass, CONFIGURATION_PROPERTIES)) {
                    return;
                }

                // 7. Final field safety (non-Spring classes): if there are final fields without initializers,
                // the annotation provides the only way to instantiate legally
                if (hasUninitializedFinalFields(aClass)) {
                    return;
                }

                // 8. Zero usages: no code anywhere calls any constructor on this class.
                // This checks both the explicit constructor and class-level references
                // (covers Lombok-generated constructor usages that resolve to the class).
                if (hasConstructorUsages(aClass, matchingConstructor)) {
                    return;
                }

                // All safety checks passed → flag the annotation as redundant
                holder.registerProblem(
                        allArgsConstructorAnn,
                        MyMessageBundle.message("inspection.allargs.constructor.redundancy.problem.descriptor"),
                        ProblemHighlightType.WARNING,
                        new AllArgsConstructorRedundancyFix()
                );
            }
        };
    }

    private boolean isSpringManaged(PsiClass aClass) {
        PsiModifierList modifierList = aClass.getModifierList();
        if (modifierList == null) {
            return false;
        }
        for (PsiAnnotation ann : modifierList.getAnnotations()) {
            String qName = ann.getQualifiedName();
            if (qName == null) {
                continue;
            }
            for (String stereotype : SPRING_STEREOTYPES) {
                if (stereotype.equals(qName) || qName.endsWith("." + stereotype.substring(stereotype.lastIndexOf('.') + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAnnotation(PsiClass aClass, String annotationFqn) {
        PsiModifierList modifierList = aClass.getModifierList();
        if (modifierList == null) {
            return false;
        }
        for (PsiAnnotation ann : modifierList.getAnnotations()) {
            String qName = ann.getQualifiedName();
            if (annotationFqn.equals(qName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUninitializedFinalFields(PsiClass aClass) {
        for (PsiField f : aClass.getFields()) {
            if (f.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            if (f.hasModifierProperty(PsiModifier.FINAL) && (f.getInitializer() == null)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConstructorUsages(PsiClass psiClass, PsiMethod explicitConstructor) {
        Project project = psiClass.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        // Check usages of the explicit constructor.
        Collection<PsiReference> refs = ReferencesSearch.search(
                explicitConstructor, scope, false).findAll();
        if (!refs.isEmpty()) {
            return true;
        }

        // Check class-level references — any new ClassName(args) call anywhere
        // means the all-args constructor is needed.  Even if the explicit
        // constructor can fill the gap after removal, the Lombok-generated
        // constructor is actively used and removing @AllArgsConstructor
        // silently redirects callers to the explicit one, which may differ.
        for (PsiReference ref : ReferencesSearch.search(psiClass, scope, false).findAll()) {
            PsiElement el = ref.getElement();
            PsiElement parent = el.getParent();
            if (parent instanceof PsiNewExpression newExpr) {
                PsiExpressionList argList = newExpr.getArgumentList();
                if ((argList != null) && (argList.getExpressions().length > 0)) {
                    return true;
                }
            }
        }

        return hasSuperInvocation(explicitConstructor);
    }

    /**
     * Scans all classes in the project for constructors that contain a
     * {@code super(...)} statement resolving to the given parent constructor.
     */
    private boolean hasSuperInvocation(PsiMethod parentConstructor) {
        PsiClass parentClass = parentConstructor.getContainingClass();
        if (parentClass == null) {
            return false;
        }

        Project project = parentConstructor.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        for (PsiClass candidate : ClassInheritorsSearch.search(parentClass, scope, false).findAll()) {
            if (candidate.isInterface() || candidate.isAnnotationType()) {
                continue;
            }
            for (PsiMethod ctor : candidate.getConstructors()) {
                if (containsSuperCallTo(ctor, parentConstructor)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the body of a constructor contains a {@code super(...)} call
     * that resolves to the given parent constructor.
     */
    private boolean containsSuperCallTo(PsiMethod constructor, PsiMethod targetParentConstructor) {
        PsiCodeBlock body = constructor.getBody();
        if (body == null) {
            return false;
        }
        for (com.intellij.psi.PsiStatement stmt : body.getStatements()) {
            if (stmt instanceof com.intellij.psi.PsiExpressionStatement exprStmt) {
                com.intellij.psi.PsiExpression expr = exprStmt.getExpression();
                if (expr instanceof com.intellij.psi.PsiMethodCallExpression call) {
                    com.intellij.psi.PsiReferenceExpression methodExpr = call.getMethodExpression();
                    if ("super".equals(methodExpr.getReferenceName())) {
                        PsiMethod resolved = call.resolveMethod();
                        if ((resolved != null) && resolved.equals(targetParentConstructor)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks whether this class is part of a Lombok builder hierarchy — either it
     * extends a class that has {@code @Builder}/{@code @SuperBuilder}, or one of
     * its subclasses does.  Removing {@code @AllArgsConstructor} from any member
     * of such a hierarchy can break nested or subclass builders that rely on the
     * generated all-args constructor.
     */
    private boolean isInBuilderHierarchy(PsiClass aClass) {
        PsiClass[] supers = aClass.getSupers();
        for (PsiClass sup : supers) {
            if (hasBuilderAnnotation(sup)) {
                return true;
            }
        }

        Project project = aClass.getProject();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (PsiClass sub : ClassInheritorsSearch.search(aClass, scope, false).findAll()) {
            if (hasBuilderAnnotation(sub)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBuilderAndNoArgsConstructor(PsiClass aClass) {
        boolean hasBuilder = false;
        boolean hasNoArgs = false;
        PsiModifierList modifierList = aClass.getModifierList();
        if (modifierList == null) {
            return false;
        }
        for (PsiAnnotation ann : modifierList.getAnnotations()) {
            String qName = ann.getQualifiedName();
            if (isBuilderAnnotation(qName)) {
                hasBuilder = true;
            } else if ("lombok.NoArgsConstructor".equals(qName) || "NoArgsConstructor".equals(qName)) {
                hasNoArgs = true;
            }
        }
        return hasBuilder && hasNoArgs;
    }
}
