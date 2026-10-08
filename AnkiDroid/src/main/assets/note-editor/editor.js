// SPDX-License-Identifier: GPL-3.0-or-later
// Android owns sessions and recovery; Anki's fields-only route owns all editing.
(() => {
    "use strict";
    const host = window.AnkiEditorHost;
    let model = { sessionId: "", generation: 0, fields: [], isCloze: false };
    let baseline = [],
        fields = [];
    let revision = 0,
        composing = false,
        inputEnabled = true,
        lastTarget = null;
    let draftId = null,
        hostStateJson = "",
        database;
    let writes = Promise.resolve(),
        operations = Promise.resolve(),
        refreshQueued = false;
    let fieldsReady = false,
        nextHostRequest = 0;
    const hostRequests = new Map();
    const api = () => window.AnkiEditorFields;
    const send = message => host.postMessage(JSON.stringify(message));
    const enqueue = operation => {
        const result = operations.then(operation);
        operations = result.catch(() => {});
        return result;
    };
    const reportError = error =>
        send({ type: "error", message: String(error?.message || error), fatal: !fieldsReady });

    function hostRequest(method, args = {}) {
        const id = ++nextHostRequest;
        return new Promise((resolve, reject) => {
            hostRequests.set(id, { resolve, reject });
            send({ type: "hostRequest", id, method, ...args });
        });
    }

    function toBase64(bytes) {
        let binary = "";
        for (let offset = 0; offset < bytes.length; offset += 32768) {
            binary += String.fromCharCode(...bytes.subarray(offset, offset + 32768));
        }
        return btoa(binary);
    }

    const fromBase64 = text => Uint8Array.from(atob(text), character => character.charCodeAt(0));
    const browserFetch = window.fetch.bind(window);
    // Installed before SvelteKit's bootstrap. Interception cannot read POST bodies;
    // the only backend operation fields need is carried over the message bridge.
    window.fetch = async (input, options = {}) => {
        const url = new URL(input instanceof Request ? input.url : input, location.href);
        if (url.origin === location.origin && url.pathname.startsWith("/_anki/")) {
            const method = options.method || (input instanceof Request ? input.method : "GET");
            if (url.pathname !== "/_anki/i18nResources" || method.toUpperCase() !== "POST") {
                throw new Error("This editor only exposes fields and translations.");
            }
            const body =
                options.body ?? (input instanceof Request ? await input.arrayBuffer() : null);
            const bytes = body instanceof Uint8Array ? body : new Uint8Array(body);
            const response = await hostRequest("i18nResources", { bytes: toBase64(bytes) });
            return new Response(fromBase64(response.bytes), {
                headers: { "Content-Type": "application/binary" },
            });
        }
        return browserFetch(input, options);
    };

    const hasChanges = () => fields.some((field, index) => field.html !== baseline[index]);
    function status() {
        send({
            type: "changed",
            revision,
            hasChanges: hasChanges(),
            composing,
            hasSelection: !!lastTarget,
        });
    }

    function validateDocument(value) {
        if (
            typeof value?.sessionId !== "string" ||
            !Number.isInteger(value.generation) ||
            !Array.isArray(value.fields) ||
            value.fields.some(
                field => typeof field.html !== "string" || typeof field.name !== "string",
            )
        ) {
            throw new Error("Invalid editor document.");
        }
    }

    async function render(
        value,
        clean = value.fields.map(field => field.html),
        savedRevisions = [],
        savedRevision = 0,
    ) {
        validateDocument(value);
        if (clean.length !== value.fields.length || clean.some(html => typeof html !== "string")) {
            throw new Error("Invalid editor baseline.");
        }
        model = structuredClone(value);
        baseline = [...clean];
        revision = savedRevision;
        composing = false;
        lastTarget = null;
        await api().load(model.fields, model.isCloze);
        await api().setInputEnabled(inputEnabled);
        const rendered = await api().snapshot();
        fields = model.fields.map((spec, index) => ({
            spec: { ...spec },
            html: spec.html,
            initialHtml: spec.html,
            initialRendered: rendered[index],
            revision: savedRevisions[index] || 0,
        }));
        status();
    }

    async function reconcile() {
        const current = await api().snapshotDocument();
        let changed = false;
        current.forEach((spec, index) => {
            const field = fields[index];
            if (!field) return;
            // Reading a field must not rewrite untouched HTML just because the
            // browser or Anki normalized its initial DOM representation.
            const html = spec.html === field.initialRendered ? field.initialHtml : spec.html;
            if (html !== field.html || spec.sourceMode !== field.spec.sourceMode) {
                field.revision++;
                changed = true;
            }
            if (spec.collapsed !== field.spec.collapsed) changed = true;
            field.html = html;
            field.spec = { ...field.spec, ...spec, html };
        });
        if (changed) revision++;
    }

    async function captureTarget() {
        await reconcile();
        const selection = await api().captureSelection();
        if (selection && fields[selection.field]) {
            lastTarget = {
                sessionId: model.sessionId,
                generation: model.generation,
                field: selection.field,
                revision: fields[selection.field].revision,
                bookmark: JSON.stringify(selection),
            };
        } else {
            lastTarget = null;
        }
        return lastTarget || {};
    }

    async function snapshot() {
        await reconcile();
        return {
            sessionId: model.sessionId,
            generation: model.generation,
            revision,
            fields: fields.map(field => field.html),
            hasChanges: hasChanges(),
        };
    }

    function scheduleRefresh() {
        if (!fieldsReady || refreshQueued) return;
        refreshQueued = true;
        enqueue(async () => {
            refreshQueued = false;
            await captureTarget();
            status();
            checkpoint().catch(reportError);
        }).catch(reportError);
    }

    async function execute({ action, target, value = "" }) {
        if (!inputEnabled || (composing && action !== "SOURCE_MODE")) return { applied: false };
        await reconcile();
        target ??= await captureTarget();
        const field = fields[target.field];
        if (
            target.sessionId !== model.sessionId ||
            target.generation !== model.generation ||
            !field ||
            target.revision !== field.revision
        )
            return { applied: false };
        let selection;
        try {
            selection = JSON.parse(target.bookmark);
        } catch (_) {
            return { applied: false };
        }
        if (
            selection.field !== target.field ||
            !["rich", "source"].includes(selection.kind) ||
            typeof selection.bookmark !== "string"
        )
            return { applied: false };
        const applied = await api().execute(action, selection, value);
        if (applied) {
            await captureTarget();
            status();
            checkpoint().catch(reportError);
        }
        return { applied };
    }

    function sendMedia(target, uri) {
        if (!target.bookmark) throw new Error("Focus a field before inserting media.");
        send({ type: "mediaPaste", target, uri });
    }

    window.bridgeCommand = (command, callback) => {
        if (command === "cutOrCopy") {
            callback?.();
            return;
        }
        if (command !== "paste") {
            reportError(new Error("Unsupported field command."));
            return;
        }
        enqueue(captureTarget)
            .then(async target => {
                const clip = await hostRequest("clipboard");
                if (clip.uri && !clip.text && !clip.html) {
                    sendMedia(target, clip.uri);
                } else {
                    const result = await enqueue(() =>
                        execute({
                            action: clip.html ? "INSERT_HTML" : "INSERT_TEXT",
                            target,
                            value: clip.html || clip.text || "",
                        }),
                    );
                    if (!result.applied)
                        throw new Error("The field changed before paste completed.");
                }
                callback?.();
            })
            .catch(reportError);
    };

    document.addEventListener("anki-editor-fields-drop", event => {
        const data = event.detail;
        enqueue(captureTarget)
            .then(async target => {
                if (data.files.length) throw new Error("Use the media button to insert this file.");
                const uri = data.uris.split(/\r?\n/).find(line => line && !line.startsWith("#"));
                if (uri && /^(content|file):/.test(uri)) {
                    sendMedia(target, uri);
                } else {
                    const result = await enqueue(() =>
                        execute({
                            action: data.html ? "INSERT_HTML" : "INSERT_TEXT",
                            target,
                            value: data.html || data.text || uri || "",
                        }),
                    );
                    if (!result.applied)
                        throw new Error("The field changed before drop completed.");
                }
            })
            .catch(reportError);
    });
    document.addEventListener("anki-editor-fields-change", scheduleRefresh);
    document.addEventListener("selectionchange", scheduleRefresh);
    document.addEventListener("anki-editor-fields-focus", event => {
        send({ type: "focus", languageTag: event.detail.languageTag || "" });
        scheduleRefresh();
    });
    document.addEventListener("anki-editor-fields-input", event => {
        composing = event.detail.composing;
        status();
        scheduleRefresh();
    });
    document.addEventListener("anki-editor-fields-ready", () => {
        fieldsReady = true;
        send({ type: "ready" });
    });
    window.addEventListener("error", event => reportError(event.error || event.message));
    window.addEventListener("unhandledrejection", event => reportError(event.reason));
    window.addEventListener("pagehide", () =>
        enqueue(async () => {
            await reconcile();
            await checkpoint();
        }).catch(reportError),
    );

    function openDatabase() {
        database ??= new Promise((resolve, reject) => {
            const request = indexedDB.open("ankidroid-note-editor", 1);
            request.onupgradeneeded = () => request.result.createObjectStore("drafts");
            request.onsuccess = () => {
                request.result.onversionchange = () => {
                    request.result.close();
                    database = undefined;
                };
                resolve(request.result);
            };
            request.onerror = () => reject(request.error);
            request.onblocked = () => reject(new Error("Draft storage is unavailable."));
        });
        return database;
    }

    async function transact(mode, operation) {
        const db = await openDatabase();
        return new Promise((resolve, reject) => {
            const transaction = db.transaction("drafts", mode);
            const request = operation(transaction.objectStore("drafts"));
            transaction.oncomplete = () => resolve(request.result);
            transaction.onabort = () =>
                reject(transaction.error || request.error || new Error("Draft storage failed."));
            transaction.onerror = () => {};
        });
    }

    function checkpoint() {
        if (!draftId) return writes;
        const key = draftId;
        const record = {
            version: 1,
            document: {
                ...model,
                fields: fields.map(field => ({ ...field.spec, html: field.html })),
            },
            baseline: [...baseline],
            hostStateJson,
            fieldRevisions: fields.map(field => field.revision),
            revision,
        };
        writes = writes
            .catch(() => {})
            .then(() => transact("readwrite", store => store.put(record, key)));
        return writes;
    }

    const methods = {
        async loadDocument(value) {
            await writes;
            draftId = null;
            const clean =
                value.resetBaseline === false
                    ? value.fields.map((_, index) => baseline[index] ?? "")
                    : undefined;
            await render(value, clean);
            return {};
        },
        async setInputEnabled({ enabled }) {
            inputEnabled = enabled;
            await api().setInputEnabled(enabled);
            return {};
        },
        async focusField({ index }) {
            await api().focusField(index);
            await captureTarget();
            status();
            return {};
        },
        snapshot,
        captureTarget,
        execute,
        async createDraft(value) {
            await writes;
            draftId = value.draftId;
            hostStateJson = value.hostStateJson;
            await reconcile();
            await checkpoint();
            return {};
        },
        async updateHostState(value) {
            hostStateJson = value.hostStateJson;
            await reconcile();
            await checkpoint();
            return {};
        },
        async restoreDraft(value) {
            await writes;
            const record = await transact("readonly", store => store.get(value.draftId));
            if (!record) return {};
            if (record.version !== 1 || typeof record.hostStateJson !== "string")
                throw new Error("Invalid editor draft.");
            await render(record.document, record.baseline, record.fieldRevisions, record.revision);
            draftId = value.draftId;
            hostStateJson = record.hostStateJson;
            return record;
        },
        async discardDraft(value) {
            if (draftId === value.draftId) draftId = null;
            await writes;
            await transact("readwrite", store => store.delete(value.draftId));
            return {};
        },
    };

    window.AnkiEditor = {
        async request(id, method, args) {
            try {
                if (!Object.hasOwn(methods, method)) throw new Error("Unknown editor method.");
                send({ type: "result", id, value: await enqueue(() => methods[method](args)) });
            } catch (error) {
                send({ type: "result", id, error: String(error.message || error) });
            }
        },
        hostResponse(id, value, error) {
            const pending = hostRequests.get(id);
            if (!pending) return;
            hostRequests.delete(id);
            if (error) pending.reject(new Error(error));
            else pending.resolve(value);
        },
    };
})();
