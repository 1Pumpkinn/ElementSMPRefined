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
import java.util.Objects;
import java.util.Set;

public final class MagmaBeamVisual {

    public static final NamespacedKey MAGMA_BEAM =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:magma_beam"));

    private static final Vector3f MODEL_AXIS = new Vector3f(0f, 0f, 1f);

    private static final Set<MagmaBeamVisual> ACTIVE = new HashSet<>();

    private final ItemStack modelItem;
    private final Player caster; // may be null: then the interpolation window stays at 1 tick
    private int smoothing = 1;
    private ItemDisplay display;

    public MagmaBeamVisual() {
        this(null);
    }

    /** @param caster whose ping decides how much the beam's movement is smoothed (null = never) */
    public MagmaBeamVisual(Player caster) {
        this.caster = caster;
        // The base material is irrelevant - the item_model component replaces its model entirely.
        this.modelItem = ModelItems.of(MAGMA_BEAM);
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

    public void update(Location from, Vector direction, double length, float thickness, float roll) {
        World world = from.getWorld();
        if (world == null || length < 0.05 || direction.lengthSquared() < 1.0E-6) {
            hide();
            return;
        }

        Vector dir = direction.clone().normalize();
        Location mid = from.clone().add(dir.clone().multiply(length / 2.0));
        if (!GroundScan.isLoaded(mid)) return;

        int wanted = PingCompensation.smoothingTicks(caster);

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
                d.setTeleportDuration(wanted);
                d.setInterpolationDuration(wanted);
                d.setPersistent(false);
                d.setInvulnerable(true);
            });
            smoothing = wanted;
            return;
        }

        if (wanted != smoothing) {
            smoothing = wanted;
            display.setTeleportDuration(wanted);
            display.setInterpolationDuration(wanted);
        }
        display.teleport(flat(mid));
        display.setTransformation(transformation);
        display.setInterpolationDelay(0);
    }

    public void hide() {
        if (display != null && display.isValid()) {
            display.remove();
        }
        display = null;
    }

    public void remove() {
        hide();
        ACTIVE.remove(this);
    }

    public static void removeAll() {
        for (MagmaBeamVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private static Location flat(Location at) {
        return new Location(at.getWorld(), at.getX(), at.getY(), at.getZ(), 0f, 0f);
    }
}