// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.content.Context
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.multimedia.getTagType
import com.ichi2.anki.reviewer.ReviewerCustomFonts
import com.ichi2.anki.utils.ext.convertToString

/** Loads the Android resources and preferences used by the card renderer. */
fun createCardRenderContext(
    context: Context,
    col: Collection,
    typeAnswer: TypeAnswer,
): CardRenderContext {
    val preferences = context.sharedPrefs()
    val appearance = createCardAppearance(ReviewerCustomFonts(), preferences, context.resources.configuration.fontScale)
    val template = CardTemplate(context.assets.open("card_template.html").convertToString())
    return CardRenderContext.create(
        col,
        typeAnswer,
        appearance,
        template,
        compareAnswer = CollectionManager::compareAnswer,
        classifyMedia = { tag, directory -> tag.getTagType(directory) },
    )
}
