// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

internal object NumberRangeInput {
    enum class ValidationResult {
        VALID,
        INVALID,
        EMPTY,
        OVERFLOW,
        UNDERFLOW,
    }

    /**
     * Validation logic for the preference input.
     *
     * @param text The raw input string.
     * @param value The parsed integer value (or null if parsing failed).
     * @param min The minimum allowed value.
     * @param max The maximum allowed value.
     * @return A [ValidationResult] representing the state of the input.
     */
    fun validate(
        text: String,
        value: Int?,
        min: Int,
        max: Int,
    ): ValidationResult =
        when {
            text.isEmpty() -> ValidationResult.EMPTY
            value == null -> ValidationResult.INVALID
            value > max -> ValidationResult.OVERFLOW
            value < min -> ValidationResult.UNDERFLOW
            else -> ValidationResult.VALID
        }
}
