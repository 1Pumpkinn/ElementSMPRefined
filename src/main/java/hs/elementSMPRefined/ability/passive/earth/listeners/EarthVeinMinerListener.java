package hs.elementSMPRefined.ability.passive.earth.listeners;

import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Earth Vein Miner - Breaks connected ore blocks when mining ores
 * Passive ability for Earth element
 */
public class EarthVeinMinerListener implements Listener {
    private final ElementManager elementManager;

    private static final Set<Material> ORES = EnumSet.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS
    );

    public EarthVeinMinerListener(ElementManager elementManager) {
        this.elementManager = elementManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        var playerData = elementManager.data(player.getUniqueId());
        if (playerData.getCurrentElement() != ElementType.EARTH) {
            return;
        }

        // Check if it's an ore
        if (!ORES.contains(block.getType())) {
            return;
        }

        // Break connected ore blocks
        breakConnectedOres(block, player);
    }

    private void breakConnectedOres(Block startBlock, Player player) {
        Material oreType = startBlock.getType();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        ItemStack tool = player.getInventory().getItemInMainHand();
        
        // Start with adjacent blocks (don't include start block since it's already being broken)
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    Block adjacent = startBlock.getRelative(x, y, z);
                    if (adjacent.getType() == oreType) {
                        queue.add(adjacent);
                        visited.add(adjacent);
                    }
                }
            }
        }

        int maxBlocks = 15; // Maximum additional blocks to break per vein mine
        int blocksBroken = 0;

        while (!queue.isEmpty() && blocksBroken < maxBlocks) {
            Block current = queue.poll();

            // Break the current block naturally (preserves fortune enchantment)
            current.breakNaturally(tool);
            blocksBroken++;

            // Check all adjacent blocks and add to queue
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;

                        Block adjacent = current.getRelative(x, y, z);
                        
                        if (visited.contains(adjacent)) continue;
                        if (adjacent.getType() != oreType) continue;

                        visited.add(adjacent);
                        queue.add(adjacent);
                    }
                }
            }
        }
    }
}
