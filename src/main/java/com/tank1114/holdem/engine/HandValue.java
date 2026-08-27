package com.tank1114.holdem.engine;

import java.util.List;

/**
 * The evaluated strength of a specific 5-card hand.
 * {@code tiebreakers} holds rank values in the order they should be compared
 * (e.g. for two pair: [highPairRank, lowPairRank, kickerRank]).
 */
public record HandValue(HandCategory category, List<Integer> tiebreakers) implements Comparable<HandValue> {

    @Override
    public int compareTo(HandValue other) {
        int byCategory = this.category.ordinal() - other.category.ordinal();
        if (byCategory != 0) {
            return byCategory;
        }
        int len = Math.min(this.tiebreakers.size(), other.tiebreakers.size());
        for (int i = 0; i < len; i++) {
            int cmp = this.tiebreakers.get(i) - other.tiebreakers.get(i);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    public boolean isTie(HandValue other) {
        return compareTo(other) == 0;
    }
}
