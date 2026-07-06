/* Auto-reveal: shows the answer after a configurable delay on the question side. */
(() => {
    "use strict";
    const ADDON = "ankidroid-sample-auto-reveal";

    let timer = null;

    function settings() {
        // host-provided; falls back to the schema defaults if unavailable
        return globalThis.ankidroid?.addonSettings?.(ADDON) ?? { delaySeconds: 10, enabled: true };
    }

    const origShowQuestion = globalThis._showQuestion;
    globalThis._showQuestion = function (...args) {
        origShowQuestion.apply(this, args);
        clearTimeout(timer);
        const { delaySeconds, enabled } = settings();
        if (!enabled) return;
        // the same scheme the type-answer <input> uses to reveal the answer
        timer = setTimeout(() => {
            window.location.href = "ankidroid://show-answer";
        }, delaySeconds * 1000);
    };

    const origShowAnswer = globalThis._showAnswer;
    globalThis._showAnswer = function (...args) {
        origShowAnswer.apply(this, args);
        clearTimeout(timer);
        timer = null;
    };
})();
