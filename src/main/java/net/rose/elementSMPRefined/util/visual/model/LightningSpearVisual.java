package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.items.builder.ModelItems;
import net.rose.elementSMPRefined.util.visual.PingCompensation;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
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
 * The Lightning Spear drawn with a resource-pack MODEL instead of particles - a single
 * {@link ItemDisplay} that is teleported along the flight path every tick, the same way
 * {@link AirCutterVisual} works.
 * <p>
 * The model is authored in Blockbench ({@code blockbench/lightning_spear.bbmodel}) with the tip
 * toward model -Z and the shaft centred on the model origin, so the display can simply be
 * rotated onto the flight direction. Always call {@link #remove()} when the effect ends
 * (or {@link #removeAll()} on plugin disable as a safety net).
 */
public final class LightningSpearVisual {

    /** {@code assets/elementsmp/items/lightning_spear.json} in the resource pack. */
    public static final NamespacedKey LIGHTNING_SPEAR =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:lightning_spear"));

    /** The model is 32 model units (2 blocks) long; x1.5 makes it the spear's 3 block length. */
    private static final float SCALE = 1.5f;
    /** Half of the scaled model length: how far the display centre sits behind the tip. */
    public static final double HALF_LENGTH = 1.5;
    /**
     * World direction the model's tip (model -Z) points at with an unrotated ItemDisplay.
     * Same convention as {@link AirCutterVisual}; if the spear ever flies backwards in game,
     * flip this to (0, 0, -1).
     */
    private static final Vector3f MODEL_FORWARD = new Vector3f(0f, 0f, 1f);

    /** Most extra interpolation ticks a high-ping caster gets (the spear is fast - see {@link AirCutterVisual}). */
    private static final int MAX_SMOOTHING_TICKS = 2;

    /** Every live visual, so plugin disable can sweep up anything still on screen. */
    private static final Set<LightningSpearVisual> ACTIVE = new HashSet<>();

    private final ItemDisplay display;
    private final Vector direction;
    private final Player caster; // may be null: then the teleport window stays at 1 tick
    private int smoothing = 1;

    public LightningSpearVisual(Location head, Vector direction) {
        this(head, direction, null);
    }

    /**
     * @param head      where the spear's TIP should be
     * @param direction flight direction
     * @param caster    whose ping decides how much the spear's movement is smoothed (null = never)
     */
    public LightningSpearVisual(Location head, Vector direction, Player caster) {
        this.caster = caster;
        this.smoothing = PingCompensation.smoothingTicks(caster, MAX_SMOOTHING_TICKS);
        this.direction = direction.clone().normalize();
        Vector3f dir = new Vector3f(
                (float) this.direction.getX(), (float) this.direction.getY(), (float) this.direction.getZ());

        Transformation transformation = new Transformation(
                new Vector3f(),
                new Quaternionf().rotationTo(MODEL_FORWARD, dir),
                new Vector3f(SCALE),
                new Quaternionf());

        // The base material is irrelevant - the item_model component replaces its model entirely.
        ItemStack modelItem = ModelItems.of(LIGHTNING_SPEAR);

        this.display = head.getWorld().spawn(centre(head), ItemDisplay.class, d -> {
            d.setItemStack(modelItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(transformation);
            d.setTeleportDuration(smoothing);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
        ACTIVE.add(this);
    }

    /** Moves the spear so its tip is at {@code head}, keeping its orientation. */
    public void moveTo(Location head) {
        World world = head.getWorld();
        if (world == null || !display.isValid() || !isLoaded(head)) return;
        int wanted = PingCompensation.smoothingTicks(caster, MAX_SMOOTHING_TICKS);
        if (wanted != smoothing) {
            smoothing = wanted;
            display.setTeleportDuration(wanted);
        }
        display.teleport(centre(head));
    }

    /** Removes the entity and unregisters. Call when the effect is over. */
    public void remove() {
        if (display.isValid()) {
            display.remove();
        }
        ACTIVE.remove(this);
    }

    /** Safety net for plugin disable - removes every spear still on screen. */
    public static void removeAll() {
        for (LightningSpearVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    /** The display's position for a given tip position: half a spear length back along the flight line. */
    private Location centre(Location head) {
        Location c = head.clone().subtract(direction.clone().multiply(HALF_LENGTH));
        return new Location(c.getWorld(), c.getX(), c.getY(), c.getZ(), 0f, 0f);
    }

    /** Teleporting into an unloaded chunk would force a synchronous load - just skip that step instead. */
    private static boolean isLoaded(Location at) {
        return at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }
}
