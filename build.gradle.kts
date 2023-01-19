import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.22"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.7.22"
    id("org.cqfn.diktat.diktat-gradle-plugin") version "1.2.3"
    `maven-publish`
}

repositories {
    mavenLocal()
    mavenCentral()
}

internal val coroutinesVersion = "1.6.4"
internal val ktorVersion = "2.2.2"
internal val saveVersion = "0.3.2"
internal val junitVersion = "5.9.1"
internal val assertjVersion = "3.23.1"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("com.saveourtool.save:save-cloud-api:$saveVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

kotlin.jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of("17"))
}

internal val Project.gradleOrSystemProperty: (key: String) -> String?
    get() = { key ->
        findProperty(key)?.toString() ?: System.getProperty(key)
    }

/**
 * Sets a system property if `lazyValue` evaluates to non-`null`.
 */
internal val Test.systemPropertyIfNotNull: (key: String, lazyValue: () -> String?) -> Unit
    get() = { key, lazyValue ->
        val value = lazyValue()
        if (value != null) {
            systemProperty(key, value)
        }
    }

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        showStandardStreams = true
        showCauses = true
        showExceptions = true
        showStackTraces = true
        exceptionFormat = FULL
        events("passed", "skipped")
    }

    filter {
        includeTestsMatching("com.saveourtool.save.*")
    }

    /*
     * Propagate certain Gradle properties to the tests, falling back to system
     * properties of the same name.
     */
    sequenceOf(
        "save-cloud.backend.url",
        "save-cloud.user",
        "save-cloud.user.auth.source",
        "save-cloud.password",
        "save-cloud.test.suite.ids",
        "save-cloud.test.version",
        "save-cloud.test.language",
        "save-cloud.use.external.files",
        "save-cloud.project.name",
        "save-cloud.contest.name",
    ).forEach { key ->
        systemPropertyIfNotNull(key) {
            gradleOrSystemProperty(key)
        }
    }
}

diktat {
    inputs {
        include("src/**/*.kt")
    }
    diktatConfigFile = file("${rootDir.path}/diktat-analysis.yml")
    /*
     * Leave enabled to see the number of errors in the output of Gradle.
     */
    debug = true
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/saveourtool/save-backend-tests")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            groupId = "com.saveourtool.save"
            version = "0.0.1-SNAPSHOT"

            from(components["java"])
        }
    }
}
