// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.android.ApplicationContextInitializer
import com.ichi2.anki.i18n.normalize
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Regression tests for #18737: voices should be listed from every installed TTS engine,
 * not only the user's default engine.
 *
 * @see TtsVoices.loadVoicesFromEngines
 */
@RunWith(AndroidJUnit4::class)
class TtsVoicesMultiEngineTest {
    @Test
    @Suppress("DEPRECATION") // Engine-provided variants may not be valid BCP 47 tags.
    fun `default languages are deduplicated after normalization`() =
        runTest {
            val aliases = setOf(Locale("en", "GB", "f00"), Locale("en", "GB", "f01"))
            val defaultLocales = CompletableDeferred<TtsVoices.EngineLocales>()
            val probe =
                fakeTts(emptySet(), setOf(Locale.US)).apply {
                    every { defaultEngine } returns ENGINE_A
                    every { engines } returns emptyList()
                }
            every { probe.availableLanguages } returns aliases

            TtsVoices.loadVoices(defaultLocales) { engine -> if (engine == null) probe else null }

            assertEquals(listOf(Locale.UK.normalize()), defaultLocales.await().locales)
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `an inaccessible default engine is unavailable`() =
        runTest {
            setUpFailingTts()

            assertNull(TtsVoices.createTts())
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `an inaccessible selected engine is unavailable`() =
        runTest {
            setUpFailingTts()

            assertNull(TtsVoices.createTts("inaccessible.engine"))
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `an inaccessible default engine completes discovery with empty caches`() =
        runTest {
            setUpFailingTts()
            val locales = CompletableDeferred<TtsVoices.EngineLocales>()

            assertEquals(emptyList(), TtsVoices.loadVoices(locales, TtsVoices::createTts))
            assertEquals(TtsVoices.EngineLocales(null, emptyList()), locales.await())
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `discovery continues after an inaccessible engine`() =
        runTest {
            setUpFailingTts()
            val workingTts = mockk<TextToSpeech>(relaxed = true)

            TtsVoices.loadVoicesFromEngines(listOf("inaccessible.engine", "working.engine")) { engine ->
                if (engine == "working.engine") workingTts else TtsVoices.createTts(engine)
            }

            verify(exactly = 1) { workingTts.voices }
            verify(exactly = 1) { workingTts.shutdown() }
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `other initialization exceptions make an engine unavailable`() =
        runTest {
            setUpFailingTts(IllegalStateException("Engine initialization failed"))

            for (engine in listOf(null, "broken.engine")) {
                assertNull(TtsVoices.createTts(engine))
            }
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `discovery continues after another initialization exception`() =
        runTest {
            setUpFailingTts(IllegalStateException("Engine initialization failed"))
            val workingTts = mockk<TextToSpeech>(relaxed = true)

            TtsVoices.loadVoicesFromEngines(listOf("broken.engine", "working.engine")) { engine ->
                if (engine == "working.engine") workingTts else TtsVoices.createTts(engine)
            }

            verify(exactly = 1) { workingTts.voices }
            verify(exactly = 1) { workingTts.shutdown() }
        }

    @Test
    @Config(application = EmptyApplication::class, shadows = [FailingTextToSpeech::class])
    @Category(EmptyApplicationCategory::class)
    fun `initialization cancellation is propagated`() =
        runTest {
            val cancellation = CancellationException("TTS creation cancelled")
            setUpFailingTts(cancellation)

            for (engine in listOf(null, "cancelled.engine")) {
                assertSame(cancellation, assertFailsWith<CancellationException> { TtsVoices.createTts(engine) })
            }
        }

    private fun setUpFailingTts(failure: Exception = SecurityException("Not allowed to bind to TTS service")) {
        ApplicationContextInitializer.setInstance(ApplicationProvider.getApplicationContext())
        FailingTextToSpeech.failure = failure
    }

    private fun fakeVoice(
        voiceName: String,
        voiceLocale: Locale,
    ): Voice =
        mockk(relaxed = true) {
            every { name } returns voiceName
            every { locale } returns voiceLocale
            every { features } returns emptySet()
        }

    private fun fakeTts(
        engineVoices: Set<Voice>,
        engineLanguages: Set<Locale>,
    ): TextToSpeech =
        mockk(relaxed = true) {
            every { voices } returns engineVoices
            every { availableLanguages } returns engineLanguages
        }

    @Implements(TextToSpeech::class)
    class FailingTextToSpeech : ShadowTextToSpeech() {
        @Implementation
        override fun initTts(): Int = throw failure

        companion object {
            lateinit var failure: Exception
        }
    }

    companion object {
        private const val ENGINE_A = "com.example.engine.a"
        private const val ENGINE_B = "com.example.engine.b"
        private const val ENGINE_C = "com.example.engine.c"
    }
}
