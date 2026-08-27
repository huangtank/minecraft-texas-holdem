package com.tank1114.holdem.layout;

import org.bukkit.Location;

/** The fixed physical layout of the one table this plugin runs: a center point plus N seats. */
public final class TableLayout {

    private final int seatCount;
    private Location center;
    private final Location[] seats;

    public TableLayout(int seatCount) {
        this.seatCount = seatCount;
        this.seats = new Location[seatCount];
    }

    public int seatCount() {
        return seatCount;
    }

    public Location center() {
        return center;
    }

    public void setCenter(Location center) {
        this.center = center;
    }

    public Location seat(int index) {
        return seats[index];
    }

    public void setSeat(int index, Location location) {
        seats[index] = location;
    }

    /** True once the center and every seat location have been recorded by an admin. */
    public boolean isComplete() {
        if (center == null) {
            return false;
        }
        for (Location seat : seats) {
            if (seat == null) {
                return false;
            }
        }
        return true;
    }
}
