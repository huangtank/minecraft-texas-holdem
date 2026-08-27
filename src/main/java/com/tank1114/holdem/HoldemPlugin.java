package com.tank1114.holdem;

import com.tank1114.holdem.command.HoldemAdminCommand;
import com.tank1114.holdem.command.HoldemCommand;
import com.tank1114.holdem.config.HoldemConfig;
import com.tank1114.holdem.display.TableDisplayManager;
import com.tank1114.holdem.game.PokerTable;
import com.tank1114.holdem.layout.TableLayout;
import com.tank1114.holdem.listener.PlayerConnectionListener;
import com.tank1114.holdem.listener.SeatInteractListener;
import com.tank1114.holdem.storage.ChipStorage;
import com.tank1114.holdem.storage.LayoutStorage;
import org.bukkit.plugin.java.JavaPlugin;

public final class HoldemPlugin extends JavaPlugin {

    private ChipStorage chipStorage;
    private TableDisplayManager displayManager;
    private PokerTable table;

    @Override
    public void onEnable() {
        HoldemConfig config = new HoldemConfig(this);
        chipStorage = new ChipStorage(this);

        LayoutStorage layoutStorage = new LayoutStorage(this);
        TableLayout layout = layoutStorage.load(config.seatCount());

        displayManager = new TableDisplayManager(this);
        table = new PokerTable(this, displayManager, chipStorage, config);

        if (layout.isComplete()) {
            displayManager.rebuild(layout);
            getLogger().info("已從 table.yml 讀取牌桌座標，重新生成牌桌顯示物件。");
        } else {
            getLogger().warning("牌桌座標尚未設定完成，請用 /holdemadmin setup center 與 /holdemadmin setup seat <1~"
                    + config.seatCount() + "> 設定好之後牌桌才會出現。");
        }

        getServer().getPluginManager().registerEvents(new SeatInteractListener(table, displayManager), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(table, displayManager), this);

        getCommand("holdem").setExecutor(new HoldemCommand(table));
        getCommand("holdemadmin").setExecutor(new HoldemAdminCommand(table, displayManager, layout, layoutStorage, config, chipStorage));
    }

    @Override
    public void onDisable() {
        if (chipStorage != null) {
            chipStorage.save();
        }
        if (displayManager != null) {
            displayManager.teardown();
        }
    }
}
