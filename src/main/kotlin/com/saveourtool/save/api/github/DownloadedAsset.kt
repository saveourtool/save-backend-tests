package com.saveourtool.save.api.github

import io.ktor.http.ContentType
import java.nio.file.Path

/**
 * A downloaded _GitHub_ release asset.
 *
 * @property localFile the local file.
 * @property contentType the MIME `Content-Type`, as reported by _GitHub_.
 */
data class DownloadedAsset(
    val localFile: Path,
    val contentType: ContentType
)
