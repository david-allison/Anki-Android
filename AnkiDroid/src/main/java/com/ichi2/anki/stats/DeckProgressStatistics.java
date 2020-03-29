package com.ichi2.anki.stats;

/** Statistics relating to how a user has progressed through a deck */
public class DeckProgressStatistics {
    private final int mTotalCount;
    private final int mUnseenCards;
    private final int mSeenCardCount;
    private final int mOnHoldCardCount;

    public DeckProgressStatistics(int totalCardCount, int unseenCardCount, int seenCardCount, int onHoldCardCount) {
        this.mTotalCount = totalCardCount;
        this.mUnseenCards = unseenCardCount;
        this.mSeenCardCount = seenCardCount;
        this.mOnHoldCardCount = onHoldCardCount;
    }


    public static DeckProgressStatistics fromTotalCount(int totalCount, int totalNewCount, int onHoldCards) {
        int seen = totalCount - totalNewCount - onHoldCards;
        return new DeckProgressStatistics(totalCount, totalNewCount, seen, onHoldCards);
    }


    public int getTotalCardCount() {
        return mTotalCount;
    }

    public int getUnseenCardCount() {
        return mUnseenCards;
    }

    public int getSeenCardCount() {
        return mSeenCardCount;
    }

    public int getOnHoldCardCount() {
        return mOnHoldCardCount;
    }
}
