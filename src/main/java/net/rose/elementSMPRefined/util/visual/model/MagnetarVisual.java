package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.items.builder.ModelItems;
import org.bukkit.Location;
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

public final class MagnetarVisual {

    public static final NamespacedKey MAGNETAR_CORE =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:magnetar_core"));
    public static final NamespacedKey MAGNETAR_CORE_RING =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:magnetar_core_ring"));

    private static final float SCALE = 1.4f;

    private static final int STEP_TICKS = 2;
    private static final int INTERPOLATION_TICKS = 3;

    private static final double BOB = 0.08;
    private static final float CORE_YAW_PER_TICK = 9f;     // degrees
    private static final float CORE_PITCH_PER_TICK = 4f;
    private static final float RING_SPIN_PER_TICK = -14f;  // opposite direction to the core
    private static final float RING_TILT = 22f;

    private static final Set<MagnetarVisual> ACTIVE = new HashSet<>();

    private final ItemDisplay core;
    private final ItemDisplay ring;

    public MagnetarVisual(Location centre) {
        this.core = spawn(centre, MAGNETAR_CORE);
        this.ring = spawn(centre, MAGNETAR_CORE_RING);
        ACTIVE.add(this);
    }

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

    public void remove() {
        if (core.isValid()) core.remove();
        if (ring.isValid()) ring.remove();
        ACTIVE.remove(this);
    }

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
        ItemStack modelItem = ModelItems.of(model);

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
