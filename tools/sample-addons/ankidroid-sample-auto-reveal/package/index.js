/* Auto-reveal: shows the answer after a delay on the question side. */
(() => {
    "use strict";
    const REVEAL_AFTER_MS = 10 * 1000;

    let timer = null;

    const origShowQuestion = globalThis._showQuestion;
    globalThis._showQuestion = function (...args) {
        origShowQuestion.apply(this, args);
        clearTimeout(timer);
        // the same scheme the type-answer <input> uses to reveal the answer
        timer = setTimeout(() => {
            window.location.href = "ankidroid://show-answer";
        }, REVEAL_AFTER_MS);
    };

    const origShowAnswer = globalThis._showAnswer;
    globalThis._showAnswer = function (...args) {
        origShowAnswer.apply(this, args);
        clearTimeout(timer);
        timer = null;
    };
})();
