package com.tank1114.holdem.table;

import com.tank1114.holdem.display.TableDisplayManager;
import com.tank1114.holdem.game.PokerTable;
import com.tank1114.holdem.layout.TableLayout;
import com.tank1114.holdem.ui.TurnHologramMenu;

/** Bundles everything one physical table needs: its layout, game state, world entities, and action-menu UI. */
public record TableInstance(int id, TableLayout layout, PokerTable table, TableDisplayManager display,
                             TurnHologramMenu turnMenu) {
}
