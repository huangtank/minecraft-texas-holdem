package com.tank1114.holdem.ui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks an open inventory as this plugin's turn-action menu for one specific seat. */
public final class ActionMenuHolder implements InventoryHolder {

    private final int seatIndex;
    private Inventory inventory;

    public ActionMenuHolder(int seatIndex) {
        this.seatIndex = seatIndex;
    }

    public int seatIndex() {
        return seatIndex;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
