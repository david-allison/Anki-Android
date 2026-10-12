// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.doOnAttach
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.snackbar.showSnackbar

/** Use the app's snackbar behaviour, creating its native host only when there is feedback. */
@Composable
internal fun EditorSnackbarHost(
    state: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val data = state.currentSnackbarData ?: return
    key(data) {
        val context = LocalContext.current
        val host = remember { CoordinatorLayout(context) }
        AndroidView(factory = { host }, modifier = modifier.fillMaxWidth())
        DisposableEffect(host) {
            var snackbar: Snackbar? = null
            host.doOnAttach {
                val duration =
                    when (data.visuals.duration) {
                        SnackbarDuration.Short -> Snackbar.LENGTH_SHORT
                        SnackbarDuration.Long -> Snackbar.LENGTH_LONG
                        SnackbarDuration.Indefinite -> Snackbar.LENGTH_INDEFINITE
                    }
                snackbar =
                    host.showSnackbar(data.visuals.message, duration) {
                        data.visuals.actionLabel?.let { label -> setAction(label) { data.performAction() } }
                        addCallback(
                            object : Snackbar.Callback() {
                                override fun onDismissed(
                                    snackbar: Snackbar,
                                    event: Int,
                                ) = data.dismiss()
                            },
                        )
                    }
            }
            onDispose { snackbar?.dismiss() }
        }
    }
}
