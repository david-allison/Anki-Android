package com.ichi2.libanki.decks;

import com.ichi2.libanki.Consts;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

public class NewConf extends ReviewingConf {
    public NewConf() {
        super();
    }
    public NewConf(JSONObject conf) {
        super(conf);
    }

    public NewConf(DConf oconf, DConf conf, int reportLimit, int version) {
        super();
        JSONArray delays;
        if (conf.has("delays") && version == 1) {
            // always true in sched v2;
            delays = conf.getDelays();
        } else {
            delays = oconf.getNew().getDelays();
        }
        // original deck
        put("ints", oconf.getNew().getInts());
        put("initialFactor", oconf.getNew().getInt("initialFactor"));
        put("bury", oconf.getNew().optBoolean("bury", true));
        put("delays", delays);
        // overrides
        put("separate", conf.getBoolean("separate"));
        put("order", Consts.NEW_CARDS_DUE);
        put("perDay", reportLimit);
    }

    public void putInitialFactor(int initFact) {
        put("initialFactor", initFact);
    }

    public void putOrder(int n) {
        put("order", n);
    }
}
