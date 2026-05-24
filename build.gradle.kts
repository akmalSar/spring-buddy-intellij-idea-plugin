import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform")

}

group = "com.sarhan.springformat"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(libs.hibernate.core) {
        isTransitive = false
    }
    implementation(libs.antlr4.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.assertj.core)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2026.1.2")
        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.spring")
        bundledPlugin("com.intellij.spring.boot")
        bundledPlugin("com.intellij.spring.data")
        bundledPlugin("com.intellij.spring.mvc")
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Spring Buddy"
        version = "1.0.0"
        description = """
            <h2>Make invisible Spring runtime behavior visible.</h2>
              <p>
                Spring Buddy is a correctness companion that models Spring consequences in real-time.
                It identifies "Legal Java" that results in "Illegal Spring," catching proxy bypasses,
                transaction pitfalls, and persistence risks before they reach your CI pipeline.
              </p>
            
              <h3>Key Capabilities</h3>
              <ul>
                <li><b>Transaction Visualization & Pitfalls:</b> Inline hints show propagation (Join, Start, Suspend, Nested). Detects proxy bypasses like self-invocation, private/final methods, and <code>@Transactional</code> on <code>@PostConstruct</code>.</li>
                <li><b>Persistence Performance:</b> Flags N+1 query risks in loops/streams, Open Session In View (OSIV) hazards, and full-load risks in projections.</li>
                <li><b>Test Ecosystem Intelligence:</b> Deep support for AssertJ migration and Mockito style alignment. Recognizes ArchUnit, MockMvc, and WebTestClient patterns as valid assertions.</li>
                <li><b>Spring Java Format:</b> Automates compliance for variable naming, import/method ordering, visibility rules, and utility constructors.</li>
                <li><b>Refactor Safety Net:</b> Bundled inspection runner to validate Spring semantics after large-scale AI or manual refactors.</li>
              </ul>
            
              <p><i>Stop debugging framework behavior and start seeing it directly in your editor.</i></p>
              <br/>
              <hr/>
              <p>
                <b>New to Spring Buddy?</b><br/>
                View our <a href="https://akmalsar.github.io/spring-buddy-intellij-idea-plugin/">Visual Feature Tour</a>
                to see how transaction proxies and persistence risks are surfaced in the IDE.
              </p>
        """.trimIndent()
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
