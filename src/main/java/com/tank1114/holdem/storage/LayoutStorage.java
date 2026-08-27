package com.tank1114.holdem.storage;

import com.tank1114.holdem.layout.TableLayout;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/** Persists the one fixed table's center + seat locations to table.yml. */
public final class LayoutStorage {

    private final Plugin plugin;
    private final File file;

    public LayoutStorage(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "table.yml");
    }

    public TableLayout load(int seatCount) {
        TableLayout layout = new TableLayout(seatCount);
        if (!file.exists()) {
            return layout;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        layout.setCenter(readLocation(yaml, "center"));
        for (int i = 0; i < seatCount; i++) {
            layout.setSeat(i, readLocation(yaml, "seats." + i));
        }
        return layout;
    }

    public void save(TableLayout layout) {
        YamlConfiguration yaml = new YamlConfiguration();
        writeLocation(yaml, "center", layout.center());
        for (int i = 0; i < layout.seatCount(); i++) {
            writeLocation(yaml, "seats." + i, layout.seat(i));
        }
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
