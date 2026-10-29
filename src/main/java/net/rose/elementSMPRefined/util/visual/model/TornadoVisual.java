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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class TornadoVisual {

    public static final NamespacedKey TORNADO_FUNNEL =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:tornado_funnel"));
    public static final NamespacedKey TORNADO_WISPS =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:tornado_wisps"));

    public static final double HEIGHT = 8.0;

    private static final float SCALE = (float) (HEIGHT / 3.0);

    private static final double[] TIER_HEIGHT = {4, 4, 5, 5, 6, 7, 8, 9};
    private static final double[] TIER_RADIUS = {5.0, 5.6, 6.8, 8.4, 10.6, 13.0, 16.0, 19.0};
    private static final double OCTAGON_FUDGE = 1.04;

    private static final int STEP_TICKS = 2;
    private static final int INTERPOLATION_TICKS = 3;
    private static final int MAX_SMOOTHING_TICKS = 3;

    private static final float BODY_SPIN = 16f;       // degrees per tick
    private static final float WISP_SPIN = -26f;       // opposite direction, faster
    private static final double SWAY_DEGREES = 3.0;    // how far the whole funnel leans side to side

    private static final double MIN_GROWTH = 0.02;

    private static final Set<TornadoVisual> ACTIVE = new HashSet<>();

    private final ItemDisplay body;
    private final ItemDisplay wisps;
    private final Player caster; // may be null: then the teleport window stays at 1 tick
    private int smoothing;

    /**
     * @param base   where the funnel's bottom touches the ground
     * @param caster whose ping decides how much the movement is smoothed (null = never)
     */
    public TornadoVisual(Location base, Player caster) {
        this.caster = caster;
        this.smoothing = PingCompensation.smoothingTicks(caster, MAX_SMOOTHING_TICKS);
        this.body = spawn(centre(base), TORNADO_FUNNEL);
        this.wisps = spawn(centre(base), TORNADO_WISPS);
        ACTIVE.add(this);
    }

    /**
     * How far from the funnel's axis its wall is at a given height, in blocks at full size.
     * Multiply by the current growth if the tornado is still growing / fading.
     * <p>
     * Each tier's width applies at the tier's middle and is blended linearly toward the next one,
     * so this follows the model's silhouette without the little steps between tiers.
     *
     * @param fraction 0 at the ground, 1 at the very top (clamped)
     */
    public static double radiusAt(double fraction) {
        double f = Math.max(0.0, Math.min(1.0, fraction));
        double y = f * 48.0; // model units above the bottom

        double radius = TIER_RADIUS[TIER_RADIUS.length - 1];
        double bottom = 0.0;
        double previousMid = 0.0;
        double previousRadius = TIER_RADIUS[0];
        for (int i = 0; i < TIER_HEIGHT.length; i++) {
            double mid = bottom + TIER_HEIGHT[i] / 2.0;
            if (y <= mid) {
                // below the first tier's middle: flat; otherwise blend from the previous tier's middle
                radius = i == 0 ? TIER_RADIUS[0]
                        : previousRadius + (TIER_RADIUS[i] - previousRadius) * ((y - previousMid) / (mid - previousMid));
                break;
            }
            previousMid = mid;
            previousRadius = TIER_RADIUS[i];
            bottom += TIER_HEIGHT[i];
        }
        return radius * OCTAGON_FUDGE * SCALE / 16.0;
    }

    public void moveTo(Location base) {
        World world = base.getWorld();
        if (world == null || !body.isValid() || !isLoaded(base)) return;
        int wanted = PingCompensation.smoothingTicks(caster, MAX_SMOOTHING_TICKS);
        if (wanted != smoothing) {
            smoothing = wanted;
            body.setTeleportDuration(wanted);
            wisps.setTeleportDuration(wanted);
        }
        Location centre = centre(base);
        body.teleport(centre);
        wisps.teleport(centre);
    }

    /**
     * Advances the spin / sway / size. Call once per tick.
     *
     * @param tick   ticks since the tornado was summoned
     * @param growth 0..1 size multiplier (grows in, shrinks out); the base stays on the ground
     */
    public void update(int tick, double growth) {
        if (tick % STEP_TICKS != 0) return;

        // Aim at the pose STEP_TICKS ahead so the motion keeps its real speed.
        int target = tick + STEP_TICKS;
        float g = (float) Math.max(MIN_GROWTH, Math.min(1.0, growth));
        float leanX = (float) Math.toRadians(Math.sin(target * 0.07) * SWAY_DEGREES);
        float leanZ = (float) Math.toRadians(Math.cos(target * 0.05) * SWAY_DEGREES);

        animate(body, target, BODY_SPIN, g, leanX, leanZ);
        animate(wisps, target, WISP_SPIN, g, leanX, leanZ);
    }

    public void remove() {
        if (body.isValid()) body.remove();
        if (wisps.isValid()) wisps.remove();
        ACTIVE.remove(this);
    }

    public static void removeAll() {
        for (TornadoVisual visual : new ArrayList<>(ACTIVE)) {
            visual.remove();
        }
    }

    private static void animate(ItemDisplay display, int tick, float spinPerTick, float growth,
                                float leanX, float leanZ) {
        // spin about the funnel's own axis first, then lean the whole thing over
        Quaternionf rotation = new Quaternionf()
                .rotateX(leanX)
                .rotateZ(leanZ)
                .rotateY((float) Math.toRadians(tick * spinPerTick));

        float scale = SCALE * growth;
        // Scaling happens around the model's centre (mid-height), so drop it by however much the
        // funnel shrank to keep its base planted on the ground.
        float lift = (float) (-(1.0 - growth) * HEIGHT / 2.0);

        display.setTransformation(new Transformation(
                new Vector3f(0f, lift, 0f), rotation, new Vector3f(scale), new Quaternionf()));
        display.setInterpolationDelay(0);
    }

    private ItemDisplay spawn(Location at, NamespacedKey model) {
        // The base material is irrelevant - the item_model component replaces its model entirely.
        ItemStack modelItem = ModelItems.of(model);

        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(modelItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            // Start tiny; the first update() blows it up so it never pops in at full size.
            float tiny = (float) (SCALE * MIN_GROWTH);
            d.setTransformation(new Transformation(
                    new Vector3f(), new Quaternionf(), new Vector3f(tiny), new Quaternionf()));
            d.setInterpolationDuration(INTERPOLATION_TICKS);
            d.setTeleportDuration(smoothing);
            d.setViewRange(2.0f); // it's big - keep it visible from further away than a normal display
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
    }

    private static Location centre(Location base) {
        return new Location(base.getWorld(), base.getX(), base.getY() + HEIGHT / 2.0, base.getZ(), 0f, 0f);
    }

    private static boolean isLoaded(Location at) {
        return at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }
}