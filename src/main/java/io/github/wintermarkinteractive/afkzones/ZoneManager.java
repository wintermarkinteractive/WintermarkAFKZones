package io.github.wintermarkinteractive.afkzones;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Loads, saves and looks up zones (plugins/WintermarkAFKZones/zones.yml). */
public final class ZoneManager {

    private final AFKZonesPlugin plugin;
    private final File file;
    private final Map<String, Zone> zones = new LinkedHashMap<>();

    public ZoneManager(AFKZonesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "zones.yml");
    }

    public void load() {
        zones.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("zones");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            Zone zone = new Zone(name, s.getString("world", "world"),
                    s.getInt("min.x"), s.getInt("min.y"), s.getInt("min.z"),
                    s.getInt("max.x"), s.getInt("max.y"), s.getInt("max.z"),
                    s.getString("profile", "default"));
            if (s.isConfigurationSection("spawn")) {
                zone.spawn(new Zone.SpawnPoint(
                        s.getDouble("spawn.x"), s.getDouble("spawn.y"), s.getDouble("spawn.z"),
                        (float) s.getDouble("spawn.yaw"), (float) s.getDouble("spawn.pitch")));
            }
            zones.put(key(name), zone);
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Zone z : zones.values()) {
            String p = "zones." + z.name() + ".";
            yaml.set(p + "world", z.world());
            yaml.set(p + "min.x", z.minX());
            yaml.set(p + "min.y", z.minY());
            yaml.set(p + "min.z", z.minZ());
            yaml.set(p + "max.x", z.maxX());
            yaml.set(p + "max.y", z.maxY());
            yaml.set(p + "max.z", z.maxZ());
            yaml.set(p + "profile", z.profile());
            Zone.SpawnPoint sp = z.spawn();
            if (sp != null) {
                yaml.set(p + "spawn.x", sp.x());
                yaml.set(p + "spawn.y", sp.y());
                yaml.set(p + "spawn.z", sp.z());
                yaml.set(p + "spawn.yaw", (double) sp.yaw());
                yaml.set(p + "spawn.pitch", (double) sp.pitch());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save zones.yml: " + e.getMessage());
        }
    }

    public Zone get(String name) {
        return zones.get(key(name));
    }

    public Collection<Zone> all() {
        return Collections.unmodifiableCollection(zones.values());
    }

    public void add(Zone zone) {
        zones.put(key(zone.name()), zone);
        save();
    }

    public boolean remove(String name) {
        boolean removed = zones.remove(key(name)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /** The zone at this location. When zones overlap, the smallest one wins. */
    public Zone zoneAt(Location loc) {
        Zone best = null;
        for (Zone z : zones.values()) {
            if (z.contains(loc) && (best == null || z.volume() < best.volume())) {
                best = z;
            }
        }
        return best;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
