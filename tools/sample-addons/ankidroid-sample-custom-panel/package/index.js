/* Applies the accent colour chosen in the custom settings panel to the answer buttons. */
(() => {
    "use strict";
    const accent = ankidroid.settings.accent;
    if (accent) {
        // the relay injects the style into the host page on our behalf
        ankidroid.injectStyle(`button { border-bottom: 3px solid ${accent} !important; }`);
    }
})();
