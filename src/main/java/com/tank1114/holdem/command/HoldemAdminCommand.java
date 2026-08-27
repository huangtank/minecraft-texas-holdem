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
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** /holdemadmin setup|start|reset|rebuy|chips - table setup and rescue tools for operators. */
public final class HoldemAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> TOP_LEVEL = List.of("setup", "start", "reset", "rebuy", "chips");
    private static final List<String> SETUP_ARGS = List.of("center", "status");
    private static final List<String> REBUY_MODES = List.of("AUTO", "DISABLED", "ADMIN_ONLY");
    private static final List<String> CHIPS_ARGS = List.of("give", "set");

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
            sendTopLevelUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setup" -> handleSetup(sender, args);
            case "start" -> handleStart(sender);
            case "reset" -> {
                table.forceReset();
                sender.sendMessage(Component.text(display.isBuilt()
                        ? "已重置牌桌。"
                        : "已重置牌桌狀態（提醒：牌桌中心點還沒設定，用 /holdemadmin setup center 設定）。"));
            }
            case "rebuy" -> handleRebuy(sender, args);
            case "chips" -> handleChips(sender, args);
            default -> sendTopLevelUsage(sender);
        }
        return true;
    }

    private void sendTopLevelUsage(CommandSender sender) {
        sender.sendMessage(Component.text(String.join("\n",
                "德州撲克管理指令：",
                "  /holdemadmin setup center   站在要放牌桌中心（公共牌）的位置，設定好會自動生成 1~"
                        + layout.seatCount() + " 號座位",
                "  /holdemadmin setup status   查看牌桌設定狀態",
                "  /holdemadmin start          強制開始新的一局（需要 ≥2 人入座、牌桌已設定）",
                "  /holdemadmin reset          卡關時強制重置，退還這局已下注的籌碼",
                "  /holdemadmin rebuy <AUTO|DISABLED|ADMIN_ONLY>   切換籌碼歸零後的補充規則",
                "  /holdemadmin chips <give|set> <玩家> <金額>     調整玩家籌碼")));
    }

    private void handleSetup(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("這個指令只能由玩家使用（需要站在要設定的位置上）。"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法：/holdemadmin setup <center|status>"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "center" -> {
                layout.setCenter(player.getLocation());
                layoutStorage.save(layout);
                display.rebuild(layout);
                sender.sendMessage(Component.text("已把牌桌中心設定在你目前的位置與朝向，"
                        + layout.seatCount() + " 個座位已自動生成在四周並面向中心。"));
            }
            case "status" -> {
                String centerStatus = layout.center() != null ? "已設定（" + layout.seatCount() + " 個座位已自動生成）" : "未設定";
                sender.sendMessage(Component.text("牌桌設定狀態：中心點 " + centerStatus
                        + "\n用 /holdemadmin setup center 站到想放桌子的地方即可重新設定。"));
            }
            default -> sender.sendMessage(Component.text("用法：/holdemadmin setup <center|status>"));
        }
    }

    private void handleStart(CommandSender sender) {
        if (!display.isBuilt()) {
            sender.sendMessage(Component.text("牌桌還沒設定完成，請先用 /holdemadmin setup center 設定中心點。"));
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
            sender.sendMessage(Component.text("目前模式：" + config.rebuyMode()
                    + "。用法：/holdemadmin rebuy <AUTO|DISABLED|ADMIN_ONLY>"
                    + "\nAUTO=自動補回起始籌碼，DISABLED=不能再要，ADMIN_ONLY=需要管理員手動補"));
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
            sender.sendMessage(Component.text("用法：/holdemadmin chips <give|set> <玩家> <金額>"
                    + "\ngive=在現有籌碼上加上這個金額，set=直接把籌碼設成這個金額"));
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], TOP_LEVEL);
        }
        return switch (args[0].toLowerCase()) {
            case "setup" -> args.length == 2 ? partial(args[1], SETUP_ARGS) : List.of();
            case "rebuy" -> args.length == 2 ? partial(args[1], REBUY_MODES) : List.of();
            case "chips" -> switch (args.length) {
                case 2 -> partial(args[1], CHIPS_ARGS);
                case 3 -> partial(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                case 4 -> List.of("100", "1000", "10000");
                default -> List.of();
            };
            default -> List.of();
        };
    }

    private List<String> partial(String token, List<String> options) {
        List<String> matches = new ArrayList<>();
        StringUtil.copyPartialMatches(token, options, matches);
        return matches.stream().sorted().collect(Collectors.toList());
    }
}
