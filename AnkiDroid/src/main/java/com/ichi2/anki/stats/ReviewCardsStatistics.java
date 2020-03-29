package com.ichi2.anki.stats;

/** Statistics for cards which are currently known */
public class ReviewCardsStatistics {
    public static final int MATURE_CARD_THRESHOLD_DAYS = 21;

    private final int mMatureCardCount;
    private final int mYoungCardCount;

    public ReviewCardsStatistics(int matureCardCount, int youngCardCount) {
        this.mMatureCardCount = matureCardCount;
        this.mYoungCardCount = youngCardCount;
    }

    public int getMatureCardCount() {
        return mMatureCardCount;
    }

    public int getYoungCardCount() {
        return mYoungCardCount;
    }
}
