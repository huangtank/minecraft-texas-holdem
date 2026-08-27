package com.tank1114.holdem.layout;

import org.bukkit.Location;

/**
 * The fixed physical layout of the one table this plugin runs. An admin only records the
 * center point; the N seats are derived automatically, spaced evenly in a ring around it
 * and rotated to match whichever way the admin was facing when they set the center.
 */
public final class TableLayout {

    private final int seatCount;
    private final double seatRadius;
    private Location center;

    public TableLayout(int seatCount, double seatRadius) {
        this.seatCount = seatCount;
        this.seatRadius = seatRadius;
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

    /** Seat index 0 sits at the center's own facing direction; the rest are spaced evenly around the ring. */
    public Location seat(int index) {
        if (center == null) {
            return null;
        }
        double angleDeg = center.getYaw() + index * (360.0 / seatCount);
        double rad = Math.toRadians(angleDeg);
        double dx = -Math.sin(rad) * seatRadius;
        double dz = Math.cos(rad) * seatRadius;

        Location seatLoc = center.clone().add(dx, 0, dz);
        seatLoc.setYaw((float) (angleDeg + 180.0));
        seatLoc.setPitch(0f);
        return seatLoc;
    }

    /** True once an admin has recorded the center point; every seat is then derivable. */
    public boolean isComplete() {
        return center != null;
    }
}
