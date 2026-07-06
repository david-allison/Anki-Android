/* Image zoom: tap a card image to view it fullscreen, tap again to dismiss. */
(() => {
    "use strict";

    const overlay = document.createElement("div");
    overlay.style.cssText =
        "position:fixed;inset:0;z-index:9998;display:none;align-items:center;" +
        "justify-content:center;background:rgba(0,0,0,.85);";
    const zoomed = document.createElement("img");
    zoomed.style.cssText = "max-width:95vw;max-height:95vh;object-fit:contain;";
    overlay.appendChild(zoomed);
    overlay.addEventListener("click", () => {
        overlay.style.display = "none";
        zoomed.src = "";
    });
    document.body.appendChild(overlay);

    // delegated: card content inside #qa is swapped for every card
    document.addEventListener("click", event => {
        const image = event.target?.closest?.("#qa img");
        if (!image || !image.src) return;
        zoomed.src = image.src;
        overlay.style.display = "flex";
    });
})();
