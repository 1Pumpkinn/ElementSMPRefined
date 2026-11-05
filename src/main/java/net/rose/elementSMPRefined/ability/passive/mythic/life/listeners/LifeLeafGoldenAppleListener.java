package net.rose.elementSMPRefined.ability.passive.mythic.life.listeners;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Life passive 1: every leaf block a Life player breaks drops exactly one golden apple.
 * <p>
 * The golden apple is guaranteed (no chance roll). Vanilla apple drops from the same break are removed so
 * the player doesn't get a regular apple on top; saplings and sticks still drop as normal.
 */
public class LifeLeafGoldenAppleListener implements Listener {
    private final ElementManager elementManager;

    public LifeLeafGoldenAppleListener(ElementManager elementManager) {
        this.elementManager = elementManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeafBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!event.isDropItems()) return;
        if (!Tag.LEAVES.isTagged(block.getType())) return;
        if (!isLife(player)) return;

        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                new ItemStack(Material.GOLDEN_APPLE, 1));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeafDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!Tag.LEAVES.isTagged(event.getBlockState().getType())) return;
        if (!isLife(player)) return;

        event.getItems().removeIf(drop -> drop.getItemStack().getType() == Material.APPLE);
    }

    private boolean isLife(Player player) {
        var data = elementManager.data(player.getUniqueId());
        return data != null && data.getCurrentElement() == ElementType.LIFE;
    }
}
