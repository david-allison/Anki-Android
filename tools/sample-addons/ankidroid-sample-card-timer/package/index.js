/* Card timer: elapsed-time badge per card, frozen when the answer is shown. */
(() => {
    "use strict";
    const badgeId = ankidroid.addElement(
        "body-end",
        "<div style='position:fixed;bottom:8px;right:8px;z-index:9999;padding:2px 8px;border-radius:10px;" +
            "font:12px monospace;opacity:.6;color:var(--canvas);background:var(--fg)'>0s</div>",
    );

    let startedAt = Date.now();
    let running = true;
    setInterval(() => {
        if (running)
            ankidroid.setElementHtml(badgeId, `${Math.round((Date.now() - startedAt) / 1000)}s`);
    }, 250);

    ankidroid.onEvent("question", () => {
        startedAt = Date.now();
        running = true;
    });
    ankidroid.onEvent("answer", () => {
        running = false;
    });
})();
