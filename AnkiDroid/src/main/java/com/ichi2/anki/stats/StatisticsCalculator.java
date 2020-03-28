package com.ichi2.anki.stats;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Sched;

/** Coordinates the calculation of statistics from various sources */
public class StatisticsCalculator {
    private final Sched mScheduler;

    private StatisticsCalculator(Sched scheduler) {
        this.mScheduler = scheduler;
    }

    public static StatisticsCalculator fromCollection(Collection collection) {
        return new StatisticsCalculator(collection.getSched());
    }

    @SuppressWarnings("unused") //selected decks are currently implicit in the scheduler.
    public SchedulerStatistics calculateReviewStatistics(Long[] deckIds) {
        return SchedulerStatistics.fromSchedulerCounts(mScheduler.counts());
    }

    public int getDeckCompletionEtaInMinutes(SchedulerStatistics statistics) {
        return mScheduler.eta(statistics.toSchedulerCounts());
    }


    public void obtainCurrentStatistics() {
        mScheduler.reset();
    }
}
