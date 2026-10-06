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
import java.util.Objects;
import java.util.Set;

/**
 * A magma beam drawn with the {@code elementsmp:magma_beam} resource-pack MODEL - one
 * {@link ItemDisplay} stretched along its long (Z) axis, the same idea as {@link ChainVisual}
 * and {@link AirCutterVisual}.
 * <p>
 * The model is a 16px-long beam centred on the entity, so {@link #update} puts the display at
 * the midpoint between the two ends, scales Z to the beam length and rotates it to face along
 * the beam. A very short length reads as a glowing orb, which the charge-up uses.
 * <p>
 * Always call {@link #remove()} when the effect ends (or {@link #removeAll()} on plugin
 * disable as a safety net) - it's a real entity and will otherwise hang around.
 */
public final class MagmaBeamVisual {

    /** {@code assets/elementsmp/items/magma_beam.json} in the resource pack. */
    public static final NamespacedKey MAGMA_BEAM =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:magma_beam"));

    /** The model's long axis. The beam is symmetric, so which way it "points" doesn't matter. */
    private static final Vector3f MODEL_AXIS = new Vector3f(0f, 0f, 1f);

    /** Every live visual, so plugin disable can sweep up anything still on screen. */
    private static final Set<MagmaBeamVisual> ACTIVE = new HashSet<>();

    private final ItemStack modelItem;
    /** Spawned lazily on the first update (and respawned if it ever goes invalid). */
    private ItemDisplay display;

    public MagmaBeamVisual() {
        // The base material is irrelevant - the item_model component replaces its model entirely.
        this.modelItem = ItemBuilder.of(Material.STICK).itemModel(MAGMA_BEAM).build();
        ACTIVE.add(this);
    }

    /**
     * Lays the beam out from {@code from} along {@code direction} for {@code length} blocks.
     *
     * @param thickness width/height of the beam in blocks (the model is 6px wide at scale 1,
     *                  so a thickness of 1.0 is a beam about 0.4 blocks wide)
     */
    public void update(Location from, Vector direction, double length, float thickness) {
        update(from, direction, length, thickness, 0f);
    }

    /**
     * Same as {@link #update(Location, Vector, double, float)} but also rolls the beam around its
     * own long axis by {@code roll} radians. The beam's cross-section is square, so feeding a
     * steadily increasing value makes it visibly spin.
     */
    public void update(Location from, Vector direction, double length, float thickness, float roll) {
        World world = from.getWorld();
        if (world == null || length < 0.05 || direction.lengthSquared() < 1.0E-6) {
            hide();
            return;
        }

        Vector dir = direction.clone().normalize();
        Location mid = from.clone().add(dir.clone().multiply(length / 2.0));
        if (!isLoaded(mid)) return;

        // aim * roll: the roll is applied around the model's own Z axis first, then the whole
        // thing is swung to face along the beam, so it spins around the beam's axis.
        Quaternionf rotation = new Quaternionf()
                .rotationTo(MODEL_AXIS, new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()))
                .rotateZ(roll);

        Transformation transformation = new Transformation(
                new Vector3f(),
                rotation,
                new Vector3f(thickness, thickness, (float) length),
                new Quaternionf());

        if (display == null || !display.isValid()) {
            display = world.spawn(flat(mid), ItemDisplay.class, d -> {
                d.setItemStack(modelItem);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(transformation);
                d.setTeleportDuration(1);
                d.setInterpolationDuration(1);
                d.setPersistent(false);
                d.setInvulnerable(true);
            });
            return;
        }

        display.teleport(flat(mid));
        display.setTransformation(transformation);
        display.setInterpolationDelay(0);
    }

    /** Removes the entity but keeps this visual usable - a later {@link #update} respawns it. */
    public void hide() {
        if (display != null && display.isValid()) {
            display.remove();
        }
        display = null;
    }

    /** Removes everything and unregisters. Call when the effect is over. */
    public void remove() {
        hide();
        ACTIVE.remove(this);
    }

    /** Safety net for plugin disable - removes every beam still on screen. */
    public static void removeAll() {
        for (MagmaBeamVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private static Location flat(Location at) {
        return new Location(at.getWorld(), at.getX(), at.getY(), at.getZ(), 0f, 0f);
    }

    /** Teleporting into an unloaded chunk would force a synchronous load - skip that step instead. */
    private static boolean isLoaded(Location at) {
        return at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }
}