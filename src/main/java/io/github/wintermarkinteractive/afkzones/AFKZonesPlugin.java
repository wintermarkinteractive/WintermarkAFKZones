package io.github.wintermarkinteractive.afkzones;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class AFKZonesPlugin extends JavaPlugin {

    public static final String PERM_USE = "wintermark.afkzones.use";
    public static final String PERM_TP = "wintermark.afkzones.tp";
    public static final String PERM_STATS = "wintermark.afkzones.stats";
    public static final String PERM_BYPASS_IP = "wintermark.afkzones.bypass-ip";
    public static final String PERM_ADMIN = "wintermark.afkzones.admin";

    private Messages messages;
    private RewardManager rewards;
    private ZoneManager zones;
    private SelectionManager selections;
    private SessionManager sessions;
    private Wand wand;
    private OutlineTask outlines;
    private StatsManager stats;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        rewards = new RewardManager(this);
        zones = new ZoneManager(this);
        selections = new SelectionManager(this);
        sessions = new SessionManager(this);
        wand = new Wand(this);
        outlines = new OutlineTask(this);
        stats = new StatsManager(this);

        loadAll();
        stats.load(); // only on startup: a reload must not throw away unsaved progress

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        registerCommand("afkzone", "Wintermark AFK Zones", List.of("afkz", "afkzones"), new AfkZoneCommand(this));

        sessions.start();
        outlines.start();
        stats.startAutosave();
        getLogger().info("Loaded " + zones.all().size() + " AFK zone(s) and " + rewards.ids().size() + " reward profile(s).");
    }

    @Override
    public void onDisable() {
        if (outlines != null) {
            outlines.stop();
        }
        if (sessions != null) {
            sessions.stop();
        }
        if (stats != null) {
            stats.stopAutosave();
        }
    }

    /** (Re)loads config.yml and zones.yml. */
    public void loadAll() {
        reloadConfig();
        addMissingConfigKeys();
        messages.load();
        rewards.load();
        zones.load();
    }

    /**
     * Adds settings that are new in this version to an existing config.yml (with their comments),
     * so server owners can see them. The "profiles" section is skipped on purpose:
     * owners may delete example profiles and we must not bring them back.
     */
    private void addMissingConfigKeys() {
        FileConfiguration config = getConfig();
        Configuration defaults = config.getDefaults();
        if (defaults == null) {
            return;
        }
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (key.equals("profiles") || key.startsWith("profiles.") || config.isSet(key)) {
                continue;
            }
            if (defaults.isConfigurationSection(key)) {
                config.createSection(key);
            } else {
                config.set(key, defaults.get(key));
            }
            config.setComments(key, defaults.getComments(key));
            changed = true;
        }
        if (changed) {
            saveConfig();
            reloadConfig();
            getLogger().info("Added new settings to config.yml.");
        }
    }

    public Messages messages() {
        return messages;
    }

    public RewardManager rewards() {
        return rewards;
    }

    public ZoneManager zones() {
        return zones;
    }

    public SelectionManager selections() {
        return selections;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public StatsManager stats() {
        return stats;
    }

    public Wand wand() {
        return wand;
    }
}
