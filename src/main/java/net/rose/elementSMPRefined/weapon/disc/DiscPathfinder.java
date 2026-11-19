package net.rose.elementSMPRefined.weapon.disc;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Collision and path finding helpers for the thrown disc. Port of the Fabric {@code DiscPathfinder}.
 * <ul>
 *   <li>{@link #sweep} is a width-aware block raycast: the disc is 0.6 wide, so the centre ray
 *       alone would let its edges clip into walls.</li>
 *   <li>{@link #findPath} runs an A* search over the block grid through cells with no collision.</li>
 * </ul>
 */
final class DiscPathfinder {
    /** Max nodes expanded per search; keeps a search cheap enough to run every few ticks. */
    private static final int MAX_NODES = 3000;
    /** Cells further than this from the search start are never considered. */
    private static final int MAX_RADIUS = 40;
    /** Half-width used for the corner rays (the disc is 0.6 wide, so 0.25 keeps a little slack). */
    private static final double HALF_WIDTH = 0.25;

    private static final double[][] CORNERS = {
        {HALF_WIDTH, HALF_WIDTH}, {HALF_WIDTH, -HALF_WIDTH},
        {-HALF_WIDTH, HALF_WIDTH}, {-HALF_WIDTH, -HALF_WIDTH}
    };
    private static final int[][] DIRS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private DiscPathfinder() {}

    /** A block hit, with the position translated to where the disc's centre stops. */
    record Hit(Vector position, BlockFace face, Block block) {}

    private record Cell(int x, int y, int z) {
        static Cell of(Vector v) {
            return new Cell((int) Math.floor(v.getX()), (int) Math.floor(v.getY()), (int) Math.floor(v.getZ()));
        }

        Cell offset(int dx, int dy, int dz) {
            return new Cell(x + dx, y + dy, z + dz);
        }

        Vector centre() {
            return new Vector(x + 0.5, y + 0.5, z + 0.5);
        }

        int manhattan(Cell other) {
            return Math.abs(x - other.x) + Math.abs(y - other.y) + Math.abs(z - other.z);
        }
    }

    private record Entry(Cell cell, double f) {}

    /** True if the disc can occupy this cell: inside the world, loaded, and nothing solid in it. */
    static boolean isFree(World world, Vector pos) {
        return isFree(world, Cell.of(pos));
    }

    private static boolean isFree(World world, Cell cell) {
        if (cell.y() < world.getMinHeight() || cell.y() >= world.getMaxHeight()) return false;
        if (!world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)) return false;
        return world.getBlockAt(cell.x(), cell.y(), cell.z()).isPassable();
    }

    /**
     * Sweeps the disc's footprint from {@code from} to {@code to}. Returns the nearest block hit with
     * its position translated to where the disc's centre stops, or null if the way is clear.
     */
    static Hit sweep(World world, Vector from, Vector to) {
        Hit nearest = clip(world, from, to, new Vector());
        double nearestDist = nearest == null ? Double.MAX_VALUE : from.distanceSquared(nearest.position());

        for (double[] corner : CORNERS) {
            Vector offset = new Vector(corner[0], 0.0, corner[1]);
            Vector start = from.clone().add(offset);
            if (!isFree(world, start)) {
                // This corner is already touching a block (we just slid or bounced off it); don't let it pin us.
                continue;
            }
            Hit hit = clip(world, start, to.clone().add(offset), offset);
            if (hit == null) continue;
            double dist = from.distanceSquared(hit.position());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = hit;
            }
        }
        return nearest;
    }

    /** Cheap single-ray check (no disc width). */
    static boolean lineClear(World world, Vector from, Vector to) {
        return clip(world, from, to, new Vector()) == null;
    }

    /** Centre of the nearest free cell to {@code pos} (its own cell, else a neighbour), or null. */
    static Vector freeCentre(World world, Vector pos) {
        Cell cell = nearestFree(world, Cell.of(pos));
        return cell == null ? null : cell.centre();
    }

    private static Hit clip(World world, Vector from, Vector to, Vector offset) {
        Vector delta = to.clone().subtract(from);
        double length = delta.length();
        if (length < 1.0E-6) return null;
        Vector direction = delta.clone().multiply(1.0 / length);
        RayTraceResult result =
                world.rayTraceBlocks(
                        new Location(world, from.getX(), from.getY(), from.getZ()),
                        direction,
                        length,
                        FluidCollisionMode.NEVER,
                        true);
        if (result == null || result.getHitBlock() == null) return null;
        BlockFace face = result.getHitBlockFace();
        if (face == null) face = faceAgainst(direction);
        Vector position = result.getHitPosition().clone().subtract(offset);
        return new Hit(position, face, result.getHitBlock());
    }

    /** The face that points back against a movement direction, for rays that start inside a block. */
    private static BlockFace faceAgainst(Vector direction) {
        double ax = Math.abs(direction.getX());
        double ay = Math.abs(direction.getY());
        double az = Math.abs(direction.getZ());
        if (ax >= ay && ax >= az) return direction.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
        if (ay >= az) return direction.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
        return direction.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }

    private static Cell nearestFree(World world, Cell cell) {
        if (isFree(world, cell)) return cell;
        for (int[] d : DIRS) {
            Cell next = cell.offset(d[0], d[1], d[2]);
            if (isFree(world, next)) return next;
        }
        return null;
    }

    /**
     * A* from {@code startPos} to {@code goalPos} through free block cells. Returns waypoints (cell
     * centres, start excluded) or null if there is no route. If the node budget runs out first, the
     * path to the explored cell closest to the goal is returned so the disc keeps making progress and
     * re-plans later.
     */
    static List<Vector> findPath(World world, Vector startPos, Vector goalPos) {
        Cell start = nearestFree(world, Cell.of(startPos));
        Cell goal = nearestFree(world, Cell.of(goalPos));
        if (start == null || goal == null) return null;
        if (start.equals(goal)) return new ArrayList<>(List.of(goal.centre()));

        PriorityQueue<Entry> open = new PriorityQueue<>((a, b) -> Double.compare(a.f(), b.f()));
        Map<Cell, Double> gScore = new HashMap<>();
        Map<Cell, Cell> parent = new HashMap<>();
        Set<Cell> closed = new HashSet<>();

        gScore.put(start, 0.0);
        open.add(new Entry(start, start.manhattan(goal)));

        Cell best = start;
        int bestH = start.manhattan(goal);
        int expanded = 0;

        while (!open.isEmpty() && expanded < MAX_NODES) {
            Cell current = open.poll().cell();
            if (!closed.add(current)) continue;
            if (current.equals(goal)) {
                best = goal;
                break;
            }
            expanded++;
            int h = current.manhattan(goal);
            if (h < bestH) {
                bestH = h;
                best = current;
            }
            double g = gScore.get(current);
            for (int[] d : DIRS) {
                Cell next = current.offset(d[0], d[1], d[2]);
                if (closed.contains(next)) continue;
                if (Math.abs(next.x() - start.x()) > MAX_RADIUS
                        || Math.abs(next.y() - start.y()) > MAX_RADIUS
                        || Math.abs(next.z() - start.z()) > MAX_RADIUS) {
                    continue;
                }
                if (!isFree(world, next)) continue;
                double ng = g + 1.0;
                Double old = gScore.get(next);
                if (old != null && old <= ng) continue;
                gScore.put(next, ng);
                parent.put(next, current);
                open.add(new Entry(next, ng + next.manhattan(goal)));
            }
        }

        if (best.equals(start)) return null;
        List<Vector> path = new ArrayList<>();
        Cell cell = best;
        while (!cell.equals(start)) {
            path.add(cell.centre());
            Cell p = parent.get(cell);
            if (p == null) break;
            cell = p;
        }
        Collections.reverse(path);
        return path;
    }
}
