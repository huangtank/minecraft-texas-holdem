package com.tank1114.holdem.command;

import com.tank1114.holdem.config.HoldemConfig;
import com.tank1114.holdem.display.TableDisplayManager;
import com.tank1114.holdem.game.GameStage;
import com.tank1114.holdem.game.PokerTable;
import com.tank1114.holdem.game.RebuyMode;
import com.tank1114.holdem.layout.TableLayout;
import com.tank1114.holdem.storage.ChipStorage;
import com.tank1114.holdem.storage.LayoutStorage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /holdemadmin setup|start|reset|rebuy|chips - table setup and rescue tools for operators. */
public final class HoldemAdminCommand implements CommandExecutor {

    private final PokerTable table;
    private final TableDisplayManager display;
    private final TableLayout layout;
    private final LayoutStorage layoutStorage;
    private final HoldemConfig config;
    private final ChipStorage chipStorage;

    public HoldemAdminCommand(PokerTable table, TableDisplayManager display, TableLayout layout,
                               LayoutStorage layoutStorage, HoldemConfig config, ChipStorage chipStorage) {
        this.table = table;
        this.display = display;
        this.layout = layout;
        this.layoutStorage = layoutStorage;
        this.config = config;
        this.chipStorage = chipStorage;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("用法：/holdemadmin <setup|start|reset|rebuy|chips>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setup" -> handleSetup(sender, args);
            case "start" -> handleStart(sender);
            case "reset" -> {
                table.forceReset();
                sender.sendMessage(Component.text(display.isBuilt()
                        ? "已重置牌桌。"
                        : "已重置牌桌狀態（提醒：牌桌座標還沒設定完成，用 /holdemadmin setup status 檢查）。"));
            }
            case "rebuy" -> handleRebuy(sender, args);
            case "chips" -> handleChips(sender, args);
            default -> sender.sendMessage(Component.text("用法：/holdemadmin <setup|start|reset|rebuy|chips>"));
        }
        return true;
    }

    private void handleSetup(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("這個指令只能由玩家使用（需要站在要設定的位置上）。"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法：/holdemadmin setup <center|seat> [座位編號]，或 /holdemadmin setup status"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "center" -> {
                layout.setCenter(player.getLocation());
                layoutStorage.save(layout);
                sender.sendMessage(Component.text("已把公共牌位置設定在你目前的位置。"));
                rebuildIfComplete(sender);
            }
            case "seat" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("用法：/holdemadmin setup seat <1~" + layout.seatCount() + ">"));
                    return;
                }
                int seatNumber;
                try {
                    seatNumber = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("座位編號必須是數字。"));
                    return;
                }
                if (seatNumber < 1 || seatNumber > layout.seatCount()) {
                    sender.sendMessage(Component.text("座位編號要在 1~" + layout.seatCount() + " 之間。"));
                    return;
                }
                layout.setSeat(seatNumber - 1, player.getLocation());
                layoutStorage.save(layout);
                sender.sendMessage(Component.text("已把第 " + seatNumber + " 號座位設定在你目前的位置與朝向。"));
                rebuildIfComplete(sender);
            }
            case "status" -> {
                StringBuilder sb = new StringBuilder("牌桌設定狀態：中心點 ")
                        .append(layout.center() != null ? "已設定" : "未設定");
                for (int i = 0; i < layout.seatCount(); i++) {
                    sb.append("\n第 ").append(i + 1).append(" 號座位：").append(layout.seat(i) != null ? "已設定" : "未設定");
                }
                sender.sendMessage(Component.text(sb.toString()));
            }
            default -> sender.sendMessage(Component.text("用法：/holdemadmin setup <center|seat|status>"));
        }
    }

    private void rebuildIfComplete(CommandSender sender) {
        if (layout.isComplete()) {
            display.rebuild(layout);
            sender.sendMessage(Component.text("牌桌所有位置都設定好了，已經生成牌桌上的顯示物件。"));
        }
    }

    private void handleStart(CommandSender sender) {
        if (!display.isBuilt()) {
            sender.sendMessage(Component.text("牌桌還沒設定完成，請先用 /holdemadmin setup 設定中心點與所有座位。"));
            return;
        }
        if (table.stage() != GameStage.WAITING) {
            sender.sendMessage(Component.text("已經有一局在進行中了。"));
            return;
        }
        table.startHand();
        sender.sendMessage(Component.text("已強制開始新的一局（需要至少 2 位玩家入座）。"));
    }

    private void handleRebuy(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("目前模式：" + config.rebuyMode() + "。用法：/holdemadmin rebuy <AUTO|DISABLED|ADMIN_ONLY>"));
            return;
        }
        try {
            RebuyMode mode = RebuyMode.valueOf(args[1].toUpperCase());
            config.setRebuyMode(mode);
            sender.sendMessage(Component.text("Re-buy 模式已改成 " + mode + "。"));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("沒有這個模式，可用：AUTO、DISABLED、ADMIN_ONLY"));
        }
    }

    private void handleChips(CommandSender sender, String[] args) {
        if (args.length < 4 || !(args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("set"))) {
            sender.sendMessage(Component.text("用法：/holdemadmin chips <give|set> <玩家> <金額>"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("金額必須是一個整數。"));
            return;
        }

        long finalAmount = amount;
        if (args[1].equalsIgnoreCase("give")) {
            finalAmount = currentBalanceOf(target) + amount;
        }
        String error = table.adminSetChips(target.getUniqueId(), finalAmount);
        if (error != null) {
            sender.sendMessage(Component.text(error));
        } else {
            sender.sendMessage(Component.text("已把 " + target.getName() + " 的籌碼設為 " + finalAmount + "。"));
        }
    }

    private long currentBalanceOf(OfflinePlayer target) {
        int seatIndex = table.seatIndexOf(target.getUniqueId());
        if (seatIndex >= 0) {
            return table.seat(seatIndex).stack();
        }
        return chipStorage.get(target.getUniqueId());
    }
}
