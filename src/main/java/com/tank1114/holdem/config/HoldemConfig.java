package com.tank1114.holdem.config;

import com.tank1114.holdem.game.RebuyMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/** Mutable runtime settings, backed by config.yml. Rebuy mode can be changed live by an admin command. */
public final class HoldemConfig {

    private final Plugin plugin;

    private final int seatCount;
    private final long smallBlind;
    private final long bigBlind;
    private final long startingStack;
    private final int turnTimeoutSeconds;
    private RebuyMode rebuyMode;

    public HoldemConfig(Plugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        this.seatCount = config.getInt("table.seat-count", 6);
        this.smallBlind = config.getLong("blinds.small", 50);
        this.bigBlind = config.getLong("blinds.big", 100);
        this.startingStack = config.getLong("chips.starting-stack", 10000);
        this.turnTimeoutSeconds = config.getInt("turn.timeout-seconds", 60);
        this.rebuyMode = RebuyMode.valueOf(config.getString("chips.rebuy-mode", "AUTO").toUpperCase());
    }

    public int seatCount() {
        return seatCount;
    }

    public long smallBlind() {
        return smallBlind;
    }

    public long bigBlind() {
        return bigBlind;
    }

    public long startingStack() {
        return startingStack;
    }

    public int turnTimeoutSeconds() {
        return turnTimeoutSeconds;
    }

    public RebuyMode rebuyMode() {
        return rebuyMode;
    }

    public void setRebuyMode(RebuyMode mode) {
        this.rebuyMode = mode;
        plugin.getConfig().set("chips.rebuy-mode", mode.name());
        plugin.saveConfig();
    }
}
