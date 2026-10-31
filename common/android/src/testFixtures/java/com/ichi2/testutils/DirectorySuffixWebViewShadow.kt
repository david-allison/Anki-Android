// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.os.Build
import android.webkit.WebView
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowWebView

/** Captures the API boundary without instrumenting the entire WebView class with MockK. */
@Implements(WebView::class)
class DirectorySuffixWebViewShadow : ShadowWebView() {
    companion object {
        val suffixes = mutableListOf<String>()

        @JvmStatic
        @Implementation(minSdk = Build.VERSION_CODES.P)
        fun setDataDirectorySuffix(suffix: String) {
            suffixes += suffix
        }

        @JvmStatic
        @Resetter
        fun resetSuffixes() {
            suffixes.clear()
        }
    }
}
