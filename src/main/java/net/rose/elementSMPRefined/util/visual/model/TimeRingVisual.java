package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.item.builder.ModelItems;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The flat ground decal of the Time Ring: a single item display wearing the
 * {@code elementsmp:time_ring} model (six concentric hoops lying in the model's y = 0 plane),
 * turning slowly around its centre, plus a second display with the {@code elementsmp:time_hand}
 * model that sweeps round on top of it like the second hand of a clock.
 * <p>
 * The model's outermost hoop ends 15/16 of a block from the centre at scale 1, so the display is
 * scaled by {@code radius / (15/16)} to make that edge land exactly on {@code radius}.
 */
public final class TimeRingVisual {

    public static final NamespacedKey TIME_RING =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:time_ring"));

    public static final NamespacedKey TIME_HAND =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:time_hand"));

    /** Distance from the model's centre to the outer edge of its outer hoop, in blocks at scale 1. */
    private static final double MODEL_RADIUS = 15.0 / 16.0;

    /** The decal floats this far above the ground so it never fights the block's top face. */
    private static final double LIFT = 0.05;

    private static final float SPIN_DEGREES_PER_TICK = 0.9f;
    private static final float HAND_DEGREES_PER_TICK = 360f / (6 * 20); // 6s a turn
    private static final int STEP_TICKS = 2;
    private static final int INTERPOLATION_TICKS = 3;
    private static final double MIN_GROWTH = 0.02;

    private static final Set<TimeRingVisual> ACTIVE = new HashSet<>();

    private final ItemDisplay display;
    private final ItemDisplay hand;
    private final float scale;

    /**
     * @param ground where the ring's centre touches the ground
     * @param radius how far the outer edge of the ring reaches, in blocks
     */
    public TimeRingVisual(Location ground, double radius) {
        this.scale = (float) (radius / MODEL_RADIUS);
        Location origin = centre(ground);
        this.display = spawn(origin, TIME_RING);
        this.hand = spawn(origin, TIME_HAND);
        ACTIVE.add(this);
    }

    /**
     * Advances the spin and the grow / shrink. Call once per tick.
     *
     * @param tick ticks since the ring was cast
     * @param growth 0..1 size multiplier; the ring stays flat on the ground while it changes size
     */
    public void update(int tick, double growth) {
        if (!display.isValid() || tick % STEP_TICKS != 0) return;

        // Aim at the pose STEP_TICKS ahead so the motion keeps its real speed.
        int target = tick + STEP_TICKS;
        float g = (float) Math.max(MIN_GROWTH, Math.min(1.0, growth));
        Quaternionf rotation =
                new Quaternionf().rotateY((float) Math.toRadians(target * SPIN_DEGREES_PER_TICK));

        // The model's flat side sits half a block (times scale) below the display's origin, and
        // scaling happens around that origin: drop it by however much the ring shrank so the
        // decal stays planted on the ground.
        float drop = -0.5f * scale * (1.0f - g);

        display.setTransformation(
                new Transformation(
                        new Vector3f(0f, drop, 0f),
                        rotation,
                        new Vector3f(scale * g),
                        new Quaternionf()));
        display.setInterpolationDelay(0);

        if (hand.isValid()) {
            Quaternionf sweep =
                    new Quaternionf().rotateY((float) Math.toRadians(target * HAND_DEGREES_PER_TICK));
            hand.setTransformation(
                    new Transformation(
                            new Vector3f(0f, drop, 0f),
                            sweep,
                            new Vector3f(scale * g),
                            new Quaternionf()));
            hand.setInterpolationDelay(0);
        }
    }

    public void remove() {
        if (display.isValid()) display.remove();
        if (hand.isValid()) hand.remove();
        ACTIVE.remove(this);
    }

    public static void removeAll() {
        for (TimeRingVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private ItemDisplay spawn(Location at, NamespacedKey model) {
        // The base material is irrelevant - the item_model component replaces its model entirely.
        ItemStack modelItem = ModelItems.of(model);
        World world = at.getWorld();

        return world.spawn(
                at,
                ItemDisplay.class,
                d -> {
                    d.setItemStack(modelItem);
                    d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                    d.setBillboard(Display.Billboard.FIXED);
                    d.setBrightness(new Display.Brightness(15, 15));
                    // Start tiny; the first update() grows it so it never pops in at full size.
                    float tiny = (float) (scale * MIN_GROWTH);
                    d.setTransformation(
                            new Transformation(
                                    new Vector3f(),
                                    new Quaternionf(),
                                    new Vector3f(tiny),
                                    new Quaternionf()));
                    d.setInterpolationDuration(INTERPOLATION_TICKS);
                    d.setViewRange(1.5f); // it's a big decal: keep it visible from further away
                    d.setPersistent(false);
                    d.setInvulnerable(true);
                });
    }

    /** The display's origin: the ring plane is half a block (times scale) beneath it. */
    private Location centre(Location ground) {
        return new Location(
                ground.getWorld(),
                ground.getX(),
                ground.getY() + LIFT + 0.5 * scale,
                ground.getZ(),
                0f,
                0f);
    }
}
