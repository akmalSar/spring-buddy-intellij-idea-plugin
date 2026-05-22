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

package com.sarhan.springformat;

import java.util.List;
import java.util.Map;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.sarhan.springformat.inspection.SpringJpaEntityMissingIdInspection;

/**
 * @author Akmal Sarhan
 */
public class RunSpringFormatInspectionsActionTest extends BasePlatformTestCase {

    public void testCollectErrorInspectionsUsesNonNullTextRanges() {
        this.myFixture.configureByText("User.java", """
                @interface Entity { }
                @Entity
                class User {
                    String name;
                }
                """);
        VirtualFile file = this.myFixture.getFile().getVirtualFile();
        ScopeToolState state = new ScopeToolState("Test",
                new LocalInspectionToolWrapper(new SpringJpaEntityMissingIdInspection()), true,
                HighlightDisplayLevel.ERROR);

        Map<String, List<String>> errors = RunSpringFormatInspectionsAction.collectErrorInspections(
                getProject(), new VirtualFile[] { file }, List.of(state));

        assertContainsElements(errors.get("User.java"),
                "JPA @Entity has no @Id or @EmbeddedId in its class hierarchy; Hibernate fails startup");
    }
}
