package io.github.wintermarkinteractive.afkzones;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** /afkzone (aliases /afkz, /afkzones). */
public final class AfkZoneCommand implements BasicCommand {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final List<String> ADMIN_SUBS = List.of("wand", "pos1", "pos2", "create", "delete", "list", "setprofile", "setspawn", "reload");
    /** Zones lower than this are made taller, so jumping does not leave the zone. */
    private static final int MIN_HEIGHT = 3;

    private final AFKZonesPlugin plugin;

    public AfkZoneCommand(AFKZonesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);

        if (ADMIN_SUBS.contains(sub) && !sender.hasPermission(AFKZonesPlugin.PERM_ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }

        switch (sub) {
            case "status" -> status(sender);
            case "tp" -> teleport(sender, args);
            case "stats" -> stats(sender, args);
            case "top" -> top(sender, args);
            case "wand" -> giveWand(sender);
            case "setspawn" -> setSpawn(sender, args);
            case "pos1", "pos2" -> pos(sender, sub.equals("pos1"));
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "list" -> list(sender);
            case "setprofile" -> setProfile(sender, args);
            case "reload" -> reload(sender);
            default -> help(sender);
        }
    }

    private void status(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            help(sender);
            return;
        }
        String zone = plugin.sessions().currentZone(player);
        int left = plugin.sessions().secondsLeft(player);
        if (zone == null || left < 0) {
            plugin.messages().send(player, "status-no-zone");
        } else {
            plugin.messages().send(player, "status-in-zone",
                    Placeholder.unparsed("zone", zone),
                    Placeholder.unparsed("time", SessionManager.formatTime(left)));
        }
        if (player.hasPermission(AFKZonesPlugin.PERM_ADMIN)) {
            plugin.messages().sendRaw(player, "<gray>Admin commands: <yellow>/afkzone help</yellow>");
        }
    }

    private void help(CommandSender sender) {
        Messages m = plugin.messages();
        m.sendRaw(sender, "<gold><bold>Wintermark AFK Zones</bold></gold> <gray>v" + plugin.getPluginMeta().getVersion());
        m.sendRaw(sender, "<yellow>/afkzone</yellow> <gray>- your AFK zone status");
        if (sender.hasPermission(AFKZonesPlugin.PERM_TP)) {
            m.sendRaw(sender, "<yellow>/afkzone tp [zone]</yellow> <gray>- teleport to an AFK zone");
        }
        if (sender.hasPermission(AFKZonesPlugin.PERM_STATS)) {
            m.sendRaw(sender, "<yellow>/afkzone stats [player]</yellow> <gray>- AFK time and rewards");
            m.sendRaw(sender, "<yellow>/afkzone top [time|rewards]</yellow> <gray>- top 10 leaderboard");
        }
        if (sender.hasPermission(AFKZonesPlugin.PERM_ADMIN)) {
            m.sendRaw(sender, "<yellow>/afkzone wand</yellow> <gray>- get the selection wand (left/right-click corners)");
            m.sendRaw(sender, "<yellow>/afkzone pos1</yellow> <gray>/</gray> <yellow>pos2</yellow> <gray>- set a corner where you stand");
            m.sendRaw(sender, "<yellow>/afkzone create <name> [profile]</yellow> <gray>- create a zone from pos1 + pos2");
            m.sendRaw(sender, "<yellow>/afkzone delete <name></yellow> <gray>- delete a zone");
            m.sendRaw(sender, "<yellow>/afkzone list</yellow> <gray>- all zones");
            m.sendRaw(sender, "<yellow>/afkzone setprofile <zone> <profile></yellow> <gray>- change the rewards of a zone");
            m.sendRaw(sender, "<yellow>/afkzone setspawn <zone></yellow> <gray>- set the teleport point (stand inside the zone)");
            m.sendRaw(sender, "<yellow>/afkzone reload</yellow> <gray>- reload config.yml and zones.yml");
        }
    }

    private void pos(CommandSender sender, boolean first) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        plugin.selections().select(player, first, player.getLocation());
    }

    private void giveWand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        for (ItemStack left : player.getInventory().addItem(plugin.wand().create()).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        plugin.messages().sendRaw(player, "<green>Here is your wand. <gray>Left-click a block for pos1, right-click for pos2. "
                + "Hold it to see zone outlines.");
    }

    private void setSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        if (args.length < 2) {
            plugin.messages().sendRaw(player, "<red>Usage: /afkzone setspawn <zone>");
            return;
        }
        Zone zone = plugin.zones().get(args[1]);
        if (zone == null) {
            plugin.messages().send(player, "unknown-zone", Placeholder.unparsed("zone", args[1]));
            return;
        }
        Location loc = player.getLocation();
        if (!zone.contains(loc)) {
            plugin.messages().sendRaw(player, "<red>Stand inside the zone <gold>" + zone.name()
                    + "</gold> to set its teleport point.");
            return;
        }
        zone.spawn(new Zone.SpawnPoint(loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
        plugin.zones().save();
        plugin.messages().sendRaw(player, "<green>Teleport point of <gold>" + zone.name()
                + "</gold> set. Players can now use <yellow>/afkzone tp " + zone.name() + "</yellow>.");
    }

    private void teleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        if (!player.hasPermission(AFKZonesPlugin.PERM_TP)) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        Zone zone;
        if (args.length >= 2) {
            zone = plugin.zones().get(args[1]);
            if (zone == null) {
                plugin.messages().send(player, "unknown-zone", Placeholder.unparsed("zone", args[1]));
                return;
            }
        } else {
            List<Zone> targets = teleportZones();
            if (targets.isEmpty()) {
                plugin.messages().send(player, "no-tp-zones");
                return;
            }
            if (targets.size() > 1) {
                List<String> names = new ArrayList<>();
                for (Zone z : targets) {
                    names.add(z.name());
                }
                plugin.messages().send(player, "choose-zone", Placeholder.unparsed("zones", String.join(", ", names)));
                return;
            }
            zone = targets.get(0);
        }
        Location target = zone.spawnLocation();
        if (target == null) {
            plugin.messages().send(player, "no-spawn", Placeholder.unparsed("zone", zone.name()));
            return;
        }
        String name = zone.name();
        player.teleportAsync(target).thenAccept(ok -> {
            if (ok) {
                plugin.messages().send(player, "teleported", Placeholder.unparsed("zone", name));
            }
        });
    }

    private void stats(CommandSender sender, String[] args) {
        if (!sender.hasPermission(AFKZonesPlugin.PERM_STATS)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        StatsManager.Stats st;
        boolean self;
        if (args.length >= 2) {
            st = plugin.stats().find(args[1]);
            self = sender instanceof Player p && p.getName().equalsIgnoreCase(args[1]);
            if (st == null) {
                plugin.messages().send(sender, "stats-unknown", Placeholder.unparsed("player", args[1]));
                return;
            }
        } else if (sender instanceof Player player) {
            st = plugin.stats().get(player.getUniqueId());
            self = true;
            if (st == null) {
                plugin.messages().send(sender, "stats-self",
                        Placeholder.unparsed("player", player.getName()),
                        Placeholder.unparsed("time", StatsManager.formatDuration(0)),
                        Placeholder.unparsed("rewards", "0"));
                return;
            }
        } else {
            plugin.messages().sendRaw(sender, "<red>Usage: /afkzone stats <player>");
            return;
        }
        plugin.messages().send(sender, self ? "stats-self" : "stats-other",
                Placeholder.unparsed("player", st.name()),
                Placeholder.unparsed("time", StatsManager.formatDuration(st.seconds())),
                Placeholder.unparsed("rewards", String.valueOf(st.rewards())));
    }

    private void top(CommandSender sender, String[] args) {
        if (!sender.hasPermission(AFKZonesPlugin.PERM_STATS)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        boolean byRewards = args.length >= 2 && args[1].equalsIgnoreCase("rewards");
        List<StatsManager.Stats> top = plugin.stats().top(byRewards, 10);
        if (top.isEmpty()) {
            plugin.messages().send(sender, "top-empty");
            return;
        }
        Messages m = plugin.messages();
        String type = m.raw(byRewards ? "top-type-rewards" : "top-type-time");
        String header = m.raw("top-header");
        if (!header.isEmpty()) {
            sender.sendMessage(m.parse(header, Placeholder.parsed("type", type)));
        }
        String line = m.raw("top-line");
        int rank = 1;
        for (StatsManager.Stats st : top) {
            String value = byRewards ? String.valueOf(st.rewards()) : StatsManager.formatDuration(st.seconds());
            sender.sendMessage(m.parse(line,
                    Placeholder.unparsed("rank", String.valueOf(rank++)),
                    Placeholder.unparsed("player", st.name()),
                    Placeholder.unparsed("value", value)));
        }
    }

    /** Zones that have a teleport point. */
    private List<Zone> teleportZones() {
        List<Zone> list = new ArrayList<>();
        for (Zone z : plugin.zones().all()) {
            if (z.spawn() != null) {
                list.add(z);
            }
        }
        return list;
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return;
        }
        if (args.length < 2) {
            plugin.messages().sendRaw(player, "<red>Usage: /afkzone create <name> [profile]");
            return;
        }
        String name = args[1];
        if (!NAME.matcher(name).matches()) {
            plugin.messages().sendRaw(player, "<red>Use only letters, numbers, - and _ (max 32) for the zone name.");
            return;
        }
        if (plugin.zones().get(name) != null) {
            plugin.messages().sendRaw(player, "<red>A zone called <white>" + name + "</white> already exists.");
            return;
        }
        String profile = args.length >= 3 ? args[2] : "default";
        if (plugin.rewards().get(profile) == null) {
            plugin.messages().sendRaw(player, "<red>Unknown reward profile <white>" + profile
                    + "</white>. Profiles: <white>" + String.join(", ", plugin.rewards().ids()));
            return;
        }
        Location a = plugin.selections().pos1(player.getUniqueId());
        Location b = plugin.selections().pos2(player.getUniqueId());
        if (a == null || b == null) {
            plugin.messages().sendRaw(player, "<red>Set both corners first: stand on one corner and type "
                    + "<yellow>/afkzone pos1</yellow>, then the opposite corner and <yellow>/afkzone pos2</yellow>.");
            return;
        }
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            plugin.messages().sendRaw(player, "<red>Pos1 and pos2 must be in the same world.");
            return;
        }

        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int maxY = Math.max(a.getBlockY(), b.getBlockY());
        boolean raised = false;
        if (maxY - minY + 1 < MIN_HEIGHT) {
            maxY = minY + MIN_HEIGHT - 1;
            raised = true;
        }

        Zone zone = new Zone(name, a.getWorld().getName(),
                a.getBlockX(), minY, a.getBlockZ(),
                b.getBlockX(), maxY, b.getBlockZ(),
                plugin.rewards().get(profile).id());
        plugin.zones().add(zone);
        plugin.selections().clear(player.getUniqueId());

        plugin.messages().sendRaw(player, "<green>Created AFK zone <gold>" + zone.name() + "</gold> <gray>("
                + zone.sizeText() + ", profile <white>" + zone.profile() + "</white>)</gray>.");
        if (raised) {
            plugin.messages().sendRaw(player, "<gray>The zone was made " + MIN_HEIGHT
                    + " blocks tall, so players don't leave it when they jump.");
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().sendRaw(sender, "<red>Usage: /afkzone delete <name>");
            return;
        }
        if (plugin.zones().remove(args[1])) {
            plugin.messages().sendRaw(sender, "<green>Deleted AFK zone <gold>" + args[1] + "</gold>.");
        } else {
            plugin.messages().sendRaw(sender, "<red>No zone called <white>" + args[1] + "</white>.");
        }
    }

    private void list(CommandSender sender) {
        if (plugin.zones().all().isEmpty()) {
            plugin.messages().sendRaw(sender, "<gray>No AFK zones yet. Create one with <yellow>/afkzone create <name>");
            return;
        }
        plugin.messages().sendRaw(sender, "<gold>AFK zones (" + plugin.zones().all().size() + "):");
        for (Zone z : plugin.zones().all()) {
            plugin.messages().sendRaw(sender, "<yellow>" + z.name() + "</yellow> <gray>- " + z.world() + " "
                    + z.minX() + "," + z.minY() + "," + z.minZ() + " to " + z.maxX() + "," + z.maxY() + "," + z.maxZ()
                    + " (" + z.sizeText() + "), profile <white>" + z.profile() + "</white>"
                    + (z.spawn() != null ? " <green>[tp]" : ""));
        }
    }

    private void setProfile(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().sendRaw(sender, "<red>Usage: /afkzone setprofile <zone> <profile>");
            return;
        }
        Zone zone = plugin.zones().get(args[1]);
        if (zone == null) {
            plugin.messages().sendRaw(sender, "<red>No zone called <white>" + args[1] + "</white>.");
            return;
        }
        if (plugin.rewards().get(args[2]) == null) {
            plugin.messages().sendRaw(sender, "<red>Unknown reward profile <white>" + args[2]
                    + "</white>. Profiles: <white>" + String.join(", ", plugin.rewards().ids()));
            return;
        }
        zone.profile(plugin.rewards().get(args[2]).id());
        plugin.zones().save();
        plugin.messages().sendRaw(sender, "<green>Zone <gold>" + zone.name() + "</gold> now uses profile <white>"
                + zone.profile() + "</white>.");
    }

    private void reload(CommandSender sender) {
        plugin.loadAll();
        plugin.sessions().refresh();
        plugin.messages().sendRaw(sender, "<green>Reloaded: <white>" + plugin.zones().all().size()
                + "</white> zone(s), <white>" + plugin.rewards().ids().size() + "</white> reward profile(s).");
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        boolean admin = sender.hasPermission(AFKZonesPlugin.PERM_ADMIN);
        List<String> options = new ArrayList<>();
        if (args.length <= 1) {
            options.add("status");
            options.add("help");
            if (sender.hasPermission(AFKZonesPlugin.PERM_TP)) {
                options.add("tp");
            }
            if (sender.hasPermission(AFKZonesPlugin.PERM_STATS)) {
                options.add("stats");
                options.add("top");
            }
            if (admin) {
                options.addAll(ADMIN_SUBS);
            }
        } else if (admin && args.length == 2 && List.of("delete", "setprofile", "setspawn").contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Zone z : plugin.zones().all()) {
                options.add(z.name());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            options.add("time");
            options.add("rewards");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                options.add(p.getName());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("tp") && sender.hasPermission(AFKZonesPlugin.PERM_TP)) {
            for (Zone z : teleportZones()) {
                options.add(z.name());
            }
        } else if (admin && ((args.length == 3 && args[0].equalsIgnoreCase("create"))
                || (args.length == 3 && args[0].equalsIgnoreCase("setprofile")))) {
            options.addAll(plugin.rewards().ids());
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(last)) {
                result.add(o);
            }
        }
        return result;
    }
}
