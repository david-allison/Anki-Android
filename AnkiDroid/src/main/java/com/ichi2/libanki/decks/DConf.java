package com.ichi2.libanki.decks;

import com.ichi2.libanki.Collection;
import com.ichi2.utils.JSONArray;
import com.ichi2.utils.JSONObject;

import androidx.annotation.Nullable;

public class DConf extends ReadOnlyJSONObject{
    protected DConf() {
        super();
    }

    protected DConf(DConf dconf) {
        super(dconf.getJSON());
    }

    protected DConf(JSONObject json) {
        super(json);
    }

    protected DConf(String json) {
        super(json);
    }

    public ReviewConf getRev() {
        return new ReviewConf(getJSON().getJSONObject("rev"));
    }

    public NewConf getNew() {
        return new NewConf(getJSON().getJSONObject("new"));
    }

    public LapseConf getLapse() {
        return new LapseConf(getJSON().getJSONObject("lapse"));
    }

    public JSONArray getDelays(){
        return getJSON().getJSONArray("delays");
    }

    public JSONObject getReminder(){
        return getJSON().getJSONObject("reminder");
    }

    public void setReminder(Object o) {
        put("reminder", o);
    }

    @Nullable
    public Boolean parseTimer() {
        //Note: Card.py used != 0, DeckOptions used == 1
        try {
            //#6089 - Anki 2.1.24 changed this to a bool, reverted in 2.1.25.
            return getInt("timer") != 0;
        } catch (Exception e) {
            try {
                return getBoolean("timer");
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public boolean parseTimerOpt(boolean defaultValue) {
        Boolean ret = parseTimer();
        if (ret == null) {
            ret = defaultValue;
        }
        return ret;
    }

    public void setMaxTaken(Object o) {
        put("maxTaken", o);
    }

    public void setTimer(int timer) {
        put("timer", timer);
    }

    public void setAutoplay(Object o) {
        put("autoplay", o);
    }

    public void setReplayq(Object o) {
        put("replayq", o);
    }

    public void setName(Object o) {
        put("name", o);
    }

    public void version10to11(Collection col) {
        ReviewingConf r = getRev();
        r.put("ivlFct", r.optDouble("ivlFct", 1));
        if (r.has("ivlfct")) {
            r.remove("ivlfct");
        }
        r.put("maxIvl", 36500);
        col.getDecks().save();
    }
}
