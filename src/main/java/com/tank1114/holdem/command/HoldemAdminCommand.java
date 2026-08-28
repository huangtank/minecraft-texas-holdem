package com.tank1114.holdem.command;

import com.tank1114.holdem.config.HoldemConfig;
import com.tank1114.holdem.game.RebuyMode;
import com.tank1114.holdem.table.TableManager;
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

/** /holdemadmin table|start|reset|pause|resume|rebuy|chips - multi-table setup and rescue tools for operators. */
public final class HoldemAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> TOP_LEVEL = List.of("table", "start", "reset", "pause", "resume", "rebuy", "chips");
    private static final List<String> TABLE_ARGS = List.of("create", "list", "delete", "cleanup");
    private static final List<String> REBUY_MODES = List.of("AUTO", "DISABLED", "ADMIN_ONLY");
    private static final List<String> CHIPS_ARGS = List.of("give", "set");

    private final TableManager tableManager;
    private final HoldemConfig config;

    public HoldemAdminCommand(TableManager tableManager, HoldemConfig config) {
        this.tableManager = tableManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendTopLevelUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "table" -> handleTable(sender, args);
            case "start" -> handleTableTargeted(sender, args, "start", tableManager::startTable, "已強制開始新的一局（需要至少 2 位玩家入座）。");
            case "reset" -> handleTableTargeted(sender, args, "reset", tableManager::resetTable, "已重置這桌，這一局的下注已全數退還。");
            case "pause" -> handleTableTargeted(sender, args, "pause", tableManager::pauseTable, "已暫停這桌。");
            case "resume" -> handleTableTargeted(sender, args, "resume", tableManager::resumeTable, "已恢復這桌。");
            case "rebuy" -> handleRebuy(sender, args);
            case "chips" -> handleChips(sender, args);
            default -> sendTopLevelUsage(sender);
        }
        return true;
    }

    private void sendTopLevelUsage(CommandSender sender) {
        sender.sendMessage(Component.text(String.join("\n",
                "德州撲克管理指令：",
                "  /holdemadmin table create [編號]  站在要放新牌桌中心（公共牌）的位置，建立一張新桌並自動生成座位；不給編號就自動從 1 開始補空缺",
                "  /holdemadmin table list          列出所有牌桌的編號、座標與狀態",
                "  /holdemadmin table delete <編號>  強制結束該桌、退還所有籌碼並徹底刪除這張桌子",
                "  /holdemadmin table cleanup       清除舊版單桌測試留下的殘留牌桌物件（不會動到現有的桌子）",
                "  /holdemadmin start <編號>        強制開始新的一局（需要 ≥2 人入座）",
                "  /holdemadmin reset <編號>        卡關時強制重置該桌，退還這局已下注的籌碼",
                "  /holdemadmin pause <編號>        暫停該桌，沒人能行動、計時器停止，但這局不會作廢",
                "  /holdemadmin resume <編號>       恢復被暫停的桌子",
                "  /holdemadmin rebuy <AUTO|DISABLED|ADMIN_ONLY>   切換籌碼歸零後的補充規則",
                "  /holdemadmin chips <give|set> <玩家> <金額>     調整玩家籌碼")));
    }

    private void handleTable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法：/holdemadmin table <create [編號]|list|delete <編號>|cleanup>"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("這個指令只能由玩家使用（需要站在要設定的位置上）。"));
                    return;
                }
                Integer requestedId = null;
                if (args.length >= 3) {
                    requestedId = parseId(sender, args[2]);
                    if (requestedId == null) {
                        return;
                    }
                }
                sender.sendMessage(Component.text(tableManager.createTable(player.getLocation(), requestedId)));
            }
            case "list" -> sender.sendMessage(Component.text(tableManager.describeAll()));
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("用法：/holdemadmin table delete <編號>"));
                    return;
                }
                Integer id = parseId(sender, args[2]);
                if (id == null) {
                    return;
                }
                sender.sendMessage(Component.text(tableManager.deleteTable(id)));
            }
            case "cleanup" -> sender.sendMessage(Component.text(tableManager.cleanupOrphans()));
            default -> sender.sendMessage(Component.text("用法：/holdemadmin table <create [編號]|list|delete <編號>|cleanup>"));
        }
    }

    private void handleTableTargeted(CommandSender sender, String[] args, String subcommand,
                                      java.util.function.IntFunction<String> action, String successMessage) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("用法：/holdemadmin " + subcommand + " <編號>"));
            return;
        }
        Integer id = parseId(sender, args[1]);
        if (id == null) {
            return;
        }
        String error = action.apply(id);
        sender.sendMessage(Component.text(error != null ? error : successMessage));
    }

    private Integer parseId(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("桌子編號必須是數字。"));
            return null;
        }
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
            finalAmount = tableManager.balanceOf(target.getUniqueId()) + amount;
        }
        String error = tableManager.adminSetChips(target.getUniqueId(), finalAmount);
        if (error != null) {
            sender.sendMessage(Component.text(error));
        } else {
            sender.sendMessage(Component.text("已把 " + target.getName() + " 的籌碼設為 " + finalAmount + "。"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], TOP_LEVEL);
        }
        return switch (args[0].toLowerCase()) {
            case "table" -> args.length == 2 ? partial(args[1], TABLE_ARGS) : List.of();
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
