/*
 * Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screenshot tests for [CardBrowser]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.CardBrowserScreenshotTest"`
 */
@RunWith(AndroidJUnit4::class)
class CardBrowserScreenshotTest : ScreenshotTest() {
    @Test
    fun cardBrowserWith30Notes() =
        withCardBrowser(noteCount = 50) { browser ->
            // Robolectric reports zero system-bar insets by default. Inject realistic ones
            // so the app's edge-to-edge layout responds as it would on a real device.
            val density = browser.resources.displayMetrics.density
            val statusBarPx = (24 * density).toInt()
            val navBarPx = (48 * density).toInt()
            val insets =
                WindowInsetsCompat
                    .Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarPx, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navBarPx))
                    .build()
            ViewCompat.dispatchApplyWindowInsets(browser.window.decorView, insets)

            // overlay a translucent band where the nav bar would sit
            // to see if content is drawn underneath it
            val decor = browser.window.decorView as ViewGroup
            val navBarOverlay =
                View(browser).apply {
                    setBackgroundColor(0x80000000.toInt())
                }
            decor.addView(
                navBarOverlay,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    navBarPx,
                    Gravity.BOTTOM,
                ),
            )

            captureScreen("30_notes")
        }
}
