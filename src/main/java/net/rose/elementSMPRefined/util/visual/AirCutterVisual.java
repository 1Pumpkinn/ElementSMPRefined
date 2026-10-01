package net.rose.elementSMPRefined.util.visual;

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
import java.util.Set;
import java.util.Objects;

/**
 * A flying air-cutter drawn with a resource-pack MODEL instead of particles - the same idea
 * as {@link ChainVisual}, but a single {@link ItemDisplay} that travels instead of a row of
 * segments.
 * <p>
 * The display is spawned once with a fixed orientation (the blade's apex points along the
 * flight direction), then {@link #moveTo(Location)} just teleports it each tick. Teleport
 * interpolation is 1 tick so a blade moving 1.5 blocks per tick still looks smooth.
 * <p>
 * Always call {@link #remove()} when the effect ends (or {@link #removeAll()} on plugin
 * disable as a safety net) - it's a real entity and will otherwise hang around.
 */
public final class AirCutterVisual {

    /** {@code assets/elementsmp/items/air_cutter.json} in the resource pack. */
    public static final NamespacedKey AIR_CUTTER =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:air_cutter"));

    /** The model is ~0.9 blocks wide; this makes it read at a distance and match the hitbox. */
    private static final float SCALE = 2.0f;
    /**
     * World direction the model's apex (the bulging, sharp front edge - model -Z) points at
     * with an unrotated ItemDisplay. If the blade ever flies backwards in game, flip this
     * to (0, 0, -1).
     */
    private static final Vector3f MODEL_FORWARD = new Vector3f(0f, 0f, 1f);

    /** Every live visual, so plugin disable can sweep up anything still on screen. */
    private static final Set<AirCutterVisual> ACTIVE = new HashSet<>();

    private final ItemDisplay display;

    public AirCutterVisual(Location at, Vector direction) {
        Vector3f dir = new Vector3f(
                (float) direction.getX(), (float) direction.getY(), (float) direction.getZ()).normalize();

        // Orientation lives entirely in the transformation (entity yaw/pitch stay 0), same as
        // ChainVisual - a display's own rotation would stack with it.
        Transformation transformation = new Transformation(
                new Vector3f(),
                new Quaternionf().rotationTo(MODEL_FORWARD, dir),
                new Vector3f(SCALE),
                new Quaternionf());

        // The base material is irrelevant - the item_model component replaces its model entirely.
        ItemStack modelItem = ItemBuilder.of(Material.STICK).itemModel(AIR_CUTTER).build();

        this.display = at.getWorld().spawn(flat(at), ItemDisplay.class, d -> {
            d.setItemStack(modelItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(transformation);
            d.setTeleportDuration(1);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
        ACTIVE.add(this);
    }

    /** Moves the blade to {@code at}, keeping its orientation. */
    public void moveTo(Location at) {
        World world = at.getWorld();
        if (world == null || !display.isValid() || !isLoaded(at)) return;
        display.teleport(flat(at));
    }

    /** Removes the entity and unregisters. Call when the effect is over. */
    public void remove() {
        if (display.isValid()) {
            display.remove();
        }
        ACTIVE.remove(this);
    }

    /** Safety net for plugin disable - removes every blade still on screen. */
    public static void removeAll() {
        for (AirCutterVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private static Location flat(Location at) {
        return new Location(at.getWorld(), at.getX(), at.getY(), at.getZ(), 0f, 0f);
    }

    /** Teleporting into an unloaded chunk would force a synchronous load - just skip that step instead. */
    private static boolean isLoaded(Location at) {
        return at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }
}
