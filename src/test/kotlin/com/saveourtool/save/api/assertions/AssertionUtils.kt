@file:JvmName("AssertionUtils")
@file:Suppress(
    "HEADER_MISSING_IN_NON_SINGLE_CLASS_FILE",
    "MISSING_KDOC_TOP_LEVEL",
    "MISSING_KDOC_ON_FUNCTION",
)

package com.saveourtool.save.api.assertions

import com.saveourtool.save.api.errors.SaveCloudError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.fail

fun SaveCloudError.fail(): Nothing =
    fail(message)

fun <T : Any> T?.assertNonNull(errorMessage: String = "A non-null value expected"): T =
    this ?: fail {
        errorMessage
    }

fun <T : Any?> List<T>.assertNonEmpty(errorMessage: String = "A non-empty list expected"): List<T> {
    assertThat(this).describedAs(errorMessage).isNotEmpty

    return this
}
