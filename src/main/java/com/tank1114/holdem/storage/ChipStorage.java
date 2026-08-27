package com.tank1114.holdem.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Persists each player's independent virtual chip balance to chips.yml, keyed by UUID. */
public final class ChipStorage {

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, Long> balances = new HashMap<>();

    public ChipStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chips.yml");
        load();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), yaml.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // Not a UUID key, skip it.
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "無法儲存籌碼到 chips.yml", e);
        }
    }

    /** Returns the stored balance, or {@code startingStack} (and records it) if the player has never played before. */
    public long getOrInit(UUID uuid, long startingStack) {
        return balances.computeIfAbsent(uuid, u -> startingStack);
    }

    public long get(UUID uuid) {
        return balances.getOrDefault(uuid, 0L);
    }

    public void set(UUID uuid, long amount) {
        balances.put(uuid, amount);
        save();
    }
}
