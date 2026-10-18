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
 * One Frost ice spike, drawn with the SAME resource-pack model as Icicle Barrage
 * ({@code elementsmp:icicle_tip}, the pointed-dripstone shape wearing the icicle texture) shown by an
 * {@link ItemDisplay}.
 * <p>
 * That model's sprite hangs point-DOWN (wide end at the top of the model, point 5 of 16 units below its
 * centre), so for a spike standing on the ground it is flipped upside down: wide end in the floor, point
 * in the air. Each spike gets a random facing and a little width jitter so a ring of them never looks
 * copy-pasted.
 * <p>
 * The whole life cycle lives in here: the spike bursts up out of the ground, holds, sinks back and
 * removes itself. Growth is a client-side interpolated transformation (same approach as
 * {@link TornadoVisual}), planted by the BASE of the model - position it at the ground contact point and
 * the visual works out where the display's centre goes.
 * <p>
 * Spikes remove themselves, but call {@link #removeAll()} on plugin disable as a safety net.
 */
public final class IceSpikeVisual {

    /** Same model Icicle Barrage fires. */
    private static final NamespacedKey MODEL = Objects.requireNonNull(NamespacedKey.fromString("elementsmp:icicle_tip"));

    /**
     * How much of the model's height the sprite actually fills: the point sits at pixel 3 and the wide end
     * at pixel 16, so 13 of 16 units. Used to turn a wanted visible height into a model scale.
     */
    private static final double SPRITE_FILL = 13.0 / 16.0;

    /** Width of a spike as a fraction of its visible height. */
    private static final double WIDTH_RATIO = 0.6;

    /** How far the wide end is sunk into the ground so there is never a gap under it, in blocks. */
    private static final double EMBED = 0.08;

    private static final int GROW_TICKS = 3;
    private static final int HOLD_TICKS = 40;
    private static final int SHRINK_TICKS = 6;

    /** Never scale to exactly zero - a degenerate transformation can render glitchy. */
    private static final float COLLAPSED = 0.05f;

    /** Every live spike display, so plugin disable can sweep up anything still on screen. */
    private static final Set<ItemDisplay> ACTIVE = new HashSet<>();

    private IceSpikeVisual() {}

    /**
     * Spawns a spike and runs its whole life cycle.
     *
     * @param base   where the spike's bottom touches the ground
     * @param height how tall the visible spike should be, in blocks (ground to point)
     */
    public static void spawn(JavaPlugin plugin, Location base, double height) {
        World world = base.getWorld();
        if (world == null) return;

        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // Model-height scale so the visible sprite (13/16 of the model) comes out `height` blocks tall.
        float sy = (float) (height / SPRITE_FILL);
        float sw = (float) (height * WIDTH_RATIO * (0.9 + rnd.nextDouble() * 0.2));
        float yaw = (float) (rnd.nextDouble() * Math.PI * 2);

        // The model's centre sits on the display and its wide end is half a (scaled) model below that,
        // so the display goes half a model above the ground (less the small embed).
        Location centre = new Location(world, base.getX(), base.getY() + 0.5 * sy - EMBED, base.getZ(), 0f, 0f);

        // The base material is irrelevant - the item_model component replaces its model entirely.
        ItemStack modelItem = ItemBuilder.of(Material.STICK).itemModel(MODEL).build();

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
     * Pose at a given growth. The model is flipped upside down (point up) and spun by {@code yaw}. Scaling
     * happens around the model's centre, so the display is dropped by however much the spike shrank to
     * keep its base planted on the ground. Width eases in more slowly than height so a half-grown spike
     * reads as rising, not as a squashed disc.
     *
     * @param growth 1.0 = fully grown, {@link #COLLAPSED} = sunk into the ground
     */
    private static Transformation pose(float width, float height, float yaw, float growth) {
        float w = width * (0.5f + 0.5f * growth);
        float h = height * growth;
        float lift = -0.5f * height * (1.0f - growth);
        // Flip first (point down -> point up), then spin around the vertical axis.
        Quaternionf rotation = new Quaternionf().rotateY(yaw).rotateX((float) Math.PI);
        return new Transformation(
                new Vector3f(0f, lift, 0f),
                rotation,
                new Vector3f(w, h, w),
                new Quaternionf());
    }
}