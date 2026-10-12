// SPDX-License-Identifier: GPL-3.0-or-later
// Run from the AnkiDroid root: node tools/editor-field-actions/check.mjs <backend checkout> [baseline assets directory]
// Uses the backend's Playwright installation and real built fields, plus the production Android bridge.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";

if (!process.argv[2])
    throw Error(
        "Usage: node tools/editor-field-actions/check.mjs <backend checkout> [baseline assets directory]",
    );
const backend = resolve(process.argv[2]);
const anki = `${backend}/anki`;
const require = createRequire(`${anki}/package.json`);
const { chromium } = require("playwright");
const { Json } = await import(`${anki}/out/ts/lib/generated/anki/generic_pb.js`);
const resources = ["editing", "keyboard"]
    .map(name => readFileSync(`${anki}/ftl/core/${name}.ftl`, "utf8"))
    .join("\n");
const translations = Buffer.from(
    new Json({
        json: new TextEncoder().encode(
            JSON.stringify({ langs: ["en-US"], resources: [resources] }),
        ),
    }).toBinary(),
).toString("base64");
const bridge = readFileSync("AnkiDroid/src/main/assets/note-editor/editor.js", "utf8");
const assets = `${anki}/out/sveltekit`;
const browser = await chromium.launch({ headless: true });
const iconPath = name =>
    [
        ...readFileSync(`AnkiDroid/src/main/res/drawable/${name}.xml`, "utf8").matchAll(
            /android:pathData="([^"]+)"/g,
        ),
    ]
        .map(match => match[1])
        .join(" ");
const action = { id: "gallery", label: "Gallery", iconPath: iconPath("ic_popup_menu_item_image") };
const pin = {
    id: "pin",
    label: "Pin",
    iconPath: iconPath("ic_baseline_push_pin_24"),
    active: false,
};
const specs = count =>
    Array.from({ length: count }, (_, index) => ({
        name: `Field ${index}`,
        html: `text ${index}`,
        fontName: "Arial",
        fontSize: 20,
        rtl: false,
    }));

async function open(assetRoot = assets, shell = bridge) {
    const page = await browser.newPage({ viewport: { width: 390, height: 700 } });
    page.on("pageerror", error => {
        throw error;
    });
    await page.route("**/*", async route => {
        const path = new URL(route.request().url()).pathname;
        if (path === "/editor-fields")
            return route.fulfill({
                contentType: "text/html",
                body: readFileSync(`${assetRoot}/editor-fields.html`, "utf8"),
            });
        const app = existsSync(`${assetRoot}/_app`) ? "_app" : "app";
        const file = path.startsWith("/_app/") ? `${assetRoot}/${app}/${path.slice(6)}` : null;
        if (file && existsSync(file))
            return route.fulfill({
                path: file,
                contentType: file.endsWith(".mjs") ? "text/javascript" : undefined,
            });
        return route.fulfill({ status: 404, body: "" });
    });
    await page.addInitScript(
        ({ shell, translations }) => {
            const pending = new Map();
            let next = 0;
            window.actions = [];
            window.AnkiEditorHost = {
                postMessage(raw) {
                    const message = JSON.parse(raw);
                    if (message.type === "hostRequest")
                        queueMicrotask(() =>
                            AnkiEditor.hostResponse(message.id, { bytes: translations }),
                        );
                    if (message.type === "quickAction") actions.push(message);
                    if (message.type === "result") {
                        const result = pending.get(message.id);
                        pending.delete(message.id);
                        message.error
                            ? result.reject(Error(message.error))
                            : result.resolve(message.value);
                    }
                },
            };
            window.request = (method, args = {}) =>
                new Promise((resolve, reject) => {
                    const id = ++next;
                    pending.set(id, { resolve, reject });
                    AnkiEditor.request(id, method, args);
                });
            eval(shell);
        },
        { shell, translations },
    );
    await page.goto("http://127.0.0.1:48993/editor-fields");
    await page.waitForFunction(() => !!window.AnkiEditorFields);
    return page;
}

async function load(page, fields, generation = 0) {
    return page.evaluate(
        ({ fields, generation }) =>
            request("loadDocumentAndCreateDraft", {
                document: { sessionId: "quick-actions-test", generation, fields, isCloze: false },
                draftId: "quick-actions-test",
                hostStateJson: "{}",
            }),
        { fields, generation },
    );
}

try {
    const page = await open();
    const fields = specs(2);
    fields[1].quickActions = [action, pin];
    await load(page, fields);
    await page.evaluate(() => request("focusField", { index: 0 }));
    const selection = await page.evaluate(() => request("captureTarget"));
    await page.getByRole("button", { name: "Gallery: Field 1", exact: true }).click();
    await page.waitForFunction(() => actions.length === 1);
    assert.deepEqual(
        await page.evaluate(() => request("captureTarget")),
        selection,
        "launch must preserve selection",
    );
    const target = await page.evaluate(() => actions[0].target);
    assert.equal(target.field, 1);
    assert.equal(
        await page.evaluate(
            async target =>
                (await request("execute", { action: "INSERT_HTML", target, value: "<b>image</b>" }))
                    .applied,
            target,
        ),
        true,
    );
    assert.deepEqual(await page.evaluate(async () => (await request("snapshot")).fields), [
        "text 0",
        "text 1<b>image</b>",
    ]);
    assert.equal(
        await page.evaluate(
            async target =>
                (await request("execute", { action: "INSERT_HTML", target, value: "stale" }))
                    .applied,
            target,
        ),
        false,
        "edits invalidate delayed targets",
    );
    await load(page, fields, 1);
    assert.equal(
        await page.evaluate(
            async target =>
                (await request("execute", { action: "INSERT_HTML", target, value: "wrong note" }))
                    .applied,
            target,
        ),
        false,
    );
    await page.evaluate(() => request("focusField", { index: 0 }));
    await page.getByRole("button", { name: "Pin: Field 1", exact: true }).click();
    await page.waitForFunction(() => actions.length === 2);
    assert.equal(
        await page.evaluate(() => actions[1].target.bookmark),
        "",
        "pin needs no content selection",
    );
    assert.equal(await page.evaluate(async () => (await request("captureTarget")).field), 0);
    await page.evaluate(
        action =>
            request("setFieldActions", {
                actions: [[], [{ ...action, id: "pin", label: "Unpin", active: true }]],
            }),
        action,
    );
    assert.equal(
        await page
            .getByRole("button", { name: "Unpin: Field 1", exact: true })
            .getAttribute("aria-pressed"),
        "true",
    );
    assert.equal(
        await page.evaluate(async () => (await request("snapshot")).hasChanges),
        false,
        "configuration must not dirty HTML",
    );
    await page.evaluate(() => request("setInputEnabled", { enabled: false }));
    assert.equal(
        await page.getByRole("button", { name: "Unpin: Field 1", exact: true }).isDisabled(),
        true,
    );
    await page.evaluate(() => request("setInputEnabled", { enabled: true }));
    fields[1].collapsed = true;
    fields[1].sourceMode = true;
    await load(page, fields, 2);
    await page.evaluate(() => request("focusField", { index: 0 }));
    await page.getByRole("button", { name: "Gallery: Field 1", exact: true }).click();
    await page.waitForFunction(() => actions.length === 3);
    const sourceTarget = await page.evaluate(() => actions[2].target);
    assert.equal(JSON.parse(sourceTarget.bookmark).kind, "source");
    assert.equal(await page.evaluate(async () => (await request("captureTarget")).field), 0);
    assert.equal(
        await page.evaluate(
            async target =>
                (
                    await request("execute", {
                        action: "INSERT_HTML",
                        target,
                        value: "<b>source image</b>",
                    })
                ).applied,
            sourceTarget,
        ),
        true,
    );
    assert.deepEqual(await page.evaluate(async () => (await request("snapshot")).fields), [
        "text 0",
        "text 1<b>source image</b>",
    ]);
    // Pin changes metadata, so it must also work while another field has an IME composition.
    await page.evaluate(() =>
        document.dispatchEvent(
            new CustomEvent("anki-editor-fields-input", { detail: { composing: true } }),
        ),
    );
    await page.getByRole("button", { name: "Pin: Field 1", exact: true }).click();
    await page.waitForFunction(() => actions.length === 4);
    assert.equal(await page.evaluate(() => actions[3].action), "pin");
    await page.evaluate(() =>
        document.dispatchEvent(
            new CustomEvent("anki-editor-fields-input", { detail: { composing: false } }),
        ),
    );
    await page.close();
    console.log(
        "PASS: production bridge targeting, focus, stale results, live configuration and disabled controls",
    );

    if (process.argv[3]) {
        const baseline = resolve(process.argv[3]);
        const baselineShell = readFileSync(`${baseline}/../baseline-editor.js`, "utf8");
        const samples = [];
        // Interleave fresh pages to avoid giving either bundle a consistently warmer machine.
        for (let run = 0; run < 12; run++)
            for (const count of [2, 400])
                for (const variant of ["before", "off", "enabled"]) {
                    const page = await open(
                        variant === "before" ? baseline : assets,
                        variant === "before" ? baselineShell : bridge,
                    );
                    const fields = specs(count).map(field =>
                        variant === "enabled" ? { ...field, quickActions: [action, pin] } : field,
                    );
                    const timing = await page.evaluate(async fields => {
                        const started = performance.now();
                        await request("loadDocumentAndCreateDraft", {
                            document: { sessionId: "timing", generation: 0, fields },
                            draftId: "timing",
                            hostStateJson: "{}",
                        });
                        await request("focusField", { index: 0 });
                        const interactive = performance.now();
                        await new Promise(resolve =>
                            requestAnimationFrame(() => requestAnimationFrame(resolve)),
                        );
                        return {
                            navigationToApiMs: started,
                            bindAndFocusMs: interactive - started,
                            bindFocusPaintMs: performance.now() - started,
                            scripts: performance
                                .getEntriesByType("resource")
                                .filter(entry => /\.m?js$/.test(entry.name)).length,
                            mounted: document.querySelectorAll(".rich-text-editable").length,
                            sources: document.querySelectorAll(".CodeMirror").length,
                        };
                    }, fields);
                    samples.push({ run, count, variant, ...timing });
                    await page.close();
                }
        console.log(JSON.stringify(samples));
    }
} finally {
    await browser.close();
}
