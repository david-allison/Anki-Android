package com.ichi2.libanki.decks;

import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

public class Deck extends JSONObject{
    public Deck() {
        super();
    }

    public Deck(Deck deck) {
        super(deck);
    }

    public Deck(JSONObject json) {
        super(json);
    }

    public Deck(String json) {
        super(json);
    }
}
