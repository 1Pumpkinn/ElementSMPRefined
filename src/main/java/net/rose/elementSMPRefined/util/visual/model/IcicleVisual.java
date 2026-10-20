package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.particles.FrostParticles;
import net.rose.elementSMPRefined.items.builder.ModelItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One ice spike styled after Mojang's 26.4 icicle, built from resource-pack models so it works on 26.2.
 * <p>
 * Like a pointed-dripstone stalagmite, a spike of height N is a stack of N segments that thin out toward
 * the point: {@code icicle_base -> icicle_middle -> icicle_frustum -> icicle_tip}. Each segment is its own
 * {@link ItemDisplay} showing one of those models ({@code elementsmp:icicle_*}, files under
 * {@code assets/elementsmp/models/item/}). The segments shoot up one after another, hold, and sink back.
 * No real blocks are ever placed, so there is nothing to clean up if the server stops.
 * <p>
 * Same signature as {@link IceSpikeVisual#spawn}, so either can be swapped in anywhere.
 */
public final class IcicleVisual {

    private static final NamespacedKey BASE = key("elementsmp:icicle_base");
    private static final NamespacedKey MIDDLE = key("elementsmp:icicle_middle");
    private static final NamespacedKey FRUSTUM = key("elementsmp:icicle_frustum");
    private static final NamespacedKey TIP = key("elementsmp:icicle_tip");

    private static final int GROW_TICKS = 3;
    private static final int HOLD_TICKS = 40;
    private static final int SHRINK_TICKS = 6;
    /** Gap between one segment starting to rise and the next, so the spike shoots up. */
    private static final int STAGGER_TICKS = 1;

    /** Never scale to exactly zero - a degenerate transformation can render glitchy. */
    private static final float COLLAPSED = 0.05f;

    private static final Set<ItemDisplay> ACTIVE = new HashSet<>();

    private IcicleVisual() {}

    /**
     * Spawns an icicle and runs its whole life cycle.
     *
     * @param base   where the spike's bottom touches the ground
     * @param height how tall the spike should be, in blocks
     */
    public static void spawn(JavaPlugin plugin, Location base, double height) {
        World world = base.getWorld();
        if (world == null) return;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int count = Math.max(1, (int) Math.round(height));
        float segmentHeight = (float) (height / count);
        // A little width jitter keeps a field of spikes from looking cloned.
        float width = (float) (0.95 + rnd.nextDouble() * 0.25);

        long shrinkAt = 1L + GROW_TICKS + (long) (count - 1) * STAGGER_TICKS + HOLD_TICKS;

        for (int i = 0; i < count; i++) {
            final int index = i;
            // The base material is irrelevant - the item_model component replaces its model entirely.
            final ItemStack item = ModelItems.of(modelFor(i, count));
            // The display's centre sits half a (scaled) segment above where the segment starts.
            final Location centre = new Location(world, base.getX(),
                    base.getY() + i * segmentHeight + 0.5 * segmentHeight, base.getZ(), 0f, 0f);
            final long spawnAt = (long) i * STAGGER_TICKS;

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemDisplay display = world.spawn(centre, ItemDisplay.class, d -> {
                    d.setItemStack(item);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTransformation(pose(width, segmentHeight, COLLAPSED));
                    d.setPersistent(false);
                    d.setInvulnerable(true);
                });
                ACTIVE.add(display);

                if (index == 0) {
                    FrostParticles.icicleSpawn(world, base);
                }

                // Interpolation only animates if the transformation changes after the entity has been
                // sent to clients, so the growth starts a tick later.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!display.isValid()) return;
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(GROW_TICKS);
                    display.setTransformation(pose(width, segmentHeight, 1.0f));
                }, 1L);

                // Every segment sinks at the same moment, so the spike never floats half-gone.
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!display.isValid()) return;
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(SHRINK_TICKS);
                    display.setTransformation(pose(width, segmentHeight, COLLAPSED));
                }, shrinkAt - spawnAt);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (display.isValid()) display.remove();
                    ACTIVE.remove(display);
                    if (index == 0) {
                        FrostParticles.icicleShatter(world, base);
                    }
                }, shrinkAt - spawnAt + SHRINK_TICKS + 2L);
            }, spawnAt);
        }
    }

    /** Safety net for plugin disable - removes every icicle still on screen. */
    public static void removeAll() {
        for (ItemDisplay display : new ArrayList<>(ACTIVE)) {
            if (display.isValid()) display.remove();
        }
        ACTIVE.clear();
    }

    /** Tip on top, then frustum, then middles, base at the bottom. */
    private static NamespacedKey modelFor(int index, int count) {
        if (index == count - 1) return TIP;
        if (index == count - 2) return FRUSTUM;
        if (index == 0) return BASE;
        return MIDDLE;
    }

    /**
     * Pose at a given growth. Scaling happens around the segment's centre, so the display is dropped by
     * however much the segment shrank to keep its bottom planted where it started.
     *
     * @param growth 1.0 = fully grown, {@link #COLLAPSED} = sunk down
     */
    private static Transformation pose(float width, float height, float growth) {
        float w = width * (0.5f + 0.5f * growth);
        float h = height * growth;
        float lift = -0.5f * height * (1.0f - growth);
        return new Transformation(
                new Vector3f(0f, lift, 0f),
                new Quaternionf(),
                new Vector3f(w, h, w),
                new Quaternionf());
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString(value));
    }
}
