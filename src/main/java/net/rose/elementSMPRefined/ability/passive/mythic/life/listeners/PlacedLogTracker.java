package net.rose.elementSMPRefined.ability.passive.mythic.life.listeners;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Remembers which logs were placed by players, so the Life tree chopper never treats them as part of a tree.
 * <p>
 * Positions are stored in each chunk's persistent data, so the record survives restarts and chunk unloads.
 * Logs placed before this tracker existed are not known and count as natural.
 */
public class PlacedLogTracker implements Listener {
    private record ChunkId(UUID world, long key) {}

    private final NamespacedKey storageKey;
    private final Map<ChunkId, Set<Long>> cache = new HashMap<>();

    public PlacedLogTracker(JavaPlugin plugin) {
        this.storageKey = new NamespacedKey(plugin, "placed_logs");
    }

    public boolean isPlaced(Block block) {
        return load(block.getChunk()).contains(pack(block));
    }

    private static long pack(Block block) {
        return ((long) (block.getY() + 4096) << 8) | ((block.getX() & 15) << 4) | (block.getZ() & 15);
    }

    private Set<Long> load(Chunk chunk) {
        return cache.computeIfAbsent(new ChunkId(chunk.getWorld().getUID(), chunk.getChunkKey()), id -> {
            Set<Long> set = new HashSet<>();
            long[] stored = chunk.getPersistentDataContainer().get(storageKey, PersistentDataType.LONG_ARRAY);
            if (stored != null) {
                for (long value : stored) set.add(value);
            }
            return set;
        });
    }

    private void save(Chunk chunk, Set<Long> set) {
        if (set.isEmpty()) {
            chunk.getPersistentDataContainer().remove(storageKey);
            return;
        }
        long[] array = new long[set.size()];
        int i = 0;
        for (long value : set) array[i++] = value;
        chunk.getPersistentDataContainer().set(storageKey, PersistentDataType.LONG_ARRAY, array);
    }

    private void mark(Block block) {
        Chunk chunk = block.getChunk();
        Set<Long> set = load(chunk);
        if (set.add(pack(block))) save(chunk, set);
    }

    private void unmark(Block block) {
        Chunk chunk = block.getChunk();
        Set<Long> set = load(chunk);
        if (set.remove(pack(block))) save(chunk, set);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (Tag.LOGS.isTagged(block.getType())) mark(block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        unmark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        unmark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::unmark);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::unmark);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        follow(event.getBlock(), event.getBlocks(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        follow(event.getBlock(), event.getBlocks(), false);
    }

    /** A placed log pushed or pulled by a piston stays a placed log at its new position. */
    private void follow(Block piston, List<Block> moved, boolean extending) {
        if (!(piston.getBlockData() instanceof Directional directional)) return;
        BlockFace move = extending ? directional.getFacing() : directional.getFacing().getOppositeFace();

        List<Block> tracked = moved.stream()
                .filter(block -> Tag.LOGS.isTagged(block.getType()) && isPlaced(block))
                .toList();
        tracked.forEach(this::unmark);
        tracked.forEach(block -> mark(block.getRelative(move)));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        cache.remove(new ChunkId(chunk.getWorld().getUID(), chunk.getChunkKey()));
    }
}
