package net.rose.elementSMPRefined.ability.passive.basic.fire.listeners;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.GameMode;
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
 * Fire Element Passive: Auto Smelt (also inherited by Lava)
 *
 * When mining ores, Fire and Lava element players automatically smelt the drops -
 * raw ore becomes ingots (and ancient debris becomes netherite scrap)
 * with no furnace or fuel needed. This is the base passive, active from
 * Upgrade I.
 *
 * Fortune still multiplies the output (vanilla loot table). Silk Touch bypasses
 * smelting entirely and drops the raw block as normal. Players can switch the
 * whole passive off/on with /autosmelt.
 */
public class FireSmeltListener implements Listener {

    private static final Map<Material, Material> SMELT_RESULTS = new EnumMap<>(Material.class);
    static {
        SMELT_RESULTS.put(Material.RAW_IRON, Material.IRON_INGOT);
        SMELT_RESULTS.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        SMELT_RESULTS.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        // SMELT_RESULTS.put(Material.ANCIENT_DEBRIS, Material.NETHERITE_SCRAP);
    }

    private final ElementManager elementManager;

    public FireSmeltListener(ElementManager elementManager) {
        this.elementManager = elementManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Creative breaks drop nothing in vanilla; dropping smelted ingots here duplicated items.
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        var playerData = elementManager.data(player.getUniqueId());

        if (playerData == null) {
            return;
        }

        // Fire, and Lava (upgraded from Fire, inherits this passive)
        ElementType current = playerData.getCurrentElement();
        if (current != ElementType.FIRE && current != ElementType.LAVA) {
            return;
        }

        // Player turned auto smelt off with /autosmelt - drops stay vanilla
        if (!playerData.isAutoSmeltEnabled()) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();

        // Silk Touch keeps the raw block/ore as-is - no smelting at all
        if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            return;
        }

        // Vanilla loot table with the real tool, so Fortune is applied by the game
        List<ItemStack> drops = List.copyOf(block.getDrops(tool, player));
        if (drops.isEmpty()) {
            return;
        }

        boolean anySmeltable = drops.stream().anyMatch(drop -> SMELT_RESULTS.containsKey(drop.getType()));
        if (!anySmeltable) {
            return;
        }

        event.setDropItems(false);
        for (ItemStack drop : drops) {
            Material smelted = SMELT_RESULTS.get(drop.getType());
            ItemStack toDrop = smelted != null ? new ItemStack(smelted, drop.getAmount()) : drop;
            block.getWorld().dropItemNaturally(block.getLocation(), toDrop);
        }
    }
}