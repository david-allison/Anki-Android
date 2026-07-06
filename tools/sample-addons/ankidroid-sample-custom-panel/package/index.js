/* Applies the accent colour chosen in the custom settings panel to the answer buttons. */
(() => {
    "use strict";
    const ADDON = "ankidroid-sample-custom-panel";

    function apply() {
        const accent = globalThis.ankidroid?.addonSettings?.(ADDON)?.accent;
        if (!accent) return;
        document.documentElement.style.setProperty("--accent-override", accent);
        for (const button of document.querySelectorAll("button")) {
            button.style.borderBottom = `3px solid ${accent}`;
        }
    }

    const origShowQuestion = globalThis._showQuestion;
    globalThis._showQuestion = function (...args) {
        origShowQuestion.apply(this, args);
        apply();
    };
    const origShowAnswer = globalThis._showAnswer;
    globalThis._showAnswer = function (...args) {
        origShowAnswer.apply(this, args);
        apply();
    };
})();
