package com.tank1114.holdem.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.random.RandomGenerator;

/** A single 52-card deck (no jokers), shuffled fresh for every hand. */
public final class Deck {

    private final Deque<Card> cards;

    public Deck(RandomGenerator random) {
        List<Card> all = new ArrayList<>(52);
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                all.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(all, java.util.Random.from(random));
        this.cards = new ArrayDeque<>(all);
    }

    public Card draw() {
        Card card = cards.poll();
        if (card == null) {
            throw new IllegalStateException("牌堆已經空了，不應該發生（52 張牌不夠發這桌的人數與公共牌）");
        }
        return card;
    }

    public int remaining() {
        return cards.size();
    }
}
