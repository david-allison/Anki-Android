// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.Context
import android.webkit.JavascriptInterface
import com.ichi2.anki.settings.Prefs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.intellij.lang.annotations.Language
import timber.log.Timber

/** An addon prepared for injection into a page: its name, script body and resolved settings */
data class PageAddon(
    val name: String,
    val script: String,
    val settings: JsonObject,
)

/**
 * Injects enabled addons into any WebView page, using the Joplin model: each addon runs in
 * its **own `sandbox="allow-scripts"` iframe** (opaque origin), and reaches the host page
 * only through a `postMessage` relay to a small, curated API — never the page's DOM directly.
 *
 * This is the single mechanism behind addon support on every page (reviewer + the shared
 * SvelteKit pages). The relay, running in the trusted page scope, performs page effects on
 * the addon's behalf:
 *
 * ```js
 * ankidroid.settings                       // resolved settings, baked in
 * ankidroid.log(msg)
 * ankidroid.setSetting(key, value)
 * ankidroid.injectStyle(css)               // -> styleId
 * ankidroid.addElement(position, html)     // "head" | "body-start" | "body-end" -> elementId
 * ankidroid.setElementStyle(id, prop, value)
 * ankidroid.setElementHtml(id, html)
 * ankidroid.removeElement(id)
 * ankidroid.onEvent(type, cb)              // page lifecycle: "question", "answer", ...
 * ankidroid.onDomEvent(selector, type, cb) // delegated host-page events, e.g. image clicks
 * ```
 *
 * The host page calls the Kotlin [AddonPageBridge] (settings/log); the sandboxed iframe never
 * can. Nothing is injected unless the JS addons developer option is on.
 */
object AddonPageHost {
    /** The `@JavascriptInterface` name the host page (not the sandboxed iframe) calls */
    const val bridgeName = "AndroidAddonPage"

    /** The enabled, valid addons that target [pageId] (empty unless the dev flag is on) */
    fun addonsForPage(
        context: Context,
        pageId: String,
    ): List<AddonModel> {
        if (!Prefs.devAddonsEnabled) return emptyList()
        val stateStore = AddonStateStore(context)
        return AddonStorage(context)
            .getInstalledAddons()
            .mapNotNull { (it.result as? AddonValidationResult.Valid)?.addonModel }
            .filter { it.targetsPage(pageId) && stateStore.isEnabled(it.name) }
    }

    /** Reads each targeting addon's script and resolves its settings. */
    fun preparePageAddons(
        context: Context,
        pageId: String,
    ): List<PageAddon> {
        val storage = AddonStorage(context)
        val stateStore = AddonStateStore(context)
        return addonsForPage(context, pageId).mapNotNull { addon ->
            // reuse the web-export resolver so the same path-traversal guard applies
            val scriptFile =
                storage.resolveWebExport("${AddonStorage.WEB_EXPORTS_PREFIX}${addon.name}/${addon.main}")
                    ?: return@mapNotNull null
            PageAddon(addon.name, scriptFile.readText(), resolveSettingsValues(addon, stateStore.getSettingsValues(addon.name)))
        }
    }

    /**
     * The bootstrap script to inject into [pageId]'s WebView, or null if no addon targets it.
     * Sets up the relay and one sandboxed iframe per addon.
     */
    fun bootstrapScript(
        context: Context,
        pageId: String,
    ): String? {
        val addons = preparePageAddons(context, pageId)
        if (addons.isEmpty()) return null
        return pageHostScript(pageId, addons)
    }

    /** The Kotlin side of the page bridge, called by the host page (never the sandboxed iframe). */
    class AddonPageBridge(
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

    /** Generates the full page bootstrap: relay host + one sandboxed iframe per addon. */
    @Language("JS")
    private fun pageHostScript(
        pageId: String,
        addons: List<PageAddon>,
    ): String {
        val iframeSetup =
            addons.joinToString("\n") { addon ->
                val name = jsString(addon.name)
                val srcdoc =
                    Json
                        .encodeToString(JsonPrimitive.serializer(), JsonPrimitive(clientShim(pageId, addon) + addon.script))
                        .replace("</", "<\\/")
                """
                (() => {
                    const frame = document.createElement("iframe");
                    frame.sandbox = "allow-scripts";
                    frame.style.display = "none";
                    frame.srcdoc = $srcdoc;
                    host.register(frame, $name);
                    document.body.appendChild(frame);
                })();
                """.trimIndent()
            }

        // language=JS
        return """
            (() => {
                if (globalThis.__ankidroidPageHost) return;
                const bridge = globalThis.$bridgeName;
                const frames = new Map(); // window -> addon name
                const elements = new Map(); // id -> element
                const domHandlers = new Map(); // "type" -> [{selector, window}]

                const host = {
                    register(frame, name) { frame.addEventListener("load", () => frames.set(frame.contentWindow, name)); },
                    fireEvent(type, detail) {
                        for (const win of frames.keys()) win.postMessage({ __ak: "event", type, detail }, "*");
                    },
                };
                globalThis.__ankidroidPageHost = host;

                function insert(position, node) {
                    if (position === "head") document.head.appendChild(node);
                    else if (position === "body-start") document.body.insertBefore(node, document.body.firstChild);
                    else document.body.appendChild(node);
                }

                window.addEventListener("message", (event) => {
                    const name = frames.get(event.source);
                    const msg = event.data;
                    if (!name || !msg || msg.__ak !== "call") return;
                    const [a0, a1, a2] = msg.args || [];
                    try {
                        switch (msg.method) {
                            case "log": bridge && bridge.log(name, String(a0)); break;
                            case "setSetting": bridge && bridge.setSetting(name, a0, JSON.stringify(a1)); break;
                            // only the app's own scheme, which the page's WebViewClient controls
                            case "navigate": if (typeof a0 === "string" && a0.startsWith("ankidroid://")) window.location.href = a0; break;
                            case "injectStyle": {
                                const style = document.createElement("style");
                                style.id = a1; style.textContent = a0; document.head.appendChild(style);
                                elements.set(a1, style); break;
                            }
                            case "addElement": {
                                const div = document.createElement("div");
                                div.id = a2; div.innerHTML = a1; insert(a0, div); elements.set(a2, div); break;
                            }
                            case "setElementStyle": { const el = elements.get(a0); if (el) el.style[a1] = a2; break; }
                            case "setElementHtml": { const el = elements.get(a0); if (el) el.innerHTML = a1; break; }
                            case "removeElement": { const el = elements.get(a0); if (el) { el.remove(); elements.delete(a0); } break; }
                            case "subscribeDomEvent": {
                                if (!domHandlers.has(a1)) {
                                    domHandlers.set(a1, []);
                                    document.addEventListener(a1, (e) => {
                                        for (const h of domHandlers.get(a1)) {
                                            const match = e.target && e.target.closest && e.target.closest(h.selector);
                                            if (match) {
                                                h.window.postMessage({ __ak: "domEvent", selector: h.selector, type: a1,
                                                    target: { tagName: match.tagName, id: match.id, src: match.src || null,
                                                        className: match.className, textContent: match.textContent } }, "*");
                                            }
                                        }
                                    }, true);
                                }
                                domHandlers.get(a1).push({ selector: a0, window: event.source }); break;
                            }
                        }
                    } catch (err) { console.log("addon page bridge error:", err); }
                });

                // reviewer lifecycle: broadcast question/answer to addons if the page provides them
                for (const [hook, type] of [["_showQuestion", "question"], ["_showAnswer", "answer"]]) {
                    const orig = globalThis[hook];
                    if (typeof orig === "function") {
                        globalThis[hook] = function (...args) { const r = orig.apply(this, args); host.fireEvent(type, {}); return r; };
                    }
                }

                $iframeSetup
            })();
            """.trimIndent()
    }

    /** Prepended to each addon script inside the sandboxed iframe; exposes `ankidroid`. */
    @Language("JS")
    private fun clientShim(
        pageId: String,
        addon: PageAddon,
    ): String {
        val name = jsString(addon.name)
        return """
            <script>
            (() => {
                let nextId = 1;
                const eventSubs = {}; // type -> [cb]
                const domSubs = []; // {selector, type, cb}
                function call(method, args) { parent.postMessage({ __ak: "call", method, args }, "*"); }
                window.addEventListener("message", (e) => {
                    const m = e.data || {};
                    if (m.__ak === "event") (eventSubs[m.type] || []).forEach((cb) => cb(m.detail));
                    else if (m.__ak === "domEvent") domSubs.filter((s) => s.selector === m.selector && s.type === m.type).forEach((s) => s.cb(m.target));
                });
                function newId() { return $name + "-" + (nextId++); }
                globalThis.ankidroid = {
                    pageId: ${jsString(pageId)},
                    addonName: $name,
                    settings: ${addon.settings},
                    log: (msg) => call("log", [msg]),
                    setSetting: (key, value) => call("setSetting", [key, value]),
                    navigate: (url) => call("navigate", [url]),
                    injectStyle: (css) => { const id = newId(); call("injectStyle", [css, id]); return id; },
                    addElement: (position, html) => { const id = newId(); call("addElement", [position, html, id]); return id; },
                    setElementStyle: (id, prop, value) => call("setElementStyle", [id, prop, value]),
                    setElementHtml: (id, html) => call("setElementHtml", [id, html]),
                    removeElement: (id) => call("removeElement", [id]),
                    onEvent: (type, cb) => { (eventSubs[type] = eventSubs[type] || []).push(cb); },
                    onDomEvent: (selector, type, cb) => { domSubs.push({ selector, type, cb }); call("subscribeDomEvent", [selector, type]); },
                };
            })();
            </script>
            """.trimIndent()
    }

    private fun jsString(value: String): String = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value))
}
