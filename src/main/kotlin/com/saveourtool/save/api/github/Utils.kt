@file:JvmName("Utils")
@file:Suppress("HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE")

package com.saveourtool.save.api.github

/**
 * Creates an organization-project pair which uniquely identifies a _GitHub_
 * project.
 *
 * @receiver the name of the organization.
 * @param projectName the name of the project.
 * @return the newly created organization-project pair.
 */
operator fun String.div(projectName: String): Pair<String, String> =
    this to projectName
