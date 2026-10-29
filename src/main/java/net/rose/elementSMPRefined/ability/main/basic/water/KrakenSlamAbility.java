package net.rose.elementSMPRefined.ability.main.basic.water;

import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.items.builder.ModelItems;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.particles.element.WaterParticles;
import net.rose.elementSMPRefined.status.StatusEffectType;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.scan.GroundScan;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KrakenSlamAbility extends BaseAbility implements Listener {

    private static final double RANGE = 25.0;
    private static final double RAY_SIZE = 0.5;

    public static final double HIT_RADIUS = 3.5;
    private static final double HIT_HALF_HEIGHT = 3.0;      // vertical reach above/below the impact point
    private static final double DAMAGE = 8.0;               // 4 hearts of true damage
    private static final double KNOCKBACK_OUT = 0.7;
    private static final double KNOCKBACK_UP = 0.65;
    private static final int SLOW_TICKS = 40;               // 2 seconds
    private static final int SLOW_AMPLIFIER = 1;

    private static final int RISE_TICKS = 10;               // tentacle grows out of the ground
    private static final int SLAM_START_TICK = 16;          // winds up until here, then swings down
    private static final int SLAM_TICKS = 5;                // the swing itself - fast
    private static final int IMPACT_TICK = SLAM_START_TICK + SLAM_TICKS;
    private static final int HOLD_TICKS = 14;               // stays planted after the hit
    private static final int RETRACT_TICKS = 10;            // sinks back into the ground
    private static final int RETRACT_START_TICK = IMPACT_TICK + HOLD_TICKS;
    private static final int END_TICK = RETRACT_START_TICK + RETRACT_TICKS;

    private static final float TILT_START = -8f;
    private static final float TILT_WINDUP = -38f;
    private static final float TILT_SLAM = 86f;
    private static final float TILT_RISE_END = -28f;        // lean reached by the end of the rise
    private static final int SLAM_FIRST_TICKS = 3;
    private static final float TILT_SLAM_MID = TILT_WINDUP + 0.36f * (TILT_SLAM - TILT_WINDUP);
    private static final float SCALE = 6.0f;
    private static final float MIN_SCALE_FACTOR = 0.05f;
    private static final double IMPACT_ALONG_BODY = 0.75;

    private static final double MODEL_HEIGHT = 24.7 / 16.0;
    private static final float MODEL_BASE_BELOW_CENTER = 0.73f / 16f;

    private static final NamespacedKey TENTACLE_MODEL =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:water_tentacle"));

    private final ElementSMPRefined plugin;
    private final Map<UUID, SlamState> slams = new ConcurrentHashMap<>();

    public KrakenSlamAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("kraken_slam", ElementType.WATER, 2, Constants.Cooldown.ABILITY_2_SECONDS, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        if (isActiveFor(player)) return false; // one slam at a time

        Location impact = findImpact(player, context);
        if (impact == null) return false;

        World world = impact.getWorld();

        // Direction the tentacle swings: from its base, over the target, towards the caster.
        double dirX = player.getLocation().getX() - impact.getX();
        double dirZ = player.getLocation().getZ() - impact.getZ();
        if (dirX * dirX + dirZ * dirZ < 1.0E-4) {
            Vector facing = player.getLocation().getDirection();
            dirX = -facing.getX();
            dirZ = -facing.getZ();
        }
        double dirLength = Math.hypot(dirX, dirZ);
        if (dirLength < 1.0E-4) {
            dirX = 0;
            dirZ = 1;
        } else {
            dirX /= dirLength;
            dirZ /= dirLength;
        }

        // The base is behind the impact point, so the fallen tentacle's body covers it.
        double length = MODEL_HEIGHT * SCALE;
        double baseX = impact.getX() - dirX * length * IMPACT_ALONG_BODY;
        double baseZ = impact.getZ() - dirZ * length * IMPACT_ALONG_BODY;
        Block ground = GroundScan.findGround(world, (int) Math.floor(baseX), (int) Math.floor(baseZ),
                impact.getBlockY(), 6, 10);
        double baseY = ground != null ? ground.getY() + 1.0 : impact.getY();
        Location base = new Location(world, baseX, baseY, baseZ);

        SlamState state = new SlamState(player, context, base, impact, dirX, dirZ);
        state.display = spawnTentacle(state, 0);
        if (state.display == null) return false;

        // Cast effects: the tentacle breaking the ground.
        SoundUtils.playTo(player, SoundUtils.Element.WATER);
        world.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 0.8f);
        world.playSound(base, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.5f, 0.6f);
        world.playSound(base, Sound.ENTITY_PLAYER_SPLASH, 1.4f, 0.5f);
        WaterParticles.graspBurst(base);
        WaterParticles.tentacleErupt(base);
        WaterParticles.krakenSlamMarker(impact, HIT_RADIUS, 0.0);

        slams.put(player.getUniqueId(), state);
        super.setActive(player, true);

        state.task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!tick(state)) {
                    endSlam(player.getUniqueId());
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    private Location findImpact(Player player, ElementContext context) {
        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        RayTraceResult result = world.rayTrace(
                eye, eye.getDirection(), RANGE,
                FluidCollisionMode.NEVER, true, RAY_SIZE,
                e -> e instanceof LivingEntity living
                        && !(e instanceof ArmorStand)
                        && !e.equals(player)
                        && isValidTarget(context, living));
        if (result == null) return null;

        Entity hitEntity = result.getHitEntity();
        if (hitEntity != null) {
            BoundingBox box = hitEntity.getBoundingBox();
            return new Location(world, box.getCenterX(), box.getMinY(), box.getCenterZ());
        }

        Vector hit = result.getHitPosition();
        if (result.getHitBlockFace() == BlockFace.UP) {
            return new Location(world, hit.getX(), hit.getY(), hit.getZ());
        }
        // Wall or ceiling: drop down to the floor below that spot.
        Block ground = GroundScan.findGround(world, (int) Math.floor(hit.getX()), (int) Math.floor(hit.getZ()),
                (int) Math.floor(hit.getY()), 2, 10);
        if (ground == null) return null;
        return new Location(world, hit.getX(), ground.getY() + 1.0, hit.getZ());
    }

    private boolean tick(SlamState state) {
        Player caster = state.caster;
        if (!caster.isOnline() || caster.isDead() || !isActiveFor(caster)) return false;
        if (!state.impact.getWorld().equals(caster.getWorld())) return false;

        int t = ++state.ticks;
        if (t > END_TICK) return false;

        // Unloaded or removed from under us: bring it back at the right point of the animation.
        if ((state.display == null || !state.display.isValid()) && t % 10 == 0) {
            state.display = spawnTentacle(state, t);
        }
        animate(state, t);

        if (t < IMPACT_TICK && t % 2 == 0) {
            WaterParticles.krakenSlamMarker(state.impact, HIT_RADIUS, (double) t / IMPACT_TICK);
        }
        if (t == SLAM_START_TICK) {
            state.impact.getWorld().playSound(state.base, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.4f, 0.5f);
        }
        if (t == IMPACT_TICK) {
            impact(state);
        }
        if (t == RETRACT_START_TICK) {
            WaterParticles.graspRelease(state.base.clone().add(0, 0.5, 0));
            state.base.getWorld().playSound(state.base, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 0.7f);
        }
        return true;
    }

    private void impact(SlamState state) {
        Location at = state.impact;
        World world = at.getWorld();

        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);
        world.playSound(at, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.5f, 0.6f);
        world.playSound(at, Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.5f, 0.7f);
        WaterParticles.krakenSlamImpact(at, HIT_RADIUS);

        for (Entity entity : world.getNearbyEntities(at, HIT_RADIUS, HIT_HALF_HEIGHT, HIT_RADIUS)) {
            if (!(entity instanceof LivingEntity target)) continue;
            if (target instanceof ArmorStand || !target.isValid() || target.isDead()) continue;
            if (target instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;
            if (!isValidTarget(state.context, target)) continue;

            double dx = target.getLocation().getX() - at.getX();
            double dz = target.getLocation().getZ() - at.getZ();
            double reach = HIT_RADIUS + target.getWidth() / 2.0;
            if (dx * dx + dz * dz > reach * reach) continue;

            boolean landed = TrueDamage.of(DAMAGE).attacker(state.caster).ability(getName()).apply(target);
            if (!landed || target.isDead()) continue; // an ability shield soaked it up

            // Thrown outward from the impact point (straight back along the swing if dead centre).
            double horizontal = Math.hypot(dx, dz);
            double outX = horizontal < 0.1 ? state.dirX : dx / horizontal;
            double outZ = horizontal < 0.1 ? state.dirZ : dz / horizontal;
            target.setVelocity(new Vector(outX * KNOCKBACK_OUT, KNOCKBACK_UP, outZ * KNOCKBACK_OUT));

            if (target instanceof Player tp) {
                plugin.getStatusEffectManager().applyEffect(state.caster, tp, StatusEffectType.SLOW,
                        SLOW_TICKS, SLOW_AMPLIFIER);
            } else {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOW_TICKS, SLOW_AMPLIFIER));
            }
        }
    }

    private ItemDisplay spawnTentacle(SlamState state, int tick) {
        Transformation initial = transformFor(state, tick);
        return state.base.getWorld().spawn(state.base, ItemDisplay.class, d -> {
            d.setItemStack(ModelItems.of(TENTACLE_MODEL));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2.0f);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setTransformation(initial);
        });
    }

    private void animate(SlamState state, int tick) {
        if (tick == 1) {
            interpolate(state, RISE_TICKS, 1f, TILT_RISE_END);
        } else if (tick == RISE_TICKS) {
            interpolate(state, SLAM_START_TICK - RISE_TICKS, 1f, TILT_WINDUP);
        } else if (tick == SLAM_START_TICK) {
            interpolate(state, SLAM_FIRST_TICKS, 1f, TILT_SLAM_MID);       // accelerating...
        } else if (tick == SLAM_START_TICK + SLAM_FIRST_TICKS) {
            interpolate(state, SLAM_TICKS - SLAM_FIRST_TICKS, 1f, TILT_SLAM); // ...then the crash
        } else if (tick == RETRACT_START_TICK) {
            interpolate(state, RETRACT_TICKS, MIN_SCALE_FACTOR, TILT_SLAM);
        }
    }

    private void interpolate(SlamState state, int durationTicks, float scaleFactor, float tiltDegrees) {
        ItemDisplay display = state.display;
        if (display == null || !display.isValid()) return;
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(durationTicks);
        display.setTransformation(transform(state, scaleFactor, tiltDegrees));
    }

    private static Transformation transformFor(SlamState state, int tick) {
        return transform(state, scaleFactor(tick), tiltDegrees(tick));
    }

    private static Transformation transform(SlamState state, float scaleFactor, float tiltDegrees) {
        float scale = SCALE * scaleFactor;
        Quaternionf tilt = new Quaternionf().rotationAxis(
                (float) Math.toRadians(tiltDegrees), state.axisX, 0f, state.axisZ);
        // The model's base is a hair below the display's centre; rotate that small offset with the
        // body so the base point stays at the display's location.
        Vector3f translation = new Vector3f(0f, MODEL_BASE_BELOW_CENTER * scale, 0f).rotate(tilt);
        // Turn the model so its curl points along the swing direction.
        Quaternionf yaw = new Quaternionf().rotationY(state.yaw);
        return new Transformation(translation, tilt, new Vector3f(scale, scale, scale), yaw);
    }

    private static float tiltDegrees(int tick) {
        if (tick < SLAM_START_TICK) {
            float p = clamp01((float) tick / SLAM_START_TICK);
            return lerp(TILT_START, TILT_WINDUP, easeOut(p));
        }
        if (tick < IMPACT_TICK) {
            float p = (float) (tick - SLAM_START_TICK) / SLAM_TICKS;
            return lerp(TILT_WINDUP, TILT_SLAM, p * p);
        }
        return TILT_SLAM;
    }

    private static float scaleFactor(int tick) {
        if (tick < RISE_TICKS) {
            return lerp(MIN_SCALE_FACTOR, 1f, easeOut(clamp01((float) tick / RISE_TICKS)));
        }
        if (tick > RETRACT_START_TICK) {
            float p = clamp01((float) (tick - RETRACT_START_TICK) / RETRACT_TICKS);
            return lerp(1f, MIN_SCALE_FACTOR, p * p);
        }
        return 1f;
    }

    private static float easeOut(float p) {
        float inv = 1f - p;
        return 1f - inv * inv * inv;
    }

    private static float lerp(float a, float b, float p) {
        return a + (b - a) * p;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void endSlam(UUID casterId) {
        SlamState state = slams.remove(casterId);
        if (state == null) return;
        if (state.task != null) state.task.cancel();
        if (state.display != null) state.display.remove();
        super.setActive(state.caster, false);
    }

    @Override
    public void setActive(Player player, boolean active) {
        if (active) {
            super.setActive(player, true);
        } else {
            // BaseElement.clearEffects (element change, death) deactivates through here: tear the
            // tentacle down instead of just dropping the flag.
            if (slams.containsKey(player.getUniqueId())) {
                endSlam(player.getUniqueId());
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
        return ChatColor.AQUA + "Kraken Slam";
    }

    @Override
    public String getDescription() {
        return "A giant tentacle slams down crushing anything under it.";
    }

    private static final class SlamState {
        final Player caster;
        final ElementContext context;
        final Location base;     // ground point the tentacle's base is planted on
        final Location impact;   // where it lands (locked at cast time)
        final double dirX;       // horizontal unit vector, base -> impact (swing direction)
        final double dirZ;
        final float yaw;         // turns the model's curl along the swing direction
        final float axisX;       // horizontal axis the tentacle tips around
        final float axisZ;
        ItemDisplay display;
        BukkitTask task;
        int ticks = 0;

        SlamState(Player caster, ElementContext context, Location base, Location impact,
                  double dirX, double dirZ) {
            this.caster = caster;
            this.context = context;
            this.base = base;
            this.impact = impact;
            this.dirX = dirX;
            this.dirZ = dirZ;
            // rotationY(phi) maps the model's +X to (cos phi, 0, -sin phi). Item displays render
            // their model flipped 180 degrees about the vertical axis, so the curl actually ends
            // up opposite to that; the extra PI makes the curl point along the swing direction
            // (towards the impact), which tips it down into the ground on the slam.
            this.yaw = (float) (Math.atan2(-dirZ, dirX) + Math.PI);
            // Rotating about (dz, 0, -dx) tips "up" towards (dx, 0, dz).
            this.axisX = (float) dirZ;
            this.axisZ = (float) -dirX;
        }
    }
}