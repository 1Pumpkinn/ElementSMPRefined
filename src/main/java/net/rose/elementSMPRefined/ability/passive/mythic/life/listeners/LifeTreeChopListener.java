package net.rose.elementSMPRefined.ability.passive.mythic.life.listeners;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Life passive 2: breaking a log of a tree with an axe chops down the whole tree.
 * <p>
 * Only trees count: the connected logs must touch leaves that grew naturally (not player-placed), and logs
 * a player placed (tracked by {@link PlacedLogTracker}) are never chopped, so a log cabin or a stack of
 * logs built next to a tree is left alone. Every extra log costs the axe durability like a normal break.
 * <p>
 * The tree comes down as an animation: one log breaks every {@link #TICKS_BETWEEN_LOGS} ticks, spreading out
 * from the log that was hit, with break particles and sound for each.
 */
public class LifeTreeChopListener implements Listener {
    private static final int MAX_LOGS = 256;
    private static final int MAX_HORIZONTAL_RADIUS = 12;

    /** Delay between one log breaking and the next. Lower = faster chop. */
    private static final long TICKS_BETWEEN_LOGS = 2L;

    private final JavaPlugin plugin;
    private final ElementManager elementManager;
    private final PlacedLogTracker placedLogs;
    /** Logs queued by a running chop animation, so a second chop can't claim them. */
    private final Set<Block> chopping = new HashSet<>();

    public LifeTreeChopListener(JavaPlugin plugin, ElementManager elementManager, PlacedLogTracker placedLogs) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        this.placedLogs = placedLogs;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block origin = event.getBlock();

        // Creative drops nothing for the first block but breakNaturally() would drop the rest.
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!Tag.LOGS.isTagged(origin.getType())) return;
        // A log a player placed is just a block they built with, not a tree.
        if (placedLogs.isPlaced(origin)) return;
        // Already queued by a running chop: just break it normally.
        if (chopping.contains(origin)) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!Tag.ITEMS_AXES.isTagged(tool.getType())) return;

        var data = elementManager.data(player.getUniqueId());
        if (data == null || data.getCurrentElement() != ElementType.LIFE) return;

        List<Block> logs = collectTree(origin);
        if (logs.isEmpty()) return;

        // The block being broken by this event already cost the axe its durability; the rest break one by one.
        chopping.addAll(logs);
        startChopAnimation(player, origin.getType(), logs);
    }

    /** Breaks {@code logs} (already in spread-out order) one per {@link #TICKS_BETWEEN_LOGS} ticks. */
    private void startChopAnimation(Player player, Material type, List<Block> logs) {
        new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                ItemStack tool = player.getInventory().getItemInMainHand();
                if (index >= logs.size() || !player.isOnline() || player.isDead()
                        || !Tag.ITEMS_AXES.isTagged(tool.getType())) {
                    finish();
                    return;
                }

                Block log = logs.get(index++);
                chopping.remove(log);
                // Changed since the chop started (broken, replaced, or now player-placed): skip it.
                if (log.getType() != type || placedLogs.isPlaced(log)) return;

                playBreakEffect(log);
                log.breakNaturally(tool);
                player.getInventory().setItemInMainHand(tool.damage(1, player));
            }

            private void finish() {
                logs.forEach(chopping::remove);
                cancel();
            }
        }.runTaskTimer(plugin, TICKS_BETWEEN_LOGS, TICKS_BETWEEN_LOGS);
    }

    /** breakNaturally() is silent and particle-less, so give each log the normal break feedback. */
    private static void playBreakEffect(Block log) {
        Location center = log.getLocation().add(0.5, 0.5, 0.5);
        var data = log.getBlockData();
        log.getWorld().spawnParticle(Particle.BLOCK, center, 20, 0.3, 0.3, 0.3, 0.05, data);
        log.getWorld().playSound(center, data.getSoundGroup().getBreakSound(), 1.0f, 1.0f);
    }

    /**
     * Logs of the same type connected to {@code origin} (origin excluded), or an empty list if this isn't a
     * natural tree.
     */
    private List<Block> collectTree(Block origin) {
        Material type = origin.getType();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        List<Block> found = new ArrayList<>();
        visited.add(origin);
        queue.add(origin);

        boolean touchesNaturalLeaves = false;
        while (!queue.isEmpty() && found.size() < MAX_LOGS) {
            Block current = queue.poll();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Block next = current.getRelative(x, y, z);
                        if (!visited.add(next)) continue;

                        if (isNaturalLeaf(next)) {
                            touchesNaturalLeaves = true;
                            continue;
                        }
                        if (next.getType() != type) continue;
                        // Player-placed logs are never chopped, nor used to reach logs beyond them.
                        if (placedLogs.isPlaced(next)) continue;
                        if (Math.abs(next.getX() - origin.getX()) > MAX_HORIZONTAL_RADIUS
                                || Math.abs(next.getZ() - origin.getZ()) > MAX_HORIZONTAL_RADIUS) continue;

                        found.add(next);
                        queue.add(next);
                    }
                }
            }
        }

        return touchesNaturalLeaves ? found : List.of();
    }

    private static boolean isNaturalLeaf(Block block) {
        return Tag.LEAVES.isTagged(block.getType())
                && block.getBlockData() instanceof Leaves leaves
                && !leaves.isPersistent();
    }
}
