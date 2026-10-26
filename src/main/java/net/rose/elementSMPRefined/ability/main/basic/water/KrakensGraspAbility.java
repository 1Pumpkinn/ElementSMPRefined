package net.rose.elementSMPRefined.ability.main.basic.water;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.items.builder.ModelItems;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.particles.element.WaterParticles;
import net.rose.elementSMPRefined.status.StatusEffectManager;
import net.rose.elementSMPRefined.status.StatusEffectType;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Water ability 2: Krakens Grasp.
 * <p>
 * Look at a mob or player: six water tentacles erupt from the ground around them, three on each
 * side, and hold them in place. The target cannot move for the duration - players are rooted
 * through the status effect system (which cancels their {@code PlayerMoveEvent}, so they can
 * still look around and fight), mobs have their AI switched off and are pinned to the spot.
 * <p>
 * The tentacles are placed from the target's bounding box, so each one stands just outside it and
 * curls its tip in over the target without ever clipping into the body.
 * <p>
 * Pressing the key again lets go early (see {@code WaterElement#canCancelAbility2}). It also ends
 * when the duration runs out, the target dies or changes world, or the caster dies / logs off /
 * changes element.
 */
public class KrakensGraspAbility extends BaseAbility implements Listener {

    private static final double RANGE = 25.0;
    private static final double RAY_SIZE = 0.5;
    private static final int DURATION_TICKS = 160;      // 8 seconds

    // ---- layout ----
    private static final int TENTACLES_PER_SIDE = 3;
    /** Angle between neighbouring tentacles on one side, seen from the target's centre. */
    private static final double SIDE_SPREAD_DEGREES = 38.0;
    /** Minimum gap between the target's hitbox and any part of a tentacle. */
    private static final double CLEARANCE = 0.12;
    private static final double MAX_DISTANCE = 16.0;    // search limit for very wide targets
    private static final double HEIGHT_FACTOR = 1.1;    // tentacles stand slightly taller than the target
    private static final double MIN_SCALE = 0.5;
    private static final double MAX_SCALE = 4.0;
    private static final float[] SCALE_VARIATION = {0.92f, 1.0f, 1.08f}; // so they don't look cloned
    private static final int GROW_TICKS = 8;
    private static final int GROW_STAGGER_TICKS = 2;    // each tentacle starts rising a little after the last

    // ---- tentacle model (elementsmp:water_tentacle), in blocks at scale 1 ----
    // 24.7/16 tall; footprint is a 4.5/16 square around the display's centre, and the tip curls
    // up to 5.35/16 towards the model's +X.
    private static final double MODEL_HEIGHT = 24.7 / 16.0;
    private static final double MODEL_FRONT = 5.35 / 16.0;       // pivot -> furthest point of the curl
    private static final double MODEL_BACK = 2.25 / 16.0;        // pivot -> back of the base
    private static final double MODEL_HALF_WIDTH = 2.25 / 16.0;  // pivot -> side of the base

    private static final NamespacedKey TENTACLE_MODEL =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:water_tentacle"));

    private final ElementSMPRefined plugin;
    private final Map<UUID, TetherState> tethers = new ConcurrentHashMap<>();

    public KrakensGraspAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("krakens_grasp", ElementType.WATER, 2, 25, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        // Pressed again while the tether is out: let go. (WaterElement lets this through without a
        // cooldown check; the cooldown from the original cast keeps running.)
        if (isActiveFor(player)) {
            endTether(player.getUniqueId(), true);
            return true;
        }

        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTrace(
                eye, eye.getDirection(), RANGE,
                FluidCollisionMode.NEVER, true, RAY_SIZE,
                e -> e instanceof LivingEntity && !(e instanceof ArmorStand) && !e.equals(player));

        if (result == null || !(result.getHitEntity() instanceof LivingEntity target)) {
            return false;
        }
        if (!isValidTarget(context, target)) {
            return false;
        }

        World world = player.getWorld();
        SoundUtils.playTo(player, SoundUtils.Element.WATER);
        world.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 0.8f);
        world.playSound(target.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.2f, 0.9f);
        world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1.2f, 0.7f);
        WaterParticles.tetherGrab(target.getLocation());

        TetherState state = new TetherState(player, target, target.getLocation().clone());
        state.tentacles.addAll(layoutTentacles(player, target));
        for (Tentacle tentacle : state.tentacles) {
            tentacle.display = spawnTentacle(tentacle, false);
            WaterParticles.tentacleErupt(tentacle.base);
        }
        grabTarget(state);

        tethers.put(player.getUniqueId(), state);
        super.setActive(player, true);

        state.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!tick(state)) {
                    endTether(player.getUniqueId(), true);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    /** One tick of the tether. Returns false when it should end. */
    private boolean tick(TetherState state) {
        Player caster = state.caster;
        LivingEntity target = state.target;

        if (!caster.isOnline() || caster.isDead() || !isActiveFor(caster)) return false;
        if (!target.isValid() || target.isDead()) return false;
        if (target instanceof Player tp && !tp.isOnline()) return false;
        if (!state.frozenAt.getWorld().equals(target.getWorld())) return false;
        if (++state.ticks > DURATION_TICKS) return false;

        holdTarget(state);
        updateTentacles(state);
        return true;
    }

    // ---- holding the target ----

    /** Starts the hold: players get ROOT, mobs lose their AI. */
    private void grabTarget(TetherState state) {
        LivingEntity target = state.target;
        if (target instanceof Player tp) {
            applyRoot(state, tp);
        } else if (target instanceof Mob mob) {
            state.mobWasAware = mob.isAware();
            mob.setAware(false);
        }
    }

    /** Keeps the hold in place every tick. */
    private void holdTarget(TetherState state) {
        LivingEntity target = state.target;

        if (target instanceof Player tp) {
            // ROOT cancels the player's PlayerMoveEvent (see StatusEffectListener). Re-apply it if
            // something stripped the effect early.
            StatusEffectManager statusEffects = plugin.getStatusEffectManager();
            if (state.ticks % 10 == 0 && !statusEffects.hasEffect(tp, StatusEffectType.ROOT)) {
                applyRoot(state, tp);
            }
            return;
        }

        // Non-players don't go through the status effect system: pin them to where they were
        // grabbed (gravity still applies, so they can drop but not walk or jump away).
        Location now = target.getLocation();
        double dx = now.getX() - state.frozenAt.getX();
        double dz = now.getZ() - state.frozenAt.getZ();
        if (dx * dx + dz * dz > 0.0025) {
            Location back = state.frozenAt.clone();
            back.setY(now.getY());
            back.setYaw(now.getYaw());
            back.setPitch(now.getPitch());
            target.teleport(back);
        }
        Vector velocity = target.getVelocity();
        target.setVelocity(new Vector(0, Math.min(0, velocity.getY()), 0));
    }

    private void applyRoot(TetherState state, Player target) {
        int remaining = Math.max(1, DURATION_TICKS - state.ticks) + 5;
        plugin.getStatusEffectManager().applyEffect(state.caster, target, StatusEffectType.ROOT, remaining, 1);
    }

    private void releaseTarget(TetherState state) {
        LivingEntity target = state.target;
        if (target instanceof Player tp) {
            if (tp.isOnline()) {
                plugin.getStatusEffectManager().removeEffect(tp, StatusEffectType.ROOT);
            }
        } else if (target instanceof Mob mob && state.mobWasAware != null && mob.isValid()) {
            mob.setAware(state.mobWasAware);
        }
    }

    // ---- tentacle layout ----

    /**
     * Places {@link #TENTACLES_PER_SIDE} tentacles on each side of the target. "Sides" are the
     * left and right as the caster sees the target, so the caster keeps a clear view of the front.
     * Every tentacle faces the target and is pushed out until its whole footprint is clear of the
     * target's hitbox.
     */
    private List<Tentacle> layoutTentacles(Player caster, LivingEntity target) {
        BoundingBox box = target.getBoundingBox();
        double centerX = box.getCenterX();
        double centerZ = box.getCenterZ();
        BoundingBox keepOut = box.clone().expand(CLEARANCE, 0, CLEARANCE);

        // Direction the caster is looking at the target from (flat).
        Location casterPos = caster.getLocation();
        double viewX = centerX - casterPos.getX();
        double viewZ = centerZ - casterPos.getZ();
        if (viewX * viewX + viewZ * viewZ < 1.0E-4) {
            Vector facing = casterPos.getDirection();
            viewX = facing.getX();
            viewZ = facing.getZ();
        }
        double viewAngle = Math.atan2(viewZ, viewX);

        double baseScale = Math.max(MIN_SCALE,
                Math.min(MAX_SCALE, target.getHeight() * HEIGHT_FACTOR / MODEL_HEIGHT));
        World world = target.getWorld();
        double feetY = box.getMinY();

        List<Tentacle> tentacles = new ArrayList<>();
        int index = 0;
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < TENTACLES_PER_SIDE; k++) {
                double spread = (k - (TENTACLES_PER_SIDE - 1) / 2.0) * Math.toRadians(SIDE_SPREAD_DEGREES);
                double angle = viewAngle + side * (Math.PI / 2.0) + spread;
                double outX = Math.cos(angle);
                double outZ = Math.sin(angle);

                float scale = (float) (baseScale * SCALE_VARIATION[k % SCALE_VARIATION.length]);
                double distance = clearDistance(keepOut, centerX, centerZ, outX, outZ, scale);

                Location base = new Location(world, centerX + outX * distance, feetY, centerZ + outZ * distance);
                // The model curls towards its +X, so turn that to point back at the target.
                float yaw = (float) Math.atan2(outZ, -outX);
                tentacles.add(new Tentacle(base, yaw, scale, 1 + index * GROW_STAGGER_TICKS));
                index++;
            }
        }
        return tentacles;
    }

    /**
     * Smallest distance from the target's centre, along (outX, outZ), at which a tentacle of the
     * given scale - curling back towards the centre - has no part inside {@code keepOut}.
     */
    private static double clearDistance(BoundingBox keepOut, double centerX, double centerZ,
                                        double outX, double outZ, double scale) {
        double inX = -outX;
        double inZ = -outZ;
        double sideX = -inZ;
        double sideZ = inX;
        double front = MODEL_FRONT * scale;
        double back = MODEL_BACK * scale;
        double half = MODEL_HALF_WIDTH * scale;

        for (double distance = 0; distance < MAX_DISTANCE; distance += 0.05) {
            double pivotX = centerX + outX * distance;
            double pivotZ = centerZ + outZ * distance;
            boolean clear = true;
            // Sample the tentacle's footprint (base column + curl) as a small grid.
            for (int i = 0; i <= 4 && clear; i++) {
                double along = -back + (front + back) * i / 4.0;
                for (int j = -1; j <= 1; j++) {
                    double across = j * half;
                    double x = pivotX + inX * along + sideX * across;
                    double z = pivotZ + inZ * along + sideZ * across;
                    if (x > keepOut.getMinX() && x < keepOut.getMaxX()
                            && z > keepOut.getMinZ() && z < keepOut.getMaxZ()) {
                        clear = false;
                        break;
                    }
                }
            }
            if (clear) return distance;
        }
        return MAX_DISTANCE;
    }

    // ---- tentacle displays ----

    private ItemDisplay spawnTentacle(Tentacle tentacle, boolean grown) {
        float scale = grown ? tentacle.scale : tentacle.scale * 0.05f;
        return tentacle.base.getWorld().spawn(tentacle.base, ItemDisplay.class, d -> {
            d.setItemStack(ModelItems.of(TENTACLE_MODEL));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.5f);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setTransformation(transform(tentacle.yaw, scale));
        });
    }

    private void updateTentacles(TetherState state) {
        for (Tentacle tentacle : state.tentacles) {
            ItemDisplay display = tentacle.display;
            if (display == null || !display.isValid()) {
                // Unloaded or removed from under us: bring it back fully grown.
                if (state.ticks % 10 == 0 && state.ticks > tentacle.growAt) {
                    tentacle.display = spawnTentacle(tentacle, true);
                }
                continue;
            }
            if (state.ticks == tentacle.growAt) {
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(GROW_TICKS);
                display.setTransformation(transform(tentacle.yaw, tentacle.scale));
            }
        }
    }

    /** Model base sits half a block below the display's centre; lift it so the base is on the ground. */
    private static Transformation transform(float yRotation, float scale) {
        return new Transformation(
                new Vector3f(0f, 0.5f * scale, 0f),
                new AxisAngle4f(yRotation, 0f, 1f, 0f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f());
    }

    // ---- lifecycle ----

    private void endTether(UUID casterId, boolean effects) {
        TetherState state = tethers.remove(casterId);
        if (state == null) return;
        if (state.task != null) state.task.cancel();
        for (Tentacle tentacle : state.tentacles) {
            if (tentacle.display != null) tentacle.display.remove();
        }
        releaseTarget(state);
        super.setActive(state.caster, false);

        if (effects && state.target.isValid()) {
            Location at = state.target.getLocation();
            at.getWorld().playSound(at, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.0f);
            WaterParticles.tetherRelease(at.clone().add(0, state.target.getHeight() / 2.0, 0));
        }
    }

    @Override
    public void setActive(Player player, boolean active) {
        if (active) {
            super.setActive(player, true);
        } else {
            // BaseElement.clearEffects (element change, death) deactivates through here: tear the
            // tentacles down instead of just dropping the flag.
            if (tethers.containsKey(player.getUniqueId())) {
                endTether(player.getUniqueId(), false);
            } else {
                super.setActive(player, false);
            }
        }
    }

    public void clearEffects(Player player) {
        setActive(player, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        setActive(event.getPlayer(), false);
    }

    @Override
    public String getName() {
        return ChatColor.AQUA + "Kraken’s Grasp";
    }

    @Override
    public String getDescription() {
        return "Tentacles erupt around an entity and holds them in place.";
    }

    /** One tentacle: where it stands, how it is turned and sized, and its display. */
    private static class Tentacle {
        final Location base;   // ground point the model stands on
        final float yaw;       // turns the model's curl towards the target
        final float scale;
        final int growAt;      // tick it starts rising
        ItemDisplay display;

        Tentacle(Location base, float yaw, float scale, int growAt) {
            this.base = base;
            this.yaw = yaw;
            this.scale = scale;
            this.growAt = growAt;
        }
    }

    private static class TetherState {
        final Player caster;
        final LivingEntity target;
        final Location frozenAt;    // where the target was grabbed
        final List<Tentacle> tentacles = new ArrayList<>();
        Boolean mobWasAware;        // mobs only: AI awareness to restore on release
        org.bukkit.scheduler.BukkitTask task;
        int ticks = 0;

        TetherState(Player caster, LivingEntity target, Location frozenAt) {
            this.caster = caster;
            this.target = target;
            this.frozenAt = frozenAt;
        }
    }
}
