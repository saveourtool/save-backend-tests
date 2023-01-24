@file:JvmName("SaveBackendUtils")
@file:Suppress(
    "HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE",
)

package com.saveourtool.save.api

import com.saveourtool.save.testsuite.TestSuiteVersioned
import org.assertj.core.api.Assumptions.assumeThat

private const val DEFAULT_AUTHORIZATION_SOURCE = "basic"
private const val DEFAULT_BACKEND_URL = "http://localhost:5800"
private const val DEFAULT_ORGANIZATION_NAME = "CQFN.org"
private const val DEFAULT_PASSWORD = ""
private const val DEFAULT_PROJECT_NAME = "Diktat-Integration"
private const val DEFAULT_TEST_LANGUAGE = "Kotlin"
private const val DEFAULT_TEST_VERSION = "master"
private const val DEFAULT_USER = "admin"
internal const val TEST_TIMEOUT_MINUTES = 20L

@Suppress("CUSTOM_GETTERS_SETTERS")
internal val backendUrl: String
    get() {
        val backendUrlOrEmpty = System.getProperty("save-cloud.backend.url", DEFAULT_BACKEND_URL)

        return when {
            backendUrlOrEmpty.isEmpty() -> DEFAULT_BACKEND_URL
            else -> backendUrlOrEmpty
        }
    }

@Suppress("CUSTOM_GETTERS_SETTERS")
internal val user: String
    get() =
        System.getProperty("save-cloud.user", DEFAULT_USER)

/**
 * @return either the password or the _personal access token_.
 */
@Suppress("CUSTOM_GETTERS_SETTERS")
internal val passwordOrToken: String
    get() =
        System.getProperty("save-cloud.password", DEFAULT_PASSWORD)

@Suppress("CUSTOM_GETTERS_SETTERS")
internal val authorizationSource: String
    get() =
        System.getProperty("save-cloud.user.auth.source", DEFAULT_AUTHORIZATION_SOURCE)

@Suppress("CUSTOM_GETTERS_SETTERS")
internal val organizationName: String
    get() =
        DEFAULT_ORGANIZATION_NAME

@Suppress("CUSTOM_GETTERS_SETTERS")
internal val projectName: String
    get() =
        System.getProperty("save-cloud.project.name", DEFAULT_PROJECT_NAME)

@Suppress(
    "NO_CORRESPONDING_PROPERTY",
    "CUSTOM_GETTERS_SETTERS",
)
internal val testSuiteIds: Set<Long>
    get() {
        val rawTestSuiteIds = System.getProperty("save-cloud.test.suite.ids")
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
        val rawTestVersion = System.getProperty("save-cloud.test.version", DEFAULT_TEST_VERSION)

        return when {
            rawTestVersion.isEmpty() -> null
            rawTestVersion.isBlank() -> null
            else -> rawTestVersion
        }
    }

@Suppress("CUSTOM_GETTERS_SETTERS")
internal val testLanguage: String?
    get() {
        val rawTestLanguage = System.getProperty("save-cloud.test.language", DEFAULT_TEST_LANGUAGE)

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
            { version == testVersion && language == testLanguage }
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
