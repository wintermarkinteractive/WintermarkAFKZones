package io.github.wintermarkinteractive.afkzones;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * While an admin holds the wand, draws particle outlines (only that admin sees them):
 * existing zones in gold, the current pos1/pos2 selection in green.
 */
public final class OutlineTask {

    private static final Particle.DustOptions ZONE = new Particle.DustOptions(Color.fromRGB(255, 170, 0), 1.0f);
    private static final Particle.DustOptions SELECTION = new Particle.DustOptions(Color.fromRGB(85, 255, 85), 1.0f);
    private static final double VIEW_DISTANCE = 96;
    private static final int MAX_POINTS_PER_EDGE = 64;

    private final AFKZonesPlugin plugin;
    private BukkitTask task;

    public OutlineTask(AFKZonesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("wand.show-outlines")) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission(AFKZonesPlugin.PERM_ADMIN)
                    || !plugin.wand().isWand(player.getInventory().getItemInMainHand())) {
                continue;
            }
            Location here = player.getLocation();
            for (Zone z : plugin.zones().all()) {
                if (here.getWorld() != null && here.getWorld().getName().equals(z.world())
                        && z.centerDistanceSquared(here) < VIEW_DISTANCE * VIEW_DISTANCE) {
                    drawBox(player, z.minX(), z.minY(), z.minZ(), z.maxX() + 1, z.maxY() + 1, z.maxZ() + 1, ZONE);
                }
            }
            Location a = plugin.selections().pos1(player.getUniqueId());
            Location b = plugin.selections().pos2(player.getUniqueId());
            if (a == null && b == null) {
                continue;
            }
            if (a == null) {
                a = b;
            }
            if (b == null) {
                b = a;
            }
            if (a.getWorld() == null || !a.getWorld().equals(here.getWorld()) || !a.getWorld().equals(b.getWorld())) {
                continue;
            }
            drawBox(player,
                    Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()),
                    Math.max(a.getBlockX(), b.getBlockX()) + 1, Math.max(a.getBlockY(), b.getBlockY()) + 1, Math.max(a.getBlockZ(), b.getBlockZ()) + 1,
                    SELECTION);
        }
    }

    /** The 12 edges of the box from (x1,y1,z1) to (x2,y2,z2) in world coordinates. */
    private void drawBox(Player p, double x1, double y1, double z1, double x2, double y2, double z2, Particle.DustOptions dust) {
        // 4 edges along X
        line(p, x1, y1, z1, x2, y1, z1, dust);
        line(p, x1, y2, z1, x2, y2, z1, dust);
        line(p, x1, y1, z2, x2, y1, z2, dust);
        line(p, x1, y2, z2, x2, y2, z2, dust);
        // 4 edges along Y
        line(p, x1, y1, z1, x1, y2, z1, dust);
        line(p, x2, y1, z1, x2, y2, z1, dust);
        line(p, x1, y1, z2, x1, y2, z2, dust);
        line(p, x2, y1, z2, x2, y2, z2, dust);
        // 4 edges along Z
        line(p, x1, y1, z1, x1, y1, z2, dust);
        line(p, x2, y1, z1, x2, y1, z2, dust);
        line(p, x1, y2, z1, x1, y2, z2, dust);
        line(p, x2, y2, z1, x2, y2, z2, dust);
    }

    private void line(Player p, double x1, double y1, double z1, double x2, double y2, double z2, Particle.DustOptions dust) {
        double length = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
        int points = (int) Math.max(2, Math.min(MAX_POINTS_PER_EDGE, Math.ceil(length * 2)));
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            p.spawnParticle(Particle.DUST, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t,
                    1, 0, 0, 0, 0, dust);
        }
    }
}
