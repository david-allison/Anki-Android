package com.ichi2.anki.stats;

import com.ichi2.libanki.DB;
import com.ichi2.libanki.Utils;

import java.util.Locale;

import androidx.annotation.CheckResult;

public class StatisticsRepository {
    private final DB db;

    public StatisticsRepository(DB database) {
        this.db = database;
    }

    @CheckResult
    private int queryDatabase(Long[] childDeckIds, String query) {
        String queryHeader = "select count(*) from cards where did in " + Utils.ids2str(childDeckIds);
        return db.queryScalar(queryHeader + query);
    }

    @CheckResult
    public int countOnHoldCards(Long[] deckIds) {
        return queryDatabase(deckIds, "and queue < 0");
    }

    @CheckResult
    public int countYoungCards(Long[] deckIds) {
        return queryDatabase( deckIds, String.format(Locale.ROOT, "and queue = 2 and ivl < %d", ReviewCardsStatistics.MATURE_CARD_THRESHOLD_DAYS));
    }

    @CheckResult
    public int countMatureCards(Long[] deckIds) {
        return queryDatabase( deckIds,String.format(Locale.ROOT, "and queue = 2 and ivl >= %d", ReviewCardsStatistics.MATURE_CARD_THRESHOLD_DAYS));
    }
}
