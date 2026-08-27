package com.tank1114.holdem.listener;

import com.tank1114.holdem.display.TableDisplayManager;
import com.tank1114.holdem.game.PokerTable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.Listener;

/**
 * Disconnecting mid-hand is an automatic fold (SKILL.md rule), and per-player entity
 * visibility for the hidden hole cards has to be re-applied after rejoin/teleport
 * since it does not survive those events on its own.
 */
public final class PlayerConnectionListener implements Listener {

    private final PokerTable table;
    private final TableDisplayManager display;

    public PlayerConnectionListener(PokerTable table, TableDisplayManager display) {
        this.table = table;
        this.display = display;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        table.handlePlayerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        reapply(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        reapply(event.getPlayer());
    }

    private void reapply(Player player) {
        if (!display.isBuilt()) {
            return;
        }
        int seatIndex = table.seatIndexOf(player.getUniqueId());
        display.reapplyVisibility(player, seatIndex);
    }
}
