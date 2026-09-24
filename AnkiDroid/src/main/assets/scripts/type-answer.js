// Keep nosuggest scoped to the marked input, including when a card has other editable fields.
(() => {
    document.addEventListener("focusin", event => {
        AnkiDroidKeyboard.setNoSuggest(event.target.dataset.ankidroidNosuggest === "true");
    });
    document.addEventListener("focusout", () => {
        AnkiDroidKeyboard.setNoSuggest(false);
    });
})();
