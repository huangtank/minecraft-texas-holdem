package com.tank1114.holdem.ui;

import com.tank1114.holdem.game.GameStage;
import com.tank1114.holdem.game.PlayerAction;
import com.tank1114.holdem.game.PokerTable;
import com.tank1114.holdem.game.Seat;
import com.tank1114.holdem.layout.TableLayout;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Replaces slash-command AND inventory-GUI betting with a set of clickable holograms
 * (a {@link TextDisplay} label paired with an invisible {@link Interaction} hitbox) that
 * float in the air above the acting player's seat. Only that player can see or click them,
 * the same way hole cards are hidden from everyone else. Only one seat can ever be acting
 * at a time, so a single set of "current" entities is enough - a new prompt (or a hand
 * ending without one) always tears down whatever was floating before it.
 */
public final class TurnHologramMenu implements Listener, PokerTable.TurnUi {

    private static final double BUTTON_TOP = 2.9;
    private static final double BUTTON_FORWARD = 0.3;
    private static final double BUTTON_VSPACING = 0.35;
    private static final double LEAVE_BUTTON_UP = 2.9;
    private static final double LEAVE_BUTTON_BACKWARD = 0.3;

    private final Plugin plugin;
    private final PokerTable table;
    private final TableLayout layout;
    private final BetAmountChatListener chatListener;

    private final List<Entity> currentEntities = new ArrayList<>();
    private final Map<UUID, BiConsumer<Player, Integer>> handlers = new HashMap<>();
    private int currentSeat = -1;

    private final Map<Integer, List<Entity>> leaveEntities = new HashMap<>();
    private final Map<UUID, UUID> leaveButtonOwner = new HashMap<>();

    public TurnHologramMenu(Plugin plugin, PokerTable table, TableLayout layout, BetAmountChatListener chatListener) {
        this.plugin = plugin;
        this.table = table;
        this.layout = layout;
        this.chatListener = chatListener;
    }

    @Override
    public void promptTurn(Player player, int seatIndex) {
        openActionRow(player, seatIndex);
    }

    @Override
    public void clear() {
        despawn();
    }

    /** Reopens the action row, e.g. when a player right-clicks their own seat mid-turn. */
    public void open(Player player, int seatIndex) {
        openActionRow(player, seatIndex);
    }

    private void openActionRow(Player player, int seatIndex) {
        despawn();
        Seat seat = table.seat(seatIndex);
        Sizing sizing = Sizing.of(table, seat);

        List<ButtonSpec> buttons = new ArrayList<>();
        buttons.add(new ButtonSpec("棄牌",
                (p, s) -> resolve(p, table.performAction(p.getUniqueId(), PlayerAction.FOLD, 0))));
        buttons.add(new ButtonSpec(sizing.toCall() <= 0 ? "看牌" : "跟注 " + Math.min(sizing.toCall(), seat.stack()),
                (p, s) -> resolve(p, table.performAction(p.getUniqueId(),
                        sizing.toCall() <= 0 ? PlayerAction.CHECK : PlayerAction.CALL, 0))));
        if (sizing.canRaise()) {
            buttons.add(new ButtonSpec(sizing.opening() ? "下注" : "加注",
                    (p, s) -> openAmountRow(p, s)));
        }
        buttons.add(new ButtonSpec("全下 " + sizing.stackCap(),
                (p, s) -> resolve(p, table.performAction(p.getUniqueId(), PlayerAction.ALL_IN, 0))));

        spawnRow(player, seatIndex, buttons);
    }

    private void openAmountRow(Player player, int seatIndex) {
        despawn();
        Seat seat = table.seat(seatIndex);
        Sizing sizing = Sizing.of(table, seat);
        PlayerAction action = sizing.opening() ? PlayerAction.BET : PlayerAction.RAISE;

        List<ButtonSpec> buttons = new ArrayList<>();
        for (int mult : new int[] {1, 2, 3}) {
            long amount = clamp(mult * table.bigBlind(), sizing);
            buttons.add(new ButtonSpec(mult + "BB（" + amount + "）",
                    (p, s) -> resolve(p, table.performAction(p.getUniqueId(), action, amount))));
        }
        buttons.add(new ButtonSpec("底池 " + sizing.potTarget(),
                (p, s) -> resolve(p, table.performAction(p.getUniqueId(), action, sizing.potTarget()))));
        buttons.add(new ButtonSpec("自訂金額", (p, s) -> {
            despawn();
            chatListener.requestAmount(p, action, table);
        }));
        buttons.add(new ButtonSpec("返回", this::openActionRow));

        spawnRow(player, seatIndex, buttons);
    }

    private long clamp(long amount, Sizing sizing) {
        return Math.max(sizing.legalMin(), Math.min(amount, sizing.stackCap()));
    }

    /** Shared tail for a leaf action button: only tears the menu down once the action actually succeeded. */
    private void resolve(Player player, String error) {
        if (error != null) {
            player.sendMessage(Component.text(error));
        } else {
            despawn();
        }
    }

    /** Buttons stack vertically (top to bottom) directly above the seat, so a wide amount menu never spills sideways into a neighboring seat. */
    private void spawnRow(Player player, int seatIndex, List<ButtonSpec> buttons) {
        Location seatLoc = layout.seat(seatIndex);
        if (seatLoc == null) {
            return;
        }
        Location top = seatLoc.clone().add(0, BUTTON_TOP, 0)
                .add(seatLoc.getDirection().setY(0).normalize().multiply(BUTTON_FORWARD));

        for (int i = 0; i < buttons.size(); i++) {
            Location loc = top.clone().add(0, -i * BUTTON_VSPACING, 0);
            ButtonSpec spec = buttons.get(i);

            TextDisplay label = spawnLabel(loc, spec.label());
            Interaction hitbox = spawnHitbox(loc);
            hideFromEveryoneExcept(label, player);
            hideFromEveryoneExcept(hitbox, player);

            currentEntities.add(label);
            currentEntities.add(hitbox);
            handlers.put(hitbox.getUniqueId(), spec.handler());
        }
        currentSeat = seatIndex;
    }

    private void despawn() {
        for (Entity entity : currentEntities) {
            handlers.remove(entity.getUniqueId());
            if (entity.isValid()) {
                entity.remove();
            }
        }
        currentEntities.clear();
        handlers.clear();
        currentSeat = -1;
    }

    /** Removes any floating menu and leave buttons immediately, used when the plugin shuts down or a table is deleted. */
    public void teardown() {
        despawn();
        for (int seatIndex : new ArrayList<>(leaveEntities.keySet())) {
            despawnLeave(seatIndex);
        }
    }

    /**
     * A "離開座位" button that floats above a seat for as long as it's occupied, independent of
     * whose turn it is. Standing up used to be a click on the chair itself, but that was too easy
     * to trigger by accident - this is now the only way to leave, same visibility trick as everything else.
     */
    @Override
    public void seatOccupied(Player player, int seatIndex) {
        despawnLeave(seatIndex);
        Location seatLoc = layout.seat(seatIndex);
        if (seatLoc == null) {
            return;
        }
        Location loc = seatLoc.clone().add(0, LEAVE_BUTTON_UP, 0)
                .add(seatLoc.getDirection().setY(0).normalize().multiply(-LEAVE_BUTTON_BACKWARD));

        TextDisplay label = spawnLabel(loc, "離開座位");
        Interaction hitbox = spawnHitbox(loc);
        hideFromEveryoneExcept(label, player);
        hideFromEveryoneExcept(hitbox, player);

        List<Entity> entities = new ArrayList<>();
        entities.add(label);
        entities.add(hitbox);
        leaveEntities.put(seatIndex, entities);
        leaveButtonOwner.put(hitbox.getUniqueId(), player.getUniqueId());
    }

    @Override
    public void seatVacated(int seatIndex) {
        despawnLeave(seatIndex);
        if (currentSeat == seatIndex) {
            despawn();
        }
    }

    private void despawnLeave(int seatIndex) {
        List<Entity> entities = leaveEntities.remove(seatIndex);
        if (entities == null) {
            return;
        }
        for (Entity entity : entities) {
            leaveButtonOwner.remove(entity.getUniqueId());
            if (entity.isValid()) {
                entity.remove();
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        UUID clickedId = event.getRightClicked().getUniqueId();

        UUID leaveOwner = leaveButtonOwner.get(clickedId);
        if (leaveOwner != null) {
            event.setCancelled(true);
            if (!leaveOwner.equals(event.getPlayer().getUniqueId())) {
                return;
            }
            String error = table.requestLeave(leaveOwner);
            if (error != null) {
                event.getPlayer().sendMessage(Component.text(error));
            }
            return;
        }

        BiConsumer<Player, Integer> handler = handlers.get(clickedId);
        if (handler == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (currentSeat < 0 || table.stage() == GameStage.WAITING || table.stage() == GameStage.SHOWDOWN
                || table.actingSeatIndex() != currentSeat
                || table.seat(currentSeat).occupant() == null
                || !table.seat(currentSeat).occupant().equals(player.getUniqueId())) {
            despawn();
            return;
        }
        handler.accept(player, currentSeat);
    }

    private TextDisplay spawnLabel(Location location, String text) {
        return location.getWorld().spawn(location, TextDisplay.class, entity -> {
            entity.text(Component.text(text));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
            entity.setSeeThrough(false);
            entity.setShadowed(true);
            entity.setPersistent(false);
        });
    }

    private Interaction spawnHitbox(Location location) {
        return location.getWorld().spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(0.5f);
            entity.setInteractionHeight(0.35f);
            entity.setPersistent(false);
        });
    }

    private void hideFromEveryoneExcept(Entity entity, Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.hideEntity(plugin, entity);
            }
        }
    }

    private record ButtonSpec(String label, BiConsumer<Player, Integer> handler) {
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
