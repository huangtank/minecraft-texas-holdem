package com.tank1114.holdem.ui;

import com.tank1114.holdem.game.GameStage;
import com.tank1114.holdem.game.PlayerAction;
import com.tank1114.holdem.game.PokerTable;
import com.tank1114.holdem.game.Seat;
import com.tank1114.holdem.layout.TableLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Two separate UIs live here, both restricted to just the one player they're for:
 * <ul>
 *   <li>The turn-action menu (fold/check/call/bet/raise/all-in, and the bet-amount submenu) is
 *   handed to the acting player as a temporary set of named hotbar items - right-clicking one
 *   performs that action. Their real hotbar is snapshotted first and restored the moment the
 *   menu closes (action resolved, turn moves on, they disconnect, etc).</li>
 *   <li>The persistent "離開座位" button is a floating hologram (a {@link TextDisplay} label
 *   paired with an invisible {@link Interaction} hitbox) above a seat for as long as it's
 *   occupied, hidden from everyone but that seat's own player via the same {@code hideEntity}
 *   trick used for hole cards.</li>
 * </ul>
 * Only one seat can ever be acting at a time, so a single "current actor" is enough for the
 * item menu; the leave button is independent of whose turn it is, so it's tracked per seat.
 */
public final class TurnHologramMenu implements Listener, PokerTable.TurnUi {

    private static final double LEAVE_BUTTON_UP = 2.9;
    // The seat's own click hitbox (see TableDisplayManager#spawnSeatMarker) is 1.0 wide, i.e. it
    // reaches 0.5 out from the seat's center in every direction. A button hitbox is 0.5 wide (0.25
    // half-width), so this offset has to clear 0.5 + 0.25 to sit fully outside the seat's own
    // footprint - otherwise clicking the seat can hit the button instead (or vice versa).
    private static final double LEAVE_BUTTON_BACKWARD = 1.0;

    private final Plugin plugin;
    private final PokerTable table;
    private final TableLayout layout;
    private final BetAmountChatListener chatListener;
    private final NamespacedKey actionTagKey;

    // ---- turn-action item menu: only ever one acting player at a time ----
    private UUID currentActorUuid;
    private ItemStack[] savedHotbar;
    private int currentSeat = -1;
    private final Map<String, BiConsumer<Player, Integer>> itemHandlers = new HashMap<>();

    // ---- persistent "leave seat" hologram: one per occupied seat, independent of turn order ----
    private final Map<Integer, List<Entity>> leaveEntities = new HashMap<>();
    private final Map<UUID, UUID> leaveButtonOwner = new HashMap<>();

    public TurnHologramMenu(Plugin plugin, PokerTable table, TableLayout layout, BetAmountChatListener chatListener) {
        this.plugin = plugin;
        this.table = table;
        this.layout = layout;
        this.chatListener = chatListener;
        this.actionTagKey = new NamespacedKey(plugin, "holdem-action-token");
    }

    @Override
    public void promptTurn(Player player, int seatIndex) {
        openActionRow(player, seatIndex);
    }

    @Override
    public void clear() {
        despawnItems();
    }

    /** Reopens the action row, e.g. when a player right-clicks their own seat mid-turn. */
    public void open(Player player, int seatIndex) {
        openActionRow(player, seatIndex);
    }

    private void openActionRow(Player player, int seatIndex) {
        Seat seat = table.seat(seatIndex);
        Sizing sizing = Sizing.of(table, seat);

        List<ButtonSpec> buttons = new ArrayList<>();
        buttons.add(new ButtonSpec(Material.BARRIER, "棄牌",
                (p, s) -> resolve(p, table.performAction(p.getUniqueId(), PlayerAction.FOLD, 0))));
        buttons.add(new ButtonSpec(Material.CARROT_ON_A_STICK,
                sizing.toCall() <= 0 ? "過牌" : "跟注 " + Math.min(sizing.toCall(), seat.stack()),
                (p, s) -> resolve(p, table.performAction(p.getUniqueId(),
                        sizing.toCall() <= 0 ? PlayerAction.CHECK : PlayerAction.CALL, 0))));
        if (sizing.canRaise()) {
            buttons.add(new ButtonSpec(Material.GOLD_INGOT, sizing.opening() ? "下注" : "加注",
                    (p, s) -> openAmountRow(p, s)));
        }
        buttons.add(new ButtonSpec(Material.NETHER_STAR, "全下 " + sizing.stackCap(),
                (p, s) -> resolve(p, table.performAction(p.getUniqueId(), PlayerAction.ALL_IN, 0))));

        showItems(player, seatIndex, buttons);
    }

    private void openAmountRow(Player player, int seatIndex) {
        Seat seat = table.seat(seatIndex);
        Sizing sizing = Sizing.of(table, seat);
        PlayerAction action = sizing.opening() ? PlayerAction.BET : PlayerAction.RAISE;

        List<ButtonSpec> buttons = new ArrayList<>();
        for (int mult : new int[] {1, 2, 3}) {
            long amount = clamp(mult * table.bigBlind(), sizing);
            buttons.add(new ButtonSpec(Material.GOLD_NUGGET, mult + "BB（" + amount + "）",
                    (p, s) -> resolve(p, table.performAction(p.getUniqueId(), action, amount))));
        }
        buttons.add(new ButtonSpec(Material.EMERALD, "底池 " + sizing.potTarget(),
                (p, s) -> resolve(p, table.performAction(p.getUniqueId(), action, sizing.potTarget()))));
        buttons.add(new ButtonSpec(Material.PAPER, "自訂金額", (p, s) -> {
            despawnItems();
            chatListener.requestAmount(p, action, table);
        }));
        buttons.add(new ButtonSpec(Material.ARROW, "返回", this::openActionRow));

        showItems(player, seatIndex, buttons);
    }

    private long clamp(long amount, Sizing sizing) {
        return Math.max(sizing.legalMin(), Math.min(amount, sizing.stackCap()));
    }

    /** Shared tail for a leaf action button: only tears the menu down once the action actually succeeded. */
    private void resolve(Player player, String error) {
        if (error != null) {
            player.sendMessage(Component.text(error));
        } else {
            despawnItems();
        }
    }

    /**
     * Replaces the acting player's hotbar with one named item per button; right-clicking an item
     * performs that action (see {@link #onItemInteract}). Always restores whoever held the menu
     * before (possibly this same player mid-turn, switching between the action row and the amount
     * submenu) before taking a fresh snapshot, so the snapshot never accidentally captures our own
     * placeholder items as if they were the player's real inventory.
     */
    private void showItems(Player player, int seatIndex, List<ButtonSpec> buttons) {
        despawnItems();

        PlayerInventory inventory = player.getInventory();
        savedHotbar = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack existing = inventory.getItem(i);
            savedHotbar[i] = existing == null ? null : existing.clone();
        }
        currentActorUuid = player.getUniqueId();

        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, i < buttons.size() ? buildActionItem(buttons.get(i)) : null);
        }
        currentSeat = seatIndex;
    }

    /** Restores the current actor's real hotbar (if they're online) and clears all menu state. */
    private void despawnItems() {
        if (currentActorUuid != null) {
            Player actor = Bukkit.getPlayer(currentActorUuid);
            if (actor != null && savedHotbar != null) {
                PlayerInventory inventory = actor.getInventory();
                for (int i = 0; i < 9; i++) {
                    inventory.setItem(i, savedHotbar[i]);
                }
            }
        }
        currentActorUuid = null;
        savedHotbar = null;
        itemHandlers.clear();
        currentSeat = -1;
    }

    private ItemStack buildActionItem(ButtonSpec spec) {
        String tag = UUID.randomUUID().toString();
        itemHandlers.put(tag, spec.handler());

        ItemStack item = new ItemStack(spec.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(spec.label()).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(actionTagKey, PersistentDataType.STRING, tag);
        item.setItemMeta(meta);
        return item;
    }

    /** Removes any open item menu and leave buttons immediately, used when the plugin shuts down or a table is deleted. */
    public void teardown() {
        despawnItems();
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
            despawnItems();
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

    /** Right-clicking one of the temporary action items in hand performs the action it's tagged with. */
    @EventHandler
    public void onItemInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        String tag = item.getItemMeta().getPersistentDataContainer().get(actionTagKey, PersistentDataType.STRING);
        if (tag == null) {
            return;
        }
        event.setCancelled(true);

        BiConsumer<Player, Integer> handler = itemHandlers.get(tag);
        if (handler == null) {
            return;
        }
        Player player = event.getPlayer();
        if (currentSeat < 0 || table.stage() == GameStage.WAITING || table.stage() == GameStage.SHOWDOWN
                || table.actingSeatIndex() != currentSeat
                || table.seat(currentSeat).occupant() == null
                || !table.seat(currentSeat).occupant().equals(player.getUniqueId())) {
            despawnItems();
            return;
        }
        handler.accept(player, currentSeat);
    }

    /** A dropped action item would otherwise litter the ground with a confusing, permanently-usable button. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(actionTagKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    /**
     * The server saves whatever's in a player's inventory the moment they quit - if the acting
     * player disconnects mid-turn without this, their temporary action items would be saved into
     * their real inventory permanently. Restoring here runs before that save happens.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(currentActorUuid)) {
            despawnItems();
        }
    }

    @EventHandler
    public void onLeaveButtonInteract(PlayerInteractEntityEvent event) {
        UUID leaveOwner = leaveButtonOwner.get(event.getRightClicked().getUniqueId());
        if (leaveOwner == null) {
            return;
        }
        event.setCancelled(true);
        if (!leaveOwner.equals(event.getPlayer().getUniqueId())) {
            return;
        }
        String error = table.requestLeave(leaveOwner);
        if (error != null) {
            event.getPlayer().sendMessage(Component.text(error));
        }
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

    private record ButtonSpec(Material material, String label, BiConsumer<Player, Integer> handler) {
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
