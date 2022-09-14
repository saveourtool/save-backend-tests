package com.saveourtool.save.api.github

import java.net.URL

/**
 * _GitHub_ project descriptor.
 *
 * @property organizationName the name of the organization.
 * @property projectName the name of the project within the organization.
 * @property tag the version control tag (usually identifies a release). If
 *   `null`, the latest release is assumed.
 */
data class GitHubProject(
    val organizationName: String,
    val projectName: String,
    val tag: String? = null
) {
    /**
     * Creates a new _GitHub_ project descriptor.
     *
     * @param organizationAndProject the organization-project pair which
     *   uniquely identifies a _GitHub_ project.
     * @param tag the version control tag (usually identifies a release). If
     *   `null`, the latest release is assumed.
     */
    constructor(organizationAndProject: Pair<String, String>, tag: String? = null) : this(
        organizationAndProject.first,
        organizationAndProject.second,
        tag
    )

    /**
     * @return the URL which returns the JSON metadata about the releases.
     * @see ReleaseMetadata
     */
    fun releaseMetadataUrl(): URL {
        val release = when (tag) {
            null -> LATEST_VERSION
            LATEST_VERSION -> LATEST_VERSION
            else -> "tags/$tag"
        }

        return URL("https://api.github.com/repos/$organizationName/$projectName/releases/$release")
    }

    private companion object {
        private const val LATEST_VERSION = "latest"
    }
}
