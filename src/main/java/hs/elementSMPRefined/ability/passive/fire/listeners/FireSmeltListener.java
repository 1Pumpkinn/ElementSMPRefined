package hs.elementSMPRefined.ability.passive.fire.listeners;

import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Fire Element Passive: Auto Smelt
 *
 * When mining ores, Fire element players automatically smelt the drops -
 * raw ore becomes ingots (and ancient debris becomes netherite scrap)
 * with no furnace or fuel needed. This is the base passive, active from
 * Upgrade I.
 *
 * Fortune still multiplies the output. Silk Touch bypasses smelting
 * entirely and drops the raw block as normal.
 */
public class FireSmeltListener implements Listener {

    // Raw block drop -> smelted equivalent
    private static final Map<Material, Material> SMELT_RESULTS = new EnumMap<>(Material.class);
    static {
        SMELT_RESULTS.put(Material.RAW_IRON, Material.IRON_INGOT);
        SMELT_RESULTS.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        SMELT_RESULTS.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        SMELT_RESULTS.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
    }

    private final ElementManager elementManager;

    public FireSmeltListener(ElementManager elementManager) {
        this.elementManager = elementManager;
    }

    /**
     * Auto-smelt ore drops for Fire element players.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        var playerData = elementManager.data(player.getUniqueId());

        // Null safety check
        if (playerData == null) {
            return;
        }

        // Check if player has Fire element
        if (playerData.getCurrentElement() != ElementType.FIRE) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();

        // Silk Touch keeps the raw block/ore as-is - no smelting
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }

        // Vanilla drops for this block (fortune already applied)
        List<ItemStack> drops = List.copyOf(block.getDrops(tool));
        if (drops.isEmpty()) {
            return;
        }

        boolean anySmeltable = drops.stream().anyMatch(drop -> SMELT_RESULTS.containsKey(drop.getType()));
        if (!anySmeltable) {
            return;
        }

        // Replace vanilla drops with the smelted versions
        event.setDropItems(false);
        for (ItemStack drop : drops) {
            Material smelted = SMELT_RESULTS.get(drop.getType());
            ItemStack toDrop = smelted != null ? new ItemStack(smelted, drop.getAmount()) : drop;
            block.getWorld().dropItemNaturally(block.getLocation(), toDrop);
        }
    }
}