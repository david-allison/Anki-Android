package com.ichi2.anki.stats;

/** Statistics relating to how a user has progressed through a deck */
public class DeckProgressStatistics {
    private final int mTotalCount;
    private final int mUnseenCards;

    public DeckProgressStatistics(int totalCardCount, int unseenCardCount) {
        this.mTotalCount = totalCardCount;
        this.mUnseenCards = unseenCardCount;
    }

    public int getTotalCardCount() {
        return mTotalCount;
    }

    public int getUnseenCardCount() {
        return mUnseenCards;
    }
}
