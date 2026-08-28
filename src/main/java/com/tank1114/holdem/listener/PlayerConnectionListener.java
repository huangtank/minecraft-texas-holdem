package com.tank1114.holdem.listener;

import com.tank1114.holdem.table.TableInstance;
import com.tank1114.holdem.table.TableManager;
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

    private final TableManager tableManager;

    public PlayerConnectionListener(TableManager tableManager) {
        this.tableManager = tableManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TableInstance instance = tableManager.findTableOfPlayer(event.getPlayer().getUniqueId());
        if (instance != null) {
            instance.table().handlePlayerQuit(event.getPlayer().getUniqueId());
        }
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
        TableInstance instance = tableManager.findTableOfPlayer(player.getUniqueId());
        if (instance == null) {
            return;
        }
        instance.display().reapplyVisibility(player, instance.table().seatIndexOf(player.getUniqueId()));
    }
}
