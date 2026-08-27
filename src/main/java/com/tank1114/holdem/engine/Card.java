package com.tank1114.holdem.engine;

public record Card(Rank rank, Suit suit) {

    public String display() {
        return suit.symbol() + rank.symbol();
    }

    @Override
    public String toString() {
        return display();
    }
}
