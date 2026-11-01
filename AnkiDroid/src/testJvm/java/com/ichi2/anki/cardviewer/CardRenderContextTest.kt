// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import anki.config.ConfigKey
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CollectionFiles
import com.ichi2.anki.reviewer.ReviewerCustomFonts
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CardRenderContextTest : JvmTest() {
    @get:Rule(order = 0)
    val media = TemporaryFolder()

    override val collectionFiles: CollectionFiles
        get() = CollectionFiles.InMemoryWithMedia(media.root)

    @Test
    fun `Show audio play buttons preference handling - sound`() {
        val card = addBasicWithTypingNote("SOUND [sound:android_audiorec.3gp]", "back").firstCard()
        assertPlayButtonPreference(card, "SOUND")
    }

    @Test
    fun `Show audio play buttons preference handling - tts`() {
        addTextToSpeechNote("TTS", "BACK")
        assertPlayButtonPreference(requireNotNull(col.sched.card), "TTS")
    }

    private fun assertPlayButtonPreference(
        card: Card,
        text: String,
    ) {
        render(card).let { content ->
            assertThat("show audio preference default value: enabled", content, containsString("playsound:q:0"))
            assertThat("show audio preference default value: enabled", content, containsString(text))
        }
        col.config.setBool(ConfigKey.Bool.HIDE_AUDIO_PLAY_BUTTONS, true)
        render(card).let { content ->
            assertThat("show audio preference disabled", content, not(containsString("playsound:q:0")))
            assertThat("show audio preference disabled", content, containsString(text))
        }
        col.config.setBool(ConfigKey.Bool.HIDE_AUDIO_PLAY_BUTTONS, false)
        render(card).let { content ->
            assertThat("show audio preference enabled explicitly", content, containsString("playsound:q:0"))
            assertThat("show audio preference enabled explicitly", content, containsString(text))
        }
    }

    private fun render(card: Card): String {
        val preferences = SPMockBuilder().createSharedPreferences()
        val typeAnswer = TypeAnswer(useInputTag = false, autoFocus = true)
        typeAnswer.updateInfo(col, card) { error("Unexpected missing field: $it") }
        val appearance = createCardAppearance(ReviewerCustomFonts(), preferences, fontScale = 1f)
        val template = CardTemplate(File("src/main/assets/card_template.html").readText())
        return CardRenderContext.create(col, typeAnswer, appearance, template).renderCard(col, card, SingleCardSide.FRONT).html
    }
}
