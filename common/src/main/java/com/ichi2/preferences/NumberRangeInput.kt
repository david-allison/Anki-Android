// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Houssam Salem <houssam.salem.au@gmail.com>

package com.ichi2.preferences

import org.slf4j.LoggerFactory

object NumberRangeInput {
    private val logger = LoggerFactory.getLogger(NumberRangeInput::class.java)

    /** Clamp the input to the preference bounds. */
    fun clamp(
        input: Int,
        min: Int,
        max: Int,
    ): Int =
        when {
            input < min -> min
            input > max -> max
            else -> input
        }

    /** Blank or unparsable input falls back to the lower bound. */
    fun clamp(
        input: String,
        min: Int,
        max: Int,
    ): Int =
        if (input.isEmpty()) {
            min
        } else {
            try {
                clamp(input.toInt(), min, max)
            } catch (e: NumberFormatException) {
                logger.warn("Invalid number range preference input", e)
                min
            }
        }

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
