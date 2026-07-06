/* Image zoom: tap a card image to view it fullscreen, tap again to dismiss. */
(() => {
    "use strict";
    let overlayId = null;

    // delegated on the host page: fires for clicks on any image inside #qa
    ankidroid.onDomEvent("#qa img", "click", target => {
        if (!target.src || overlayId) return;
        overlayId = ankidroid.addElement(
            "body-end",
            "<div class='addon-zoom-overlay' style='position:fixed;inset:0;z-index:9998;display:flex;" +
                "align-items:center;justify-content:center;background:rgba(0,0,0,.85)'>" +
                `<img src='${target.src}' style='max-width:95vw;max-height:95vh;object-fit:contain'></div>`,
        );
    });

    ankidroid.onDomEvent(".addon-zoom-overlay", "click", () => {
        if (overlayId) {
            ankidroid.removeElement(overlayId);
            overlayId = null;
        }
    });
})();
