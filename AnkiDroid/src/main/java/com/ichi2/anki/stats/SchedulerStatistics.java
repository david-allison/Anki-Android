package com.ichi2.anki.stats;

/** Statistics relating to a current session of reviewing */
public class SchedulerStatistics {
    private final int mNewCards;
    private final int mLearningCounts;
    private final int mReviewCounts;

    public SchedulerStatistics(int newCards, int learningCounts, int reviewCounts) {
        this.mNewCards = newCards;
        this.mLearningCounts = learningCounts;
        this.mReviewCounts = reviewCounts;
    }

    public static SchedulerStatistics fromSchedulerCounts(int[] counts) {
        return new SchedulerStatistics(counts[0], counts[1], counts[2]);
    }

    public int[] toSchedulerCounts() {
        return new int[] { getNewCount(), getLearningCardCount(), getReviewCardCount() };
    }

    public int getNewCount() {
        return mNewCards;
    }

    public int getLearningCardCount() {
        return mLearningCounts;
    }

    public int getReviewCardCount() {
        return mReviewCounts;
    }
}
