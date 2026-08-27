package com.tank1114.holdem.ui;

import com.tank1114.holdem.game.GameStage;
import com.tank1114.holdem.game.PlayerAction;
import com.tank1114.holdem.game.PokerTable;
import com.tank1114.holdem.game.Seat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Replaces slash-command betting with a clickable GUI: this is what pops up whenever it's a
 * seated player's turn (and can be reopened by right-clicking your own seat mid-turn).
 * Fixed preset sizes cover most plays instantly; "自訂金額" hands off to
 * {@link BetAmountChatListener} for an exact number typed in plain chat.
 */
public final class ActionMenuListener implements Listener, PokerTable.TurnUi {

    private static final int SLOT_FOLD = 0;
    private static final int SLOT_CHECK_CALL = 1;
    private static final int SLOT_MIN = 3;
    private static final int SLOT_POT = 4;
    private static final int SLOT_CUSTOM = 5;
    private static final int SLOT_ALL_IN = 7;

    private final PokerTable table;
    private final BetAmountChatListener chatListener;

    public ActionMenuListener(PokerTable table, BetAmountChatListener chatListener) {
        this.table = table;
        this.chatListener = chatListener;
    }

    @Override
    public void promptTurn(Player player, int seatIndex) {
        open(player, seatIndex);
    }

    public void open(Player player, int seatIndex) {
        Seat seat = table.seat(seatIndex);
        Sizing sizing = Sizing.of(table, seat);

        ActionMenuHolder holder = new ActionMenuHolder(seatIndex);
        Inventory inv = Bukkit.createInventory(holder, 9, Component.text("你的回合 - 底池 " + table.potTotal()));
        holder.setInventory(inv);

        inv.setItem(SLOT_FOLD, button(Material.RED_CONCRETE, "棄牌"));
        inv.setItem(SLOT_CHECK_CALL, sizing.toCall() <= 0
                ? button(Material.LIME_CONCRETE, "看牌")
                : button(Material.YELLOW_CONCRETE, "跟注 " + Math.min(sizing.toCall(), seat.stack())));

        if (sizing.canRaise()) {
            String verb = sizing.opening() ? "下注 " : "加注到 ";
            inv.setItem(SLOT_MIN, button(Material.LIGHT_BLUE_CONCRETE, verb + sizing.legalMin()));
            inv.setItem(SLOT_POT, button(Material.BLUE_CONCRETE, verb + sizing.potTarget() + "（底池）"));
            inv.setItem(SLOT_CUSTOM, button(Material.PAPER, "自訂金額（聊天輸入）"));
        }
        inv.setItem(SLOT_ALL_IN, button(Material.ORANGE_CONCRETE, "全下 " + sizing.stackCap()));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ActionMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int seatIndex = holder.seatIndex();
        if (table.stage() == GameStage.WAITING || table.stage() == GameStage.SHOWDOWN
                || table.actingSeatIndex() != seatIndex) {
            player.closeInventory();
            return;
        }

        Seat seat = table.seat(seatIndex);
        Sizing sizing = Sizing.of(table, seat);

        String error = null;
        boolean keepOpen = false;
        switch (event.getSlot()) {
            case SLOT_FOLD -> error = table.performAction(player.getUniqueId(), PlayerAction.FOLD, 0);
            case SLOT_CHECK_CALL -> error = table.performAction(player.getUniqueId(),
                    sizing.toCall() <= 0 ? PlayerAction.CHECK : PlayerAction.CALL, 0);
            case SLOT_MIN -> {
                if (sizing.canRaise()) {
                    error = table.performAction(player.getUniqueId(),
                            sizing.opening() ? PlayerAction.BET : PlayerAction.RAISE, sizing.legalMin());
                }
            }
            case SLOT_POT -> {
                if (sizing.canRaise()) {
                    error = table.performAction(player.getUniqueId(),
                            sizing.opening() ? PlayerAction.BET : PlayerAction.RAISE, sizing.potTarget());
                }
            }
            case SLOT_CUSTOM -> {
                if (sizing.canRaise()) {
                    chatListener.requestAmount(player, sizing.opening() ? PlayerAction.BET : PlayerAction.RAISE);
                    keepOpen = true;
                }
            }
            case SLOT_ALL_IN -> error = table.performAction(player.getUniqueId(), PlayerAction.ALL_IN, 0);
            default -> keepOpen = true;
        }

        if (error != null) {
            player.sendMessage(Component.text(error));
        } else if (!keepOpen) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ActionMenuHolder) {
            event.setCancelled(true);
        }
    }

    private ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    /** Precomputed legal bet/raise sizing for the seat whose turn it currently is. */
    private record Sizing(long toCall, long stackCap, long currentBet, long legalMin, long potTarget) {

        static Sizing of(PokerTable table, Seat seat) {
            long currentBet = table.currentBet();
            long toCall = currentBet - seat.committedThisRound();
            long stackCap = seat.committedThisRound() + seat.stack();
            boolean opening = currentBet == 0;
            long legalMin = Math.min(opening ? table.bigBlind() : currentBet + table.minRaiseIncrement(), stackCap);
            long potGuess = opening ? table.potTotal() : currentBet + table.potTotal();
            long potTarget = Math.max(legalMin, Math.min(potGuess, stackCap));
            return new Sizing(toCall, stackCap, currentBet, legalMin, potTarget);
        }

        boolean opening() {
            return currentBet == 0;
        }

        boolean canRaise() {
            return stackCap > currentBet;
        }
    }
}
