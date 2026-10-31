// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import android.content.res.Resources
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection

fun TypeAnswer.Companion.createInstance(preferences: SharedPreferences): TypeAnswer =
    TypeAnswer(
        useInputTag = preferences.getBoolean("useInputTag", false),
        autoFocus = preferences.getBoolean("autoFocusTypeInAnswer", true),
    )

fun TypeAnswer.updateInfo(
    col: Collection,
    card: Card,
    resources: Resources,
) = updateInfo(col, card) { resources.getString(CommonString.unknown_type_field_warning, it) }

fun TypeAnswer.filterAnswer(answer: String): String = filterAnswer(answer, CollectionManager::compareAnswer)
