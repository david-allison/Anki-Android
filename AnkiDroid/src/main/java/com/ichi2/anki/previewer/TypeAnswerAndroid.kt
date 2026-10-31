// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.previewer

import android.os.LocaleList
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Card

suspend fun TypeAnswer.Companion.getInstance(
    card: Card,
    text: String,
): TypeAnswer? = withCol { getInstance(this, card, text) }

suspend fun TypeAnswer.answerFilter(typedAnswer: String = ""): String = withCol { answerFilter(this, typedAnswer) }

val TypeAnswer.imeHintLocales: LocaleList? get() = keyboardLanguageHint?.let { LocaleList(it) }
