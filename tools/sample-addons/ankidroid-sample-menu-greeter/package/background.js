/* Handles clicks on the deck-picker menu item this addon contributes. */
ankidroid.onMenuClick(menuId => {
    if (menuId === "greet") {
        ankidroid.log("Hello from the Menu greeter addon's background context!");
    }
});
