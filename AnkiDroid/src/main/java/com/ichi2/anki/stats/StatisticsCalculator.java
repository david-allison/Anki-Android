package com.ichi2.anki.stats;

import com.ichi2.libanki.Collection;
import com.ichi2.libanki.Sched;

/** Coordinates the calculation of statistics from various sources */
public class StatisticsCalculator {
    private final Sched mScheduler;
    private final StatisticsRepository mStatisticsRepository;


    private StatisticsCalculator(Sched scheduler, StatisticsRepository statisticsRepository) {
        this.mScheduler = scheduler;
        this.mStatisticsRepository = statisticsRepository;
    }

    public static StatisticsCalculator fromCollection(Collection collection) {
        return new StatisticsCalculator(collection.getSched(), new StatisticsRepository(collection.getDb()));
    }

    @SuppressWarnings("unused") //selected decks are currently implicit in the scheduler.
    public SchedulerStatistics calculateReviewStatistics(Long[] deckIds) {
        return SchedulerStatistics.fromSchedulerCounts(mScheduler.counts());
    }

    public DeckProgressStatistics calculateDeckProgressStatistics(Long[] selectedDecks) {
        int totalNewCount = mScheduler.totalNewForCurrentDeck();
        int totalCount = mScheduler.cardCount();
        int onHoldCards = this.mStatisticsRepository.countOnHoldCards(selectedDecks);

        return DeckProgressStatistics.fromTotalCount(totalCount, totalNewCount, onHoldCards);
    }

    public ReviewCardsStatistics calculateReviewCardsStatistics(Long[] selectedDecks) {
        int youngCards = this.mStatisticsRepository.countYoungCards(selectedDecks);
        int matureCards = this.mStatisticsRepository.countMatureCards(selectedDecks);

        return new ReviewCardsStatistics(matureCards, youngCards);
    }

    public int getDeckCompletionEtaInMinutes(SchedulerStatistics statistics) {
        return mScheduler.eta(statistics.toSchedulerCounts());
    }


    public void obtainCurrentStatistics() {
        mScheduler.reset();
    }
}
