/* Card timer: elapsed-time badge per card, frozen when the answer is shown. */
(() => {
    "use strict";

    const badge = document.createElement("div");
    badge.style.cssText =
        "position:fixed;bottom:8px;right:8px;z-index:9999;padding:2px 8px;" +
        "border-radius:10px;font:12px monospace;opacity:.6;pointer-events:none;" +
        "color:var(--canvas);background:var(--fg);";
    badge.textContent = "0s";
    document.body.appendChild(badge);

    let startedAt = Date.now();
    let running = true;
    setInterval(() => {
        if (!running) return;
        badge.textContent = `${Math.round((Date.now() - startedAt) / 1000)}s`;
    }, 250);

    const origShowQuestion = globalThis._showQuestion;
    globalThis._showQuestion = function (...args) {
        origShowQuestion.apply(this, args);
        startedAt = Date.now();
        running = true;
        badge.textContent = "0s";
    };

    const origShowAnswer = globalThis._showAnswer;
    globalThis._showAnswer = function (...args) {
        origShowAnswer.apply(this, args);
        running = false;
    };
})();
