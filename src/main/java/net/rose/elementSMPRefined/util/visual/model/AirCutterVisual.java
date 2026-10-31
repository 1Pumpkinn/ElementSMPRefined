package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.util.scan.GroundScan;
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
import java.util.Set;
import java.util.Objects;

public final class AirCutterVisual {

    public static final NamespacedKey AIR_CUTTER =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:air_cutter"));

    private static final float SCALE = 3.0f;
    private static final float DROP = 0.1f;
    private static final Vector3f MODEL_FORWARD = new Vector3f(0f, 0f, 1f);
    private static final float ROLL_DEGREES = 0f;

    private static final int MAX_SMOOTHING_TICKS = 2;

    private static final Set<AirCutterVisual> ACTIVE = new HashSet<>();

    private final ItemDisplay display;
    private final Player caster; // may be null: then the teleport window stays at 1 tick
    private int smoothing = 1;

    public AirCutterVisual(Location at, Vector direction) {
        this(at, direction, null);
    }

    /** @param caster whose ping decides how much the blade's movement is smoothed (null = never) */
    public AirCutterVisual(Location at, Vector direction, Player caster) {
        this.caster = caster;
        this.smoothing = PingCompensation.smoothingTicks(caster, MAX_SMOOTHING_TICKS);
        Vector3f dir = new Vector3f(
                (float) direction.getX(), (float) direction.getY(), (float) direction.getZ()).normalize();

        // Orientation lives entirely in the transformation (entity yaw/pitch stay 0), same as
        // ChainVisual - a display's own rotation would stack with it.
        // Yaw (left/right) and pitch (up/down) are applied separately instead of one shortest-arc
        // rotation, so the blade tips up/down with the aim but never rolls onto its side.
        Vector3f flatDir = new Vector3f(dir.x, 0f, dir.z);
        if (flatDir.lengthSquared() < 1.0E-6f) {
            flatDir.set(MODEL_FORWARD);
        }
        flatDir.normalize();

        Quaternionf yaw = new Quaternionf().rotationTo(MODEL_FORWARD, flatDir);
        float pitch = (float) Math.asin(Math.max(-1f, Math.min(1f, dir.y)));
        // Horizontal axis pointing to the blade's side; tipping around it can't add any roll.
        Vector3f side = new Vector3f(0f, 1f, 0f).cross(flatDir).normalize();
        Quaternionf orientation = new Quaternionf().rotateAxis(-pitch, side).mul(yaw);
        if (ROLL_DEGREES != 0f) {
            orientation = new Quaternionf().rotateAxis((float) Math.toRadians(ROLL_DEGREES), dir).mul(orientation);
        }

        Transformation transformation = new Transformation(
                new Vector3f(0f, -DROP, 0f),
                orientation,
                new Vector3f(SCALE),
                new Quaternionf());

        // The base material is irrelevant - the item_model component replaces its model entirely.
        ItemStack modelItem = ModelItems.of(AIR_CUTTER);

        this.display = at.getWorld().spawn(flat(at), ItemDisplay.class, d -> {
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

    public void moveTo(Location at) {
        World world = at.getWorld();
        if (world == null || !display.isValid() || !GroundScan.isLoaded(at)) return;
        int wanted = PingCompensation.smoothingTicks(caster, MAX_SMOOTHING_TICKS);
        if (wanted != smoothing) {
            smoothing = wanted;
            display.setTeleportDuration(wanted);
        }
        display.teleport(flat(at));
    }

    public void remove() {
        if (display.isValid()) {
            display.remove();
        }
        ACTIVE.remove(this);
    }

    public static void removeAll() {
        for (AirCutterVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private static Location flat(Location at) {
        return new Location(at.getWorld(), at.getX(), at.getY(), at.getZ(), 0f, 0f);
    }
}