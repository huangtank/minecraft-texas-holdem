package com.tank1114.holdem.display;

import com.tank1114.holdem.engine.Card;
import com.tank1114.holdem.layout.TableLayout;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
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
    /** Height of the tabletop's own flat top surface above the center point. */
    private static final double TABLE_SURFACE_UP = 1.0;
    /** Community cards float just above the tabletop surface so they never render inside it. */
    private static final double COMMUNITY_UP = TABLE_SURFACE_UP + 0.08;
    private static final double COMMUNITY_SPACING = 0.32;
    private static final float CARD_SCALE = 0.45f;
    /** Tabletop radius relative to seat radius - kept well under 1.0 so chairs never overlap the table's edge. */
    private static final double TABLE_RADIUS_RATIO = 0.6;
    private static final double MANAGED_ENTITY_SEARCH_RADIUS = 12.0;
    private static final double AVATAR_UP = 0.3;
    private static final double AVATAR_FORWARD = 0.15;

    private final Plugin plugin;
    private final NamespacedKey managedKey;
    private final NamespacedKey seatIndexKey;

    private Interaction[] seatMarkers;
    private BlockDisplay[] chairProps;
    private BlockDisplay tableProp;
    private ItemDisplay[][] holeCardDisplays; // [seat][0 or 1]
    private ItemDisplay[] communityCardDisplays; // size 5
    private ArmorStand[] occupantAvatars;
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
        chairProps = new BlockDisplay[seatCount];
        holeCardDisplays = new ItemDisplay[seatCount][2];
        communityCardDisplays = new ItemDisplay[5];
        occupantAvatars = new ArmorStand[seatCount];
        seatMarkerToSeat.clear();

        for (int i = 0; i < seatCount; i++) {
            Location seatLoc = layout.seat(i);
            seatMarkers[i] = spawnSeatMarker(seatLoc, i);
            seatMarkerToSeat.put(seatMarkers[i].getUniqueId(), i);
            chairProps[i] = spawnChairProp(seatLoc);

            Vector right = rightVector(seatLoc);
            Location base = seatLoc.clone().add(0, SEAT_CARD_UP, 0)
                    .add(seatLoc.getDirection().setY(0).normalize().multiply(SEAT_CARD_FORWARD));
            holeCardDisplays[i][0] = spawnCardDisplay(base.clone().subtract(right.clone().multiply(SEAT_CARD_SPACING)));
            holeCardDisplays[i][1] = spawnCardDisplay(base.clone().add(right.clone().multiply(SEAT_CARD_SPACING)));
            hideFromEveryone(holeCardDisplays[i][0]);
            hideFromEveryone(holeCardDisplays[i][1]);
        }

        Location center = layout.center();
        double tableRadius = layout.seatCount() > 0 ? center.distance(layout.seat(0)) * TABLE_RADIUS_RATIO : 1.5;
        tableProp = spawnTableProp(center, tableRadius);
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
        if (chairProps != null) {
            for (BlockDisplay chair : chairProps) {
                if (chair != null && chair.isValid()) {
                    chair.remove();
                }
            }
        }
        if (tableProp != null && tableProp.isValid()) {
            tableProp.remove();
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
        if (occupantAvatars != null) {
            for (ArmorStand avatar : occupantAvatars) {
                if (avatar != null && avatar.isValid()) {
                    avatar.remove();
                }
            }
        }
        seatMarkerToSeat.clear();
        seatMarkers = null;
        chairProps = null;
        tableProp = null;
        holeCardDisplays = null;
        communityCardDisplays = null;
        occupantAvatars = null;
    }

    public boolean isBuilt() {
        return seatMarkers != null;
    }

    /**
     * Removes every plugin-tagged entity in every loaded world that isn't within reach of any
     * currently live table's center. Before multi-table support, re-running the setup command
     * at a new spot only ever tracked one center point, so the old spot's chairs/markers/cards
     * were left behind as permanent debris - this sweeps that up regardless of where it ended up,
     * without ever touching a table that's actually still in use. Returns how many were removed.
     */
    public static int removeOrphans(Plugin plugin, List<Location> liveCenters) {
        NamespacedKey key = new NamespacedKey(plugin, "managed");
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                    continue;
                }
                boolean nearLiveTable = false;
                for (Location center : liveCenters) {
                    if (center.getWorld().equals(world)
                            && center.distance(entity.getLocation()) <= MANAGED_ENTITY_SEARCH_RADIUS) {
                        nearLiveTable = true;
                        break;
                    }
                }
                if (!nearLiveTable) {
                    entity.remove();
                    removed++;
                }
            }
        }
        return removed;
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

    /**
     * Spawns a small floating player-head marker sitting on a seat's chair, visible to everyone,
     * so an occupied seat is recognizable at a glance without needing to walk up and check.
     */
    public void setOccupantAvatar(int seatIndex, Player player) {
        if (occupantAvatars == null) {
            return;
        }
        removeOccupantAvatar(seatIndex);
        Location seatLoc = seatMarkers[seatIndex].getLocation();
        Location avatarLoc = seatLoc.clone().add(0, AVATAR_UP, 0)
                .add(seatLoc.getDirection().setY(0).normalize().multiply(AVATAR_FORWARD));
        avatarLoc.setYaw(seatLoc.getYaw());
        occupantAvatars[seatIndex] = avatarLoc.getWorld().spawn(avatarLoc, ArmorStand.class, entity -> {
            entity.setSmall(true);
            entity.setInvisible(true);
            entity.setBasePlate(false);
            entity.setArms(false);
            entity.setMarker(true);
            entity.setPersistent(true);
            entity.getPersistentDataContainer().set(managedKey, PersistentDataType.BYTE, (byte) 1);
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(player);
            skull.setItemMeta(meta);
            entity.getEquipment().setHelmet(skull);
        });
    }

    public void clearOccupantAvatar(int seatIndex) {
        removeOccupantAvatar(seatIndex);
    }

    private void removeOccupantAvatar(int seatIndex) {
        if (occupantAvatars == null) {
            return;
        }
        ArmorStand existing = occupantAvatars[seatIndex];
        if (existing != null && existing.isValid()) {
            existing.remove();
        }
        occupantAvatars[seatIndex] = null;
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
            // Matches the chair's own 1x1 footprint (see spawnChairProp) so the visible stair
            // model sits fully inside its own clickable box instead of poking out past it.
            entity.setInteractionWidth(1.0f);
            entity.setInteractionHeight(1.9f);
            entity.setPersistent(true);
            entity.getPersistentDataContainer().set(managedKey, PersistentDataType.BYTE, (byte) 1);
            entity.getPersistentDataContainer().set(seatIndexKey, PersistentDataType.INTEGER, seatIndex);
            entity.customName(Component.text("第 " + (seatIndex + 1) + " 號座位"));
            entity.setCustomNameVisible(true);
        });
        return interaction;
    }

    /** Purely decorative "chair" so a seat is visible before anyone's ever sat there or a hand's been dealt. */
    private BlockDisplay spawnChairProp(Location seatLoc) {
        return seatLoc.getWorld().spawn(seatLoc, BlockDisplay.class, entity -> {
            Stairs stairs = (Stairs) org.bukkit.Material.OAK_STAIRS.createBlockData();
            // Pin the block's own baked-in model orientation to a known, fixed reference (SOUTH,
            // i.e. local +Z) instead of leaving it at whatever createBlockData()'s undocumented
            // default happens to be. yawRotatedUnitBlock below rotates *from* that known reference,
            // so its math only holds if the baseline is actually pinned down here.
            stairs.setFacing(BlockFace.SOUTH);
            entity.setBlock(stairs);
            entity.setPersistent(true);
            entity.getPersistentDataContainer().set(managedKey, PersistentDataType.BYTE, (byte) 1);
            entity.setTransformation(yawRotatedUnitBlock(seatLoc.getYaw()));
        });
    }

    /**
     * A 1x1x1 block's transformation, rotated in place around its own center to face an exact yaw.
     * Six seats are spaced 60 degrees apart, but {@link org.bukkit.block.data.type.Stairs}'s "facing"
     * block state only offers 4 cardinal directions - snapping to the nearest one would leave most
     * chairs up to 30 degrees off. Rotating the display's transformation instead hits the exact angle.
     *
     * <p>A display's transformation always rotates local points around local origin (0,0,0) - one
     * corner of the unit cube - never its center. To make the block stay centered on the seat's own
     * location (matching where the interaction hitbox above is centered) regardless of yaw, the
     * translation has to land the cube's own center exactly on the entity's position: for local point
     * {@code p}, the final world offset is {@code R*p + T}; solving {@code R*center + T = 0} (the
     * center must map to offset zero, i.e. the entity's own spot) gives {@code T = -R*center}. Only
     * the X/Z half of "center" belongs in that vector, though - a block's local Y already spans 0..1
     * exactly as wanted (sitting on the ground, not centered vertically), so the Y component of
     * "center" must be 0, not 0.5, or every seat's chair ends up pushed sideways by an extra half
     * block in whatever direction that seat's own rotation happens to point.
     */
    private Transformation yawRotatedUnitBlock(float yawDegrees) {
        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(-yawDegrees));
        Vector3f horizontalCenter = new Vector3f(0.5f, 0f, 0.5f);
        Vector3f translation = rotation.transform(new Vector3f(horizontalCenter)).mul(-1f);
        return new Transformation(translation, rotation, new Vector3f(1f, 1f, 1f), new Quaternionf());
    }

    /** Purely decorative "tabletop" under the community cards so the table is visible even with no cards dealt. */
    private BlockDisplay spawnTableProp(Location center, double radius) {
        return center.getWorld().spawn(center, BlockDisplay.class, entity -> {
            entity.setBlock(org.bukkit.Material.SMOOTH_STONE_SLAB.createBlockData());
            entity.setPersistent(true);
            entity.getPersistentDataContainer().set(managedKey, PersistentDataType.BYTE, (byte) 1);
            float diameter = (float) (radius * 2);
            // Slab block models occupy the bottom half (y 0 to 0.5) of their local unit cube, so
            // translating up by (TABLE_SURFACE_UP - 0.5) puts the slab's flat top exactly at
            // TABLE_SURFACE_UP - below the community cards, never poking through them.
            entity.setTransformation(new Transformation(
                    new Vector3f(-diameter / 2f, (float) (TABLE_SURFACE_UP - 0.5), -diameter / 2f),
                    new Quaternionf(),
                    new Vector3f(diameter, 1f, diameter),
                    new Quaternionf()
            ));
        });
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
