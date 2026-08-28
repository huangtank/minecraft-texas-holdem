package com.tank1114.holdem.ui;

import com.tank1114.holdem.game.PlayerAction;
import com.tank1114.holdem.game.PokerTable;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures a free-typed chat message as a custom bet/raise amount, so entering a precise
 * number never requires a slash command - just plain chat after clicking the "自訂金額"
 * hologram button. Shared across every table: each pending request carries its own table
 * reference, since a player could in principle be prompted by any of them.
 */
public final class BetAmountChatListener implements Listener {

    private final Plugin plugin;
    private final Map<UUID, PendingAmount> pending = new ConcurrentHashMap<>();

    public BetAmountChatListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public void requestAmount(Player player, PlayerAction action, PokerTable table) {
        pending.put(player.getUniqueId(), new PendingAmount(action, table));
        player.sendMessage(Component.text("請直接在聊天室輸入金額（數字），或輸入 cancel 取消。"));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        PendingAmount request = pending.remove(event.getPlayer().getUniqueId());
        if (request == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String message = event.getMessage().trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(Component.text("已取消。"));
                return;
            }
            long amount;
            try {
                amount = Long.parseLong(message);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("金額必須是整數，操作已取消，請重新點選按鈕。"));
                return;
            }
            String error = request.table().performAction(player.getUniqueId(), request.action(), amount);
            if (error != null) {
                player.sendMessage(Component.text(error));
            }
        });
    }

    private record PendingAmount(PlayerAction action, PokerTable table) {
    }
}
