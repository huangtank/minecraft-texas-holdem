package com.tank1114.holdem.command;

import com.tank1114.holdem.game.PlayerAction;
import com.tank1114.holdem.game.PokerTable;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /holdem fold|check|call|bet <amount>|raise <total>|allin|leave - the in-hand player actions. */
public final class HoldemCommand implements CommandExecutor {

    private final PokerTable table;

    public HoldemCommand(PokerTable table) {
        this.table = table;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("這個指令只能由玩家使用。"));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("用法：/holdem <fold|check|call|bet|raise|allin|leave> [金額]"));
            return true;
        }

        String sub = args[0].toLowerCase();
        String error = switch (sub) {
            case "fold" -> table.performAction(player.getUniqueId(), PlayerAction.FOLD, 0);
            case "check" -> table.performAction(player.getUniqueId(), PlayerAction.CHECK, 0);
            case "call" -> table.performAction(player.getUniqueId(), PlayerAction.CALL, 0);
            case "allin" -> table.performAction(player.getUniqueId(), PlayerAction.ALL_IN, 0);
            case "bet" -> withAmount(args, amount -> table.performAction(player.getUniqueId(), PlayerAction.BET, amount));
            case "raise" -> withAmount(args, amount -> table.performAction(player.getUniqueId(), PlayerAction.RAISE, amount));
            case "leave" -> table.requestLeave(player.getUniqueId());
            default -> "不認識的指令，用法：/holdem <fold|check|call|bet|raise|allin|leave> [金額]";
        };

        if (error != null) {
            player.sendMessage(Component.text(error));
        }
        return true;
    }

    private interface AmountAction {
        String apply(long amount);
    }

    private String withAmount(String[] args, AmountAction action) {
        if (args.length < 2) {
            return "請指定金額，例如：/holdem " + args[0] + " 100";
        }
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            return "金額必須是一個整數。";
        }
        return action.apply(amount);
    }
}
