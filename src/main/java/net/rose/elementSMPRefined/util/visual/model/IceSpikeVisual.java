package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.items.builder.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
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
 * One Frost ice spike cluster, drawn with a resource-pack MODEL shown by an {@link ItemDisplay}.
 * <p>
 * There are two hand-built variants ({@code elementsmp:ice_spike_a/b}, sources in
 * {@code blockbench/}), each a tall main spike with smaller shards leaning out beside it and a few
 * low chunks of ice at the base. One is picked at random per spike and given a random facing,
 * width and height, so a ring of them never looks copy-pasted.
 * <p>
 * The whole life cycle lives in here: the spike bursts up out of the ground, holds, sinks back and
 * removes itself. Growth is a client-side interpolated transformation (same approach as
 * {@link TornadoVisual}), planted by the BASE of the model - position it at the ground contact
 * point and the visual works out where the display's centre goes.
 * <p>
 * If you edit a model's height in Blockbench, update {@link #MODEL_UNITS} to match (tallest point
 * of the model, in model units, 16 = 1 block).
 * <p>
 * Spikes remove themselves, but call {@link #removeAll()} on plugin disable as a safety net.
 */
public final class IceSpikeVisual {

    private static final NamespacedKey[] MODELS = {
            key("elementsmp:ice_spike_a"),
            key("elementsmp:ice_spike_b")
    };

    /** Tallest point of each model above its base, in model units (16 per block). Mirrors the models. */
    private static final double[] MODEL_UNITS = {29, 24};

    private static final int GROW_TICKS = 3;
    private static final int HOLD_TICKS = 40;
    private static final int SHRINK_TICKS = 6;

    /** Never scale to exactly zero - a degenerate transformation can render glitchy. */
    private static final float COLLAPSED = 0.05f;

    /** Every live spike display, so plugin disable can sweep up anything still on screen. */
    private static final Set<ItemDisplay> ACTIVE = new HashSet<>();

    private IceSpikeVisual() {}

    /**
     * Spawns a spike cluster and runs its whole life cycle.
     *
     * @param base   where the spike's bottom touches the ground
     * @param height how tall the tallest point should be, in blocks
     */
    public static void spawn(JavaPlugin plugin, Location base, double height) {
        World world = base.getWorld();
        if (world == null) return;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int variant = rnd.nextInt(MODELS.length);

        float sy = (float) (height / (MODEL_UNITS[variant] / 16.0));
        float sw = sy * (float) (0.9 + rnd.nextDouble() * 0.2);
        float yaw = (float) (rnd.nextDouble() * Math.PI * 2);

        // The model's centre (8, 8, 8) sits on the display, and its base is 8 units below that,
        // so the display goes half a (scaled) block above the ground.
        Location centre = new Location(world, base.getX(), base.getY() + 0.5 * sy, base.getZ(), 0f, 0f);

        // The base material is irrelevant - the item_model component replaces its model entirely.
        ItemStack modelItem = ItemBuilder.of(Material.STICK).itemModel(MODELS[variant]).build();

        ItemDisplay display = world.spawn(centre, ItemDisplay.class, d -> {
            d.setItemStack(modelItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(pose(sw, sy, yaw, COLLAPSED));
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
        ACTIVE.add(display);

        world.spawnParticle(Particle.BLOCK, base.clone().add(0, 0.1, 0), 6, 0.3, 0.1, 0.3, 0.0,
                Material.PACKED_ICE.createBlockData(), true);
        world.spawnParticle(Particle.SNOWFLAKE, base.clone().add(0, 0.5, 0), 6, 0.3, 0.4, 0.3, 0.02, null, true);

        // Interpolation only animates if the transformation changes after the entity has been
        // sent to clients, so the growth starts a tick later.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid()) return;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(GROW_TICKS);
            display.setTransformation(pose(sw, sy, yaw, 1.0f));
        }, 1L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!display.isValid()) return;
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(SHRINK_TICKS);
            display.setTransformation(pose(sw, sy, yaw, COLLAPSED));
        }, 1L + GROW_TICKS + HOLD_TICKS);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) display.remove();
            ACTIVE.remove(display);
            world.spawnParticle(Particle.BLOCK, base.clone().add(0, 0.5, 0), 10, 0.4, 0.5, 0.4, 0.0,
                    Material.ICE.createBlockData(), true);
        }, 1L + GROW_TICKS + HOLD_TICKS + SHRINK_TICKS + 2L);
    }

    /** Safety net for plugin disable - removes every spike still on screen. */
    public static void removeAll() {
        for (ItemDisplay display : new ArrayList<>(ACTIVE)) {
            if (display.isValid()) display.remove();
        }
        ACTIVE.clear();
    }

    /**
     * Pose at a given growth. Scaling happens around the model's centre (mid-height), so the display
     * is dropped by however much the spike shrank to keep its base planted on the ground. Width
     * eases in more slowly than height so a half-grown spike reads as rising, not as a squashed disc.
     *
     * @param growth 1.0 = fully grown, {@link #COLLAPSED} = sunk into the ground
     */
    private static Transformation pose(float width, float height, float yaw, float growth) {
        float w = width * (0.5f + 0.5f * growth);
        float h = height * growth;
        float lift = -0.5f * height * (1.0f - growth);
        return new Transformation(
                new Vector3f(0f, lift, 0f),
                new Quaternionf().rotateY(yaw),
                new Vector3f(w, h, w),
                new Quaternionf());
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString(value));
    }
}
