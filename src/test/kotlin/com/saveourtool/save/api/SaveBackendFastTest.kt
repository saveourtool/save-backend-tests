package com.saveourtool.save.api

import com.saveourtool.save.api.assertions.assertNonEmpty
import com.saveourtool.save.api.assertions.assertNonNull
import com.saveourtool.save.api.assertions.fail
import com.saveourtool.save.api.errors.SaveCloudError
import com.saveourtool.save.testsuite.TestSuiteDto
import com.saveourtool.save.utils.getLogger
import arrow.core.flatMap
import arrow.core.getOrHandle
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.request.headers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.URL
import java.time.Duration
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import kotlinx.coroutines.runBlocking

@Suppress("Destructure")
class SaveBackendFastTest {
    private lateinit var client: SaveCloudClientEx

    @BeforeEach
    fun before() {
        client = SaveCloudClientEx(
            URL(backendUrl),
            requestTimeoutMillis = SECONDS.toMillis(500L)
        ) {
            basic {
                sendWithoutRequest { requestBuilder ->
                    requestBuilder.headers {
                        this["X-Authorization-Source"] = authorizationSource
                    }

                    true
                }

                credentials {
                    BasicAuthCredentials(username = user, password = passwordOrToken)
                }
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    fun `list organizations`() {
        with(client) {
            runBlocking {
                val organizations = listOrganizations()
                    .getOrHandle(SaveCloudError::fail)
                    .assertNonEmpty("No organizations found")

                logger.debug("Found ${organizations.size} organization(s):")
                organizations.forEachIndexed { index, organization ->
                    logger.debug("\t$index: $organization")
                }
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    fun `list projects`() {
        with(client) {
            runBlocking {
                val projects = listOrganizations()
                    .getOrHandle(SaveCloudError::fail)
                    .firstOrNull { organization ->
                        organization.name == organizationName
                    }
                    .assertNonNull("An organization named \"$organizationName\" not found or not accessible")
                    .listProjects()
                    .getOrHandle(SaveCloudError::fail)
                    .assertNonEmpty("No projects found")

                logger.debug("Found ${projects.size} project(s):")
                projects.forEachIndexed { index, project ->
                    logger.debug("\t$index: $project")
                }
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    fun `list test suites`() {
        with(client) {
            runBlocking {
                val testSuites = listOrganizations()
                    .getOrHandle(SaveCloudError::fail)
                    .firstOrNull { organization ->
                        organization.name == organizationName
                    }
                    .assertNonNull("An organization named \"$organizationName\" not found or not accessible")
                    .listTestSuites()
                    .getOrHandle(SaveCloudError::fail)
                    .assertNonEmpty("No test suites found")

                logger.debug("Found ${testSuites.size} test suite(s):")
                testSuites.asSequence().sortedBy(TestSuiteDto::id).forEach { testSuite ->
                    logger.debug("\t${testSuite.id}: $testSuite")
                }
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    fun `filter test suites`() {
        with(client) {
            runBlocking {
                val testSuites = listOrganizations()
                    .getOrHandle(SaveCloudError::fail)
                    .firstOrNull { organization ->
                        organization.name == organizationName
                    }
                    .assertNonNull("An organization named \"$organizationName\" not found or not accessible")
                    .listTestSuites()
                    .getOrHandle(SaveCloudError::fail)
                    .filtered()
                    .assertNonEmpty("No test suites found")

                logger.debug("Filtered ${testSuites.size} test suite(s):")
                testSuites.asSequence().sortedBy(TestSuiteDto::id).forEach { testSuite ->
                    logger.debug("\t${testSuite.id}: $testSuite")
                }
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    fun `list files`() {
        with(client) {
            runBlocking {
                val organization = listOrganizations()
                    .getOrHandle(SaveCloudError::fail)
                    .firstOrNull { organization ->
                        organization.name == organizationName
                    }.assertNonNull("An organization named \"$organizationName\" not found or not accessible")

                val project = organization.listProjects()
                    .getOrHandle(SaveCloudError::fail)
                    .firstOrNull { project ->
                        project.name == projectName
                    }
                    .assertNonNull("A project named \"$projectName\" not found or not accessible")

                val files = organization.listFiles(project.name)
                    .getOrHandle(SaveCloudError::fail)

                logger.debug("Found ${files.size} file(s):")
                files.forEachIndexed { index, file ->
                    val fileAgeMillis = System.currentTimeMillis() - file.key.uploadedMillis
                    val fileAge = Duration.ofMillis(fileAgeMillis)
                    assertThat(fileAge.isNegative)
                        .describedAs("The age of ${file.name} is negative")
                        .isFalse

                    logger.debug("\t$index: ${file.name}, size ${file.sizeBytes} byte(s), uploaded ${fileAge.toDays()} day(s) ago")
                }
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    fun `list contests`() {
        with(client) {
            runBlocking {
                val contests = listOrganizations().flatMap { organizations ->
                    val organization = organizations.firstOrNull { organization ->
                        organization.name == organizationName
                    }.assertNonNull("An organization named \"$organizationName\" not found or not accessible")

                    organization.listProjects().flatMap { projects ->
                        val project = projects.firstOrNull { project ->
                            project.name == projectName
                        }.assertNonNull("A project named \"$projectName\" not found or not accessible")

                        organization.listActiveContests(project.name)
                    }
                }.getOrHandle(SaveCloudError::fail)
                    .assertNonEmpty("No contests found")

                logger.debug("Found ${contests.size} contest(s):")
                contests.forEachIndexed { index, contest ->
                    logger.debug("\t$index: $contest")
                }
            }
        }
    }

    companion object {
        @Suppress("GENERIC_VARIABLE_WRONG_DECLARATION")
        private val logger = getLogger<SaveBackendFastTest>()
    }
}
