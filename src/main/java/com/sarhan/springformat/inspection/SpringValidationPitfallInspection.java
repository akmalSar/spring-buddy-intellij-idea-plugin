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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * Detects Spring validation pitfalls including missing {@code @Valid} on nested DTOs,
 * misplaced {@code BindingResult} parameters, and missing {@code @Validated} on service
 * classes that use constraint annotations on method parameters.
 *
 * @author Akmal Sarhan
 */
public class SpringValidationPitfallInspection extends AbstractBaseJavaLocalInspectionTool {

    private final Kind kind;

    protected SpringValidationPitfallInspection(Kind kind) {
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
            public void visitField(@NotNull PsiField field) {
                if (SpringValidationPitfallInspection.this.kind == Kind.NESTED_DTO_VALID && isNestedDtoFieldNeedingValid(field)) {
                    holder.registerProblem(field,
                            "Nested DTO field has validation constraints but is missing @Valid",
                            ProblemHighlightType.ERROR, new AddValidAnnotationFix());
                }
            }

            @Override
            public void visitMethod(@NotNull PsiMethod method) {
                if (SpringValidationPitfallInspection.this.kind == Kind.BINDING_RESULT_ORDER) {
                    checkBindingResultOrder(method, holder);
                }
                PsiClass containingClass = method.getContainingClass();
                if (SpringValidationPitfallInspection.this.kind == Kind.MISSING_VALIDATED
                        && isSpringComponent(containingClass)
                        && !isControllerClass(containingClass)
                        && !isRepositoryClass(containingClass)
                        && !classHasAnnotation(containingClass, "Validated")
                        && !isOverrideMethod(method)) {
                    PsiParameter firstConstrained = firstConstrainedParameter(method);
                    if (firstConstrained != null) {
                        holder.registerProblem(firstConstrained,
                                "Constraint annotations on method parameters require @Validated on the containing class",
                                ProblemHighlightType.ERROR, new AddValidatedAnnotationFix());
                    }
                }
            }
        };
    }

    private static boolean isNestedDtoFieldNeedingValid(PsiField field) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            return false;
        }
        if (hasAnnotation(field.getModifierList(), "Valid") || hasConstraintAnnotation(field.getModifierList())) {
            return false;
        }
        PsiClass containingClass = field.getContainingClass();
        if (containingClass != null && SpringInspectionUtil.isJpaEntity(containingClass)) {
            return false;
        }
        return hasNestedConstraintType(field.getType());
    }

    private static boolean hasNestedConstraintType(PsiType type) {
        if (!(type instanceof PsiClassType classType)) {
            return false;
        }
        PsiClass resolved = classType.resolve();
        if (resolved == null) {
            return false;
        }
        // A JPA entity is a domain object, not a DTO — skip it to avoid false
        // positives on internal context/builder classes that reference entities.
        if (SpringInspectionUtil.isJpaEntity(resolved)) {
            return false;
        }
        for (PsiField nestedField : resolved.getFields()) {
            if (hasConstraintAnnotation(nestedField.getModifierList())) {
                return true;
            }
        }
        return false;
    }

    private static void checkBindingResultOrder(PsiMethod method, ProblemsHolder holder) {
        PsiParameterList list = method.getParameterList();
        PsiParameter[] parameters = list.getParameters();
        for (PsiParameter p : parameters) {
            if (hasAnnotation(p.getModifierList(), "Valid") || hasAnnotation(p.getModifierList(), "Validated")) {
                for (int i = 0; i < parameters.length; i++) {
                    if (!isBindingResult(parameters[i])) {
                        continue;
                    }
                    if (i == 0 || !(hasAnnotation(parameters[i - 1].getModifierList(), "Valid")
                            || hasAnnotation(parameters[i - 1].getModifierList(), "Validated"))) {
                                holder.registerProblem(parameters[i],
                                        "BindingResult must immediately follow the @Valid/@Validated parameter it belongs to",
                                        ProblemHighlightType.ERROR, new MoveBindingResultAfterValidatedParameterFix());
                    }
                }
                return;
            }
        }
    }

    private static PsiParameter previousValidatedParameter(PsiParameter bindingResult) {
        PsiParameterList list = (PsiParameterList) bindingResult.getParent();
        PsiParameter[] parameters = list.getParameters();
        int index = list.getParameterIndex(bindingResult);
        for (int i = index - 1; i >= 0; i--) {
            if (hasAnnotation(parameters[i].getModifierList(), "Valid") || hasAnnotation(parameters[i].getModifierList(), "Validated")) {
                return parameters[i];
            }
        }
        return null;
    }

    private static boolean methodHasValidatedParameters(PsiMethod method) {
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            if (hasConstraintAnnotation(parameter.getModifierList())) {
                return true;
            }
        }
        return false;
    }

    private static PsiParameter firstConstrainedParameter(PsiMethod method) {
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            if (hasConstraintAnnotation(parameter.getModifierList())) {
                return parameter;
            }
        }
        return null;
    }

    private static boolean isBindingResult(PsiParameter parameter) {
        if (parameter.getType() instanceof PsiClassType classType) {
            PsiClass resolved = classType.resolve();
            if (resolved != null) {
                String qualifiedName = resolved.getQualifiedName();
                String name = resolved.getName();
                return "org.springframework.validation.BindingResult".equals(qualifiedName)
                        || "org.springframework.validation.Errors".equals(qualifiedName)
                        || "BindingResult".equals(name) || "Errors".equals(name);
            }
        }
        String type = parameter.getType().getCanonicalText();
        return "org.springframework.validation.BindingResult".equals(type)
                || "org.springframework.validation.Errors".equals(type);
    }

    private static boolean hasConstraintAnnotation(PsiModifierList modifiers) {
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
            // Only match Bean Validation constraints — not org.jetbrains.annotations.NotNull etc.
            boolean isBv = name.startsWith("javax.validation.constraints.")
                    || name.startsWith("jakarta.validation.constraints.")
                    || name.startsWith("javax.validation.")
                    || name.startsWith("jakarta.validation.");
            if (!isBv && name.contains(".")) {
                continue;
            }
            String shortName = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
            if (shortName != null && (shortName.startsWith("Not") || "Size".equals(shortName)
                    || "Min".equals(shortName) || "Max".equals(shortName) || "Pattern".equals(shortName)
                    || "Email".equals(shortName) || shortName.startsWith("Positive")
                    || shortName.startsWith("Negative") || shortName.startsWith("Decimal"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnnotation(PsiModifierList modifiers, String shortName) {
        if (modifiers == null) {
            return false;
        }
        for (PsiAnnotation annotation : modifiers.getAnnotations()) {
            String name = annotation.getQualifiedName();
            if (name == null && annotation.getNameReferenceElement() != null) {
                name = annotation.getNameReferenceElement().getText();
            }
            if (shortName.equals(name) || (name != null && name.endsWith("." + shortName))) {
                return true;
            }
        }
        return false;
    }

    private static boolean classHasAnnotation(PsiClass psiClass, String shortName) {
        PsiClass current = psiClass;
        while (current != null) {
            if (hasAnnotation(current.getModifierList(), shortName)) {
                return true;
            }
            current = current.getSuperClass();
        }
        return false;
    }



    /**
     * Returns {@code true} if the method overrides a superclass or interface method.
     * Override methods inherit their validation contract from the declaring class,
     * so we should not force the implementing class to add {@code @Validated}.
     */
    private static boolean isOverrideMethod(@NotNull PsiMethod method) {
        return method.findSuperMethods().length > 0;
    }

    /**
     * Returns {@code true} if the class is a Spring MVC controller
     * ({@code @Controller} or {@code @RestController}). Controllers have their
     * own parameter-validation pipeline (through {@code HandlerMethodArgumentResolver}
     * and {@code WebDataBinder}), so {@code @Validated} is not required for
     * method-level constraint annotations to take effect.
     */
    private static boolean isControllerClass(@NotNull PsiClass psiClass) {
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
                if ("org.springframework.stereotype.Controller".equals(name)
                        || "org.springframework.web.bind.annotation.RestController".equals(name)
                        || "Controller".equals(name)
                        || "RestController".equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the class is a Spring Data repository.
     * Repositories are infrastructure-layer proxies; method parameter validation
     * with {@code @Validated} is not appropriate there — validation belongs in
     * the service or controller layer before the repository is called.
     */
    private static boolean isRepositoryClass(@NotNull PsiClass psiClass) {
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
                if ("org.springframework.stereotype.Repository".equals(name)
                        || "Repository".equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSpringComponent(PsiClass psiClass) {
        if (psiClass == null) {
            return false;
        }
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

    private static String validationNamespace(PsiField field) {
        String text = field.getContainingFile().getText();
        if (text.contains("javax.validation.") || !text.contains("jakarta.validation.")) {
            return "javax.validation";
        }
        return "jakarta.validation";
    }

    private static void ensureImport(Project project, PsiJavaFile javaFile, PsiField context, String fqn) {
        PsiImportList importList = javaFile.getImportList();
        if (importList == null) {
            return;
        }
        for (PsiImportStatement statement : importList.getImportStatements()) {
            if (fqn.equals(statement.getQualifiedName())) {
                return;
            }
        }
        PsiClass imported = JavaPsiFacade.getInstance(project).findClass(fqn, context.getResolveScope());
        if (imported != null) {
            importList.add(JavaPsiFacade.getElementFactory(project).createImportStatement(imported));
            return;
        }
        PsiJavaFile tempFile = (PsiJavaFile) com.intellij.psi.PsiFileFactory.getInstance(project)
                .createFileFromText("Import.java", JavaLanguage.INSTANCE, "import " + fqn + "; class Import {}\n");
        if (tempFile.getImportList() != null && tempFile.getImportList().getImportStatements().length > 0) {
            importList.add(tempFile.getImportList().getImportStatements()[0]);
        }
    }

    private enum Kind {NESTED_DTO_VALID, BINDING_RESULT_ORDER, MISSING_VALIDATED}

    public static class NestedDtoValid extends SpringValidationPitfallInspection {
        public NestedDtoValid() {
            super(Kind.NESTED_DTO_VALID);
        }
    }

    public static class BindingResultOrder extends SpringValidationPitfallInspection {
        public BindingResultOrder() {
            super(Kind.BINDING_RESULT_ORDER);
        }
    }

    public static class MissingValidated extends SpringValidationPitfallInspection {
        public MissingValidated() {
            super(Kind.MISSING_VALIDATED);
        }
    }

    private static final class AddValidAnnotationFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Add @Valid annotation";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiField field = descriptor.getPsiElement() instanceof PsiField f ? f
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiField.class);
            if (field != null && field.getModifierList() != null) {
                field.getModifierList().addAnnotation("Valid");
                if (field.getContainingFile() instanceof PsiJavaFile javaFile) {
                    ensureImport(project, javaFile, field, validationNamespace(field) + ".Valid");
                }
            }
        }
    }

    private static final class AddValidatedAnnotationFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Add @Validated annotation";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiClass psiClass = descriptor.getPsiElement() instanceof PsiClass c ? c
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiClass.class);
            if (psiClass != null && psiClass.getModifierList() != null) {
                if (classHasAnnotation(psiClass, "Validated")) {
                    return;
                }
                ensureImport(project, psiClass, "org.springframework.validation.annotation.Validated");
                psiClass.getModifierList().addAnnotation("Validated");
            }
        }

        private static void ensureImport(Project project, PsiClass psiClass, String fqn) {
            if (!(psiClass.getContainingFile() instanceof PsiJavaFile javaFile)) {
                return;
            }
            PsiImportList importList = javaFile.getImportList();
            if (importList == null) {
                return;
            }
            for (PsiImportStatement statement : importList.getImportStatements()) {
                if (fqn.equals(statement.getQualifiedName())) {
                    return;
                }
            }
            PsiClass imported = JavaPsiFacade.getInstance(project).findClass(fqn, psiClass.getResolveScope());
            if (imported != null) {
                importList.add(JavaPsiFacade.getElementFactory(project).createImportStatement(imported));
            } else {
                PsiJavaFile tempFile = (PsiJavaFile) com.intellij.psi.PsiFileFactory.getInstance(project)
                        .createFileFromText("Import.java", JavaLanguage.INSTANCE,
                                "import " + fqn + "; class Import {}\n");
                if (tempFile.getImportList() != null && tempFile.getImportList().getImportStatements().length > 0) {
                    importList.add(tempFile.getImportList().getImportStatements()[0]);
                }
            }
        }
    }

    private static final class MoveBindingResultAfterValidatedParameterFix implements LocalQuickFix {
        @Override
        @NotNull
        public String getName() {
            return "Move BindingResult after validated parameter";
        }

        @Override
        @NotNull
        public String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiParameter bindingResult = descriptor.getPsiElement() instanceof PsiParameter p ? p
                    : com.intellij.psi.util.PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiParameter.class);
            if (bindingResult == null || !(bindingResult.getParent() instanceof PsiParameterList list)) {
                return;
            }
            PsiParameter validated = previousValidatedParameter(bindingResult);
            if (validated == null) {
                return;
            }
            PsiParameter copy = (PsiParameter) bindingResult.copy();
            bindingResult.delete();
            list.addAfter(copy, validated);
        }
    }
}
