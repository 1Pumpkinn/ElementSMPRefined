package net.rose.elementSMPRefined.util.visual.model;

import net.rose.elementSMPRefined.items.builder.ModelItems;
import net.rose.elementSMPRefined.util.scan.GroundScan;
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
import java.util.function.Consumer;

/**
 * Green energy beam with a white core, plus a star flare at each end (muzzle / impact).
 * <p>
 * The beam is one {@link ItemDisplay} wearing {@code elementsmp:energy_beam} (four crossed planes through
 * the beam axis, each textured with a smooth lateral gradient so the glow is soft from every angle) and each flare is a camera-facing {@link ItemDisplay} wearing {@code elementsmp:energy_flare}.
 * Both item definitions carry a marker tint that the pack's core shader overlay recognises: with the shader
 * the beam is fullbright, soft-edged and recoloured green -> white; without it the models still render as a
 * plain green beam.
 */
public final class EnergyBlastVisual {

    public static final NamespacedKey BEAM_MODEL =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:energy_beam"));
    public static final NamespacedKey FLARE_MODEL =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:energy_flare"));

    private static final Vector3f MODEL_AXIS = new Vector3f(0f, 0f, 1f);

    private static final Set<EnergyBlastVisual> ACTIVE = new HashSet<>();

    private final Player caster; // may be null: then the interpolation window stays at 1 tick
    private int smoothing = 1;

    private ItemDisplay beam;
    private ItemDisplay muzzle;
    private ItemDisplay impact;

    public EnergyBlastVisual(Player caster) {
        this.caster = caster;
        ACTIVE.add(this);
    }

    /**
     * Lays the beam out from {@code from} along {@code direction} for {@code length} blocks.
     *
     * @param thickness scale of the beam cross-section (each plane is 24px wide at 1.0, i.e. 1.5 blocks; the bright body is roughly the middle third)
     * @param roll      spin around the beam's own axis, in radians
     */
    public void updateBeam(Location from, Vector direction, double length, float thickness, float roll) {
        World world = from.getWorld();
        if (world == null || length < 0.05 || direction.lengthSquared() < 1.0E-6) {
            hideBeam();
            return;
        }

        Vector dir = direction.clone().normalize();
        Location mid = from.clone().add(dir.clone().multiply(length / 2.0));
        if (!GroundScan.isLoaded(mid)) return;

        Quaternionf rotation = new Quaternionf()
                .rotationTo(MODEL_AXIS, new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()))
                .rotateZ(roll);

        Transformation transformation = new Transformation(
                new Vector3f(), rotation,
                new Vector3f(thickness, thickness, (float) length),
                new Quaternionf());

        beam = place(beam, BEAM_MODEL, Display.Billboard.FIXED, mid, transformation);
    }

    /** Star flare at the start of the beam (charge orb / muzzle). {@code size} is the quad's width in blocks. */
    public void updateMuzzle(Location at, float size, float roll) {
        muzzle = placeFlare(muzzle, at, size, roll);
    }

    /** Star flare where the beam ends. */
    public void updateImpact(Location at, float size, float roll) {
        impact = placeFlare(impact, at, size, roll);
    }

    public void hideBeam() {
        beam = discard(beam);
    }

    public void hideImpact() {
        impact = discard(impact);
    }

    public void remove() {
        beam = discard(beam);
        muzzle = discard(muzzle);
        impact = discard(impact);
        ACTIVE.remove(this);
    }

    public static void removeAll() {
        for (EnergyBlastVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private ItemDisplay placeFlare(ItemDisplay current, Location at, float size, float roll) {
        World world = at.getWorld();
        if (world == null || size < 0.01f || !GroundScan.isLoaded(at)) return discard(current);

        Transformation transformation = new Transformation(
                new Vector3f(), new Quaternionf().rotateZ(roll),
                new Vector3f(size, size, size), new Quaternionf());
        return place(current, FLARE_MODEL, Display.Billboard.CENTER, at, transformation);
    }

    private ItemDisplay place(ItemDisplay current, NamespacedKey model, Display.Billboard billboard,
                              Location at, Transformation transformation) {
        World world = at.getWorld();
        int wanted = PingCompensation.smoothingTicks(caster);
        Location spot = flat(at);

        if (current == null || !current.isValid()) {
            ItemStack item = ModelItems.translucent(model);
            Consumer<ItemDisplay> setup = d -> {
                d.setItemStack(item);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(billboard);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(transformation);
                d.setTeleportDuration(wanted);
                d.setInterpolationDuration(wanted);
                d.setPersistent(false);
                d.setInvulnerable(true);
            };
            smoothing = wanted;
            return world.spawn(spot, ItemDisplay.class, setup);
        }

        if (wanted != smoothing) {
            smoothing = wanted;
            current.setTeleportDuration(wanted);
            current.setInterpolationDuration(wanted);
        }
        current.teleport(spot);
        current.setTransformation(transformation);
        current.setInterpolationDelay(0);
        return current;
    }

    private static ItemDisplay discard(ItemDisplay display) {
        if (display != null && display.isValid()) {
            display.remove();
        }
        return null;
    }

    private static Location flat(Location at) {
        return new Location(at.getWorld(), at.getX(), at.getY(), at.getZ(), 0f, 0f);
    }
}
