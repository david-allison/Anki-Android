// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.common.progress

/** A completed amount and total, using Long values to support byte counts. */
data class ProgressAmount(
    val current: Long,
    val max: Long,
)
