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
import arrow.core.flatMap
import arrow.core.getOrHandle
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.request.headers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assumptions.assumeThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.net.URL
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.system.measureNanoTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class SaveBackendTest {
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
                    .firstOrNull { (name) ->
                        name == organizationName
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
                    .firstOrNull { (name) ->
                        name == organizationName
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
                    .firstOrNull { (name) ->
                        name == organizationName
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
                    .firstOrNull { (name) ->
                        name == organizationName
                    }.assertNonNull("An organization named \"$organizationName\" not found or not accessible")

                val project = organization.listProjects()
                    .getOrHandle(SaveCloudError::fail)
                    .firstOrNull { (name) ->
                        name == projectName
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
                    val organization = organizations.firstOrNull { (name) ->
                        name == organizationName
                    }.assertNonNull("An organization named \"$organizationName\" not found or not accessible")

                    organization.listProjects().flatMap { projects ->
                        val project = projects.firstOrNull { (name) ->
                            name == projectName
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

    @Test
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    fun `private tests`(@TempDir tmpDir: Path) {
        with(client) {
            runBlocking {
                doTest(tmpDir, PRIVATE_TESTS)
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    fun `public tests`(@TempDir tmpDir: Path) {
        with(client) {
            runBlocking {
                doTest(tmpDir, PUBLIC_TESTS)
            }
        }
    }

    @Test
    @Timeout(TEST_TIMEOUT_MINUTES, unit = MINUTES)
    fun `contest mode`(@TempDir tmpDir: Path) {
        with(client) {
            runBlocking {
                val contest = listOrganizations().flatMap { organizations ->
                    val organization = organizations.firstOrNull { (name) ->
                        name == organizationName
                    }.assertNonNull("An organization named \"$organizationName\" not found or not accessible")

                    organization.listProjects().flatMap { projects ->
                        val project = projects.firstOrNull { (name) ->
                            name == projectName
                        }.assertNonNull("A project named \"$projectName\" not found or not accessible")

                        organization.listActiveContests(project.name)
                    }
                }
                    .getOrHandle(SaveCloudError::fail)
                    .firstOrNull { (name) ->
                        name == contestName
                    }
                    .assertNonNull("A contest named \"$contestName\" not found or not accessible")

                doTest(tmpDir, contest = contest)
            }
        }
    }

    @Suppress("TOO_LONG_FUNCTION")
    private suspend fun SaveCloudClientEx.doTest(
        tmpDir: Path,
        testingType: TestingType = CONTEST_MODE,
        contest: ContestDto? = null
    ) {
        require((testingType == CONTEST_MODE) == (contest != null))

        val organization = listOrganizations()
            .getOrHandle(SaveCloudError::fail)
            .firstOrNull { (name) ->
                name == organizationName
            }.assertNonNull("An organization named \"$organizationName\" not found or not accessible")

        val project = organization.listProjects()
            .getOrHandle(SaveCloudError::fail)
            .firstOrNull { (name) ->
                name == projectName
            }
            .assertNonNull("A project named \"$projectName\" not found or not accessible")

        val testSuites = organization.listTestSuites()
            .getOrHandle(SaveCloudError::fail)
            .filtered()
            .assertNonEmpty("No test suites found")

        val files = when {
            useExternalFiles -> downloadFromGitHub(
                downloadDir = tmpDir,
                *gitHubProjects.toTypedArray()
            ).map { asset ->
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

        try {
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
        } finally {
            files.forEach { file ->
                organization.deleteFile(projectName, file.key)
                    .getOrHandle(SaveCloudError::fail)
            }
        }
    }

    companion object {
        @Suppress("GENERIC_VARIABLE_WRONG_DECLARATION")
        private val logger = getLogger<SaveBackendTest>()
        private const val DEFAULT_AUTHORIZATION_SOURCE = "basic"
        private const val DEFAULT_BACKEND_URL = "http://localhost:5800"
        private const val DEFAULT_ORGANIZATION_NAME = "CQFN.org"
        private const val DEFAULT_PASSWORD = ""
        private const val DEFAULT_PROJECT_NAME = "Diktat"
        private const val DEFAULT_TEST_LANGUAGE = "Kotlin"
        private const val DEFAULT_TEST_VERSION = "master"
        private const val DEFAULT_USER = "admin"
        private const val DEFAULT_USE_EXTERNAL_FILES = true
        private const val POLL_DELAY_MILLIS = 100L
        private const val TEST_TIMEOUT_MINUTES = 5L
        private val gitHubProjects = listOf(
            GitHubProject("saveourtool" / "diktat"),
            GitHubProject("pinterest" / "ktlint", tag = "0.46.1")
        )

        @Suppress("CUSTOM_GETTERS_SETTERS")
        private val backendUrl: String
            get() {
                val backendUrlOrEmpty = System.getProperty("save-cloud.backend.url", DEFAULT_BACKEND_URL)

                return when {
                    backendUrlOrEmpty.isEmpty() -> DEFAULT_BACKEND_URL
                    else -> backendUrlOrEmpty
                }
            }

        @Suppress("CUSTOM_GETTERS_SETTERS")
        private val user: String
            get() =
                System.getProperty("save-cloud.user", DEFAULT_USER)

        /**
         * @return either the password or the _personal access token_.
         */
        @Suppress("CUSTOM_GETTERS_SETTERS")
        private val passwordOrToken: String
            get() =
                System.getProperty("save-cloud.password", DEFAULT_PASSWORD)

        @Suppress("CUSTOM_GETTERS_SETTERS")
        private val authorizationSource: String
            get() =
                System.getProperty("save-cloud.user.auth.source", DEFAULT_AUTHORIZATION_SOURCE)

        @Suppress("CUSTOM_GETTERS_SETTERS")
        private val organizationName: String
            get() =
                DEFAULT_ORGANIZATION_NAME

        @Suppress("CUSTOM_GETTERS_SETTERS")
        private val projectName: String
            get() =
                DEFAULT_PROJECT_NAME

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

        @Suppress(
            "NO_CORRESPONDING_PROPERTY",
            "CUSTOM_GETTERS_SETTERS",
        )
        private val testSuiteIds: Set<Long>
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
        private val testVersion: String?
            get() {
                val rawTestVersion = System.getProperty("save-cloud.test.version", DEFAULT_TEST_VERSION)

                return when {
                    rawTestVersion.isEmpty() -> null
                    rawTestVersion.isBlank() -> null
                    else -> rawTestVersion
                }
            }

        @Suppress("CUSTOM_GETTERS_SETTERS")
        private val testLanguage: String?
            get() {
                val rawTestLanguage = System.getProperty("save-cloud.test.language", DEFAULT_TEST_LANGUAGE)

                return when {
                    rawTestLanguage.isEmpty() -> null
                    rawTestLanguage.isBlank() -> null
                    else -> rawTestLanguage
                }
            }

        @Suppress("CUSTOM_GETTERS_SETTERS")
        private val useExternalFiles: Boolean
            get() =
                System.getProperty(
                    "save-cloud.use.external.files",
                    DEFAULT_USE_EXTERNAL_FILES.toString()
                ).toBooleanStrict()

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

        private fun List<TestSuiteDto>.filtered(): List<TestSuiteDto> {
            val selectById = testSuiteIds.isNotEmpty()
            val selectByVersionAndLanguage = testVersion != null && testLanguage != null

            assumeThat(selectById || selectByVersionAndLanguage)
                .describedAs("Test suite selector not specified: version = $testVersion, language = $testLanguage, test suite ids = $testSuiteIds")
                .isTrue

            @Suppress("NO_BRACES_IN_CONDITIONALS_AND_LOOPS")
            val predicate: TestSuiteDto.() -> Boolean = when {
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
    }
}
