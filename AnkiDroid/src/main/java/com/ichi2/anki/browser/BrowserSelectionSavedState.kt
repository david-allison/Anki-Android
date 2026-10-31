// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.lifecycle.SavedStateHandle
import com.ichi2.anki.browser.CardBrowserViewModel.Companion.STATE_MULTISELECT_VALUES
import com.ichi2.anki.common.storage.IdSnapshotFile

/** Transports a selection snapshot when Android saves or restores the browser. */
interface BrowserSelectionSavedState {
    fun restore(): IdSnapshotFile?

    fun registerProvider(provider: () -> IdSnapshotFile?)
}

/** Keeps Bundle and Parcelable handling at the Android saved-state boundary. */
class SavedStateHandleBrowserSelectionState(
    private val handle: SavedStateHandle,
) : BrowserSelectionSavedState {
    override fun restore(): IdSnapshotFile? =
        handle.get<Bundle>(STATE_MULTISELECT_VALUES)?.let { bundle ->
            BundleCompat.getParcelable(bundle, STATE_MULTISELECT_VALUES, IdsFile::class.java)
        }

    override fun registerProvider(provider: () -> IdSnapshotFile?) {
        handle.setSavedStateProvider(STATE_MULTISELECT_VALUES) {
            Bundle().apply {
                provider()?.let { putParcelable(STATE_MULTISELECT_VALUES, IdsFile(it.path)) }
            }
        }
    }
}
