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
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

fun SaveCloudError.fail(): Nothing =
    fail(message)

@OptIn(ExperimentalContracts::class)
fun <T : Any> T?.assertNonNull(errorMessage: String = "A non-null value expected"): T {
    contract {
        returns() implies (this@assertNonNull != null)
    }

    return this ?: fail {
        errorMessage
    }
}

fun <T : Any?> List<T>.assertNonEmpty(errorMessage: String = "A non-empty list expected"): List<T> {
    assertThat(this).describedAs(errorMessage).isNotEmpty

    return this
}
