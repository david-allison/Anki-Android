// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.ichi2.anki.settings.Prefs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.intellij.lang.annotations.Language
import timber.log.Timber

/** A background addon prepared for the host: its name, script body and resolved settings */
data class BackgroundAddon(
    val name: String,
    val script: String,
    val settings: JsonObject,
)

/**
 * Runs the background scripts of enabled addons that declare a `background` entry.
 *
 * This is the AnkiDroid form of the cross-platform background context from
 * `docs/addons/README.md`: **one hidden, app-scoped WebView** containing **one sandboxed
 * iframe per background addon** (distinct opaque origins), reusing the same iframe + RPC
 * machinery as the settings panel. It lets addons react to events and hold cross-screen
 * state without a visible WebView.
 *
 * Lifecycle-gated: only created while the app is foregrounded and the JS addons dev option
 * is on, and torn down when the app is backgrounded. It is never created if no enabled
 * addon declares a background entry.
 */
class AddonBackgroundHost(
    private val context: Context,
) {
    private var webView: WebView? = null

    /** Starts the host if the dev flag is on and a background addon is enabled; else a no-op. */
    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        if (webView != null) return
        val addons = prepareBackgroundAddons(context)
        if (addons.isEmpty()) return

        Timber.i("Starting addon background host for %d addon(s)", addons.size)
        webView =
            WebView(context).apply {
                settings.javaScriptEnabled = true
                addJavascriptInterface(BackgroundBridge(context), BRIDGE_NAME)
                loadDataWithBaseURL(
                    "https://addon-background.invalid/",
                    hostPageHtml(addons),
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        current = this
    }

    /** Tears the host down; safe to call when not started. */
    fun stop() {
        webView?.let {
            Timber.i("Stopping addon background host")
            it.destroy()
        }
        webView = null
        if (current === this) current = null
    }

    /**
     * Delivers a native menu click to [addonName]'s background script, where it is handled by
     * `ankidroid.onMenuClick`. A no-op if the addon has no running background context.
     */
    fun fireMenuClick(
        addonName: String,
        menuId: String,
    ) {
        val js = "window.__ankidroidFireMenu(${jsString(addonName)}, ${jsString(menuId)})"
        webView?.evaluateJavascript(js, null)
    }

    private fun jsString(value: String): String = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value))

    /** The Kotlin side of the background bridge, called by the host page (not the iframes). */
    class BackgroundBridge(
        private val context: Context,
    ) {
        @JavascriptInterface
        fun log(
            addon: String,
            message: String,
        ) = Timber.i("[addon:%s] %s", addon, message)

        @JavascriptInterface
        fun setSetting(
            addon: String,
            key: String,
            valueJson: String,
        ) = AddonStateStore(context).setSettingValue(addon, key, Json.parseToJsonElement(valueJson))
    }

    companion object {
        const val BRIDGE_NAME = "AndroidAddonBackground"

        /**
         * The currently-running host, if any, so native screens can dispatch menu clicks
         * without owning a reference. Set while foregrounded with background addons enabled.
         */
        var current: AddonBackgroundHost? = null
            private set

        /** The enabled, valid addons that declare a background entry (empty unless the dev flag is on) */
        fun enabledBackgroundAddons(context: Context): List<AddonModel> {
            if (!Prefs.devAddonsEnabled) return emptyList()
            val stateStore = AddonStateStore(context)
            return AddonStorage(context)
                .getInstalledAddons()
                .mapNotNull { (it.result as? AddonValidationResult.Valid)?.addonModel }
                .filter { it.background != null && stateStore.isEnabled(it.name) }
        }

        /** Reads each enabled background addon's script and resolves its settings. */
        fun prepareBackgroundAddons(context: Context): List<BackgroundAddon> {
            val storage = AddonStorage(context)
            val stateStore = AddonStateStore(context)
            return enabledBackgroundAddons(context).mapNotNull { addon ->
                val background = addon.background ?: return@mapNotNull null
                // reuse the web-export resolver so the same path-traversal guard applies
                val scriptFile =
                    storage.resolveWebExport("${AddonStorage.WEB_EXPORTS_PREFIX}${addon.name}/$background")
                        ?: return@mapNotNull null
                BackgroundAddon(
                    name = addon.name,
                    script = scriptFile.readText(),
                    settings = resolveSettingsValues(addon, stateStore.getSettingsValues(addon.name)),
                )
            }
        }

        /**
         * The hidden host page: one sandboxed iframe per addon, each running that addon's
         * background script with a client shim bound to the addon's own name and settings.
         */
        @Language("HTML")
        fun hostPageHtml(addons: List<BackgroundAddon>): String {
            val frames =
                addons.joinToString("\n") { addon ->
                    val srcdoc =
                        Json
                            .encodeToString(JsonPrimitive.serializer(), JsonPrimitive(clientShim(addon) + addon.script))
                            .replace("</", "<\\/")
                    """<iframe sandbox="allow-scripts" data-addon='${jsAttr(addon.name)}' srcdoc-data='$srcdoc'></iframe>"""
                }
            // language=HTML
            return """
                <!DOCTYPE html>
                <html><body>
                $frames
                <script>
                    const frames = new Map(); // addon name -> iframe window, for delivering menu clicks
                    for (const frame of document.querySelectorAll("iframe[srcdoc-data]")) {
                        const name = frame.getAttribute("data-addon");
                        frame.addEventListener("load", () => frames.set(name, frame.contentWindow));
                        frame.srcdoc = JSON.parse(frame.getAttribute("srcdoc-data"));
                    }
                    // called from Kotlin (AddonBackgroundHost.fireMenuClick) to notify an addon
                    window.__ankidroidFireMenu = (name, menuId) => {
                        const w = frames.get(name);
                        if (w) w.postMessage({ __ak: "menu", menuId }, "*");
                    };
                    window.addEventListener("message", (event) => {
                        const { addon, method, args } = event.data || {};
                        try {
                            if (method === "log") $BRIDGE_NAME.log(addon, String(args[0]));
                            else if (method === "setSetting") $BRIDGE_NAME.setSetting(addon, args[0], JSON.stringify(args[1]));
                        } catch (e) { console.log("background bridge error:", e); }
                    });
                </script>
                </body></html>
                """.trimIndent()
        }

        /**
         * Client shim prepended to each background script; binds a per-addon `ankidroid`
         * object (with baked-in settings) so an addon only ever refers to itself.
         */
        @Language("JS")
        private fun clientShim(addon: BackgroundAddon): String {
            val name = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(addon.name))
            return """
                <script>
                (() => {
                    const menuSubs = [];
                    window.addEventListener("message", (e) => {
                        if (e.data && e.data.__ak === "menu") menuSubs.forEach((cb) => cb(e.data.menuId));
                    });
                    globalThis.ankidroid = {
                        addonName: $name,
                        settings: ${addon.settings},
                        log: (message) => parent.postMessage({ addon: $name, method: "log", args: [message] }, "*"),
                        setSetting: (key, value) => parent.postMessage({ addon: $name, method: "setSetting", args: [key, value] }, "*"),
                        onMenuClick: (cb) => menuSubs.push(cb),
                    };
                })();
                </script>
                """.trimIndent()
        }

        private fun jsAttr(value: String): String = value.replace("'", "&#39;")
    }
}
