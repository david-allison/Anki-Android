/* Session progress bar: fills as cards are seen, towards a session goal. */
(() => {
    "use strict";
    const SESSION_GOAL = 50;

    // a fixed bar at the top of the page; the relay owns the DOM, we own the logic
    const barId = ankidroid.addElement(
        "body-start",
        "<div style='position:fixed;top:0;left:0;height:3px;width:0;background:#4caf50;transition:width .3s;z-index:9999'></div>",
    );

    let cardsSeen = 0;
    ankidroid.onEvent("question", () => {
        cardsSeen += 1;
        ankidroid.setElementStyle(
            barId,
            "width",
            `${Math.min(100, (cardsSeen / SESSION_GOAL) * 100)}%`,
        );
    });
})();
