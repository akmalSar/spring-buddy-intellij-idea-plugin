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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class SpringImportOrderInspectionTest extends BasePlatformTestCase {

    public void testReordersImportsInAscendingSpringGroups() {
        this.myFixture.enableInspections(new SpringImportOrderInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.util.ObjectUtils;
                
                import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
                import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
                import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
                import org.hibernate.annotations.DynamicUpdate;
                import lombok.experimental.SuperBuilder;
                import lombok.ToString;
                import lombok.NoArgsConstructor;
                import lombok.EqualsAndHashCode;
                import lombok.Data;
                import lombok.Builder;
                import io.swagger.v3.oas.annotations.media.Schema;
                import de.nexnet.techstack.jsonview.boundary.DataView;
                import com.fasterxml.jackson.databind.annotation.JsonSerialize;
                import com.fasterxml.jackson.annotation.JsonView;
                import com.fasterxml.jackson.annotation.JsonIgnore;
                import com.fasterxml.jackson.annotation.JsonFormat;
                import jakarta.validation.groups.Default;
                import jakarta.validation.constraints.Size;
                import jakarta.validation.constraints.NotNull;
                import jakarta.validation.constraints.NotBlank;
                
                import java.util.UUID;
                import java.util.Set;
                import java.util.HashSet;
                import java.math.BigDecimal;
                
                class <warning descr="Imports are not ordered according to spring-javaformat conventions">Sample</warning> { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Reorder imports according to spring-javaformat");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.indexOf("import java.math.BigDecimal;") < text.indexOf("import com.fasterxml.jackson.annotation.JsonFormat;"));
        assertTrue(text.indexOf("import io.swagger.v3.oas.annotations.media.Schema;") < text.indexOf("import jakarta.validation.constraints.NotBlank;"));
        assertTrue(text.indexOf("import jakarta.validation.groups.Default;") < text.indexOf("import lombok.Builder;"));
        assertTrue(text.indexOf("import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;") < text.indexOf("import org.springframework.util.ObjectUtils;"));
        assertTrue(text.contains("import org.springframework.util.ObjectUtils;"));
    }

    public void testFlagsMissingBlankLineBetweenGroups() {
        this.myFixture.enableInspections(new SpringImportOrderInspection());
        this.myFixture.configureByText("Sample.java", """
                import java.util.UUID;
                import com.fasterxml.jackson.annotation.JsonFormat;

                class <warning descr="Imports are not grouped correctly (blank lines between groups are required)">Sample</warning> { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Reorder imports according to spring-javaformat");
        this.myFixture.launchAction(fix);
        String text = this.myFixture.getFile().getText();
        assertTrue(text.indexOf("import java.util.UUID;") < text.indexOf("import com.fasterxml.jackson.annotation.JsonFormat;"));
    }

    public void testFlagsExtraBlankLineInsideGroup() {
        this.myFixture.enableInspections(new SpringImportOrderInspection());
        this.myFixture.configureByText("Sample.java", """
                import de.nexnet.sbcloud.middleware.cockpit.representative.RepresentativeDto;

                import jakarta.validation.ConstraintValidator;
                import jakarta.validation.ConstraintValidatorContext;

                class <warning descr="Imports are not grouped correctly (blank lines between groups are required)">Sample</warning> { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
