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
 * The Metal Shield drawn with the {@code elementsmp:metal_shield} resource-pack MODEL - a
 * stepped heater shield (steel plate, dark rim, cross brace, gold boss, copper rivets).
 * <p>
 * One {@link ItemDisplay} hovers just off the owner's left side, slightly in front of them -
 * like a shield held on the arm - so it reads clearly without sitting in the middle of their
 * screen. {@link #update(Location, int)} re-places it every tick (with a gentle bob), and the
 * entity's own yaw is set to the owner's so the shield keeps facing the way they do. The model
 * is symmetrical front-to-back, so there's no "wrong way round".
 * <p>
 * {@link #pulse()} briefly swells the shield - used as hit feedback when it absorbs a blow.
 * <p>
 * Always call {@link #remove()} when the effect ends (or {@link #removeAll()} on plugin
 * disable as a safety net) - it's a real entity and will otherwise hang around.
 */
public final class MetalShieldVisual {

    /** {@code assets/elementsmp/items/metal_shield.json} in the resource pack. */
    public static final NamespacedKey METAL_SHIELD =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:metal_shield"));

    /** The model is 12 x 16 units (0.75 x 1 block) - this makes it about 0.8 x 1.1 blocks in the world. */
    private static final float SCALE = 1.1f;
    private static final float PULSE_SCALE = 1.25f;
    private static final int PULSE_TICKS = 3;

    /** Where the shield hovers relative to the owner's feet: ahead, to the left, and up to chest height. */
    private static final double FORWARD = 1.0;
    private static final double SIDE = 0.9;
    private static final double HEIGHT = 0.85;
    private static final double BOB = 0.03;

    /** Every live visual, so plugin disable can sweep up anything still on screen. */
    private static final Set<MetalShieldVisual> ACTIVE = new HashSet<>();

    private final ItemDisplay display;
    private int pulseTicksLeft;

    public MetalShieldVisual(Location owner) {
        // The base material is irrelevant - the item_model component replaces its model entirely.
        ItemStack modelItem = ItemBuilder.of(Material.STICK).itemModel(METAL_SHIELD).build();

        this.display = owner.getWorld().spawn(anchor(owner, 0), ItemDisplay.class, d -> {
            d.setItemStack(modelItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(scaled(SCALE));
            d.setTeleportDuration(1);
            d.setInterpolationDuration(2);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
        ACTIVE.add(this);
    }

    /** Moves the shield to hover beside {@code owner}. Call once per tick. */
    public void update(Location owner, int tick) {
        if (!display.isValid() || !isLoaded(owner)) return;
        display.teleport(anchor(owner, tick));

        if (pulseTicksLeft > 0 && --pulseTicksLeft == 0) {
            display.setTransformation(scaled(SCALE));
            display.setInterpolationDelay(0);
        }
    }

    /** Quick swell-and-settle - hit feedback. */
    public void pulse() {
        if (!display.isValid()) return;
        display.setTransformation(scaled(PULSE_SCALE));
        display.setInterpolationDelay(0);
        pulseTicksLeft = PULSE_TICKS;
    }

    /** Where the shield currently is (for particles / sounds). */
    public Location getLocation() {
        return display.getLocation();
    }

    /** Removes the entity and unregisters. Call when the effect is over. */
    public void remove() {
        if (display.isValid()) {
            display.remove();
        }
        ACTIVE.remove(this);
    }

    /** Safety net for plugin disable - removes every shield still on screen. */
    public static void removeAll() {
        for (MetalShieldVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private static Transformation scaled(float scale) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale), new Quaternionf());
    }

    /** Hover point: ahead of and to the left of the owner (relative to where they face), plus a slow bob. */
    private static Location anchor(Location owner, int tick) {
        double yaw = Math.toRadians(owner.getYaw());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double leftX = Math.cos(yaw);
        double leftZ = Math.sin(yaw);
        double bob = Math.sin(tick * 0.15) * BOB;

        return new Location(owner.getWorld(),
                owner.getX() + forwardX * FORWARD + leftX * SIDE,
                owner.getY() + HEIGHT + bob,
                owner.getZ() + forwardZ * FORWARD + leftZ * SIDE,
                owner.getYaw(), 0f);
    }

    /** Teleporting into an unloaded chunk would force a synchronous load - just skip that step instead. */
    private static boolean isLoaded(Location at) {
        return at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }
}
