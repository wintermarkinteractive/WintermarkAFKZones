package io.github.wintermarkinteractive.afkzones;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Remembers pos1/pos2 per admin while they create a zone. */
public final class SelectionManager {

    private final AFKZonesPlugin plugin;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public SelectionManager(AFKZonesPlugin plugin) {
        this.plugin = plugin;
    }

    /** Stores a corner (block position) and tells the player, including the size once both corners are set. */
    public void select(Player player, boolean first, Location loc) {
        Location block = loc.toBlockLocation();
        (first ? pos1 : pos2).put(player.getUniqueId(), block);
        String text = "<green>" + (first ? "Pos1" : "Pos2") + " set to <white>"
                + block.getBlockX() + ", " + block.getBlockY() + ", " + block.getBlockZ() + "</white>.";
        Location a = pos1(player.getUniqueId());
        Location b = pos2(player.getUniqueId());
        if (a != null && b != null && a.getWorld() != null && a.getWorld().equals(b.getWorld())) {
            int sx = Math.abs(a.getBlockX() - b.getBlockX()) + 1;
            int sy = Math.abs(a.getBlockY() - b.getBlockY()) + 1;
            int sz = Math.abs(a.getBlockZ() - b.getBlockZ()) + 1;
            text += " <gray>Selection: " + sx + "x" + sy + "x" + sz
                    + ". Now type <yellow>/afkzone create <name></yellow>";
        }
        plugin.messages().sendRaw(player, text);
    }

    public Location pos1(UUID id) {
        return pos1.get(id);
    }

    public Location pos2(UUID id) {
        return pos2.get(id);
    }

    public void clear(UUID id) {
        pos1.remove(id);
        pos2.remove(id);
    }
}
