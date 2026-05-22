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
public class SpringOptionalEntityLookupExceptionInspectionTest extends BasePlatformTestCase {

    public void testFlagsRepositoryFindByIdGetAndFixes() {
        this.myFixture.enableInspections(new SpringOptionalEntityLookupExceptionInspection());
        this.myFixture.configureByText("UserService.java", """
                import java.util.Optional;
                class UserService {
                    UserRepository userRepository;
                    User load(Long id) {
                        return userRepository.findById(id).<warning descr="Repository Optional lookup should throw an exception with entity and id context"><caret>get()</warning>;
                    }
                }
                interface UserRepository { Optional<User> findById(Long id); }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
        IntentionAction fix = this.myFixture.findSingleIntention("Use contextual orElseThrow");
        this.myFixture.launchAction(fix);
        this.myFixture.checkResult("""
                import java.util.Optional;
                class UserService {
                    UserRepository userRepository;
                    User load(Long id) {
                        return userRepository.findById(id).orElseThrow(() -> new IllegalStateException("User not found: " + id));
                    }
                }
                interface UserRepository { Optional<User> findById(Long id); }
                class User { }
                """);
    }

    public void testFlagsRepositoryFindByIdNoArgOrElseThrow() {
        this.myFixture.enableInspections(new SpringOptionalEntityLookupExceptionInspection());
        this.myFixture.configureByText("UserService.java", """
                import java.util.Optional;
                class UserService {
                    UserRepository repo;
                    User load(Long id) {
                        return repo.findById(id).<warning descr="Repository Optional lookup should throw an exception with entity and id context">orElseThrow()</warning>;
                    }
                }
                interface UserRepository { Optional<User> findById(Long id); }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testDoesNotFlagContextualOrElseThrow() {
        this.myFixture.enableInspections(new SpringOptionalEntityLookupExceptionInspection());
        this.myFixture.configureByText("UserService.java", """
                import java.util.Optional;
                class UserService {
                    UserRepository userRepository;
                    User load(Long id) {
                        return userRepository.findById(id)
                            .orElseThrow(() -> new IllegalStateException("User not found: " + id));
                    }
                }
                interface UserRepository { Optional<User> findById(Long id); }
                class User { }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }
}
