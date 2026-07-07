// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>

package com.ichi2.anki.jsaddons

import android.content.Context
import android.webkit.JavascriptInterface
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.intellij.lang.annotations.Language
import timber.log.Timber

/** An addon prepared for injection into a page: its name, script, settings and granted permissions */
data class PageAddon(
    val name: String,
    val script: String,
    val settings: JsonObject,
    val granted: Set<String>,
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
    const val BRIDGE_NAME = "AndroidAddonPage"

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
            PageAddon(
                name = addon.name,
                script = scriptFile.readText(),
                settings = resolveSettingsValues(addon, stateStore.getSettingsValues(addon.name)),
                granted = stateStore.getGrantedPermissions(addon.name),
            )
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

    /**
     * The Kotlin side of the page bridge, called by the host page (never the sandboxed iframe).
     *
     * @param scope runs async [query] work; defaults suit tests that don't exercise queries
     * @param evalJs evaluates JS back in the page, to resolve a [query]'s promise
     */
    class AddonPageBridge(
        private val context: Context,
        private val scope: CoroutineScope = MainScope(),
        private val evalJs: (String) -> Unit = {},
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

        /**
         * Runs a gated collection [method] for [addon] and resolves the promise keyed by [qkey].
         * Fire-and-forget: the result comes back asynchronously through [evalJs].
         */
        @JavascriptInterface
        fun query(
            addon: String,
            qkey: String,
            method: String,
            argsJson: String,
        ) {
            scope.launch {
                val envelope = AddonCollectionApi.handle(context, addon, method, argsJson)
                val key = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(qkey))
                evalJs("window.__ankidroidResolveQuery($key, $envelope)")
            }
        }
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

        val grantsMap =
            buildJsonObject {
                for (addon in addons) put(addon.name, JsonArray(addon.granted.map { JsonPrimitive(it) }))
            }

        // language=JS
        return """
            (() => {
                if (globalThis.__ankidroidPageHost) return;
                const bridge = globalThis.$BRIDGE_NAME;
                // granted permissions per addon, baked in from host state: the sandboxed
                // iframe cannot forge these - it only reaches the relay via postMessage
                const grants = $grantsMap;
                const has = (name, id) => (grants[name] || []).includes(id);
                const frames = new Map(); // window -> addon name
                const elements = new Map(); // id -> element
                const domHandlers = new Map(); // "type" -> [{selector, window}]
                const pendingQueries = new Map(); // qkey -> {win, qid}

                // resolves a collection query, called from Kotlin (AddonPageBridge.query)
                window.__ankidroidResolveQuery = (qkey, envelope) => {
                    const p = pendingQueries.get(qkey);
                    if (!p) return;
                    pendingQueries.delete(qkey);
                    p.win.postMessage({ __ak: "queryResult", qid: p.qid, envelope }, "*");
                };

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
                            case "query": {
                                // a0=qid, a1=method, a2=args; qkey namespaces the query per addon
                                const qkey = name + "#" + a0;
                                pendingQueries.set(qkey, { win: event.source, qid: a0 });
                                if (bridge) bridge.query(name, qkey, a1, JSON.stringify(a2 || {}));
                                break;
                            }
                            case "navigate": {
                                // gated by the 'navigate' capability; and only the app's own
                                // scheme, which the page's WebViewClient controls
                                if (!has(name, "navigate")) { bridge && bridge.log(name, "denied: navigate permission not granted"); break; }
                                if (typeof a0 === "string" && a0.startsWith("ankidroid://")) window.location.href = a0;
                                break;
                            }
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
                let nextQ = 1;
                const eventSubs = {}; // type -> [cb]
                const domSubs = []; // {selector, type, cb}
                const pendingQ = new Map(); // qid -> {resolve, reject}
                function call(method, args) { parent.postMessage({ __ak: "call", method, args }, "*"); }
                window.addEventListener("message", (e) => {
                    const m = e.data || {};
                    if (m.__ak === "event") (eventSubs[m.type] || []).forEach((cb) => cb(m.detail));
                    else if (m.__ak === "domEvent") domSubs.filter((s) => s.selector === m.selector && s.type === m.type).forEach((s) => s.cb(m.target));
                    else if (m.__ak === "queryResult") {
                        const p = pendingQ.get(m.qid);
                        if (!p) return;
                        pendingQ.delete(m.qid);
                        if (m.envelope && m.envelope.ok) p.resolve(m.envelope.value);
                        else p.reject(new Error((m.envelope && m.envelope.error) || "collection error"));
                    }
                });
                function newId() { return $name + "-" + (nextId++); }
                // a gated collection call; resolves the returned value or rejects with the error
                function query(method, args) {
                    return new Promise((resolve, reject) => {
                        const qid = "q" + (nextQ++);
                        pendingQ.set(qid, { resolve, reject });
                        call("query", [qid, method, args || {}]);
                    });
                }
                globalThis.ankidroid = {
                    pageId: ${jsString(pageId)},
                    addonName: $name,
                    settings: ${addon.settings},
                    permissions: ${JsonArray(addon.granted.map { JsonPrimitive(it) })},
                    hasPermission: (id) => ${JsonArray(addon.granted.map { JsonPrimitive(it) })}.includes(id),
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
                    query: query,
                    // gated collection API; each namespace requires the matching permission
                    decks: {
                        all: () => query("decks.all"),
                        current: () => query("decks.current"),
                        add: (name) => query("decks.add", { name }),
                    },
                    notes: {
                        find: (search) => query("notes.find", { query: search }),
                        info: (noteId) => query("notes.info", { noteId: String(noteId) }),
                    },
                };
            })();
            </script>
            """.trimIndent()
    }

    private fun jsString(value: String): String = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value))
}
