package com.ichi2.libanki.decks;

import com.ichi2.utils.JSONObject;

public class DConf extends JSONObject{
    public DConf() {
        super();
    }
    public DConf(DConf dconf) {
        super(dconf);
    }

    public DConf(JSONObject json) {
        super(json);
    }

    public DConf(String json) {
        super(json);
    }
}
