package io.github.wintermarkinteractive.afkzones;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class PlayerListener implements Listener {

    private final AFKZonesPlugin plugin;

    public PlayerListener(AFKZonesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.sessions().remove(event.getPlayer());
        plugin.selections().clear(event.getPlayer().getUniqueId());
        plugin.stats().save();
    }

    /** Wand: left-click a block = pos1, right-click a block = pos2. */
    @EventHandler(priority = EventPriority.LOW)
    public void onWandClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !plugin.wand().isWand(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null || !player.hasPermission(AFKZonesPlugin.PERM_ADMIN)) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            plugin.selections().select(player, true, block.getLocation());
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            plugin.selections().select(player, false, block.getLocation());
        } else {
            return;
        }
        event.setCancelled(true);
    }

    /** Never break blocks with the wand (e.g. in creative mode). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onWandBreak(BlockBreakEvent event) {
        if (plugin.wand().isWand(event.getPlayer().getInventory().getItemInMainHand())
                && event.getPlayer().hasPermission(AFKZonesPlugin.PERM_ADMIN)) {
            event.setCancelled(true);
        }
    }
}
