package net.rose.elementSMPRefined.util.scan;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;

/**
 * Shared world-scanning helpers: the "find the floor of this column" scan used by the
 * ground-spawning abilities (Ice Spikes, Shockwave), and the chunk-loaded check the visuals use.
 * <p>
 * Behaves exactly like the copies it replaces, but only creates a {@link Block} for columns that
 * are actually solid, and never loads a chunk just to look for ground.
 */
public final class GroundScan {

    private GroundScan() {}

    /** True if the chunk containing {@code at} is loaded (never loads it). */
    public static boolean isLoaded(Location at) {
        return at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }

    /**
     * Topmost solid, non-special block with open air above it in the column, scanning from
     * {@code startY + up} down to {@code startY - down}.
     *
     * @return the ground block, or null if there is none, the chunk is not loaded, or the first
     *         candidate is unbreakable (bedrock, barrier) or a tile entity (chest, spawner, sign)
     */
    public static Block findGround(World world, int x, int z, int startY, int up, int down) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        int top = Math.min(maxY, startY + up);
        int bottom = Math.max(minY, startY - down);

        for (int y = top; y >= bottom; y--) {
            // getType(x, y, z) avoids allocating a Block for every air cell we pass through.
            Material type = world.getType(x, y, z);
            if (!type.isSolid()) continue;

            Block b = world.getBlockAt(x, y, z);
            if (!b.getRelative(0, 1, 0).isPassable()) continue;
            if (type.getHardness() < 0) return null;                 // bedrock, barriers, etc.
            if (b.getState(false) instanceof TileState) return null; // chests, spawners, signs
            return b;
        }
        return null;
    }
}
