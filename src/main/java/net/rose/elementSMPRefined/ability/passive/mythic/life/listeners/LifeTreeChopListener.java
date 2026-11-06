package net.rose.elementSMPRefined.ability.passive.mythic.life.listeners;

import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
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
 * logs built next to a tree is left alone. Every extra log costs the axe durability like a normal break, but
 * it is charged in one go when the chop ends: changing the held item every couple of ticks makes the client
 * drop the block the player is mining, so nothing else could be mined while a tree came down.
 * <p>
 * The tree comes down as an animation: one log breaks every {@link #TICKS_BETWEEN_LOGS} ticks, spreading out
 * from the log that was hit, with break particles and sound for each.
 */
public class LifeTreeChopListener implements Listener {
    private static final int MAX_LOGS = 256;
    private static final int MAX_HORIZONTAL_RADIUS = 12;

    /**
     * Dark oaks generate small groups of branch logs around where the trunk started, but the trunk leans away
     * from there, so these logs float with a gap of up to 3 blocks to the trunk. This is how far to look
     * for them from the logs of the tree.
     */
    private static final int DARK_OAK_BRANCH_REACH = 4;
    /** A floating group bigger than this is another tree, not a branch. */
    private static final int MAX_BRANCH_GROUP = 48;

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

        // The block being broken by this event still costs the axe one durability; the rest must fit in what's left.
        int budget = remainingDurability(tool) - 1;
        if (budget <= 0) return;
        if (logs.size() > budget) logs = new ArrayList<>(logs.subList(0, budget));

        chopping.addAll(logs);
        startChopAnimation(player, origin.getType(), logs);
    }

    private static int remainingDurability(ItemStack tool) {
        if (!(tool.getItemMeta() instanceof Damageable meta) || meta.isUnbreakable()) return Integer.MAX_VALUE;
        int max = meta.hasMaxDamage() ? meta.getMaxDamage() : tool.getType().getMaxDurability();
        return max <= 0 ? Integer.MAX_VALUE : max - meta.getDamage();
    }

    /** Breaks {@code logs} (already in spread-out order) one per {@link #TICKS_BETWEEN_LOGS} ticks. */
    private void startChopAnimation(Player player, Material type, List<Block> logs) {
        final int slot = player.getInventory().getHeldItemSlot();
        new BukkitRunnable() {
            private int index = 0;
            private int broken = 0;

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
                broken++;
            }

            private void finish() {
                logs.forEach(chopping::remove);
                cancel();
                chargeAxe();
            }

            private void chargeAxe() {
                if (broken == 0 || !player.isOnline()) return;
                ItemStack held = player.getInventory().getItem(slot);
                if (held == null || !Tag.ITEMS_AXES.isTagged(held.getType())) return;
                player.getInventory().setItem(slot, held.damage(broken, player));
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

        if (!touchesNaturalLeaves) return List.of();
        if (type == Material.DARK_OAK_LOG) {
            addFloatingBranches(origin, type, found);
        }
        return found;
    }

    /**
     * Adds the small groups of logs that hang in a dark oak's canopy without touching the trunk. A group only
     * counts if it touches natural leaves, is small, and does not stand on the ground (which would make it
     * the trunk of a neighbouring tree).
     */
    private void addFloatingBranches(Block origin, Material type, List<Block> found) {
        List<Block> sources = new ArrayList<>(found);
        sources.add(origin);
        Set<Block> seen = new HashSet<>(sources);

        for (Block source : sources) {
            for (int x = -DARK_OAK_BRANCH_REACH; x <= DARK_OAK_BRANCH_REACH; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -DARK_OAK_BRANCH_REACH; z <= DARK_OAK_BRANCH_REACH; z++) {
                        Block candidate = source.getRelative(x, y, z);
                        if (candidate.getType() != type || seen.contains(candidate)) continue;
                        if (placedLogs.isPlaced(candidate)) continue;

                        List<Block> group = collectFloatingGroup(candidate, type, seen);
                        if (group == null) continue;
                        for (Block log : group) {
                            if (found.size() >= MAX_LOGS) return;
                            if (Math.abs(log.getX() - origin.getX()) > MAX_HORIZONTAL_RADIUS
                                    || Math.abs(log.getZ() - origin.getZ()) > MAX_HORIZONTAL_RADIUS) continue;
                            found.add(log);
                        }
                    }
                }
            }
        }
    }

    /** Every touching log of {@code type} around {@code start}, or null if it is not a floating branch group. */
    private List<Block> collectFloatingGroup(Block start, Material type, Set<Block> seen) {
        List<Block> group = new ArrayList<>();
        Queue<Block> queue = new LinkedList<>();
        seen.add(start);
        group.add(start);
        queue.add(start);

        boolean grounded = false;
        boolean touchesNaturalLeaves = false;
        boolean tooBig = false;
        while (!queue.isEmpty()) {
            Block current = queue.poll();

            Material below = current.getRelative(BlockFace.DOWN).getType();
            if (below.isSolid() && !Tag.LOGS.isTagged(below) && !Tag.LEAVES.isTagged(below)) {
                grounded = true;
            }

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        Block next = current.getRelative(x, y, z);
                        if (isNaturalLeaf(next)) {
                            touchesNaturalLeaves = true;
                            continue;
                        }
                        if (next.getType() != type || placedLogs.isPlaced(next) || !seen.add(next)) continue;
                        if (group.size() >= MAX_BRANCH_GROUP) {
                            tooBig = true;
                            continue;
                        }
                        group.add(next);
                        queue.add(next);
                    }
                }
            }
        }

        return tooBig || grounded || !touchesNaturalLeaves ? null : group;
    }

    private static boolean isNaturalLeaf(Block block) {
        return Tag.LEAVES.isTagged(block.getType())
                && block.getBlockData() instanceof Leaves leaves
                && !leaves.isPersistent();
    }
}
