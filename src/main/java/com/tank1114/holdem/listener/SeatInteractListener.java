package com.tank1114.holdem.listener;

import com.tank1114.holdem.game.GameStage;
import com.tank1114.holdem.game.PokerTable;
import com.tank1114.holdem.table.TableInstance;
import com.tank1114.holdem.table.TableManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Right-clicking a seat's chair entity sits you down. Right-clicking your own seat again
 * reopens your action menu if it's currently your turn (otherwise it does nothing) - standing
 * up is only ever done through the "離開座位" hologram button, never by clicking the chair,
 * since that was too easy to trigger by accident.
 */
public final class SeatInteractListener implements Listener {

    private final TableManager tableManager;

    public SeatInteractListener(TableManager tableManager) {
        this.tableManager = tableManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        TableManager.SeatRef ref = tableManager.resolveSeatMarker(event.getRightClicked().getUniqueId());
        if (ref == null) {
            return;
        }
        event.setCancelled(true);

        TableInstance instance = ref.instance();
        PokerTable table = instance.table();
        int seatIndex = ref.seatIndex();
        Player player = event.getPlayer();
        int currentSeat = table.seatIndexOf(player.getUniqueId());

        if (currentSeat == seatIndex) {
            if (table.stage() != GameStage.WAITING && table.stage() != GameStage.SHOWDOWN
                    && table.actingSeatIndex() == seatIndex) {
                instance.turnMenu().open(player, seatIndex);
            }
            return;
        }

        TableInstance existing = tableManager.findTableOfPlayer(player.getUniqueId());
        if (existing != null) {
            player.sendMessage(Component.text("你已經坐在第 " + existing.id() + " 號桌了，請先從那邊離座。"));
            return;
        }

        String error = table.sitDown(player, seatIndex);
        if (error != null) {
            player.sendMessage(Component.text(error));
        }
    }
}
