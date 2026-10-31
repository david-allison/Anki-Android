// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import com.ichi2.anki.CollectionManager.TR

/** Human-readable label using the application's active translation backend. */
val CardState.label: String get() = label(TR)
