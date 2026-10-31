// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import com.ichi2.anki.CollectionManager.withCol

suspend fun SearchRequest.toSearchString(): String? = withCol { toSearchString() }.getOrNull()?.value
