package io.github.wintermarkinteractive.afkzones;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The selection wand: a normal item with a hidden tag, so renamed copies of the material don't count. */
public final class Wand {

    private final AFKZonesPlugin plugin;
    private final NamespacedKey key;

    public Wand(AFKZonesPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "wand");
    }

    public ItemStack create() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("wand.material", "BLAZE_ROD"));
        if (material == null || !material.isItem() || material.isAir()) {
            material = Material.BLAZE_ROD;
        }
        ItemStack item = new ItemStack(material);
        Messages m = plugin.messages();
        item.editMeta(meta -> {
            meta.displayName(m.parse("<!italic><gold><bold>AFK Zone Wand"));
            meta.lore(List.of(
                    m.parse("<!italic><gray>Left-click a block: <yellow>pos1"),
                    m.parse("<!italic><gray>Right-click a block: <yellow>pos2"),
                    m.parse("<!italic><gray>Hold it to see zone outlines.")));
        });
        item.editPersistentDataContainer(pdc -> pdc.set(key, PersistentDataType.BYTE, (byte) 1));
        return item;
    }

    public boolean isWand(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(key);
    }
}
