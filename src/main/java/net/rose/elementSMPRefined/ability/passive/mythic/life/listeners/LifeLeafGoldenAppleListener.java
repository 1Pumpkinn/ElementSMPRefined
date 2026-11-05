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
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Life passive 1: every leaf block a Life player breaks has a {@link #GOLDEN_APPLE_CHANCE} chance to drop a
 * golden apple, on top of the normal leaf drops (saplings, sticks, apples).
 */
public class LifeLeafGoldenAppleListener implements Listener {
    /** Chance (0.0 - 1.0) per leaf block broken. 0.005 = 0.5%. */
    private static final double GOLDEN_APPLE_CHANCE = 0.005;

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
        if (ThreadLocalRandom.current().nextDouble() >= GOLDEN_APPLE_CHANCE) return;

        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                new ItemStack(Material.GOLDEN_APPLE, 1));
    }

    private boolean isLife(Player player) {
        var data = elementManager.data(player.getUniqueId());
        return data != null && data.getCurrentElement() == ElementType.LIFE;
    }
}
