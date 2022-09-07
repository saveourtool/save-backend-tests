package com.saveourtool.save

import com.saveourtool.save.api.SaveCloudClient
import com.saveourtool.save.api.authorization.Authorization
import com.saveourtool.save.api.config.EvaluatedToolProperties
import com.saveourtool.save.api.config.WebClientProperties
import com.saveourtool.save.execution.TestingType.CONTEST_MODE

import arrow.core.getOrElse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assumptions.assumeThat
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.fail
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER
import org.junit.jupiter.params.ParameterizedTest.DISPLAY_NAME_PLACEHOLDER
import org.junit.jupiter.params.provider.MethodSource

import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit.MINUTES
import java.util.stream.Stream

import kotlinx.coroutines.runBlocking

class SaveBackendTest {
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    @ParameterizedTest(name = DEFAULT_TEST_NAME)
    @MethodSource("getBackendUrls")
    fun `should return HTTP 401 if unauthorized`(backendUrl: String) = runBlocking<Unit> {
        try {
            val webClientProperties = WebClientProperties(backendUrl)
            val evaluatedToolProperties = EvaluatedToolProperties(
                organizationName = "",
                projectName = "",
                testSuites = "42,43")
            val testingType = CONTEST_MODE
            val client = SaveCloudClient(
                webClientProperties,
                evaluatedToolProperties,
                testingType,
                Authorization("foo@bar", "token"))
            val executionResult = client.start()

            assertThat(executionResult.isEmpty())
            assertThat(executionResult.swap().getOrElse { "" }).contains("HTTP 401 Unauthorized")
        } catch (ce: ConnectException) {
            fail(ce.message ?: ce.toString(), ce)
        } catch (uhe: UnknownHostException) {
            fail(uhe.message ?: uhe.toString(), uhe)
        }
    }

    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    @ParameterizedTest(name = DEFAULT_TEST_NAME)
    @MethodSource("getBackendUrls")
    fun `contest mode`(backendUrl: String) = runBlocking {
        try {
            val (user, key) = getAuthorization()

            val webClientProperties = WebClientProperties(backendUrl)
            val evaluatedToolProperties = EvaluatedToolProperties(
                organizationName = organizationName,
                projectName = projectName,
                testSuites = testSuiteId.toString())
            val testingType = CONTEST_MODE
            val client = SaveCloudClient(
                webClientProperties,
                evaluatedToolProperties,
                testingType,
                Authorization(user, key))
            val executionResult = client.start()

            if (executionResult.isEmpty()) {
                assertThat(executionResult.swap().getOrElse { "" })
                    .describedAs("Error message")
                    .contains("HTTP")
                    .doesNotContain("HTTP 401 Unauthorized")
                    .doesNotContain("HTTP 500 Internal Server Error")
            }
        } catch (ce: ConnectException) {
            fail(ce.message ?: ce.toString(), ce)
        } catch (uhe: UnknownHostException) {
            fail(uhe.message ?: uhe.toString(), uhe)
        }
    }

    companion object {
        private const val DEFAULT_TEST_NAME = "$DISPLAY_NAME_PLACEHOLDER [$ARGUMENTS_WITH_NAMES_PLACEHOLDER]"

        private const val DEFAULT_BACKEND_URL = "http://localhost:5800"

        private const val DEFAULT_ORGANIZATION_NAME = "Organization-Name-1"

        private const val DEFAULT_PROJECT_NAME = "Tool-Name-1"

        private const val DEFAULT_TEST_SUITE_ID = 9

        private const val TEST_TIMEOUT_MINUTES = 10L

        @JvmStatic
        val backendUrls: Stream<String>
            get() {
                val backendUrlOrEmpty = System.getProperty("save-cloud.backend.url", DEFAULT_BACKEND_URL)

                val backendUrl = when {
                    backendUrlOrEmpty.isEmpty() -> DEFAULT_BACKEND_URL
                    else -> backendUrlOrEmpty
                }

                return Stream.of(backendUrl)
            }

        private fun getAuthorization(): Pair<String, String> {
            val user = System.getProperty("gpr.user")
            assumeThat(user).describedAs("gpr.user system property is either null or empty")
                .isNotNull
                .isNotEmpty

            val key = System.getProperty("gpr.key")
            assumeThat(key).describedAs("gpr.key system property is either null or empty")
                .isNotNull
                .isNotEmpty

            return user to key
        }

        private val organizationName: String
            get() =
                DEFAULT_ORGANIZATION_NAME

        private val projectName: String
            get() =
                DEFAULT_PROJECT_NAME

        private val testSuiteId: Int
            get() =
                DEFAULT_TEST_SUITE_ID
    }
}
