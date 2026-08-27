package com.tank1114.holdem.engine;

/** Ordered weakest to strongest; ordinal() is used directly for comparison. */
public enum HandCategory {
    HIGH_CARD("高牌"),
    ONE_PAIR("一對"),
    TWO_PAIR("兩對"),
    THREE_OF_A_KIND("三條"),
    STRAIGHT("順子"),
    FLUSH("同花"),
    FULL_HOUSE("葫蘆"),
    FOUR_OF_A_KIND("四條"),
    STRAIGHT_FLUSH("同花順"),
    ROYAL_FLUSH("皇家同花順");

    private final String label;

    HandCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
