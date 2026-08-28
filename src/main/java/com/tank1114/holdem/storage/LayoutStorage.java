package com.tank1114.holdem.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/** Persists every table's center point to table.yml, keyed by its numeric id. Seats are derived from it, not stored. */
public final class LayoutStorage {

    private static final String SECTION = "tables";

    private final Plugin plugin;
    private final File file;

    public LayoutStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "table.yml");
    }

    /** Loads every persisted table's center point, keyed by id. Iteration order matches the file. */
    public Map<Integer, Location> loadAll() {
        Map<Integer, Location> result = new LinkedHashMap<>();
        if (!file.exists()) {
            return result;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection tables = yaml.getConfigurationSection(SECTION);
        if (tables == null) {
            // Pre-multi-table format: a single top-level "center" key. Migrate it to id 1 so an
            // admin's already-set-up table isn't silently lost the first time this version boots.
            Location legacy = readLocation(yaml, "center");
            if (legacy != null) {
                yaml.set("center", null);
                writeLocation(yaml, SECTION + ".1", legacy);
                saveYaml(yaml);
                result.put(1, legacy);
            }
            return result;
        }
        for (String key : tables.getKeys(false)) {
            try {
                int id = Integer.parseInt(key);
                Location location = readLocation(yaml, SECTION + "." + key);
                if (location != null) {
                    result.put(id, location);
                }
            } catch (NumberFormatException ignored) {
                // stray non-numeric key in the file - skip it
            }
        }
        return result;
    }

    public void save(int id, Location center) {
        YamlConfiguration yaml = loadYaml();
        writeLocation(yaml, SECTION + "." + id, center);
        saveYaml(yaml);
    }

    public void delete(int id) {
        YamlConfiguration yaml = loadYaml();
        yaml.set(SECTION + "." + id, null);
        saveYaml(yaml);
    }

    private YamlConfiguration loadYaml() {
        return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private void saveYaml(YamlConfiguration yaml) {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "無法儲存牌桌座標到 table.yml", e);
        }
    }

    private Location readLocation(YamlConfiguration yaml, String path) {
        if (!yaml.isConfigurationSection(path)) {
            return null;
        }
        String worldName = yaml.getString(path + ".world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = yaml.getDouble(path + ".x");
        double y = yaml.getDouble(path + ".y");
        double z = yaml.getDouble(path + ".z");
        float yaw = (float) yaml.getDouble(path + ".yaw");
        float pitch = (float) yaml.getDouble(path + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void writeLocation(YamlConfiguration yaml, String path, Location location) {
        if (location == null) {
            return;
        }
        yaml.set(path + ".world", location.getWorld().getName());
        yaml.set(path + ".x", location.getX());
        yaml.set(path + ".y", location.getY());
        yaml.set(path + ".z", location.getZ());
        yaml.set(path + ".yaw", (double) location.getYaw());
        yaml.set(path + ".pitch", (double) location.getPitch());
    }
}
