// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.TtsVoiceMatch
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import java.util.Locale

class AndroidTtsVoiceSelectionTest {
    @Test
    fun `locale fallback respects engine preference order`() {
        val first = AndroidTtsVoice(fakeVoice("first", Locale.FRANCE), ENGINE_A)
        val second = AndroidTtsVoice(fakeVoice("second", Locale.FRANCE), ENGINE_B)

        for (voices in listOf(listOf(first, second), listOf(second, first))) {
            val player = AndroidTtsPlayer(voices)
            assertThat(player.voiceForTag(frenchTag()), equalTo(TtsVoiceMatch(voices.first(), rank = -100)))
        }
    }

    @Test
    fun `unavailable voices follow installed voices with engine order preserved in both groups`() {
        val installedB = AndroidTtsVoice(fakeVoice("installed-b", Locale.FRANCE), ENGINE_B)
        val unavailableB = AndroidTtsVoice(fakeVoice("unavailable-b", Locale.FRANCE, installed = false), ENGINE_B)
        val installedA = AndroidTtsVoice(fakeVoice("installed-a", Locale.FRANCE), ENGINE_A)
        val unavailableA = AndroidTtsVoice(fakeVoice("unavailable-a", Locale.FRANCE, installed = false), ENGINE_A)
        val installedC = AndroidTtsVoice(fakeVoice("installed-c", Locale.FRANCE), ENGINE_C)
        val unavailableC = AndroidTtsVoice(fakeVoice("unavailable-c", Locale.FRANCE, installed = false), ENGINE_C)
        val player = AndroidTtsPlayer(listOf(unavailableB, installedB, unavailableA, installedA, unavailableC, installedC))

        assertThat(player.voices(), equalTo(listOf(installedB, installedA, installedC, unavailableB, unavailableA, unavailableC)))
        assertThat(player.voiceForTag(frenchTag()), equalTo(TtsVoiceMatch(installedB, rank = -100)))
    }

    @Test
    fun `locale fallback uses another engine when the default lacks the language`() {
        val french = AndroidTtsVoice(fakeVoice("french", Locale.FRANCE), ENGINE_A)
        val english = AndroidTtsVoice(fakeVoice("english", Locale.US), ENGINE_B)
        val player = AndroidTtsPlayer(listOf(english, french))

        assertThat(player.voiceForTag(frenchTag()), equalTo(TtsVoiceMatch(french, rank = -100)))
    }

    @Test
    fun `installed voice from another engine precedes unavailable default engine voice`() {
        val installed = AndroidTtsVoice(fakeVoice("installed", Locale.FRANCE), ENGINE_A)
        val unavailable = AndroidTtsVoice(fakeVoice("unavailable", Locale.FRANCE, installed = false), ENGINE_B)
        val player = AndroidTtsPlayer(listOf(unavailable, installed))

        assertThat(player.voiceForTag(frenchTag()), equalTo(TtsVoiceMatch(installed, rank = -100)))
    }

    @Test
    fun `explicit voice from another engine takes precedence over the default engine`() {
        val requested = AndroidTtsVoice(fakeVoice("requested", Locale.FRANCE), ENGINE_A)
        val preferred = AndroidTtsVoice(fakeVoice("preferred", Locale.FRANCE), ENGINE_B)
        val player = AndroidTtsPlayer(listOf(preferred, requested))

        assertThat(player.voiceForTag(frenchTag(requested.name)), equalTo(TtsVoiceMatch(requested, rank = 0)))
        assertThat(player.voiceForTag(frenchTag("missing")), equalTo(TtsVoiceMatch(preferred, rank = -100)))
    }

    @Test
    fun `network timeout is reported with its specific error code`() {
        val error = AndroidTtsError.fromErrorCode(TextToSpeech.ERROR_NETWORK_TIMEOUT)

        assertThat(error.developerString, equalTo("ERROR_NETWORK_TIMEOUT"))
    }

    @Test
    fun `locale fallback prefers the first installed voice`() {
        val unavailable = frenchVoice("unavailable", installed = false)
        val first = frenchVoice("first")
        val second = frenchVoice("second")
        val player = AndroidTtsPlayer(listOf(unavailable, first, second))

        assertThat(player.voiceForTag(frenchTag()), equalTo(TtsVoiceMatch(first, rank = -100)))
    }

    @Test
    fun `locale fallback keeps the first unavailable voice when no matching voice is installed`() {
        val first = frenchVoice("first", installed = false)
        val second = frenchVoice("second", installed = false)
        val english = AndroidTtsVoice(fakeVoice("english", Locale.US), ENGINE_A)
        val player = AndroidTtsPlayer(listOf(first, english, second))

        assertThat(player.voiceForTag(frenchTag()), equalTo(TtsVoiceMatch(first, rank = -100)))
    }

    @Test
    fun `requested voice order takes precedence over availability`() {
        val installed = frenchVoice("installed")
        val unavailable = frenchVoice("unavailable", installed = false)
        val player = AndroidTtsPlayer(listOf(installed, unavailable))
        val tag = frenchTag("missing", unavailable.name, installed.name)

        assertThat(player.voiceForTag(tag), equalTo(TtsVoiceMatch(unavailable, rank = -1)))
    }

    @Test
    fun `unmatched requested voices fall back to an installed voice`() {
        val unavailable = frenchVoice("unavailable", installed = false)
        val installed = frenchVoice("installed")
        val player = AndroidTtsPlayer(listOf(unavailable, installed))

        assertThat(player.voiceForTag(frenchTag("missing")), equalTo(TtsVoiceMatch(installed, rank = -100)))
    }

    @Test
    fun `no match when no voice shares the language`() {
        val english = AndroidTtsVoice(fakeVoice("english", Locale.US), ENGINE_A)
        val player = AndroidTtsPlayer(listOf(english))

        assertThat(player.voiceForTag(frenchTag()), nullValue())
    }

    @Test
    fun `no match when there are no voices`() {
        val player = AndroidTtsPlayer(emptyList())

        assertThat(player.voiceForTag(frenchTag()), nullValue())
    }

    private fun frenchVoice(
        name: String,
        installed: Boolean = true,
    ) = AndroidTtsVoice(fakeVoice(name, Locale.FRANCE, installed), ENGINE_A)

    private fun frenchTag(vararg voices: String) =
        TTSTag(fieldText = "bonjour", lang = "fr_FR", voices = voices.toList(), speed = null, otherArgs = emptyList())

    private fun fakeVoice(
        voiceName: String,
        voiceLocale: Locale,
        installed: Boolean = true,
    ): Voice =
        mockk(relaxed = true) {
            every { name } returns voiceName
            every { locale } returns voiceLocale
            every { features } returns if (installed) emptySet() else setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }

    companion object {
        private const val ENGINE_A = "com.example.engine.a"
        private const val ENGINE_B = "com.example.engine.b"
        private const val ENGINE_C = "com.example.engine.c"
    }
}
