package com.tank1114.holdem.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Splits everything committed to the pot this hand into a main pot plus any
 * side pots created by players who went all-in for less than others.
 */
final class PotCalculator {

    private PotCalculator() {
    }

    static List<Pot> compute(List<Seat> seats) {
        List<Seat> contributors = seats.stream().filter(s -> s.committedThisHand() > 0).toList();
        if (contributors.isEmpty()) {
            return List.of();
        }

        TreeSet<Long> levels = new TreeSet<>();
        for (Seat s : contributors) {
            levels.add(s.committedThisHand());
        }

        List<Pot> slices = new ArrayList<>();
        long previous = 0L;
        for (long level : levels) {
            long delta = level - previous;
            List<Seat> atOrAbove = contributors.stream().filter(s -> s.committedThisHand() >= level).toList();
            long amount = delta * atOrAbove.size();
            if (amount > 0) {
                Set<Integer> eligible = new LinkedHashSet<>();
                for (Seat s : atOrAbove) {
                    if (!s.isFolded()) {
                        eligible.add(s.index());
                    }
                }
                slices.add(new Pot(amount, eligible));
            }
            previous = level;
        }

        // Merge consecutive slices that share the exact same eligible winners, purely for tidier display.
        List<Pot> merged = new ArrayList<>();
        for (Pot slice : slices) {
            if (!merged.isEmpty() && merged.get(merged.size() - 1).eligibleSeatIndices().equals(slice.eligibleSeatIndices())) {
                Pot last = merged.remove(merged.size() - 1);
                merged.add(new Pot(last.amount() + slice.amount(), last.eligibleSeatIndices()));
            } else {
                merged.add(slice);
            }
        }
        return merged;
    }
}
