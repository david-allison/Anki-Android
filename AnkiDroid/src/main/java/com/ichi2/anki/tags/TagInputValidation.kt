// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

/** Whether tag input can be submitted, with rejection reasons for the UI to localize. */
internal enum class TagInputValidation { VALID, EMPTY, EMPTY_COMPONENT, UNCHANGED, ALREADY_EXISTS, ERROR }

/** Checks syntax locally; backend identity and collisions are checked asynchronously. */
internal fun validateTagRenameInput(
    source: TagName,
    text: String,
): TagInputValidation {
    val candidate = TagName.build(text.trim()) ?: return TagInputValidation.EMPTY
    return when {
        candidate.value.split("::").any { it.isBlank() } -> TagInputValidation.EMPTY_COMPONENT
        candidate.value == source.value -> TagInputValidation.UNCHANGED
        else -> TagInputValidation.VALID
    }
}
