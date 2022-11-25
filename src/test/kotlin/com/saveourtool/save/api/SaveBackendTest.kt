package com.saveourtool.save.api

import com.saveourtool.save.api.assertions.assertNonEmpty
import com.saveourtool.save.api.assertions.assertNonNull
import com.saveourtool.save.api.assertions.fail
import com.saveourtool.save.api.errors.SaveCloudError
import com.saveourtool.save.api.github.DownloadedAsset
import com.saveourtool.save.api.github.GitHubClient
import com.saveourtool.save.api.github.GitHubProject
import com.saveourtool.save.api.github.div
import com.saveourtool.save.domain.FileInfo
import com.saveourtool.save.domain.Jdk
import com.saveourtool.save.domain.ProjectCoordinates
import com.saveourtool.save.entities.ContestDto
import com.saveourtool.save.entities.Organization
import com.saveourtool.save.entities.Project
import com.saveourtool.save.execution.ExecutionDto
import com.saveourtool.save.execution.ExecutionStatus.FINISHED
import com.saveourtool.save.execution.ExecutionStatus.PENDING
import com.saveourtool.save.execution.ExecutionStatus.RUNNING
import com.saveourtool.save.execution.TestingType
import com.saveourtool.save.execution.TestingType.CONTEST_MODE
import com.saveourtool.save.execution.TestingType.PRIVATE_TESTS
import com.saveourtool.save.execution.TestingType.PUBLIC_TESTS
import com.saveourtool.save.request.CreateExecutionRequest
import com.saveourtool.save.testsuite.TestSuiteDto
import com.saveourtool.save.utils.getLogger
import arrow.core.getOrHandle
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.request.headers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assumptions.assumeThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.system.measureNanoTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

@Suppress("Destructure")
class SaveBackendTest {
    @Test
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    fun `private tests`(@TempDir tmpDir: Path) {
        with(client) {
            runBlocking {
                doTest(PRIVATE_TESTS)
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    fun `public tests`(@TempDir tmpDir: Path) {
        with(client) {
            runBlocking {
                doTest(PUBLIC_TESTS)
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    fun `contest mode`(@TempDir tmpDir: Path) {
        with(client) {
            runBlocking {
                val contest = organization.listActiveContests(project.name)
                    .getOrHandle(SaveCloudError::fail)
                    .firstOrNull { contest ->
                        contest.name == contestName
                    }
                    .assertNonNull("A contest named \"$contestName\" not found or not accessible")

                doTest(contest = contest)
            }
        }
    }

    @Suppress("TOO_LONG_FUNCTION")
    private suspend fun SaveCloudClientEx.doTest(
        testingType: TestingType = CONTEST_MODE,
        contest: ContestDto? = null
    ) {
        require((testingType == CONTEST_MODE) == (contest != null))

        val testSuites = organization.listTestSuites()
            .getOrHandle(SaveCloudError::fail)
            .filtered()
            .assertNonEmpty("No test suites found")

        val executionRequest = CreateExecutionRequest(
            projectCoordinates = ProjectCoordinates(
                organizationName,
                projectName,
            ),
            testSuiteIds = testSuites.asSequence()
                .map(TestSuiteDto::id)
                .filterNotNull()
                .toList(),
            files = files.map(FileInfo::key),
            sdk = Jdk(version = "11"),
            testingType = testingType,
            contestName = contest?.name
        )

        val executionId = submitExecution(executionRequest)
            .getOrHandle(SaveCloudError::fail)
            .id
        logger.debug("Waiting for execution (id = $executionId) to complete...")

        var execution: ExecutionDto
        val nanos = measureNanoTime {
            do {
                execution = getExecutionById(executionId)
                    .getOrHandle(SaveCloudError::fail)
                delay(POLL_DELAY_MILLIS)
            } while (execution.status in arrayOf(PENDING, RUNNING))
        }
        @Suppress("FLOAT_IN_ACCURATE_CALCULATIONS")
        logger.debug("The execution (id = $executionId) has completed in ${nanos / 1000L / 1e3} ms.")

        /*
         * Requires that the orchestrator and the preprocessor are running, too.
         */
        assertThat(execution.status)
            .describedAs("execution status")
            .isEqualTo(FINISHED)
        assertThat(execution.allTests)
            .describedAs("the number of tests")
            .isGreaterThan(0L)
    }

    companion object {
        @Suppress("GENERIC_VARIABLE_WRONG_DECLARATION")
        private val logger = getLogger<SaveBackendTest>()
        private const val DEFAULT_USE_EXTERNAL_FILES = true
        private const val POLL_DELAY_MILLIS = 100L
        private val gitHubProjects = listOf(
            GitHubProject("saveourtool" / "diktat", tag = "v1.2.3"),
            GitHubProject("pinterest" / "ktlint", tag = "0.46.1")
        )

        @Suppress("CUSTOM_GETTERS_SETTERS")
        private val contestName: String?
            get() {
                val rawContestName = System.getProperty("save-cloud.contest.name")

                assumeThat(rawContestName)
                    .describedAs("Contest name")
                    .isNotNull
                    .isNotEmpty

                return rawContestName
            }

        @Suppress("CUSTOM_GETTERS_SETTERS")
        private val useExternalFiles: Boolean
            get() =
                System.getProperty(
                    "save-cloud.use.external.files",
                    DEFAULT_USE_EXTERNAL_FILES.toString()
                ).toBooleanStrict()

        private lateinit var client: SaveCloudClientEx

        private lateinit var organization: Organization

        private lateinit var project: Project

        private lateinit var files: List<FileInfo>

        @JvmStatic
        @BeforeAll
        fun beforeAll(@TempDir tmpDir: Path) {
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

            with(client) {
                runBlocking {
                    organization = listOrganizations()
                        .getOrHandle(SaveCloudError::fail)
                        .firstOrNull { organization ->
                            organization.name == organizationName
                        }.assertNonNull("An organization named \"$organizationName\" not found or not accessible")

                    project = organization.listProjects()
                        .getOrHandle(SaveCloudError::fail)
                        .firstOrNull { project ->
                            project.name == projectName
                        }
                        .assertNonNull("A project named \"$projectName\" not found or not accessible")

                    files = when {
                        useExternalFiles -> runBlocking {
                            downloadFromGitHub(
                                downloadDir = tmpDir,
                                *gitHubProjects.toTypedArray()
                            )
                        }.map { asset ->
                            logger.debug("Uploading $asset...")
                            organization.uploadFile(
                                project.name,
                                asset.localFile,
                                asset.contentType,
                                stripVersionFromName = true
                            ).getOrHandle(SaveCloudError::fail)
                        }

                        else -> emptyList()
                    }
                }
            }
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            with(client) {
                runBlocking {
                    files.forEach { file ->
                        organization.deleteFile(projectName, file.key)
                            .getOrHandle(SaveCloudError::fail)
                    }
                }
            }
        }

        private suspend fun downloadFromGitHub(
            downloadDir: Path,
            vararg projects: GitHubProject
        ): List<DownloadedAsset> =
            with(GitHubClient()) {
                projects.flatMap { project ->
                    project.downloadTo(downloadDir)
                        .getOrHandle(SaveCloudError::fail)
                }
            }
    }
}
