// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.R
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.utils.isWindowCompact
import com.ichi2.compose.theme.AnkiDroidTheme

class CustomSyncServerSettingsFragment :
    Fragment(),
    PreferenceXmlSource {
    // Retained as metadata for settings search and preference key checks.
    override val preferenceResource = R.xml.preferences_custom_sync_server

    private val viewModel: CustomSyncServerSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Analytics.sendAnalyticsScreenView("prefs.custom_sync_server")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    val server by viewModel.server.collectAsStateWithLifecycle()
                    val configuration by viewModel.configuration.collectAsStateWithLifecycle()
                    CustomSyncServerSettingsScreen(
                        server = server,
                        configuration = configuration,
                        onToggleEnabled = viewModel::toggleEnabled,
                        onSaveUrl = viewModel::saveUrl,
                        onSaveCertificate = viewModel::saveCertificate,
                        onNavigateUp = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        showNavigateUp = resources.isWindowCompact(),
                    )
                }
            }
        }
}
