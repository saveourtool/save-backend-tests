@file:Suppress("TYPE_ALIAS")

package com.saveourtool.save.api.github.impl

import com.saveourtool.save.api.errors.SaveCloudError
import com.saveourtool.save.api.github.DownloadedAsset
import com.saveourtool.save.api.github.GitHubClient
import com.saveourtool.save.api.github.GitHubProject
import com.saveourtool.save.api.github.ReleaseAsset
import com.saveourtool.save.api.github.ReleaseMetadata
import com.saveourtool.save.api.http.getAndCheck
import com.saveourtool.save.api.http.getAndOpenChannel
import com.saveourtool.save.api.io.writeChannel
import com.saveourtool.save.utils.getLogger
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerAuthConfig
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.url
import io.ktor.http.ContentType.Application
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.cio.use
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.div
import kotlin.system.measureNanoTime
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * _GitHub_ API client, the default implementation.
 *
 * @param ioContext the context to be used for I/O.
 * @param requestTimeoutMillis HTTP request timeout, in milliseconds.
 *   Should be large enough, otherwise the process of downloading large
 *   files may fail.
 * @param socketTimeoutMillis TCP socket timeout, in milliseconds.
 * @param bearerAuthConfiguration _HTTP Bearer_ authentication
 *   configuration.
 */
internal class DefaultGitHubClient(
    private val ioContext: CoroutineContext,
    requestTimeoutMillis: Long,
    socketTimeoutMillis: Long,
    bearerAuthConfiguration: BearerAuthConfig.() -> Unit
) : GitHubClient {
    private val httpClient = httpClient(
        requestTimeoutMillis,
        socketTimeoutMillis,
        bearerAuthConfiguration
    )

    override suspend fun downloadMetadata(project: GitHubProject): Either<SaveCloudError, ReleaseMetadata> =
        withContext(ioContext) {
            httpClient.getAndCheck {
                url(project.releaseMetadataUrl())
                accept(Application.Json)
            }
        }

    override suspend fun openChannel(asset: ReleaseAsset): Either<SaveCloudError, ByteReadChannel> =
        withContext(ioContext) {
            httpClient.getAndOpenChannel {
                url(asset.downloadUrl)
                accept(asset.contentType())
            }
        }

    override suspend fun download(
        project: GitHubProject,
        downloadDir: Path
    ): Either<SaveCloudError, List<DownloadedAsset>> =
        downloadMetadata(project).map { releaseMetadata ->
            logger.debug(releaseMetadata.toString())

            val assets = releaseMetadata.assets.asSequence()
                .filterNot(ReleaseAsset::isDigest)
                .toList()

            assets.map { asset ->
                val assetPath = downloadDir / asset.name
                logger.debug("Downloading from ${asset.downloadUrl} to $assetPath...")

                val bytesWritten: Long
                val nanos = measureNanoTime {
                    assetPath.writeChannel(ioContext).use {
                        bytesWritten = openChannel(asset).getOrElse { error ->
                            return error.left()
                        }.copyTo(this)
                    }
                }
                @Suppress("MagicNumber", "FLOAT_IN_ACCURATE_CALCULATIONS")
                logger.debug("Downloaded $bytesWritten byte(s) in ${nanos / 1000L / 1e3} ms.")
                check(asset.size == bytesWritten) {
                    "Asset size: ${asset.size} (expected), bytes written: $bytesWritten (actual)"
                }

                DownloadedAsset(assetPath, asset.contentType())
            }
        }

    private companion object {
        @Suppress("GENERIC_VARIABLE_WRONG_DECLARATION")
        private val logger = getLogger<DefaultGitHubClient>()

        private fun httpClient(
            requestTimeoutMillis: Long,
            socketTimeoutMillis: Long,
            bearerAuthConfiguration: BearerAuthConfig.() -> Unit
        ): HttpClient =
            HttpClient {
                install(HttpTimeout) {
                    this.requestTimeoutMillis = requestTimeoutMillis
                    this.socketTimeoutMillis = socketTimeoutMillis
                }
                install(Auth) {
                    bearer {
                        sendWithoutRequest {
                            true
                        }

                        bearerAuthConfiguration()
                    }
                }
                install(ContentNegotiation) {
                    val json = Json {
                        ignoreUnknownKeys = true
                    }

                    json(json)
                }
            }
    }
}
