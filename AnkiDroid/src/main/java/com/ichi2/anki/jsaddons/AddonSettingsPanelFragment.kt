// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import timber.log.Timber

/**
 * Hosts an addon's custom settings page (`settingsPage`) in a sandboxed WebView.
 *
 * The addon's HTML runs inside a `sandbox="allow-scripts"` iframe with an opaque origin (see
 * [AddonPanelHost]); the only bridge is [PanelBridge], scoped to this one addon's settings.
 */
class AddonSettingsPanelFragment : Fragment(R.layout.fragment_addon_settings_panel) {
    private val addonName: String by lazy { requireArguments().getString(ARG_ADDON_NAME)!! }
    private val stateStore by lazy { AddonStateStore(requireContext()) }
    private val addon: AddonModel? by lazy {
        val installed = AddonStorage(requireContext()).getInstalledAddons().firstOrNull { it.directoryName == addonName }
        (installed?.result as? AddonValidationResult.Valid)?.addonModel
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = addon?.addonTitle ?: addonName
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }

        val panelFile =
            addon?.settingsPage?.let {
                AddonStorage(requireContext()).getManifestFile(addonName).parentFile?.let { pkg -> java.io.File(pkg, it) }
            }
        val panelHtml = panelFile?.takeIf { it.isFile }?.readText()
        val webView = view.findViewById<WebView>(R.id.panel_webview)
        if (panelHtml == null) {
            Timber.w("Addon '%s' has no readable settings page", addonName)
            requireActivity().onBackPressedDispatcher.onBackPressed()
            return
        }

        webView.settings.javaScriptEnabled = true
        webView.addJavascriptInterface(PanelBridge(), AddonPanelHost.bridgeName)
        // a non-app base URL: the host page is not same-origin with anything in the app
        webView.loadDataWithBaseURL(
            "https://addon-settings.invalid/",
            AddonPanelHost.hostPageHtml(panelHtml),
            "text/html",
            "utf-8",
            null,
        )
    }

    /**
     * The Kotlin side of the panel bridge, called by the **host page** (never directly by the
     * sandboxed iframe). Scoped to [addonName]: the panel cannot reach another addon's state.
     */
    inner class PanelBridge {
        @JavascriptInterface
        fun getSettings(): String = stateStore.getSettingsValues(addonName).toString()

        @JavascriptInterface
        fun setSettings(json: String) {
            val parsed = Json.parseToJsonElement(json) as? JsonObject ?: return
            stateStore.replaceSettingsValues(addonName, parsed)
        }
    }

    companion object {
        const val ARG_ADDON_NAME = "addonName"

        fun arguments(addonName: String) = Bundle().apply { putString(ARG_ADDON_NAME, addonName) }
    }
}
