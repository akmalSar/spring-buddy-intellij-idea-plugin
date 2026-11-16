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

import java.util.List;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

/**
 * @author Akmal Sarhan
 */
public class BannedApiInspectionTest extends BasePlatformTestCase {

    private static final String CONFIG = """
            {
              "version": 1,
              "layers": { "clock": ["com.acme.support.time.."] },
              "bannedApis": [
                {
                  "id": "no-legacy-clock",
                  "symbol": "com.acme.legacy.LegacyClock",
                  "allowedIn": "clock",
                  "severity": "error",
                  "message": "Use the Clock wrapper"
                },
                {
                  "id": "no-raw-send",
                  "symbol": "com.acme.mail.Mailer#send",
                  "allowedIn": ["com.acme.support.mail.."],
                  "severity": "warning"
                },
                {
                  "id": "no-legacy-tx",
                  "symbol": "com.acme.legacy.LegacyTransactional",
                  "severity": "error",
                  "message": "Use Spring @Transactional",
                  "fixHint": "replace with org.springframework.transaction.annotation.Transactional"
                }
              ]
            }
            """;

    private static final String NOT_A_FIX = " Suppressing or working around this check is not a fix.";

    private static final String CLOCK = "[SB-ARCH-006 no-legacy-clock] Banned API com.acme.legacy.LegacyClock is used "
            + "outside 'com.acme.support.time..'. Why: Use the Clock wrapper. Fix: Call the wrapper in "
            + "'com.acme.support.time..' instead of using com.acme.legacy.LegacyClock directly." + NOT_A_FIX;

    private static final String SEND = "[SB-ARCH-006 no-raw-send] Banned API com.acme.mail.Mailer#send is used outside "
            + "'com.acme.support.mail..'. Fix: Call the wrapper in 'com.acme.support.mail..' instead of using "
            + "com.acme.mail.Mailer#send directly." + NOT_A_FIX;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        this.myFixture.enableInspections(new BannedApiInspection());
        this.myFixture.addFileToProject("com/acme/legacy/LegacyClock.java", """
                package com.acme.legacy;
                public class LegacyClock {
                    public static long now() { return 0; }
                }
                """);
        this.myFixture.addFileToProject("com/acme/legacy/LegacyTransactional.java", """
                package com.acme.legacy;
                public @interface LegacyTransactional { }
                """);
        this.myFixture.addFileToProject("com/acme/mail/Mailer.java", """
                package com.acme.mail;
                public class Mailer {
                    public void send(String to) { }
                    public void send(String to, String body) { }
                    public void close() { }
                }
                """);
        this.myFixture.addFileToProject("com/acme/mail/SmtpMailer.java", """
                package com.acme.mail;
                public class SmtpMailer extends Mailer {
                    @Override
                    public void send(String to) { }
                }
                """);
    }

    public void testReportsBannedClassOnceAtImport() {
        addConfig(CONFIG);
        check("com/acme/order/OrderService.java", """
                package com.acme.order;
                import <error descr="%CLOCK%">com.acme.legacy.LegacyClock</error>;
                public class OrderService {
                    LegacyClock clock;
                    long at() { return LegacyClock.now(); }
                }
                """);
    }

    public void testReportsBannedClassFullyQualifiedWithoutImport() {
        addConfig(CONFIG);
        check("com/acme/order/OrderService.java", """
                package com.acme.order;
                public class OrderService {
                    long at() { return <error descr="%CLOCK%">com.acme.legacy.LegacyClock</error>.now(); }
                }
                """);
    }

    public void testReportsBannedAnnotationWithFixHint() {
        addConfig(CONFIG);
        check("com/acme/order/OrderService.java", """
                package com.acme.order;
                public class OrderService {
                    @<error descr="[SB-ARCH-006 no-legacy-tx] Banned API com.acme.legacy.LegacyTransactional is used. Why: Use Spring @Transactional. Fix: replace with org.springframework.transaction.annotation.Transactional. Suppressing or working around this check is not a fix.">com.acme.legacy.LegacyTransactional</error>
                    void save() { }
                }
                """);
    }

    public void testReportsEveryCallOfBannedMethodIncludingOverloadsOverridesAndReferences() {
        addConfig(CONFIG);
        check("com/acme/order/OrderService.java", """
                package com.acme.order;
                import com.acme.mail.Mailer;
                import com.acme.mail.SmtpMailer;
                public class OrderService {
                    void notify(Mailer mailer, SmtpMailer smtp) {
                        mailer.<warning descr="%SEND%">send</warning>("a");
                        mailer.<warning descr="%SEND%">send</warning>("a", "b");
                        smtp.<warning descr="%SEND%">send</warning>("a");
                        Sink c = mailer::<warning descr="%SEND%">send</warning>;
                        mailer.close();
                    }
                }
                interface Sink { void accept(String to); }
                """);
    }

    public void testAllowedZoneReportsNothing() {
        addConfig(CONFIG);
        check("com/acme/support/time/Clock.java", """
                package com.acme.support.time;
                import com.acme.legacy.LegacyClock;
                public class Clock {
                    long now() { return LegacyClock.now(); }
                }
                """);
        check("com/acme/support/mail/MailGateway.java", """
                package com.acme.support.mail;
                import com.acme.mail.Mailer;
                public class MailGateway {
                    void send(Mailer mailer) { mailer.send("a"); }
                }
                """);
    }

    public void testAllowedZoneOfOneApiDoesNotAllowAnother() {
        addConfig(CONFIG);
        check("com/acme/support/time/Clock.java", """
                package com.acme.support.time;
                import com.acme.mail.Mailer;
                public class Clock {
                    void ping(Mailer mailer) { mailer.<warning descr="%SEND%">send</warning>("a"); }
                }
                """);
    }

    public void testUnresolvedSymbolsAndLookalikesReportNothing() {
        addConfig(CONFIG);
        check("com/acme/order/OrderService.java", """
                package com.acme.order;
                import com.acme.legacy.Missing;
                public class OrderService {
                    LegacyClock unresolved;
                    void notify(Other other) { other.send("a"); unknown.send("a"); }
                }
                class LegacyTransactional { }
                class Other { void send(String to) { } }
                """);
    }

    public void testOffEntryReportsNothing() {
        addConfig(CONFIG.replace("\"severity\": \"error\",\n      \"message\": \"Use the Clock wrapper\"",
                "\"severity\": \"off\""));
        check("com/acme/order/OrderService.java", """
                package com.acme.order;
                import com.acme.legacy.LegacyClock;
                public class OrderService { LegacyClock clock; }
                """);
    }

    public void testMissingConfigReportsNothing() {
        check("com/acme/order/OrderService.java", """
                package com.acme.order;
                import com.acme.legacy.LegacyClock;
                public class OrderService { LegacyClock clock; }
                """);
    }

    public void testSkipsTestSourcesByDefault() throws java.io.IOException {
        addConfig(CONFIG);
        VirtualFile testRoot = createTestRoot();
        try {
            check("testsrc/com/acme/order/OrderServiceTest.java", """
                    package com.acme.order;
                    import com.acme.legacy.LegacyClock;
                    public class OrderServiceTest { LegacyClock clock; }
                    """);
        }
        finally {
            PsiTestUtil.removeSourceRoot(getModule(), testRoot);
        }
    }

    public void testChecksTestSourcesWhenIncluded() throws java.io.IOException {
        addConfig(CONFIG.replace("\"version\": 1,", "\"version\": 1, \"includeTests\": true,"));
        VirtualFile testRoot = createTestRoot();
        try {
            check("testsrc/com/acme/order/OrderServiceTest.java", """
                    package com.acme.order;
                    import <error descr="%CLOCK%">com.acme.legacy.LegacyClock</error>;
                    public class OrderServiceTest { LegacyClock clock; }
                    """);
        }
        finally {
            PsiTestUtil.removeSourceRoot(getModule(), testRoot);
        }
    }

    private VirtualFile createTestRoot() throws java.io.IOException {
        VirtualFile root = this.myFixture.getTempDirFixture().findOrCreateDir("testsrc");
        PsiTestUtil.addSourceRoot(getModule(), root, true);
        return root;
    }

    private void addConfig(String json) {
        this.myFixture.addFileToProject(".spring-buddy/architecture.json", json);
    }

    private void check(String path, String text) {
        String content = text.replace("%CLOCK%", CLOCK).replace("%SEND%", SEND);
        PsiFile file = this.myFixture.addFileToProject(path, content);
        this.myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        this.myFixture.checkHighlighting(true, false, true, true);
        int expected = countMarkers(content);
        List<HighlightInfo> infos = this.myFixture.doHighlighting();
        long actual = infos.stream()
                .filter((HighlightInfo info) -> info.getDescription() != null
                        && info.getDescription().startsWith("[SB-ARCH-"))
                .count();
        assertEquals("architecture problems: " + infos, expected, actual);
    }

    private static int countMarkers(String content) {
        int count = 0;
        int index = content.indexOf("descr=\"[SB-ARCH-");
        while (index >= 0) {
            count++;
            index = content.indexOf("descr=\"[SB-ARCH-", index + 1);
        }
        return count;
    }
}
