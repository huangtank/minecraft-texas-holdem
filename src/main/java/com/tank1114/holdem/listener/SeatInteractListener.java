package com.tank1114.holdem.listener;

import com.tank1114.holdem.display.TableDisplayManager;
import com.tank1114.holdem.game.GameStage;
import com.tank1114.holdem.game.PokerTable;
import com.tank1114.holdem.ui.ActionMenuListener;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Right-clicking a seat's chair entity sits you down. Right-clicking your own seat again either
 * reopens your action menu (if it's currently your turn) or stands you up (otherwise) - there is
 * no command for any of this, it's all click-driven.
 */
public final class SeatInteractListener implements Listener {

    private final PokerTable table;
    private final TableDisplayManager display;
    private final ActionMenuListener actionMenu;

    public SeatInteractListener(PokerTable table, TableDisplayManager display, ActionMenuListener actionMenu) {
        this.table = table;
        this.display = display;
        this.actionMenu = actionMenu;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Integer seatIndex = display.seatForMarker(event.getRightClicked().getUniqueId());
        if (seatIndex == null) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        int currentSeat = table.seatIndexOf(player.getUniqueId());

        if (currentSeat == seatIndex) {
            if (table.stage() != GameStage.WAITING && table.stage() != GameStage.SHOWDOWN
                    && table.actingSeatIndex() == seatIndex) {
                actionMenu.open(player, seatIndex);
                return;
            }
            String error = table.requestLeave(player.getUniqueId());
            if (error != null) {
                player.sendMessage(Component.text(error));
            }
            return;
        }

        String error = table.sitDown(player, seatIndex);
        if (error != null) {
            player.sendMessage(Component.text(error));
        }
    }
}
