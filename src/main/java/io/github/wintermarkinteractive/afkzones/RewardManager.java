package io.github.wintermarkinteractive.afkzones;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Reward profiles from config.yml ("profiles" section) and handing them out. */
public final class RewardManager {

    public record RewardEntry(String id, double chance, List<ItemStack> items, List<String> commands, String message) {
    }

    public record RewardProfile(String id, int intervalSeconds, List<RewardEntry> entries) {
    }

    private final AFKZonesPlugin plugin;
    private final Map<String, RewardProfile> profiles = new LinkedHashMap<>();

    public RewardManager(AFKZonesPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        profiles.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("profiles");
        if (root == null) {
            plugin.getLogger().warning("config.yml has no 'profiles' section - zones will not give rewards.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }
            int interval = Math.max(1, sec.getInt("interval-seconds", 300));
            List<RewardEntry> entries = new ArrayList<>();
            ConfigurationSection rs = sec.getConfigurationSection("rewards");
            if (rs != null) {
                for (String rid : rs.getKeys(false)) {
                    ConfigurationSection r = rs.getConfigurationSection(rid);
                    if (r == null) {
                        continue;
                    }
                    entries.add(new RewardEntry(rid,
                            r.getDouble("chance", 100.0),
                            parseItems(r.getStringList("items"), id + "." + rid),
                            r.getStringList("commands"),
                            r.getString("message", "")));
                }
            }
            profiles.put(id.toLowerCase(Locale.ROOT), new RewardProfile(id, interval, entries));
        }
    }

    private List<ItemStack> parseItems(List<String> lines, String where) {
        List<ItemStack> items = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split(":");
            Material mat = Material.matchMaterial(parts[0].trim());
            if (mat == null || !mat.isItem()) {
                plugin.getLogger().warning("Unknown item '" + parts[0] + "' in profiles." + where);
                continue;
            }
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Bad amount in '" + line + "' in profiles." + where);
                }
            }
            items.add(new ItemStack(mat, amount));
        }
        return items;
    }

    public RewardProfile get(String id) {
        return id == null ? null : profiles.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(profiles.keySet());
    }

    /** Rolls every entry of the profile for this player. Returns how many entries were given. */
    public int give(Player player, RewardProfile profile, Zone zone) {
        int given = 0;
        for (RewardEntry entry : profile.entries()) {
            if (ThreadLocalRandom.current().nextDouble(100.0) >= entry.chance()) {
                continue;
            }
            given++;
            for (ItemStack item : entry.items()) {
                for (ItemStack left : player.getInventory().addItem(item.clone()).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }
            for (String cmd : entry.commands()) {
                String line = cmd.replace("<player>", player.getName()).replace("<zone>", zone.name());
                if (line.startsWith("/")) {
                    line = line.substring(1);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
            }
            plugin.messages().sendRaw(player, entry.message(),
                    Placeholder.unparsed("player", player.getName()),
                    Placeholder.unparsed("zone", zone.name()));
        }
        return given;
    }
}
