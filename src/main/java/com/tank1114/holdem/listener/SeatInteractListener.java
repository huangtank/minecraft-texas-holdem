package com.tank1114.holdem.listener;

import com.tank1114.holdem.display.TableDisplayManager;
import com.tank1114.holdem.game.PokerTable;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/** Right-clicking a seat's chair entity sits you down; right-clicking your own seat again stands you up. */
public final class SeatInteractListener implements Listener {

    private final PokerTable table;
    private final TableDisplayManager display;

    public SeatInteractListener(PokerTable table, TableDisplayManager display) {
        this.table = table;
        this.display = display;
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

        String error;
        if (currentSeat == seatIndex) {
            error = table.requestLeave(player.getUniqueId());
        } else {
            error = table.sitDown(player, seatIndex);
        }
        if (error != null) {
            player.sendMessage(Component.text(error));
        }
    }
}
