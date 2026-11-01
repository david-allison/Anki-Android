// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.content.Context
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.reviewer.ReviewerCustomFonts

/** Loads the Android resources and preferences used by the card renderer. */
fun createCardRenderContext(
    context: Context,
    col: Collection,
    typeAnswer: TypeAnswer,
): CardRenderContext {
    val preferences = context.sharedPrefs()
    val appearance = CardAppearance.create(ReviewerCustomFonts(), preferences, context.resources.configuration.fontScale)
    return CardRenderContext.create(col, typeAnswer, appearance, CardTemplate.load(context))
}
