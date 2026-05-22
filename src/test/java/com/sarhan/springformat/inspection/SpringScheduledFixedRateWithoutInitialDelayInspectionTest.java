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
public class SpringScheduledFixedRateWithoutInitialDelayInspectionTest extends BasePlatformTestCase {

    public void testReportsFixedRateWithoutInitialDelay() {
        this.myFixture.enableInspections(new SpringScheduledFixedRateWithoutInitialDelayInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.scheduling.annotation.Scheduled;
                import org.springframework.stereotype.Component;

                @Component
                class Sample {
                    @Scheduled(fixedRate = 5000)
                    void <warning descr="@Scheduled with fixedRate/fixedDelay should specify initialDelay to avoid startup stampede">work</warning>() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testReportsFixedDelayWithoutInitialDelay() {
        this.myFixture.enableInspections(new SpringScheduledFixedRateWithoutInitialDelayInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.scheduling.annotation.Scheduled;
                import org.springframework.stereotype.Component;

                @Component
                class Sample {
                    @Scheduled(fixedDelay = 5000)
                    void <warning descr="@Scheduled with fixedRate/fixedDelay should specify initialDelay to avoid startup stampede">work</warning>() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithInitialDelay() {
        this.myFixture.enableInspections(new SpringScheduledFixedRateWithoutInitialDelayInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.scheduling.annotation.Scheduled;
                import org.springframework.stereotype.Component;

                @Component
                class Sample {
                    @Scheduled(fixedRate = 5000, initialDelay = 1000)
                    void work() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testNoReportWithCron() {
        this.myFixture.enableInspections(new SpringScheduledFixedRateWithoutInitialDelayInspection());
        this.myFixture.configureByText("Sample.java", """
                import org.springframework.scheduling.annotation.Scheduled;
                import org.springframework.stereotype.Component;

                @Component
                class Sample {
                    @Scheduled(cron = "0 0 * * * *")
                    void work() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);
    }

    public void testFixAddsInitialDelayToValueAttribute() {
        this.myFixture.enableInspections(new SpringScheduledFixedRateWithoutInitialDelayInspection());
        this.myFixture.configureByText("Sample.java", """
                @interface Scheduled {
                    long fixedRate() default -1;
                    long initialDelay() default -1;
                }

                class Sample {
                    @<warning descr="@Scheduled with fixedRate/fixedDelay should specify initialDelay to avoid startup stampede">Scheduled<caret></warning>(fixedRate = 5000)
                    void work() { }
                }
                """);
        this.myFixture.checkHighlighting(false, false, true, true);

        IntentionAction fix = this.myFixture.findSingleIntention("Add initialDelay = 1000");
        this.myFixture.launchAction(fix);

        this.myFixture.checkResult("""
                @interface Scheduled {
                    long fixedRate() default -1;
                    long initialDelay() default -1;
                }

                class Sample {
                    @Scheduled(fixedRate = 5000, initialDelay = 1000)
                    void work() { }
                }
                """);
    }
}
