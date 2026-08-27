package com.tank1114.holdem.display;

import com.tank1114.holdem.engine.Card;
import com.tank1114.holdem.layout.TableLayout;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every entity that makes the fixed poker table physically visible in the world:
 * per-seat "chair" interaction hitboxes, per-seat hidden hole-card item displays,
 * and the shared community-card item displays.
 */
public final class TableDisplayManager {

    private static final double SEAT_CARD_FORWARD = 0.55;
    private static final double SEAT_CARD_UP = 1.15;
    private static final double SEAT_CARD_SPACING = 0.18;
    private static final double COMMUNITY_UP = 1.25;
    private static final double COMMUNITY_SPACING = 0.32;
    private static final float CARD_SCALE = 0.45f;
    private static final double MANAGED_ENTITY_SEARCH_RADIUS = 12.0;

    private final Plugin plugin;
    private final NamespacedKey managedKey;
    private final NamespacedKey seatIndexKey;

    private Interaction[] seatMarkers;
    private ItemDisplay[][] holeCardDisplays; // [seat][0 or 1]
    private ItemDisplay[] communityCardDisplays; // size 5
    private final Map<UUID, Integer> seatMarkerToSeat = new HashMap<>();

    public TableDisplayManager(Plugin plugin) {
        this.plugin = plugin;
        this.managedKey = new NamespacedKey(plugin, "managed");
        this.seatIndexKey = new NamespacedKey(plugin, "seat-index");
    }

    public Integer seatForMarker(UUID entityId) {
        return seatMarkerToSeat.get(entityId);
    }

    /** Wipes any previously spawned table entities near the layout and spawns fresh ones from it. */
    public void rebuild(TableLayout layout) {
        teardown();
        if (!layout.isComplete()) {
            return;
        }

        removeStaleManagedEntities(layout.center());

        int seatCount = layout.seatCount();
        seatMarkers = new Interaction[seatCount];
        holeCardDisplays = new ItemDisplay[seatCount][2];
        communityCardDisplays = new ItemDisplay[5];
        seatMarkerToSeat.clear();

        for (int i = 0; i < seatCount; i++) {
            Location seatLoc = layout.seat(i);
            seatMarkers[i] = spawnSeatMarker(seatLoc, i);
            seatMarkerToSeat.put(seatMarkers[i].getUniqueId(), i);

            Vector right = rightVector(seatLoc);
            Location base = seatLoc.clone().add(0, SEAT_CARD_UP, 0)
                    .add(seatLoc.getDirection().setY(0).normalize().multiply(SEAT_CARD_FORWARD));
            holeCardDisplays[i][0] = spawnCardDisplay(base.clone().subtract(right.clone().multiply(SEAT_CARD_SPACING)));
            holeCardDisplays[i][1] = spawnCardDisplay(base.clone().add(right.clone().multiply(SEAT_CARD_SPACING)));
            hideFromEveryone(holeCardDisplays[i][0]);
            hideFromEveryone(holeCardDisplays[i][1]);
        }

        Location center = layout.center();
        Vector right = rightVector(center);
        Location communityBase = center.clone().add(0, COMMUNITY_UP, 0);
        for (int i = 0; i < 5; i++) {
            double offset = (i - 2) * COMMUNITY_SPACING;
            communityCardDisplays[i] = spawnCardDisplay(communityBase.clone().add(right.clone().multiply(offset)));
        }
    }

    /** Removes every entity this plugin previously tagged within range of the table, in case of a stale reload. */
    private void removeStaleManagedEntities(Location center) {
        for (Entity entity : center.getWorld().getNearbyEntities(center, MANAGED_ENTITY_SEARCH_RADIUS,
                MANAGED_ENTITY_SEARCH_RADIUS, MANAGED_ENTITY_SEARCH_RADIUS)) {
            if (entity.getPersistentDataContainer().has(managedKey, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }

    public void teardown() {
        if (seatMarkers != null) {
            for (Interaction marker : seatMarkers) {
                if (marker != null && marker.isValid()) {
                    marker.remove();
                }
            }
        }
        if (holeCardDisplays != null) {
            for (ItemDisplay[] pair : holeCardDisplays) {
                for (ItemDisplay display : pair) {
                    if (display != null && display.isValid()) {
                        display.remove();
                    }
                }
            }
        }
        if (communityCardDisplays != null) {
            for (ItemDisplay display : communityCardDisplays) {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
        }
        seatMarkerToSeat.clear();
        seatMarkers = null;
        holeCardDisplays = null;
        communityCardDisplays = null;
    }

    public boolean isBuilt() {
        return seatMarkers != null;
    }

    public void setHoleCards(int seatIndex, List<Card> cards, Player owner) {
        if (holeCardDisplays == null) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            ItemDisplay display = holeCardDisplays[seatIndex][i];
            Card card = i < cards.size() ? cards.get(i) : null;
            applyCard(display, card);
        }
        for (ItemDisplay display : holeCardDisplays[seatIndex]) {
            hideFromEveryone(display);
            owner.showEntity(plugin, display);
        }
    }

    public void clearHoleCards(int seatIndex) {
        if (holeCardDisplays == null) {
            return;
        }
        for (ItemDisplay display : holeCardDisplays[seatIndex]) {
            applyCard(display, null);
            hideFromEveryone(display);
        }
    }

    /** Shows a seat's hole cards to everyone online, used at showdown. */
    public void revealHoleCards(int seatIndex) {
        if (holeCardDisplays == null) {
            return;
        }
        for (ItemDisplay display : holeCardDisplays[seatIndex]) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showEntity(plugin, display);
            }
        }
    }

    public void setCommunityCard(int index, Card card) {
        if (communityCardDisplays == null) {
            return;
        }
        applyCard(communityCardDisplays[index], card);
    }

    public void clearCommunityCards() {
        if (communityCardDisplays == null) {
            return;
        }
        for (ItemDisplay display : communityCardDisplays) {
            applyCard(display, null);
        }
    }

    /** Re-applies hidden/shown state for one player, e.g. after they (re)join or teleport. */
    public void reapplyVisibility(Player player, int occupiedSeatOfPlayer) {
        if (holeCardDisplays == null) {
            return;
        }
        for (int seat = 0; seat < holeCardDisplays.length; seat++) {
            for (ItemDisplay display : holeCardDisplays[seat]) {
                if (display == null || !display.isValid()) {
                    continue;
                }
                if (seat == occupiedSeatOfPlayer) {
                    player.showEntity(plugin, display);
                } else {
                    player.hideEntity(plugin, display);
                }
            }
        }
    }

    private void hideFromEveryone(ItemDisplay display) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideEntity(plugin, display);
        }
    }

    private void applyCard(ItemDisplay display, Card card) {
        if (card == null) {
            display.setItemStack(new ItemStack(Material.AIR));
            display.customName(null);
            display.setCustomNameVisible(false);
            return;
        }
        ItemStack item = new ItemStack(Material.PAPER);
        display.setItemStack(item);
        display.customName(Component.text(card.display()));
        display.setCustomNameVisible(true);
    }

    private Interaction spawnSeatMarker(Location location, int seatIndex) {
        Interaction interaction = location.getWorld().spawn(location, Interaction.class, entity -> {
            entity.setInteractionWidth(0.8f);
            entity.setInteractionHeight(1.9f);
            entity.setPersistent(true);
            entity.getPersistentDataContainer().set(managedKey, PersistentDataType.BYTE, (byte) 1);
            entity.getPersistentDataContainer().set(seatIndexKey, PersistentDataType.INTEGER, seatIndex);
        });
        return interaction;
    }

    private ItemDisplay spawnCardDisplay(Location location) {
        return location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setItemStack(new ItemStack(Material.AIR));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(true);
            entity.getPersistentDataContainer().set(managedKey, PersistentDataType.BYTE, (byte) 1);
            entity.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(),
                    new Vector3f(CARD_SCALE, CARD_SCALE, CARD_SCALE),
                    new Quaternionf()
            ));
        });
    }

    private Vector rightVector(Location location) {
        Vector facing = location.getDirection().setY(0);
        if (facing.lengthSquared() < 1.0e-4) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        return new Vector(-facing.getZ(), 0, facing.getX()).normalize();
    }
}
