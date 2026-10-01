package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.items.builder.ItemBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A chain drawn with a resource-pack MODEL instead of particles.
 * <p>
 * The chain is a row of {@link ItemDisplay} entities, each showing the 1-block-long chain
 * model (long axis = Y, see {@code assets/elementsmp/models/item/hell_chain.json}). Every
 * call to {@link #update(Location, Location)} re-lays the row between two points:
 * <ul>
 *     <li>The number of segments follows the distance (about one per block), so links keep
 *     roughly their natural proportions instead of being stretched into one long smear.</li>
 *     <li>Segments are pooled - spawned when the chain grows, removed when it shrinks, and
 *     otherwise just moved/re-rotated - so a chain that's redrawn every tick doesn't churn
 *     through entities.</li>
 *     <li>Teleport + transformation interpolation are set to 1 tick so a chain being dragged
 *     around by a fast-moving caster stays smooth on the client.</li>
 *     <li>Segments are non-persistent and fullbright, so they never get saved to disk and
 *     the chain glows regardless of the light level around it.</li>
 * </ul>
 * Without the matching resource pack, clients just see the missing-model placeholder - the
 * pack has to be applied for the chain to look right.
 * <p>
 * Always call {@link #remove()} when the effect ends (or {@link #removeAll()} on plugin
 * disable as a safety net) - segments are real entities and will otherwise hang around.
 */
public final class ChainVisual {

    /** {@code assets/elementsmp/items/hell_chain.json} in the resource pack. */
    public static final NamespacedKey HELL_CHAIN =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:hell_chain"));

    /** Aim for roughly one segment per block of chain. */
    private static final double SEGMENT_LENGTH = 1.0;
    /** Hard cap so an absurd distance can never spawn an absurd number of entities. */
    private static final int MAX_SEGMENTS = 48;
    /** Below this the chain is too short to be worth drawing at all. */
    private static final double MIN_LENGTH = 0.3;
    /** Widens the 3px-wide vanilla-style chain so it reads at a distance. */
    private static final float THICKNESS = 1.5f;
    /** The model's long axis. */
    private static final Vector3f MODEL_AXIS = new Vector3f(0f, 1f, 0f);

    /** Every live visual, so plugin disable can sweep up anything still on screen. */
    private static final Set<ChainVisual> ACTIVE = new HashSet<>();

    private final ItemStack modelItem;
    /** Entries may be null where a segment couldn't be spawned (unloaded chunk) - retried next update. */
    private final List<ItemDisplay> segments = new ArrayList<>();

    public ChainVisual(NamespacedKey model) {
        // The base material is irrelevant - the item_model component replaces its model entirely.
        this.modelItem = ItemBuilder.of(Material.STICK).itemModel(model).build();
        ACTIVE.add(this);
    }

    /** Lays the chain out from {@code from} to {@code to}, spawning/moving/removing segments as needed. */
    public void update(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || !world.equals(to.getWorld())) {
            hide();
            return;
        }

        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < MIN_LENGTH) {
            hide();
            return;
        }

        int count = Math.min(MAX_SEGMENTS, Math.max(1, (int) Math.round(length / SEGMENT_LENGTH)));
        float segmentLength = (float) (length / count);

        Vector3f direction = new Vector3f(
                (float) (delta.getX() / length),
                (float) (delta.getY() / length),
                (float) (delta.getZ() / length));

        // Scale is applied in model space (Y = chain axis) BEFORE the rotation, so stretching Y
        // lengthens the segment and the left rotation then points it along the chain.
        Transformation transformation = new Transformation(
                new Vector3f(),
                new Quaternionf().rotationTo(MODEL_AXIS, direction),
                new Vector3f(THICKNESS, segmentLength, THICKNESS),
                new Quaternionf());

        while (segments.size() > count) {
            removeSegment(segments.remove(segments.size() - 1));
        }

        for (int i = 0; i < count; i++) {
            // Yaw/pitch left at 0 on purpose: a display's own rotation stacks with its
            // transformation, and all orientation is handled by the transformation above.
            Location at = new Location(world,
                    from.getX() + delta.getX() * ((i + 0.5) / count),
                    from.getY() + delta.getY() * ((i + 0.5) / count),
                    from.getZ() + delta.getZ() * ((i + 0.5) / count),
                    0f, 0f);

            ItemDisplay display = i < segments.size() ? segments.get(i) : null;
            if (display == null || !display.isValid()) {
                display = isLoaded(at) ? spawn(at, transformation) : null;
                if (i < segments.size()) {
                    segments.set(i, display);
                } else {
                    segments.add(display);
                }
                continue; // freshly spawned with the right transform already
            }

            display.teleport(at);
            display.setTransformation(transformation);
            display.setInterpolationDelay(0);
        }
    }

    /** Removes the segments but keeps this visual usable - a later {@link #update} respawns them. */
    public void hide() {
        for (ItemDisplay display : segments) {
            removeSegment(display);
        }
        segments.clear();
    }

    /** Removes everything and unregisters. Call when the effect is over. */
    public void remove() {
        hide();
        ACTIVE.remove(this);
    }

    /** Safety net for plugin disable - removes every chain still on screen. */
    public static void removeAll() {
        for (ChainVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private ItemDisplay spawn(Location at, Transformation transformation) {
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(modelItem);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setBillboard(Display.Billboard.FIXED);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(transformation);
            display.setTeleportDuration(1);
            display.setInterpolationDuration(1);
            display.setPersistent(false);
            display.setInvulnerable(true);
        });
    }

    private static void removeSegment(ItemDisplay display) {
        if (display != null && display.isValid()) {
            display.remove();
        }
    }

    /** Spawning into an unloaded chunk would force a synchronous load - just skip that segment instead. */
    private static boolean isLoaded(Location at) {
        return at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }
}