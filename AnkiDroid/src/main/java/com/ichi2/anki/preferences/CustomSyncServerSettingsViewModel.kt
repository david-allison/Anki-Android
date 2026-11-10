// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.lifecycle.ViewModel
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface CustomSyncServerState {
    data object Disabled : CustomSyncServerState

    data class Enabled(
        val endpoint: HttpUrl,
    ) : CustomSyncServerState
}

/** Saved values are retained independently of whether the custom server is selected. */
data class CustomSyncServerConfiguration(
    val url: String = "",
    val certificate: String = "",
)

class CustomSyncServerSettingsViewModel(
    private val prefs: PrefsRepository = Prefs,
    private val updateCertificate: (String) -> Boolean = CollectionManager::updateCustomCertificate,
) : ViewModel() {
    private val _configuration =
        MutableStateFlow(
            CustomSyncServerConfiguration(
                url = prefs.customSyncUri.orEmpty(),
                certificate = prefs.customSyncCertificate.orEmpty(),
            ),
        )
    val configuration = _configuration.asStateFlow()

    private val _server =
        MutableStateFlow<CustomSyncServerState>(
            if (prefs.isCustomSyncEnabled) {
                _configuration.value.url
                    .toHttpUrlOrNull()
                    ?.let(CustomSyncServerState::Enabled) ?: CustomSyncServerState.Disabled
            } else {
                CustomSyncServerState.Disabled
            },
        )
    val server = _server.asStateFlow()

    /** Apply the URL and selection together, after the user saves the form. */
    fun saveServer(
        enabled: Boolean,
        url: String,
    ): Boolean {
        val selectedServer =
            if (enabled) {
                val endpoint = url.toHttpUrlOrNull() ?: return false
                CustomSyncServerState.Enabled(endpoint)
            } else {
                CustomSyncServerState.Disabled
            }
        if (prefs.customSyncUri.orEmpty() != url || prefs.isCustomSyncEnabled != enabled) {
            // A redirect from the previous server must not override the new setting.
            prefs.currentSyncUri = null
        }
        prefs.customSyncUri = url
        prefs.isCustomSyncEnabled = enabled
        _configuration.value = _configuration.value.copy(url = url)
        _server.value = selectedServer
        return true
    }

    fun saveCertificate(certificate: String): Boolean {
        // Certificate trust applies to sync independently of the custom server selection.
        // The backend validates PEM input; empty input removes the custom certificate.
        if (!updateCertificate(certificate)) return false
        prefs.customSyncCertificate = certificate
        _configuration.value = _configuration.value.copy(certificate = certificate)
        return true
    }
}
