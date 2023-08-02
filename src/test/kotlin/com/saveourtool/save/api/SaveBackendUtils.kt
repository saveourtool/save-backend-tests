@file:JvmName("SaveBackendUtils")
@file:Suppress(
    "HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE",
)

package com.saveourtool.save.api

import com.saveourtool.save.testsuite.TestSuiteVersioned
import org.assertj.core.api.Assumptions.assumeThat

private const val DEFAULT_BACKEND_URL = "http://localhost:5800"
private const val DEFAULT_ORGANIZATION_NAME = "saveourtool"
private const val DEFAULT_PASSWORD = ""
private const val DEFAULT_PROJECT_NAME = "Diktat-Integration"
private const val DEFAULT_TEST_LANGUAGE = "Kotlin"
private const val DEFAULT_TEST_VERSION = "master"
private const val DEFAULT_USER = "admin"
internal const val TEST_TIMEOUT_MINUTES = 20L

@Suppress("CUSTOM_GETTERS_SETTERS")
internal val backendUrl: String
    get() {
        val backendUrlOrEmpty = getenvOrProperty("save-cloud.backend.url", DEFAULT_BACKEND_URL)

        return when {
            backendUrlOrEmpty.isEmpty() -> DEFAULT_BACKEND_URL
            else -> backendUrlOrEmpty
        }
    }

@Suppress("CUSTOM_GETTERS_SETTERS")
internal val user: String
    get() =
        getenvOrProperty("save-cloud.user", DEFAULT_USER)

/**
 * @return either the password or the _personal access token_.
 */
@Suppress("CUSTOM_GETTERS_SETTERS")
internal val passwordOrToken: String
    get() =
        getenvOrProperty("save-cloud.password", DEFAULT_PASSWORD)

@Suppress("CUSTOM_GETTERS_SETTERS")
internal val organizationName: String
    get() =
        DEFAULT_ORGANIZATION_NAME

@Suppress("CUSTOM_GETTERS_SETTERS")
internal val projectName: String
    get() =
        getenvOrProperty("save-cloud.project.name", DEFAULT_PROJECT_NAME)

@Suppress(
    "NO_CORRESPONDING_PROPERTY",
    "CUSTOM_GETTERS_SETTERS",
)
internal val testSuiteIds: Set<Long>
    get() {
        val rawTestSuiteIds = getenvOrProperty("save-cloud.test.suite.ids")
            ?: return emptySet()

        return rawTestSuiteIds
            .splitToSequence(',')
            .map(String::trim)
            .map(String::toLongOrNull)
            .filterNotNull()
            .toSet()
    }

@Suppress("CUSTOM_GETTERS_SETTERS")
internal val testVersion: String?
    get() {
        val rawTestVersion = getenvOrProperty("save-cloud.test.version", DEFAULT_TEST_VERSION)

        return when {
            rawTestVersion.isEmpty() -> null
            rawTestVersion.isBlank() -> null
            else -> rawTestVersion
        }
    }

@Suppress("CUSTOM_GETTERS_SETTERS")
internal val testLanguage: String?
    get() {
        val rawTestLanguage = getenvOrProperty("save-cloud.test.language", DEFAULT_TEST_LANGUAGE)

        return when {
            rawTestLanguage.isEmpty() -> null
            rawTestLanguage.isBlank() -> null
            else -> rawTestLanguage
        }
    }

/**
 * @return the list of test suites, filtered either by id, or by source code
 *   version and programming language.
 * @see testSuiteIds
 * @see testVersion
 * @see testLanguage
 */
internal fun List<TestSuiteVersioned>.filtered(): List<TestSuiteVersioned> {
    val selectById = testSuiteIds.isNotEmpty()
    val selectByVersionAndLanguage = testVersion != null && testLanguage != null

    assumeThat(selectById || selectByVersionAndLanguage)
        .describedAs("Test suite selector not specified: version = $testVersion, language = $testLanguage, test suite ids = $testSuiteIds")
        .isTrue

    @Suppress("NO_BRACES_IN_CONDITIONALS_AND_LOOPS")
    val predicate: TestSuiteVersioned.() -> Boolean = when {
        selectById -> {
            {
                val id0 = id
                id0 != null && id0 in testSuiteIds
            }
        }

        selectByVersionAndLanguage -> {
            { hasVersion(testVersion) && language == testLanguage }
        }

        else -> {
            { false }
        }
    }

    return asSequence().filter(predicate).toList()
}

/**
 * @return the list of test suites within the organization identified with
 *   [organizationName].
 */
internal fun List<TestSuiteVersioned>.withinOrganization(): List<TestSuiteVersioned> =
    filter { testSuite ->
        testSuite.organizationName == organizationName
    }

/**
 * If [version] is a branch name, the test suite version returned by the
 * back-end may look like `<branch name> (<commit hash>)`.
 *
 * @param version git tag, branch name, or commit hash.
 */
private fun TestSuiteVersioned.hasVersion(version: String?): Boolean =
    this.version == version ||
            (version != null && this.version.matches(Regex("""^\Q$version\E\h+\([0-9A-Fa-f]+\)$""")))

/**
 * @param name env name or property name.
 * @return env value or property value if env is missed.
 */
private fun getenvOrProperty(name: String): String? =
    System.getenv(name) ?: System.getProperty(name)

/**
 * @param name env name or property name.
 * @param defaultValue value when env and property are missed.
 * @return env value or property value if env is missed.
 */
private fun getenvOrProperty(name: String, defaultValue: String): String = getenvOrProperty(name) ?: defaultValue
