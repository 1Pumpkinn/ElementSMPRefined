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
 * The Metal Anchor Core drawn with two resource-pack MODELS: a spiky steel core with a glowing
 * cyan eye ({@code elementsmp:anchor_core}) wrapped in a tilted octagonal ring
 * ({@code elementsmp:anchor_core_ring}). The core tumbles, the ring spins the other way, and the
 * whole thing bobs gently in place.
 * <p>
 * Orientation lives entirely in each display's transformation (entity yaw/pitch stay 0), so
 * {@link #update(int)} just feeds it a new rotation every tick.
 * <p>
 * Always call {@link #remove()} when the effect ends (or {@link #removeAll()} on plugin disable
 * as a safety net) - they're real entities and will otherwise hang around.
 */
public final class AnchorCoreVisual {

    /** {@code assets/elementsmp/items/anchor_core.json} in the resource pack. */
    public static final NamespacedKey ANCHOR_CORE =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:anchor_core"));
    /** {@code assets/elementsmp/items/anchor_core_ring.json} in the resource pack. */
    public static final NamespacedKey ANCHOR_CORE_RING =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:anchor_core_ring"));

    /** The models are 16 units (1 block) across before scaling; the ring reaches ~0.7 blocks from the centre at this scale. */
    private static final float SCALE = 1.4f;

    private static final double BOB = 0.08;
    private static final float CORE_YAW_PER_TICK = 9f;     // degrees
    private static final float CORE_PITCH_PER_TICK = 4f;
    private static final float RING_SPIN_PER_TICK = -14f;  // opposite direction to the core
    private static final float RING_TILT = 22f;

    /** Every live visual, so plugin disable can sweep up anything still on screen. */
    private static final Set<AnchorCoreVisual> ACTIVE = new HashSet<>();

    private final Location centre;
    private final ItemDisplay core;
    private final ItemDisplay ring;

    public AnchorCoreVisual(Location centre) {
        this.centre = centre.clone();
        this.core = spawn(centre, ANCHOR_CORE);
        this.ring = spawn(centre, ANCHOR_CORE_RING);
        ACTIVE.add(this);
    }

    /** Advances the animation. Call once per tick with the number of ticks since the core was placed. */
    public void update(int tick) {
        Location bobbed = centre.clone().add(0, Math.sin(tick * 0.1) * BOB, 0);

        if (core.isValid()) {
            Quaternionf spin = new Quaternionf().rotationYXZ(
                    (float) Math.toRadians(tick * CORE_YAW_PER_TICK),
                    (float) Math.toRadians(tick * CORE_PITCH_PER_TICK), 0f);
            animate(core, bobbed, spin);
        }
        if (ring.isValid()) {
            // spin about the ring's own axis first, then tip the whole ring over
            Quaternionf spin = new Quaternionf()
                    .rotateX((float) Math.toRadians(RING_TILT))
                    .rotateY((float) Math.toRadians(tick * RING_SPIN_PER_TICK));
            animate(ring, bobbed, spin);
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
        for (AnchorCoreVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private static void animate(ItemDisplay display, Location at, Quaternionf rotation) {
        display.teleport(at);
        display.setTransformation(new Transformation(
                new Vector3f(), rotation, new Vector3f(SCALE), new Quaternionf()));
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
            d.setInterpolationDuration(1);
            d.setTeleportDuration(1);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
    }
}
