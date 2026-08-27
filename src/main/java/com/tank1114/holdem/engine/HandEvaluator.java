package com.tank1114.holdem.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Evaluates the best possible 5-card poker hand out of a set of cards
 * (used with 2 hole cards + 5 community cards = 7 candidates).
 */
public final class HandEvaluator {

    private HandEvaluator() {
    }

    /** Picks the best 5-card {@link HandValue} achievable from 5, 6 or 7 cards. */
    public static HandValue evaluateBest(List<Card> cards) {
        if (cards.size() < 5) {
            throw new IllegalArgumentException("至少需要 5 張牌才能組成牌型");
        }
        if (cards.size() == 5) {
            return evaluate5(cards);
        }
        HandValue best = null;
        int n = cards.size();
        // Enumerate every 5-card subset by choosing which (n - 5) cards to exclude.
        for (List<Integer> excluded : combinations(n, n - 5)) {
            List<Card> five = new ArrayList<>(5);
            for (int i = 0; i < n; i++) {
                if (!excluded.contains(i)) {
                    five.add(cards.get(i));
                }
            }
            HandValue candidate = evaluate5(five);
            if (best == null || candidate.compareTo(best) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    private static List<List<Integer>> combinations(int n, int k) {
        List<List<Integer>> result = new ArrayList<>();
        combine(n, k, 0, new ArrayList<>(), result);
        return result;
    }

    private static void combine(int n, int k, int start, List<Integer> current, List<List<Integer>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < n; i++) {
            current.add(i);
            combine(n, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    public static HandValue evaluate5(List<Card> five) {
        if (five.size() != 5) {
            throw new IllegalArgumentException("evaluate5 需要正好 5 張牌");
        }

        boolean flush = five.stream().map(Card::suit).distinct().count() == 1;

        // rank value -> how many cards of that rank, kept sorted descending by rank.
        Map<Integer, Integer> countByRank = new TreeMap<>(Comparator.reverseOrder());
        for (Card c : five) {
            countByRank.merge(c.rank().value(), 1, Integer::sum);
        }

        Integer straightHigh = straightHighCard(countByRank.keySet());
        boolean straight = straightHigh != null;

        if (flush && straight) {
            HandCategory cat = straightHigh == 14 ? HandCategory.ROYAL_FLUSH : HandCategory.STRAIGHT_FLUSH;
            return new HandValue(cat, List.of(straightHigh));
        }

        // Groups sorted by (count desc, rank desc) - the natural tiebreak order for
        // four-of-a-kind / full house / trips / two pair / pair / high card.
        List<Map.Entry<Integer, Integer>> groups = new ArrayList<>(countByRank.entrySet());
        groups.sort((a, b) -> {
            int byCount = b.getValue() - a.getValue();
            return byCount != 0 ? byCount : b.getKey() - a.getKey();
        });

        int[] counts = groups.stream().mapToInt(Map.Entry::getValue).toArray();

        if (counts[0] == 4) {
            return new HandValue(HandCategory.FOUR_OF_A_KIND, List.of(groups.get(0).getKey(), groups.get(1).getKey()));
        }
        if (counts[0] == 3 && counts.length > 1 && counts[1] == 2) {
            return new HandValue(HandCategory.FULL_HOUSE, List.of(groups.get(0).getKey(), groups.get(1).getKey()));
        }
        if (flush) {
            List<Integer> kickers = groups.stream().map(Map.Entry::getKey).toList();
            return new HandValue(HandCategory.FLUSH, kickers);
        }
        if (straight) {
            return new HandValue(HandCategory.STRAIGHT, List.of(straightHigh));
        }
        if (counts[0] == 3) {
            List<Integer> tiebreak = new ArrayList<>();
            tiebreak.add(groups.get(0).getKey());
            tiebreak.add(groups.get(1).getKey());
            tiebreak.add(groups.get(2).getKey());
            return new HandValue(HandCategory.THREE_OF_A_KIND, tiebreak);
        }
        if (counts[0] == 2 && counts.length > 1 && counts[1] == 2) {
            List<Integer> tiebreak = new ArrayList<>();
            tiebreak.add(groups.get(0).getKey());
            tiebreak.add(groups.get(1).getKey());
            tiebreak.add(groups.get(2).getKey());
            return new HandValue(HandCategory.TWO_PAIR, tiebreak);
        }
        if (counts[0] == 2) {
            List<Integer> tiebreak = new ArrayList<>();
            tiebreak.add(groups.get(0).getKey());
            tiebreak.add(groups.get(1).getKey());
            tiebreak.add(groups.get(2).getKey());
            tiebreak.add(groups.get(3).getKey());
            return new HandValue(HandCategory.ONE_PAIR, tiebreak);
        }
        List<Integer> kickers = groups.stream().map(Map.Entry::getKey).toList();
        return new HandValue(HandCategory.HIGH_CARD, kickers);
    }

    /** Returns the straight's high card value, or null if the 5 distinct ranks aren't consecutive. */
    private static Integer straightHighCard(java.util.Set<Integer> distinctRanksDesc) {
        if (distinctRanksDesc.size() != 5) {
            return null;
        }
        List<Integer> ranks = new ArrayList<>(distinctRanksDesc); // already sorted descending (TreeMap keySet)
        if (ranks.get(0) - ranks.get(4) == 4) {
            return ranks.get(0);
        }
        // Wheel: A-2-3-4-5, ace plays low, five is the effective high card.
        if (ranks.equals(List.of(14, 5, 4, 3, 2))) {
            return 5;
        }
        return null;
    }
}
