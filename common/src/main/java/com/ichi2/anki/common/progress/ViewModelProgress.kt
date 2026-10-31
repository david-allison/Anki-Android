// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.common.progress

/** Progress state observed by the UI. See [ProgressManager] for concurrent-op semantics. */
sealed interface ViewModelProgress<out M : Any> {
    data object Idle : ViewModelProgress<Nothing>

    data class Active<M : Any>(
        val message: M? = null,
        val amount: ProgressAmount? = null,
        val cancellable: Boolean = false,
        val formatAmount: (ProgressAmount) -> String =
            { (current, max) -> "$current/$max" },
        val separator: String = " ",
    ) : ViewModelProgress<M>
}
