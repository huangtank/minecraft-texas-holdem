package com.tank1114.holdem;

import com.tank1114.holdem.command.HoldemAdminCommand;
import com.tank1114.holdem.config.HoldemConfig;
import com.tank1114.holdem.listener.PlayerConnectionListener;
import com.tank1114.holdem.listener.SeatInteractListener;
import com.tank1114.holdem.storage.ChipStorage;
import com.tank1114.holdem.storage.LayoutStorage;
import com.tank1114.holdem.table.TableManager;
import com.tank1114.holdem.ui.BetAmountChatListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class HoldemPlugin extends JavaPlugin {

    private ChipStorage chipStorage;
    private TableManager tableManager;

    @Override
    public void onEnable() {
        HoldemConfig config = new HoldemConfig(this);
        chipStorage = new ChipStorage(this);
        LayoutStorage layoutStorage = new LayoutStorage(this);

        BetAmountChatListener chatListener = new BetAmountChatListener(this);
        tableManager = new TableManager(this, config, chipStorage, layoutStorage, chatListener);
        tableManager.loadAll();

        getServer().getPluginManager().registerEvents(new SeatInteractListener(tableManager), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(tableManager), this);
        getServer().getPluginManager().registerEvents(chatListener, this);

        HoldemAdminCommand adminCommand = new HoldemAdminCommand(tableManager, config);
        getCommand("holdemadmin").setExecutor(adminCommand);
        getCommand("holdemadmin").setTabCompleter(adminCommand);
    }

    @Override
    public void onDisable() {
        if (chipStorage != null) {
            chipStorage.save();
        }
        if (tableManager != null) {
            tableManager.shutdownAll();
        }
    }
}
