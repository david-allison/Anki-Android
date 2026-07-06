/* Session progress bar: fills as cards are seen, towards a session goal. */
(() => {
    "use strict";
    const SESSION_GOAL = 50;

    const track = document.createElement("div");
    track.style.cssText =
        "position:fixed;top:0;left:0;right:0;height:3px;z-index:9999;" +
        "background:color-mix(in srgb, var(--fg) 15%, transparent);pointer-events:none;";
    const bar = document.createElement("div");
    bar.style.cssText = "height:100%;width:0%;background:#4caf50;transition:width .3s;";
    track.appendChild(bar);
    document.body.appendChild(track);

    let cardsSeen = 0;
    const origShowQuestion = globalThis._showQuestion;
    globalThis._showQuestion = function (...args) {
        origShowQuestion.apply(this, args);
        cardsSeen += 1;
        bar.style.width = `${Math.min(100, (cardsSeen / SESSION_GOAL) * 100)}%`;
    };
})();
