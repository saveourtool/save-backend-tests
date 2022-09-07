import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
}

repositories {
    mavenLocal()
    mavenCentral()
}

internal val coroutinesVersion = "1.6.4"
internal val arrowKtVersion = "1.0.1"
internal val saveVersion = "0.3.0-SNAPSHOT"
internal val junitVersion = "5.9.0"
internal val assertjVersion = "3.23.1"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.arrow-kt:arrow-core:$arrowKtVersion")

    /*
     * save-cloud-* projects have incorrectly generated POMs; that's why we
     * explicitly list the whole hierarchy.
     */
    implementation("com.saveourtool.save:save-cloud-api:$saveVersion")
    implementation("com.saveourtool.save:save-cloud-common:$saveVersion")
    implementation("com.saveourtool.save:save-cloud-common-jvm:$saveVersion")

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

tasks.test {
    useJUnitPlatform()

    filter {
        includeTestsMatching("com.saveourtool.save.*")
    }

    /*
     * Propagate certain Gradle properties to the tests, falling back to system
     * properties of the same name.
     */
    sequenceOf("save-cloud.backend.url", "gpr.user", "gpr.key").forEach { key ->
        systemPropertyIfNotNull(key) {
            gradleOrSystemProperty(key)
        }
    }
}
