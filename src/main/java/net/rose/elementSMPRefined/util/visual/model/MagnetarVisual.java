package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.items.builder.ItemBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
 * The Metal Magnetar Core drawn with two resource-pack MODELS: a spiky steel core with a glowing
 * cyan eye ({@code elementsmp:magnetar_core}) wrapped in a tilted octagonal ring
 * ({@code elementsmp:magnetar_core_ring}). The core tumbles, the ring spins the other way, and the
 * whole thing bobs gently in place.
 * <p>
 * Orientation AND the bobbing both live in each display's transformation (the entities never move),
 * so the client does all the animating. {@link #update(int)} only sends a new pose every
 * {@link #STEP_TICKS} ticks and each pose is interpolated over {@link #INTERPOLATION_TICKS}: the
 * windows overlap, so a late or bunched-up packet on a high-ping connection no longer freezes or
 * skips the spin, and half as many packets are sent.
 * <p>
 * Always call {@link #remove()} when the effect ends (or {@link #removeAll()} on plugin disable
 * as a safety net) - they're real entities and will otherwise hang around.
 */
public final class MagnetarVisual {

    /** {@code assets/elementsmp/items/magnetar_core.json} in the resource pack. */
    public static final NamespacedKey MAGNETAR_CORE =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:magnetar_core"));
    /** {@code assets/elementsmp/items/magnetar_core_ring.json} in the resource pack. */
    public static final NamespacedKey MAGNETAR_CORE_RING =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:magnetar_core_ring"));

    /** The models are 16 units (1 block) across before scaling; the ring reaches ~0.7 blocks from the centre at this scale. */
    private static final float SCALE = 1.4f;

    /** A new pose is sent every this many ticks... */
    private static final int STEP_TICKS = 2;
    /** ...and interpolated over this many, so consecutive windows overlap (slack for packet jitter). */
    private static final int INTERPOLATION_TICKS = 3;

    private static final double BOB = 0.08;
    private static final float CORE_YAW_PER_TICK = 9f;     // degrees
    private static final float CORE_PITCH_PER_TICK = 4f;
    private static final float RING_SPIN_PER_TICK = -14f;  // opposite direction to the core
    private static final float RING_TILT = 22f;

    /** Every live visual, so plugin disable can sweep up anything still on screen. */
    private static final Set<MagnetarVisual> ACTIVE = new HashSet<>();

    private final ItemDisplay core;
    private final ItemDisplay ring;

    public MagnetarVisual(Location centre) {
        this.core = spawn(centre, MAGNETAR_CORE);
        this.ring = spawn(centre, MAGNETAR_CORE_RING);
        ACTIVE.add(this);
    }

    /** Advances the animation. Call once per tick with the number of ticks since the core was placed. */
    public void update(int tick) {
        if (tick % STEP_TICKS != 0) return;

        // Aim at the pose STEP_TICKS ahead so the motion keeps its real speed.
        int target = tick + STEP_TICKS;
        float bob = (float) (Math.sin(target * 0.1) * BOB);

        if (core.isValid()) {
            Quaternionf spin = new Quaternionf().rotationYXZ(
                    (float) Math.toRadians(target * CORE_YAW_PER_TICK),
                    (float) Math.toRadians(target * CORE_PITCH_PER_TICK), 0f);
            animate(core, bob, spin);
        }
        if (ring.isValid()) {
            // spin about the ring's own axis first, then tip the whole ring over
            Quaternionf spin = new Quaternionf()
                    .rotateX((float) Math.toRadians(RING_TILT))
                    .rotateY((float) Math.toRadians(target * RING_SPIN_PER_TICK));
            animate(ring, bob, spin);
        }
    }

    /** Removes the entities and unregisters. Call when the effect is over. */
    public void remove() {
        if (core.isValid()) core.remove();
        if (ring.isValid()) ring.remove();
        ACTIVE.remove(this);
    }

    /** Safety net for plugin disable - removes every core still on screen. */
    public static void removeAll() {
        for (MagnetarVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private static void animate(ItemDisplay display, float bob, Quaternionf rotation) {
        display.setTransformation(new Transformation(
                new Vector3f(0f, bob, 0f), rotation, new Vector3f(SCALE), new Quaternionf()));
        display.setInterpolationDelay(0);
    }

    private static ItemDisplay spawn(Location at, NamespacedKey model) {
        // The base material is irrelevant - the item_model component replaces its model entirely.
        ItemStack modelItem = ItemBuilder.of(Material.STICK).itemModel(model).build();

        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(modelItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(), new Vector3f(SCALE), new Quaternionf()));
            d.setInterpolationDuration(INTERPOLATION_TICKS);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
    }
}
