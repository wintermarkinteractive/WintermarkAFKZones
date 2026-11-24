package io.github.wintermarkinteractive.afkzones;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** A box of blocks in one world. Both corners are included. */
public final class Zone {

    /** Where /afkzone tp sends players. */
    public record SpawnPoint(double x, double y, double z, float yaw, float pitch) {
    }

    private final String name;
    private final String world;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private String profile;
    private SpawnPoint spawn;

    public Zone(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2, String profile) {
        this.name = name;
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.profile = profile;
    }

    public boolean contains(Location loc) {
        World w = loc.getWorld();
        if (w == null || !w.getName().equals(world)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** Distance squared from the zone center to a location (same world assumed). */
    public double centerDistanceSquared(Location loc) {
        double cx = (minX + maxX + 1) / 2.0;
        double cy = (minY + maxY + 1) / 2.0;
        double cz = (minZ + maxZ + 1) / 2.0;
        double dx = cx - loc.getX();
        double dy = cy - loc.getY();
        double dz = cz - loc.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /** The teleport location, or null when no spawn is set or the world is not loaded. */
    public Location spawnLocation() {
        World w = Bukkit.getWorld(world);
        if (spawn == null || w == null) {
            return null;
        }
        return new Location(w, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
    }

    public String name() { return name; }
    public String world() { return world; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }
    public String profile() { return profile; }
    public SpawnPoint spawn() { return spawn; }

    public void profile(String profile) {
        this.profile = profile;
    }

    public void spawn(SpawnPoint spawn) {
        this.spawn = spawn;
    }

    public String sizeText() {
        return (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1);
    }
}
