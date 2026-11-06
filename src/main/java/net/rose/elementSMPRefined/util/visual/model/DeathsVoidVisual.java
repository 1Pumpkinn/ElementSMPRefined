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
 * The spinning three-blade fan used by Death's Dark Blade ability.
 * <p>
 * The Blockbench model uses two rotation axes per element, which Java block models can't express, so it is
 * shipped as three models ({@code purple_blade_1..3}, one per blade group) with the Y fan baked in. Each
 * group's Z roll ({@link #BASE_ROLLS}) is applied here as part of the display's rotation, so the three
 * displays together look exactly like the original model, and spinning is just an extra roll on top.
 */
public final class DeathsVoidVisual {

    private static final NamespacedKey[] MODELS = {
            key("elementsmp:purple_blade_1"),
            key("elementsmp:purple_blade_2"),
            key("elementsmp:purple_blade_3")
    };

    /** Z roll (degrees) of each blade group, straight from the bbmodel. Flip {@link #ROLL_SIGN} if they look mirrored. */
    private static final float[] BASE_ROLLS = {-120f, 120f, 180f};
    private static final float ROLL_SIGN = 1f;

    /** The blades lead toward +Z. Use (0, 0, -1) to flip. */
    private static final Vector3f MODEL_FORWARD = new Vector3f(0f, 0f, 1f);

    public static final float SPIN_DEGREES_PER_TICK = 45f;

    // Fast projectile: every extra tick of smoothing makes the blades trail the hit position.
    private static final int MAX_SMOOTHING_TICKS = 2;

    private static final Set<DeathsVoidVisual> ACTIVE = new HashSet<>();

    private final ItemDisplay[] displays = new ItemDisplay[MODELS.length];
    private final float scale;
    private final Vector direction;
    private final Player caster; // may be null: then the teleport window stays at 1 tick
    private int smoothing = 1;
    private float spin;

    public DeathsVoidVisual(Location at, Vector direction, float scale) {
        this(at, direction, scale, null);
    }

    /** @param caster whose ping decides how much the blades' movement is smoothed (null = never) */
    public DeathsVoidVisual(Location at, Vector direction, float scale, Player caster) {
        this.caster = caster;
        this.smoothing = PingCompensation.smoothingTicks(caster, MAX_SMOOTHING_TICKS);
        this.scale = scale;
        this.direction = direction.clone().normalize();
        World world = at.getWorld();
        // Zero yaw/pitch: display entities rotate with their own yaw/pitch ON TOP of the transformation,
        // so the heading must live only in the transformation.
        Location flat = upright(at);
        for (int i = 0; i < MODELS.length; i++) {
            ItemStack modelItem = ModelItems.of(MODELS[i]);
            Transformation transformation = transformation(i);
            displays[i] = world.spawn(flat, ItemDisplay.class, d -> {
                d.setItemStack(modelItem);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(transformation);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(1);
                d.setTeleportDuration(smoothing);
                d.setPersistent(false);
                d.setInvulnerable(true);
            });
        }
        ACTIVE.add(this);
    }

    /** Moves the blades to {@code at} and advances the spin by one tick. */
    public void tick(Location at) {
        spin += SPIN_DEGREES_PER_TICK;
        int wanted = PingCompensation.smoothingTicks(caster, MAX_SMOOTHING_TICKS);
        boolean smoothingChanged = wanted != smoothing;
        smoothing = wanted;
        for (int i = 0; i < displays.length; i++) {
            ItemDisplay display = displays[i];
            if (!display.isValid()) continue;
            if (smoothingChanged) display.setTeleportDuration(smoothing);
            display.teleport(upright(at));
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(1);
            display.setTransformation(transformation(i));
        }
    }

    public boolean isValid() {
        return displays[0].isValid();
    }

    public void remove() {
        for (ItemDisplay display : displays) {
            if (display.isValid()) display.remove();
        }
        ACTIVE.remove(this);
    }

    public static void removeAll() {
        for (DeathsVoidVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private static Location upright(Location l) {
        return new Location(l.getWorld(), l.getX(), l.getY(), l.getZ(), 0f, 0f);
    }

    private Transformation transformation(int index) {
        Vector3f dir = new Vector3f((float) direction.getX(), (float) direction.getY(), (float) direction.getZ());
        float roll = (float) Math.toRadians(ROLL_SIGN * BASE_ROLLS[index] + spin);
        Quaternionf rotation = new Quaternionf().rotationTo(MODEL_FORWARD, dir).rotateZ(roll);
        return new Transformation(new Vector3f(), rotation, new Vector3f(scale), new Quaternionf());
    }

    private static NamespacedKey key(String s) {
        return Objects.requireNonNull(NamespacedKey.fromString(s));
    }
}