/* Auto-reveal: shows the answer after a configurable delay on the question side. */
(() => {
    "use strict";
    const { delaySeconds = 10, enabled = true } = ankidroid.settings;

    let timer = null;
    ankidroid.onEvent("question", () => {
        clearTimeout(timer);
        if (!enabled) return;
        // the app's own scheme, the same one the type-answer <input> uses to reveal
        timer = setTimeout(
            () => ankidroid.navigate("ankidroid://show-answer"),
            delaySeconds * 1000,
        );
    });
    ankidroid.onEvent("answer", () => clearTimeout(timer));
})();
