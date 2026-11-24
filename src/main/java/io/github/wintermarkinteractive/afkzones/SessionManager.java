package io.github.wintermarkinteractive.afkzones;

import io.github.wintermarkinteractive.afkzones.RewardManager.RewardProfile;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Checks every online player once per second: which zone are they in,
 * counts their time, hands out rewards and updates the bossbar.
 */
public final class SessionManager {

    /** Per-player state. */
    private static final class Session {
        String zone;                                      // current zone name, or null
        final Map<String, Integer> seconds = new HashMap<>(); // seconds counted per zone
        BossBar bar;
        String ip;                                        // set while counted as an earner for anti-alt
        boolean blocked;                                  // in a zone but blocked by anti-alt
    }

    private final AFKZonesPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<String> warnedProfiles = new HashSet<>();
    /** Anti-alt: which players are earning, per IP address. */
    private final Map<String, Set<UUID>> earnersByIp = new HashMap<>();
    private BukkitTask task;

    public SessionManager(AFKZonesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID id : sessions.keySet()) {
            hideBar(id);
        }
        sessions.clear();
        earnersByIp.clear();
    }

    /** After /afkzone reload: rebuild bossbars with the new style, keep progress. */
    public void refresh() {
        for (UUID id : sessions.keySet()) {
            hideBar(id);
        }
        warnedProfiles.clear();
    }

    public void remove(Player player) {
        hideBar(player.getUniqueId());
        Session s = sessions.remove(player.getUniqueId());
        if (s != null) {
            releaseEarner(player.getUniqueId(), s);
        }
    }

    /** Seconds left until the next reward, or -1 when the player is not in a zone. */
    public int secondsLeft(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.zone == null) {
            return -1;
        }
        Zone zone = plugin.zones().get(s.zone);
        RewardProfile profile = zone == null ? null : plugin.rewards().get(zone.profile());
        if (profile == null) {
            return -1;
        }
        return profile.intervalSeconds() - s.seconds.getOrDefault(zone.name(), 0);
    }

    public String currentZone(Player player) {
        Session s = sessions.get(player.getUniqueId());
        return s == null ? null : s.zone;
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        Zone zone = null;
        if (player.hasPermission(AFKZonesPlugin.PERM_USE) && player.getGameMode() != GameMode.SPECTATOR) {
            zone = plugin.zones().zoneAt(player.getLocation());
        }

        Session s = sessions.computeIfAbsent(player.getUniqueId(), id -> new Session());

        if (zone == null) {
            if (s.zone != null) {
                leave(player, s, true);
            }
            return;
        }

        RewardProfile profile = plugin.rewards().get(zone.profile());

        if (!zone.name().equals(s.zone)) {
            if (s.zone != null) {
                leave(player, s, false);
            }
            s.zone = zone.name();
            if (profile != null) {
                plugin.messages().send(player, "enter", placeholders(player, zone, profile, 0));
                playSound(player, "sounds.enter");
            }
        }

        if (profile == null) {
            if (warnedProfiles.add(zone.profile().toLowerCase(Locale.ROOT))) {
                plugin.getLogger().warning("Zone '" + zone.name() + "' uses profile '" + zone.profile()
                        + "', which does not exist in config.yml.");
            }
            hideBar(player.getUniqueId());
            return;
        }

        if (!canEarn(player, s)) {
            if (!s.blocked) {
                s.blocked = true;
                plugin.messages().send(player, "alt-blocked",
                        Placeholder.unparsed("max", String.valueOf(maxPerIp())));
            }
            updateBlockedBar(player, s, zone, profile);
            return;
        }
        s.blocked = false;

        int sec = s.seconds.merge(zone.name(), 1, Integer::sum);
        plugin.stats().addSecond(player);
        if (sec >= profile.intervalSeconds()) {
            int given = plugin.rewards().give(player, profile, zone);
            if (given > 0) {
                plugin.stats().addReward(player);
                playSound(player, "sounds.reward");
            } else {
                plugin.messages().send(player, "reward-nothing", placeholders(player, zone, profile, 0));
            }
            sec = 0;
            s.seconds.put(zone.name(), 0);
        }

        updateBar(player, s, zone, profile, sec);
    }

    private void leave(Player player, Session s, boolean announce) {
        String old = s.zone;
        s.zone = null;
        hideBar(player.getUniqueId());
        releaseEarner(player.getUniqueId(), s);
        if (plugin.getConfig().getBoolean("reset-on-leave")) {
            s.seconds.remove(old);
        }
        if (announce) {
            plugin.messages().send(player, "leave", Placeholder.unparsed("zone", old));
        }
    }

    // ---------------------------------------------------------------- anti-alt

    private int maxPerIp() {
        return plugin.getConfig().getInt("anti-alt.max-per-ip");
    }

    /**
     * True when this player may earn: anti-alt is off, the player can bypass it,
     * or fewer than max-per-ip players from the same IP are earning already (first come, first served).
     */
    private boolean canEarn(Player player, Session s) {
        int max = maxPerIp();
        if (max <= 0 || player.hasPermission(AFKZonesPlugin.PERM_BYPASS_IP) || player.getAddress() == null) {
            return true;
        }
        if (s.ip != null) {
            return true;
        }
        String ip = player.getAddress().getAddress().getHostAddress();
        Set<UUID> earners = earnersByIp.computeIfAbsent(ip, k -> new HashSet<>());
        earners.removeIf(id -> Bukkit.getPlayer(id) == null);
        if (earners.size() >= max) {
            return false;
        }
        earners.add(player.getUniqueId());
        s.ip = ip;
        return true;
    }

    private void releaseEarner(UUID id, Session s) {
        s.blocked = false;
        if (s.ip == null) {
            return;
        }
        Set<UUID> earners = earnersByIp.get(s.ip);
        if (earners != null) {
            earners.remove(id);
            if (earners.isEmpty()) {
                earnersByIp.remove(s.ip);
            }
        }
        s.ip = null;
    }

    private void updateBlockedBar(Player player, Session s, Zone zone, RewardProfile profile) {
        if (!plugin.getConfig().getBoolean("bossbar.enabled")) {
            hideBar(player.getUniqueId());
            return;
        }
        Component title = plugin.messages().parse(plugin.getConfig().getString("bossbar.blocked-text", ""),
                TagResolver.resolver(placeholders(player, zone, profile, 0)),
                Placeholder.unparsed("max", String.valueOf(maxPerIp())));
        if (s.bar == null) {
            s.bar = BossBar.bossBar(title, 0f, BossBar.Color.RED, barOverlay());
            player.showBossBar(s.bar);
        } else {
            s.bar.name(title);
            s.bar.progress(0f);
            s.bar.color(BossBar.Color.RED);
        }
    }

    // ---------------------------------------------------------------- bossbar

    private void updateBar(Player player, Session s, Zone zone, RewardProfile profile, int sec) {
        if (!plugin.getConfig().getBoolean("bossbar.enabled")) {
            hideBar(player.getUniqueId());
            return;
        }
        float progress = Math.min(1f, Math.max(0f, (float) sec / profile.intervalSeconds()));
        Component title = plugin.messages().parse(
                plugin.getConfig().getString("bossbar.text", "<zone> <time>"),
                placeholders(player, zone, profile, sec));
        if (s.bar == null) {
            s.bar = BossBar.bossBar(title, progress, barColor(), barOverlay());
            player.showBossBar(s.bar);
        } else {
            s.bar.name(title);
            s.bar.progress(progress);
            s.bar.color(barColor());
        }
    }

    private void hideBar(UUID id) {
        Session s = sessions.get(id);
        if (s == null || s.bar == null) {
            return;
        }
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            player.hideBossBar(s.bar);
        }
        s.bar = null;
    }

    private TagResolver[] placeholders(Player player, Zone zone, RewardProfile profile, int sec) {
        return new TagResolver[]{
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("zone", zone.name()),
                Placeholder.unparsed("time", formatTime(profile.intervalSeconds() - sec)),
                Placeholder.unparsed("interval", formatTime(profile.intervalSeconds()))
        };
    }

    public static String formatTime(int totalSeconds) {
        int t = Math.max(0, totalSeconds);
        return String.format(Locale.ROOT, "%d:%02d", t / 60, t % 60);
    }

    private BossBar.Color barColor() {
        String name = plugin.getConfig().getString("bossbar.color", "YELLOW");
        try {
            return BossBar.Color.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.YELLOW;
        }
    }

    private BossBar.Overlay barOverlay() {
        String name = plugin.getConfig().getString("bossbar.style", "PROGRESS");
        try {
            return BossBar.Overlay.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    private void playSound(Player player, String path) {
        String key = plugin.getConfig().getString(path, "");
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            player.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 1f, 1f));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Bad sound '" + key + "' at " + path + " in config.yml.");
        }
    }
}
