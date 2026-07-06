// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.intellij.lang.annotations.Language

/**
 * Generates the host page for an addon's custom settings panel (`settingsPage`).
 *
 * The addon's own HTML runs inside a `sandbox="allow-scripts"` iframe, which has an
 * **opaque origin**: it can run scripts but cannot reach the parent page, the app, cookies,
 * or make same-origin requests. Its only channel out is `postMessage` to the parent host
 * page, which relays a small, fixed API to the Kotlin [bridgeName] interface:
 *
 * - `ankidroidAddon.getSettings()` → the addon's stored settings values
 * - `ankidroidAddon.setSettings(obj)` → persist them
 *
 * The addon never sees another addon's data: the host binds the bridge to one addon.
 */
object AddonPanelHost {
    /** The `@JavascriptInterface` name the host page (not the sandboxed iframe) calls */
    const val bridgeName = "AndroidAddonPanel"

    /**
     * The host page HTML.
     *
     * @param panelHtml the addon's settings page HTML, run sandboxed
     */
    @Language("HTML")
    fun hostPageHtml(panelHtml: String): String {
        // JSON-encode the panel HTML so it embeds as a JS string, and prepend the client
        // shim the panel uses to reach the host. kotlinx JSON does not escape '</', so a
        // panel containing '</script>' would otherwise terminate the host's inline <script>
        // early and inject into the host page: neutralise it (\/ is valid in JSON and JS)
        val srcdoc =
            Json
                .encodeToString(JsonPrimitive.serializer(), JsonPrimitive(clientShim + panelHtml))
                .replace("</", "<\\/")
        // language=HTML
        return """
            <!DOCTYPE html>
            <html>
            <head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="margin:0">
                <iframe id="panel" sandbox="allow-scripts"
                    style="border:0;width:100vw;height:100vh"></iframe>
                <script>
                    const frame = document.getElementById("panel");
                    frame.srcdoc = $srcdoc;
                    // relay requests from the sandboxed iframe to the Kotlin bridge
                    window.addEventListener("message", async (event) => {
                        if (event.source !== frame.contentWindow) return;
                        const { id, method, arg } = event.data || {};
                        let result = null;
                        try {
                            if (method === "getSettings") {
                                result = JSON.parse($bridgeName.getSettings());
                            } else if (method === "setSettings") {
                                $bridgeName.setSettings(JSON.stringify(arg));
                            }
                        } catch (e) {
                            console.log("addon panel bridge error:", e);
                        }
                        frame.contentWindow.postMessage({ id, result }, "*");
                    });
                </script>
            </body>
            </html>
            """.trimIndent()
    }

    /** Prepended to the addon panel HTML; exposes `ankidroidAddon` to the sandboxed page. */
    @Language("JS")
    private val clientShim =
        """
        <script>
        (() => {
            let nextId = 1;
            const pending = new Map();
            window.addEventListener("message", (event) => {
                const { id, result } = event.data || {};
                const resolve = pending.get(id);
                if (resolve) { pending.delete(id); resolve(result); }
            });
            function call(method, arg) {
                return new Promise((resolve) => {
                    const id = nextId++;
                    pending.set(id, resolve);
                    parent.postMessage({ id, method, arg }, "*");
                });
            }
            window.ankidroidAddon = {
                getSettings: () => call("getSettings"),
                setSettings: (obj) => call("setSettings", obj),
            };
        })();
        </script>
        """.trimIndent()
}
