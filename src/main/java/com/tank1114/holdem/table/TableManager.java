package com.tank1114.holdem.table;

import com.tank1114.holdem.config.HoldemConfig;
import com.tank1114.holdem.display.TableDisplayManager;
import com.tank1114.holdem.game.GameStage;
import com.tank1114.holdem.game.PokerTable;
import com.tank1114.holdem.layout.TableLayout;
import com.tank1114.holdem.storage.ChipStorage;
import com.tank1114.holdem.storage.LayoutStorage;
import com.tank1114.holdem.ui.BetAmountChatListener;
import com.tank1114.holdem.ui.TurnHologramMenu;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every table this plugin runs, keyed by a stable numeric id that an admin uses to
 * target one from a command. Ids are handed out in increasing order and are never reused
 * within a server's lifetime, even after a table is deleted, so an id always refers to
 * exactly the table an admin remembers creating.
 */
public final class TableManager {

    private final Plugin plugin;
    private final HoldemConfig config;
    private final ChipStorage chipStorage;
    private final LayoutStorage layoutStorage;
    private final BetAmountChatListener chatListener;

    private final Map<Integer, TableInstance> tables = new LinkedHashMap<>();
    private int nextId = 1;

    public TableManager(Plugin plugin, HoldemConfig config, ChipStorage chipStorage,
                         LayoutStorage layoutStorage, BetAmountChatListener chatListener) {
        this.plugin = plugin;
        this.config = config;
        this.chipStorage = chipStorage;
        this.layoutStorage = layoutStorage;
        this.chatListener = chatListener;
    }

    /** Recreates every table persisted from a previous run. Call once, during plugin startup. */
    public void loadAll() {
        for (Map.Entry<Integer, Location> entry : layoutStorage.loadAll().entrySet()) {
            int id = entry.getKey();
            tables.put(id, build(id, entry.getValue()));
            nextId = Math.max(nextId, id + 1);
        }
        plugin.getLogger().info(tables.isEmpty()
                ? "目前沒有已設定的牌桌，請用 /holdemadmin table create 建立第一張。"
                : "已從 table.yml 讀取 " + tables.size() + " 張牌桌。");
    }

    /** Creates a brand-new table centered on the given location, assigns it the next id, and persists it. */
    public int createTable(Location center) {
        int id = nextId++;
        tables.put(id, build(id, center));
        layoutStorage.save(id, center);
        return id;
    }

    private TableInstance build(int id, Location center) {
        TableLayout layout = new TableLayout(config.seatCount(), config.seatRadius());
        layout.setCenter(center);

        TableDisplayManager display = new TableDisplayManager(plugin);
        display.rebuild(layout);

        PokerTable table = new PokerTable(plugin, display, chipStorage, config);
        TurnHologramMenu turnMenu = new TurnHologramMenu(plugin, table, layout, chatListener);
        table.setTurnUi(turnMenu);
        plugin.getServer().getPluginManager().registerEvents(turnMenu, plugin);

        return new TableInstance(id, layout, table, display, turnMenu);
    }

    /** Voids the table's current hand, kicks every seated player out (refunding chips), and removes it entirely. */
    public String deleteTable(int id) {
        TableInstance instance = tables.remove(id);
        if (instance == null) {
            return "沒有第 " + id + " 號桌子。";
        }
        instance.table().disbandAndVacateAll();
        HandlerList.unregisterAll(instance.turnMenu());
        instance.display().teardown();
        layoutStorage.delete(id);
        return null;
    }

    public String startTable(int id) {
        TableInstance instance = tables.get(id);
        if (instance == null) {
            return "沒有第 " + id + " 號桌子。";
        }
        if (instance.table().isPaused()) {
            return "這桌已經暫停，請先 /holdemadmin resume " + id + "。";
        }
        if (instance.table().stage() != GameStage.WAITING) {
            return "第 " + id + " 號桌已經有一局在進行中了。";
        }
        instance.table().startHand();
        return null;
    }

    public String resetTable(int id) {
        TableInstance instance = tables.get(id);
        if (instance == null) {
            return "沒有第 " + id + " 號桌子。";
        }
        instance.table().forceReset();
        return null;
    }

    public String pauseTable(int id) {
        TableInstance instance = tables.get(id);
        if (instance == null) {
            return "沒有第 " + id + " 號桌子。";
        }
        return instance.table().pause();
    }

    public String resumeTable(int id) {
        TableInstance instance = tables.get(id);
        if (instance == null) {
            return "沒有第 " + id + " 號桌子。";
        }
        return instance.table().resume();
    }

    /** Finds which table (if any) a player is currently seated at - a player may only ever be seated at one. */
    public TableInstance findTableOfPlayer(UUID uuid) {
        for (TableInstance instance : tables.values()) {
            if (instance.table().seatIndexOf(uuid) >= 0) {
                return instance;
            }
        }
        return null;
    }

    /** Resolves a clicked seat-marker entity back to the table and seat it belongs to. */
    public SeatRef resolveSeatMarker(UUID entityId) {
        for (TableInstance instance : tables.values()) {
            Integer seatIndex = instance.display().seatForMarker(entityId);
            if (seatIndex != null) {
                return new SeatRef(instance, seatIndex);
            }
        }
        return null;
    }

    /** Updates a player's durable chip balance, and their live seat stack too if they're currently seated. */
    public String adminSetChips(UUID uuid, long amount) {
        TableInstance instance = findTableOfPlayer(uuid);
        if (instance != null) {
            return instance.table().adminSetChips(uuid, amount);
        }
        if (amount < 0) {
            return "籌碼不能是負數。";
        }
        chipStorage.set(uuid, amount);
        return null;
    }

    public long balanceOf(UUID uuid) {
        TableInstance instance = findTableOfPlayer(uuid);
        if (instance != null) {
            int seatIndex = instance.table().seatIndexOf(uuid);
            return instance.table().seat(seatIndex).stack();
        }
        return chipStorage.get(uuid);
    }

    public String describeAll() {
        if (tables.isEmpty()) {
            return "目前沒有任何牌桌，用 /holdemadmin table create 建立一張。";
        }
        StringBuilder sb = new StringBuilder("目前的牌桌：");
        for (TableInstance instance : tables.values()) {
            Location center = instance.layout().center();
            int occupied = 0;
            for (int i = 0; i < instance.table().seatCount(); i++) {
                if (instance.table().seat(i).isOccupied()) {
                    occupied++;
                }
            }
            sb.append("\n第 ").append(instance.id()).append(" 號：")
                    .append(center.getWorld().getName())
                    .append(" (").append((int) center.getX()).append(", ")
                    .append((int) center.getY()).append(", ").append((int) center.getZ()).append(")")
                    .append("，狀態：").append(instance.table().isPaused() ? "已暫停" : instance.table().stage())
                    .append("，人數：").append(occupied).append("/").append(instance.table().seatCount());
        }
        return sb.toString();
    }

    /**
     * Sweeps every loaded world for plugin-tagged entities left over from tables that predate
     * multi-table support (each re-run of the old single-table setup command abandoned whatever
     * was at the previous spot). Never touches a table that's still tracked here.
     */
    public String cleanupOrphans() {
        List<Location> liveCenters = new ArrayList<>();
        for (TableInstance instance : tables.values()) {
            liveCenters.add(instance.layout().center());
        }
        int removed = TableDisplayManager.removeOrphans(plugin, liveCenters);
        return removed == 0 ? "沒有找到殘留的牌桌物件。" : "已清除 " + removed + " 個殘留的牌桌物件。";
    }

    /** Removes every table's world entities without touching persisted state, used on plugin shutdown. */
    public void shutdownAll() {
        for (TableInstance instance : tables.values()) {
            instance.turnMenu().teardown();
            instance.display().teardown();
        }
    }

    public record SeatRef(TableInstance instance, int seatIndex) {
    }
}
