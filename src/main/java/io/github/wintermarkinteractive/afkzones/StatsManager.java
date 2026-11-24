package io.github.wintermarkinteractive.afkzones;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** AFK time and rewards per player, saved in plugins/WintermarkAFKZones/data.yml. */
public final class StatsManager {

    public static final class Stats {
        private final UUID id;
        private String name;
        private long seconds;
        private int rewards;

        Stats(UUID id, String name, long seconds, int rewards) {
            this.id = id;
            this.name = name;
            this.seconds = seconds;
            this.rewards = rewards;
        }

        public UUID id() { return id; }
        public String name() { return name; }
        public long seconds() { return seconds; }
        public int rewards() { return rewards; }
    }

    private static final long AUTOSAVE_TICKS = 20L * 60 * 5; // every 5 minutes

    private final AFKZonesPlugin plugin;
    private final File file;
    private final Map<UUID, Stats> stats = new HashMap<>();
    private boolean dirty;
    private BukkitTask autosave;

    public StatsManager(AFKZonesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        stats.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s != null) {
                stats.put(id, new Stats(id, s.getString("name", "?"), s.getLong("seconds"), s.getInt("rewards")));
            }
        }
    }

    public void save() {
        if (!dirty) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Stats s : stats.values()) {
            String p = "players." + s.id() + ".";
            yaml.set(p + "name", s.name());
            yaml.set(p + "seconds", s.seconds());
            yaml.set(p + "rewards", s.rewards());
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml: " + e.getMessage());
        }
    }

    public void startAutosave() {
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::save, AUTOSAVE_TICKS, AUTOSAVE_TICKS);
    }

    public void stopAutosave() {
        if (autosave != null) {
            autosave.cancel();
            autosave = null;
        }
        save();
    }

    private Stats of(Player player) {
        Stats s = stats.computeIfAbsent(player.getUniqueId(), id -> new Stats(id, player.getName(), 0, 0));
        s.name = player.getName();
        return s;
    }

    public void addSecond(Player player) {
        of(player).seconds++;
        dirty = true;
    }

    public void addReward(Player player) {
        of(player).rewards++;
        dirty = true;
    }

    public Stats get(UUID id) {
        return stats.get(id);
    }

    /** Finds stats by the last known player name (case-insensitive). */
    public Stats find(String name) {
        for (Stats s : stats.values()) {
            if (s.name().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    /** Top players by AFK time (byRewards = false) or by rewards received (byRewards = true). */
    public List<Stats> top(boolean byRewards, int limit) {
        List<Stats> list = new ArrayList<>(stats.values());
        Comparator<Stats> order = byRewards
                ? Comparator.comparingInt(Stats::rewards).thenComparingLong(Stats::seconds)
                : Comparator.comparingLong(Stats::seconds).thenComparingInt(Stats::rewards);
        list.sort(order.reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }

    /** 3725 seconds -> "1h 2m", 59 -> "59s", 90061 -> "1d 1h 1m". */
    public static String formatDuration(long totalSeconds) {
        long t = Math.max(0, totalSeconds);
        long days = t / 86400;
        long hours = (t % 86400) / 3600;
        long minutes = (t % 3600) / 60;
        if (days > 0) {
            return String.format(Locale.ROOT, "%dd %dh %dm", days, hours, minutes);
        }
        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %dm", hours, minutes);
        }
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm %ds", minutes, t % 60);
        }
        return t + "s";
    }
}
