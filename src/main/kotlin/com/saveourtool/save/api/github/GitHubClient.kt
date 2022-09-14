@file:Suppress("TYPE_ALIAS")

package com.saveourtool.save.api.github

import com.saveourtool.save.api.errors.SaveCloudError
import com.saveourtool.save.api.github.impl.DefaultGitHubClient
import arrow.core.Either
import io.ktor.client.plugins.auth.providers.BearerAuthConfig
import io.ktor.utils.io.ByteReadChannel
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

/**
 * _GitHub_ API client.
 */
interface GitHubClient {
    /**
     * Downloads the metadata for a _GitHub_ project.
     *
     * @param project the project descriptor which uniquely identifies the
     *   project and its release.
     * @return either the downloaded metadata, or the error if an error has
     *   occurred.
     */
    suspend fun downloadMetadata(project: GitHubProject): Either<SaveCloudError, ReleaseMetadata>

    /**
     * Opens a read-channel to the remote release [asset].
     *
     * @param asset the remote release asset.
     * @return the newly-opened read-channel.
     */
    suspend fun openChannel(asset: ReleaseAsset): Either<SaveCloudError, ByteReadChannel>

    /**
     * Downloads the given release of a GitHub project to the specified
     * directory.
     *
     * @param project the project descriptor which identifies the project and
     *   the release.
     * @param downloadDir the target download directory.
     * @return the list of downloaded assets.
     * @see GitHubProject.downloadTo
     */
    suspend fun download(project: GitHubProject, downloadDir: Path): Either<SaveCloudError, List<DownloadedAsset>>

    /**
     * Downloads the release of this GitHub project to the specified directory.
     *
     * @param downloadDir the target download directory.
     * @return the list of downloaded assets.
     * @see GitHubClient.download
     */
    suspend fun GitHubProject.downloadTo(downloadDir: Path): Either<SaveCloudError, List<DownloadedAsset>> =
        download(this, downloadDir)

    /**
     * The factory object.
     */
    companion object Factory {
        private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 100_000L
        private const val DEFAULT_SOCKET_TIMEOUT_MILLIS = 100_000L

        /**
         * Creates a new _GitHub_ API client.
         *
         * @param ioContext the context to be used for I/O, defaults to
         *   [Dispatchers.IO].
         * @param requestTimeoutMillis HTTP request timeout, in milliseconds.
         *   Should be large enough, otherwise the process of downloading large
         *   files may fail.
         * @param socketTimeoutMillis TCP socket timeout, in milliseconds.
         * @param bearerAuthConfiguration _HTTP Bearer_ authentication
         *   configuration.
         * @return the newly-created client instance.
         */
        operator fun invoke(
            ioContext: CoroutineContext = Dispatchers.IO,
            requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
            socketTimeoutMillis: Long = DEFAULT_SOCKET_TIMEOUT_MILLIS,
            bearerAuthConfiguration: BearerAuthConfig.() -> Unit = {}
        ): GitHubClient =
            DefaultGitHubClient(
                ioContext,
                requestTimeoutMillis,
                socketTimeoutMillis,
                bearerAuthConfiguration
            )
    }
}
