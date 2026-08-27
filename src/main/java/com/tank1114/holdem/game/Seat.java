package com.tank1114.holdem.game;

import com.tank1114.holdem.engine.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One of the table's fixed physical seats. */
public final class Seat {

    private final int index;

    private UUID occupant;
    private String occupantName;
    private long stack;

    private final List<Card> holeCards = new ArrayList<>(2);
    private long committedThisHand;
    private long committedThisRound;
    private boolean folded;
    private boolean allIn;
    private boolean actedThisRound;

    public Seat(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    public boolean isOccupied() {
        return occupant != null;
    }

    public UUID occupant() {
        return occupant;
    }

    public String occupantName() {
        return occupantName;
    }

    public void seat(UUID uuid, String name, long startingStack) {
        this.occupant = uuid;
        this.occupantName = name;
        this.stack = startingStack;
        resetForNewHand();
    }

    public void vacate() {
        this.occupant = null;
        this.occupantName = null;
        this.stack = 0;
        resetForNewHand();
    }

    public long stack() {
        return stack;
    }

    public void setStack(long stack) {
        this.stack = stack;
    }

    public List<Card> holeCards() {
        return holeCards;
    }

    public void dealHoleCard(Card card) {
        holeCards.add(card);
    }

    public long committedThisHand() {
        return committedThisHand;
    }

    public long committedThisRound() {
        return committedThisRound;
    }

    public boolean isFolded() {
        return folded;
    }

    public void fold() {
        this.folded = true;
    }

    public boolean isAllIn() {
        return allIn;
    }

    public boolean isActedThisRound() {
        return actedThisRound;
    }

    public void markActed() {
        this.actedThisRound = true;
    }

    /** Used when a bet/raise reopens the action: everyone else must act again. */
    public void clearActedFlag() {
        this.actedThisRound = false;
    }

    /** True if this seat can still be asked to act (occupied, not folded, has chips left). */
    public boolean isActive() {
        return isOccupied() && !folded && !allIn;
    }

    /** Moves chips from the seat's stack into the pot, tracking hand + round totals. Returns the actual amount moved (capped by stack). */
    public long commit(long amount) {
        long actual = Math.min(amount, stack);
        stack -= actual;
        committedThisHand += actual;
        committedThisRound += actual;
        if (stack == 0) {
            allIn = true;
        }
        return actual;
    }

    public void startNewBettingRound() {
        committedThisRound = 0;
        actedThisRound = false;
    }

    public void resetForNewHand() {
        holeCards.clear();
        committedThisHand = 0;
        committedThisRound = 0;
        folded = false;
        allIn = false;
        actedThisRound = false;
    }
}
