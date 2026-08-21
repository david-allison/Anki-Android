// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.WindowInsetsCompat.Type.statusBars
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.reviewer.FullScreenMode
import com.ichi2.testutils.insetsOf
import com.ichi2.utils.dp
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Screenshot tests for the legacy [Reviewer]
 *
 * The card itself is a WebView, which Robolectric renders as a blank area: these tests cover the
 * native chrome around it (toolbar, counts bar and answer buttons)
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.ReviewerScreenshotTest"`
 */
class ReviewerScreenshotTest : ScreenshotTest() {
    // rendering a card requires a media folder
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun question() =
        withReviewer { reviewer ->
            reviewer.simulateNavigationBar()
            captureScreen("question")
        }

    @Test
    fun answer() =
        withReviewer { reviewer ->
            reviewer.displayCardAnswer()
            advanceRobolectricLooper()
            reviewer.simulateNavigationBar()
            captureScreen("answer")
        }

    @Test
    fun answerButtonsAtTop() {
        targetContext.sharedPrefs().edit { putString("answerButtonPosition", "top") }
        withReviewer { reviewer ->
            reviewer.simulateNavigationBar()
            captureScreen("answer_buttons_top")
        }
    }

    /**
     * Landscape with 3-button navigation: the navigation bar is a side inset and the camera
     * cutout is on the opposite side.
     */
    @Test
    fun landscape() {
        RuntimeEnvironment.setQualifiers("+land")
        withReviewer { reviewer ->
            reviewer.simulateSideNavigationBar()
            captureScreen("landscape")
        }
    }

    /** 'Hide the system bars', immersive mode active: only the camera cutout insets the content */
    @Test
    fun fullscreenBarsHidden() {
        setFullscreenMode(FullScreenMode.BUTTONS_ONLY)
        withReviewer { reviewer ->
            reviewer.simulateHiddenBarsWithCutout()
            captureScreen("fullscreen_bars_hidden")
        }
    }

    /** 'Hide the system bars', after the user swipes the bars back into view */
    @Test
    fun fullscreenBarsRevealed() {
        setFullscreenMode(FullScreenMode.BUTTONS_ONLY)
        withReviewer { reviewer ->
            reviewer.simulateRevealedSystemBars()
            captureScreen("fullscreen_bars_revealed")
        }
    }

    /** #14201: 'Hide the system bars' with the answer buttons at the top */
    @Test
    fun fullscreenAnswerButtonsAtTop() {
        setFullscreenMode(FullScreenMode.BUTTONS_ONLY)
        targetContext.sharedPrefs().edit { putString("answerButtonPosition", "top") }
        withReviewer { reviewer ->
            reviewer.simulateHiddenBarsWithCutout()
            captureScreen("fullscreen_answer_buttons_top")
        }
    }

    /** 'Hide the system bars and answer buttons', after the user swipes the bars into view */
    @Test
    fun fullscreenAllGoneBarsRevealed() {
        setFullscreenMode(FullScreenMode.FULLSCREEN_ALL_GONE)
        withReviewer { reviewer ->
            reviewer.simulateRevealedSystemBars()
            captureScreen("fullscreen_all_gone_bars_revealed")
        }
    }

    private fun setFullscreenMode(mode: FullScreenMode) = FullScreenMode.setPreference(targetContext.sharedPrefs(), mode)

    private fun withReviewer(block: (Reviewer) -> Unit) {
        addBasicNote("Hello", "World")
        val reviewer = ReviewerTest.startReviewer(this)
        advanceRobolectricLooper()
        block(reviewer)
    }

    /**
     * Robolectric reports zero system-bar insets by default. Inject realistic ones so the app's
     * edge-to-edge layout responds as it would on a real device, and overlay a translucent band
     * where the nav bar would sit to see if content is drawn underneath it.
     */
    private fun Reviewer.simulateNavigationBar() {
        val navBarHeight = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(bottom = navBarHeight))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)

        val decor = window.decorView as ViewGroup
        val navBarOverlay =
            View(this).apply {
                setBackgroundColor(0x80000000.toInt())
            }
        decor.addView(
            navBarOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                navBarHeight.toPx(targetContext),
                Gravity.BOTTOM,
            ),
        )
    }

    /**
     * Immersive mode with the bars hidden: their insets are zero, but the camera cutout still
     * insets the content. A translucent band marks where the camera sits.
     */
    private fun Reviewer.simulateHiddenBarsWithCutout() {
        val cutoutHeight = 32.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(displayCutout(), insetsOf(top = cutoutHeight))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)

        val decor = window.decorView as ViewGroup
        val cutoutOverlay =
            View(this).apply {
                setBackgroundColor(0x80000000.toInt())
            }
        decor.addView(
            cutoutOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                cutoutHeight.toPx(targetContext),
                Gravity.TOP,
            ),
        )
    }

    /**
     * Immersive mode after the user swipes the system bars back into view: they overlay the
     * content. Translucent bands mark the status and navigation bars.
     */
    private fun Reviewer.simulateRevealedSystemBars() {
        val statusBarHeight = 24.dp
        val navBarHeight = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = statusBarHeight))
                    .setInsets(navigationBars(), insetsOf(bottom = navBarHeight))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)

        val decor = window.decorView as ViewGroup
        val statusBarOverlay =
            View(this).apply {
                setBackgroundColor(0x80000000.toInt())
            }
        decor.addView(
            statusBarOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                statusBarHeight.toPx(targetContext),
                Gravity.TOP,
            ),
        )
        val navBarOverlay =
            View(this).apply {
                setBackgroundColor(0x80000000.toInt())
            }
        decor.addView(
            navBarOverlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                navBarHeight.toPx(targetContext),
                Gravity.BOTTOM,
            ),
        )
    }

    /**
     * As [simulateNavigationBar], but for landscape with 3-button navigation: the navigation bar
     * is a side inset, with the camera cutout on the opposite side.
     */
    private fun Reviewer.simulateSideNavigationBar() {
        val navBarWidth = 48.dp
        val insets =
            with(targetContext) {
                WindowInsetsCompat
                    .Builder()
                    .setInsets(statusBars(), insetsOf(top = 24.dp))
                    .setInsets(navigationBars(), insetsOf(right = navBarWidth))
                    .setInsets(displayCutout(), insetsOf(left = 32.dp))
                    .build()
            }
        ViewCompat.dispatchApplyWindowInsets(window.decorView, insets)

        val decor = window.decorView as ViewGroup
        val navBarOverlay =
            View(this).apply {
                setBackgroundColor(0x80000000.toInt())
            }
        decor.addView(
            navBarOverlay,
            FrameLayout.LayoutParams(
                navBarWidth.toPx(targetContext),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END,
            ),
        )
    }
}
