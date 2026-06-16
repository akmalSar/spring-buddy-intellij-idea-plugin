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

package com.sarhan.inspection.builtin;


import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.GlobalSimpleInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.VariableTypeCanBeExplicitInspection;
import com.intellij.codeInspection.canBeFinal.CanBeFinalInspection;
import com.intellij.codeInspection.javaDoc.JavaDocReferenceInspection;
import com.intellij.codeInspection.logging.LoggingPlaceholderCountMatchesArgumentCountInspection;
import com.intellij.codeInspection.test.TestInProductSourceInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.spring.boot.model.SpringBootApplicationSetupInspection;
import com.intellij.spring.data.jdbc.inspection.SpringDataJdbcEmptySequenceNameInspection;
import com.intellij.spring.data.jpa.inspections.SpringDataMethodInconsistencyInspection;
import com.intellij.spring.data.jpa.inspections.SpringDataModifyingAnnotationMissingInspection;
import com.intellij.spring.data.jpa.inspections.SpringDataPageableParameterMissingInspection;
import com.intellij.spring.data.jpa.inspections.SpringDataRepositoryMethodParametersInspection;
import com.intellij.spring.data.jpa.inspections.SpringDataRepositoryMethodReturnTypeInspection;
import com.intellij.spring.mvc.pathVariables.MVCPathVariableInspection;
import com.siyeh.ig.bugs.InfiniteRecursionInspection;
import com.siyeh.ig.classlayout.ClassWithOnlyPrivateConstructorsInspection;
import com.siyeh.ig.classlayout.NonFinalUtilityClassInspection;
import com.siyeh.ig.classlayout.UtilityClassWithPublicConstructorInspection;
import com.siyeh.ig.controlflow.SimplifiableBooleanExpressionInspection;
import com.siyeh.ig.controlflow.SimplifiableConditionalExpressionInspection;
import com.siyeh.ig.inheritance.MissingOverrideAnnotationInspection;
import com.siyeh.ig.maturity.SystemOutErrInspection;
import com.siyeh.ig.style.ControlFlowStatementWithoutBracesInspection;
import com.siyeh.ig.style.LambdaParameterTypeCanBeSpecifiedInspection;
import com.siyeh.ig.style.MethodRefCanBeReplacedWithLambdaInspection;
import com.siyeh.ig.style.MissortedModifiersInspection;
import com.siyeh.ig.style.MultipleVariablesInDeclarationInspection;
import com.siyeh.ig.style.SimplifiableAnnotationInspection;
import com.siyeh.ig.style.UnnecessaryModifierInspection;
import com.siyeh.ig.style.UnnecessarySemicolonInspection;
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection;
import com.siyeh.ig.style.UnqualifiedMethodAccessInspection;
import com.siyeh.ig.testFrameworks.SimplifiableAssertionInspection;
import com.siyeh.ig.visibility.LambdaParameterHidingMemberVariableInspection;
import org.intellij.lang.regexp.inspection.custom.CustomRegExpInspection;
import org.jetbrains.annotations.NotNull;

/**
 * For final built-in inspections that cannot be subclassed,
 * we delegate to an instance of the original and override only
 * the group metadata so results appear under Spring Buddy > Built-in.
 *
 * @author Akmal Sarhan
 */
public final class BuiltInInspectionWrappers {

    private static final String ROOT = "Spring Buddy";
    private static final String CHILD = "Built-in";

    private BuiltInInspectionWrappers() {
    }

    // -------------------------------------------------------------------------
    // Base delegating wrapper
    // -------------------------------------------------------------------------

    private abstract static class Delegating extends LocalInspectionTool {

        private final LocalInspectionTool delegate;
        private final String shortName;
        private final String displayName;

        protected Delegating(LocalInspectionTool delegate) {
            this(delegate, null, null);
        }

        protected Delegating(LocalInspectionTool delegate, String shortName, String displayName) {
            this.delegate = delegate;
            this.shortName = shortName;
            this.displayName = displayName;
        }


        @Override
        @NotNull
        public String getDisplayName() {
            if (this.displayName != null) {
                return this.displayName;
            }
            return this.delegate.getDisplayName();
        }

        @Override
        @NotNull
        public String getShortName() {
            if (this.shortName != null) {
                return this.shortName;
            }
            return "BuiltIn" + this.delegate.getShortName();
        }

        @Override
        public String getStaticDescription() {
            return this.delegate.getStaticDescription();
        }

        @Override
        @NotNull
        public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
            return this.delegate.buildVisitor(holder, isOnTheFly);
        }

        @Override
        @NotNull
        public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly,
                                              @NotNull LocalInspectionToolSession session) {
            return this.delegate.buildVisitor(holder, isOnTheFly, session);
        }
    }

    private static LocalInspectionTool loadOptionalLocalInspection(String className) {
        try {
            Class<?> inspectionClass = BuiltInInspectionWrappers.class.getClassLoader().loadClass(className);
            Object inspection = inspectionClass.getDeclaredConstructor().newInstance();
            if (inspection instanceof LocalInspectionTool localInspection) {
                return localInspection;
            }
        }
        catch (ReflectiveOperationException | LinkageError ex) {
            // Optional bundled inspection not present in every IntelliJ target IDE.
        }
        return new AbstractBaseJavaLocalInspectionTool() {
        };
    }

    // -------------------------------------------------------------------------
    // Base for global inspections
    // -------------------------------------------------------------------------

    private abstract static class DelegatingGlobal extends GlobalInspectionTool {

        private final GlobalInspectionTool delegate;

        protected DelegatingGlobal(GlobalInspectionTool delegate) {
            this.delegate = delegate;
        }


        @Override
        @NotNull
        public String getDisplayName() {
            return this.delegate.getDisplayName();
        }

        @Override
        @NotNull
        public String getShortName() {
            return "BuiltIn" + this.delegate.getShortName();
        }

        @Override
        public String getStaticDescription() {
            return this.delegate.getStaticDescription();
        }


        @Override
        public void checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager,
                              @NotNull ProblemsHolder problemsHolder,
                              @NotNull GlobalInspectionContext globalContext,
                              @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
            if (this.delegate instanceof GlobalSimpleInspectionTool simple) {
                simple.checkFile(file, manager, problemsHolder, globalContext, problemDescriptionsProcessor);
            }
        }

        @Override
        public boolean isReadActionNeeded() {
            return this.delegate.isReadActionNeeded();
        }
    }

    private abstract static class DelegatingGlobalSimple extends GlobalSimpleInspectionTool {

        private final GlobalSimpleInspectionTool delegate;

        protected DelegatingGlobalSimple(GlobalSimpleInspectionTool delegate) {
            this.delegate = delegate;
        }

        @Override
        @NotNull
        public String getGroupDisplayName() {
            return CHILD;
        }


        @Override
        @NotNull
        public String getDisplayName() {
            return this.delegate.getDisplayName();
        }

        @Override
        @NotNull
        public String getShortName() {
            return "BuiltIn" + this.delegate.getShortName();
        }

        @Override
        public String getStaticDescription() {
            return this.delegate.getStaticDescription();
        }


        @Override
        public void checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager,
                              @NotNull ProblemsHolder problemsHolder,
                              @NotNull GlobalInspectionContext globalContext,
                              @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
            if (this.delegate instanceof GlobalSimpleInspectionTool simple) {
                simple.checkFile(file, manager, problemsHolder, globalContext, problemDescriptionsProcessor);
            }
        }

        @Override
        public boolean isReadActionNeeded() {
            return this.delegate.isReadActionNeeded();
        }
    }

    // -------------------------------------------------------------------------
    // Global wrappers
    // -------------------------------------------------------------------------

    public static final class CanBeFinal extends DelegatingGlobal {
        public CanBeFinal() {
            super(new CanBeFinalInspection());
        }
    }

    public static final class UnusedImport extends DelegatingGlobalSimple {
        public UnusedImport() {
            super(new UnusedImportInspection());
        }
    }

    public static final class CustomRegExp extends Delegating {
        public CustomRegExp() {
            super(new CustomRegExpInspection());
        }
    }

    public static final class ControlFlowStatementWithoutBraces extends Delegating {
        public ControlFlowStatementWithoutBraces() {
            super(new ControlFlowStatementWithoutBracesInspection());
        }
    }

    public static final class MissortedModifiers extends Delegating {
        public MissortedModifiers() {
            super(createInspection());
        }

        private static MissortedModifiersInspection createInspection() {
            MissortedModifiersInspection inspection = new MissortedModifiersInspection();
            inspection.m_requireAnnotationsFirst = true;
            return inspection;
        }
    }

    public static final class MultipleVariablesInDeclaration extends Delegating {
        public MultipleVariablesInDeclaration() {
            super(new MultipleVariablesInDeclarationInspection());
        }
    }

    public static final class MissingOverrideAnnotation extends Delegating {
        public MissingOverrideAnnotation() {
            super(createInspection());
        }

        private static MissingOverrideAnnotationInspection createInspection() {
            MissingOverrideAnnotationInspection inspection = new MissingOverrideAnnotationInspection();
            inspection.ignoreObjectMethods = true;
            inspection.ignoreAnonymousClassMethods = false;
            return inspection;
        }
    }

    public static final class UnnecessaryModifier extends Delegating {
        public UnnecessaryModifier() {
            super(new UnnecessaryModifierInspection());
        }
    }

    public static final class AsyncMethod extends Delegating {
        public AsyncMethod() {
            super(loadOptionalLocalInspection("com.intellij.spring.model.highlighting.jam.AsyncMethodInspection"),
                    "BuiltInAsyncMethod", "Async method");
        }
    }

    public static final class SpringProfileExpression extends Delegating {
        public SpringProfileExpression() {
            super(loadOptionalLocalInspection("com.intellij.spring.profiles.SpringProfileExpressionInspection"),
                    "BuiltInSpringProfileExpression", "Spring profile expression");
        }
    }

    public static final class SpringBootApplicationSetup extends Delegating {
        public SpringBootApplicationSetup() {
            super(new SpringBootApplicationSetupInspection());
        }
    }

    public static final class SpringDataModifyingAnnotationMissing extends Delegating {
        public SpringDataModifyingAnnotationMissing() {
            super(new SpringDataModifyingAnnotationMissingInspection());
        }
    }

    public static final class SpringDataRepositoryMethodReturnType extends Delegating {
        public SpringDataRepositoryMethodReturnType() {
            super(new SpringDataRepositoryMethodReturnTypeInspection());
        }
    }

    public static final class SpringDataRepositoryMethodParameters extends Delegating {
        public SpringDataRepositoryMethodParameters() {
            super(new SpringDataRepositoryMethodParametersInspection());
        }
    }

    public static final class SpringDataMethodInconsistency extends Delegating {
        public SpringDataMethodInconsistency() {
            super(new SpringDataMethodInconsistencyInspection());
        }
    }

    public static final class SpringDataPageableParameterMissing extends Delegating {
        public SpringDataPageableParameterMissing() {
            super(new SpringDataPageableParameterMissingInspection());
        }
    }

    public static final class SpringDataJdbcEmptySequenceName extends Delegating {
        public SpringDataJdbcEmptySequenceName() {
            super(new SpringDataJdbcEmptySequenceNameInspection());
        }
    }

    public static final class SpringMvcPathVariableDeclaration extends Delegating {
        public SpringMvcPathVariableDeclaration() {
            super(loadOptionalLocalInspection(
                    "com.intellij.spring.mvc.pathVariables.SpringMvcPathVariableDeclarationInspection"),
                    "BuiltInSpringMvcPathVariableDeclaration", "Spring MVC path variable declaration");
        }
    }

    public static final class MVCPathVariable extends Delegating {
        public MVCPathVariable() {
            super(new MVCPathVariableInspection());
        }
    }


    public static final class UnnecessarySemicolon extends Delegating {
        public UnnecessarySemicolon() {
            super(new UnnecessarySemicolonInspection());
        }
    }

    public static final class UnqualifiedFieldAccess extends Delegating {
        public UnqualifiedFieldAccess() {
            super(new UnqualifiedFieldAccessInspection());
        }
    }

    public static final class UnqualifiedMethodAccess extends Delegating {
        public UnqualifiedMethodAccess() {
            super(new UnqualifiedMethodAccessInspection());
        }
    }

    public static final class LambdaParameterHidingMemberVariable extends Delegating {
        public LambdaParameterHidingMemberVariable() {
            super(new LambdaParameterHidingMemberVariableInspection());
        }
    }

    public static final class StaticImport extends Delegating {
        public StaticImport() {
            super(new com.siyeh.ig.imports.StaticImportInspection());
        }
    }

    public static final class ClassWithOnlyPrivateConstructors extends Delegating {
        public ClassWithOnlyPrivateConstructors() {
            super(new ClassWithOnlyPrivateConstructorsInspection());
        }
    }

    public static final class NonFinalUtilityClass extends Delegating {
        public NonFinalUtilityClass() {
            super(new NonFinalUtilityClassInspection());
        }
    }

    public static final class UtilityClassWithPublicConstructor extends Delegating {
        public UtilityClassWithPublicConstructor() {
            super(new UtilityClassWithPublicConstructorInspection());
        }
    }

    public static final class SimplifiableBooleanExpression extends Delegating {
        public SimplifiableBooleanExpression() {
            super(new SimplifiableBooleanExpressionInspection());
        }
    }

    public static final class SimplifiableConditionalExpression extends Delegating {
        public SimplifiableConditionalExpression() {
            super(new SimplifiableConditionalExpressionInspection());
        }
    }

    public static final class VariableTypeCanBeExplicit extends Delegating {
        public VariableTypeCanBeExplicit() {
            super(new VariableTypeCanBeExplicitInspection());
        }
    }

    public static final class SimplifiableAssertion extends Delegating {
        public SimplifiableAssertion() {
            super(new SimplifiableAssertionInspection());
        }
    }

    public static final class SimplifiableAnnotation extends Delegating {
        public SimplifiableAnnotation() {
            super(new SimplifiableAnnotationInspection());
        }
    }

    public static final class Convert2MethodRef extends Delegating {
        public Convert2MethodRef() {
            super(new LambdaCanBeMethodReferenceInspection());
        }
    }

    public static final class MethodRefCanBeReplacedWithLambda extends Delegating {
        public MethodRefCanBeReplacedWithLambda() {
            super(new MethodRefCanBeReplacedWithLambdaInspection());
        }
    }

    public static final class LambdaParameterTypeCanBeSpecified extends Delegating {
        public LambdaParameterTypeCanBeSpecified() {
            super(new LambdaParameterTypeCanBeSpecifiedInspection());
        }
    }

    public static final class InfiniteRecursion extends Delegating {
        public InfiniteRecursion() {
            super(new InfiniteRecursionInspection());
        }
    }

    public static final class PlaceholderCountMatchesArgumentCount extends Delegating {
        public PlaceholderCountMatchesArgumentCount() {
            super(new LoggingPlaceholderCountMatchesArgumentCountInspection());
        }
    }

    public static final class SystemOutErr extends Delegating {
        public SystemOutErr() {
            super(new SystemOutErrInspection());
        }
    }

//    public static final class UseOfObsoleteAssert extends Delegating {
//        public UseOfObsoleteAssert() { super(new UseOfObsoleteAssertInspection()); }
//    }

    public static final class TestInProductSource extends Delegating {
        public TestInProductSource() {
            super(new TestInProductSourceInspection());
        }
    }

    public static final class JavaDoc extends Delegating {
        public JavaDoc() {
            super(new JavaDocReferenceInspection());
        }
    }
}
