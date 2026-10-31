// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.common.utils.ext.getStringOrNull
import java.util.Locale

/** AnkiDroid's optional language hint stored alongside a note-type field. */
var Field.keyboardLanguageHint: Locale?
    get() = jsonObject.getStringOrNull("ad-hint-locale")?.let(Locale::forLanguageTag)
    set(value) {
        jsonObject.put("ad-hint-locale", value?.toLanguageTag())
    }
