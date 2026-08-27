package com.tank1114.holdem.game;

import java.util.Set;

/** One pot (main pot, or a side pot) with the seat indices allowed to win it. */
public record Pot(long amount, Set<Integer> eligibleSeatIndices) {
}
