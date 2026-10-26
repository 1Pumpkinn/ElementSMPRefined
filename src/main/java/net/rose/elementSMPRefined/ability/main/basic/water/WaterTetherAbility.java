package net.rose.elementSMPRefined.ability.main.basic.water;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.items.builder.ModelItems;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.particles.element.WaterParticles;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Water ability 2: Tidal Tether.
 * <p>
 * Look at a mob or player: a water tentacle erupts from the ground at their feet - that spot is
 * the <b>anchor</b> (the tether's core point; it is not attached to the caster) - , and a
 * single tentacle coils up the target's body from feet to shoulders, wrapping around their legs and chest. While it holds,
 * the target is leashed to the anchor: they can move freely within {@link #LEASH_LENGTH} blocks
 * of it, but past that the tentacle yanks them back (a half-heart sting at most once a second)
 * and they're slowed. A wavy water rope links the anchor to the target so the leash reads.
 * <p>
 * Pressing the key again lets go early (see {@code WaterElement#canCancelAbility2}). It also ends
 * when the duration runs out, the target dies or changes world, or the caster dies / logs off /
 * changes element.
 */
public class WaterTetherAbility extends BaseAbility implements Listener {

    private static final double RANGE = 25.0;
    private static final double RAY_SIZE = 0.5;
    private static final int DURATION_TICKS = 160;      // 8 seconds

    private static final double LEASH_LENGTH = 5.0;     // free movement radius around the ground anchor
    private static final double ANCHOR_SCALE = 1.3;     // the ground tentacle that is the tether's core point
    private static final int GROW_TICKS = 8;
    private static final double PULL_BASE = 0.35;       // blocks/tick the instant the leash is exceeded
    private static final double PULL_PER_BLOCK = 0.10;  // extra pull per block past the leash
    private static final double PULL_MAX = 1.1;
    private static final double PULL_MAX_UPWARD = 0.30; // never launch the target over the caster's head
    private static final double PULL_GROUND_LIFT = 0.12;// just enough to break ground friction
    private static final double SNAP_STOP_DISTANCE = 1.0;// stop pulling once this close to the leash edge

    private static final double YANK_DAMAGE = 1.0;      // half a heart of true damage
    private static final int YANK_DAMAGE_COOLDOWN = 20; // ticks between stings
    private static final int SLOW_DURATION = 10;        // refreshed while grabbed

    // Ground anchor model (the original tentacle): 24.7/16 blocks tall, tip curls ~4.6/16 blocks towards +X.
    private static final double MODEL_HEIGHT = 24.7 / 16.0;
    private static final double MODEL_CURL = 4.6 / 16.0;
    // Wrap model: ONE tentacle coiled ~2.25 turns around a 0.6 x 1.8 block body, base at the feet.
    private static final double WRAP_BODY_WIDTH = 0.6;
    private static final double WRAP_BODY_HEIGHT = 1.8;
    private static final double SPIN_PER_TICK = 0.12;   // radians/tick the coil slowly twists
    private static final int WRAP_RISE_TICKS = 10;      // how long the coil takes to climb the body
    private static final int ROPE_PARTICLE_INTERVAL = 2; // ticks between rope particle updates

    /** Model height is 24.7/16 of a block with its base at y=0 of the 16-unit cube, so half a block under the display's centre. */
    private static final NamespacedKey TENTACLE_MODEL =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:water_tentacle"));

    private final ElementSMPRefined plugin;
    private final Map<UUID, TetherState> tethers = new ConcurrentHashMap<>();

    public WaterTetherAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("water_tether", ElementType.WATER, 2, 25, 2, configManager);
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

        TetherState state = new TetherState(player, target, groundBelow(target.getLocation()));
        tethers.put(player.getUniqueId(), state);
        super.setActive(player, true);

        state.anchorTentacle = spawnAnchor(state);

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
        if (!state.anchor.getWorld().equals(target.getWorld())) return false;
        if (++state.ticks > DURATION_TICKS) return false;

        Location targetPos = target.getLocation();
        double distance = targetPos.distance(state.anchor);

        // Grabbed targets are slowed for as long as the tentacle holds them.
        if (state.ticks % 5 == 1) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, SLOW_DURATION, 0, true, false, false));
        }

        boolean yanked = false;
        if (distance > LEASH_LENGTH) {
            yank(target, state.anchor, targetPos, distance);
            yanked = true;
            if (state.yankCooldown <= 0) {
                TrueDamage.of(YANK_DAMAGE).attacker(caster).ability(getName()).apply(target);
                target.getWorld().playSound(targetPos, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.9f, 0.8f);
                WaterParticles.tetherYank(targetPos.clone().add(0, target.getHeight() / 2.0, 0));
                state.yankCooldown = YANK_DAMAGE_COOLDOWN;
            }
        }
        if (state.yankCooldown > 0) state.yankCooldown--;

        updateAnchor(state);
        if (state.ticks % ROPE_PARTICLE_INTERVAL == 0) {
            WaterParticles.tetherRope(anchorTip(state), target.getLocation().add(0, target.getHeight() * 0.5, 0),
                    yanked, state.ticks);
        }
        return true;
    }

    /** Pulls the target back towards the anchor, harder the further past the leash they are. */
    private void yank(LivingEntity target, Location anchor, Location targetPos, double distance) {
        double overshoot = distance - LEASH_LENGTH;
        if (overshoot < 0.05) return;

        Vector pull = anchor.toVector().subtract(targetPos.toVector());
        double horizontalLen = Math.hypot(pull.getX(), pull.getZ());
        if (horizontalLen < 0.0001) return;

        double strength = Math.min(PULL_MAX, PULL_BASE + overshoot * PULL_PER_BLOCK);
        // Don't overshoot the leash edge on the last bit of the pull.
        strength = Math.min(strength, overshoot + SNAP_STOP_DISTANCE);

        Vector velocity = new Vector(pull.getX() / horizontalLen * strength, 0, pull.getZ() / horizontalLen * strength);

        double upward = pull.getY() / Math.max(1.0, distance) * strength;
        if (target.isOnGround()) upward = Math.max(upward, PULL_GROUND_LIFT);
        double currentY = target.getVelocity().getY();
        velocity.setY(Math.max(-0.5, Math.min(PULL_MAX_UPWARD, Math.max(currentY, upward))));

        target.setVelocity(velocity);
    }


    /** Coil model sized to the target's body: xz from its width, y from its height; {@code rise} (0-1) is how far up it has climbed. */
    private static Transformation wrapTransform(LivingEntity target, float spin, float rise) {
        float sx = (float) Math.max(0.5, target.getWidth() / WRAP_BODY_WIDTH);
        float sy = (float) Math.max(0.5, target.getHeight() / WRAP_BODY_HEIGHT) * rise;
        // Model base is half a block (scaled) below the display's centre; lift it onto the target's feet.
        return new Transformation(
                new Vector3f(0f, 0.5f * sy, 0f),
                new AxisAngle4f(spin, 0f, 1f, 0f),
                new Vector3f(sx, sy, sx),
                new AxisAngle4f());
    }

    // ---- anchor (the tether's core point, on the ground) ----

    /** Finds the ground below a location (up to 20 blocks down); falls back to the location itself. */
    private static Location groundBelow(Location loc) {
        RayTraceResult hit = loc.getWorld().rayTraceBlocks(loc.clone().add(0, 0.5, 0), new Vector(0, -1, 0),
                20.0, FluidCollisionMode.NEVER, true);
        if (hit == null) return loc.clone();
        Location ground = hit.getHitPosition().toLocation(loc.getWorld());
        ground.setYaw(0);
        ground.setPitch(0);
        return ground;
    }

    private ItemDisplay spawnAnchor(TetherState state) {
        Location at = state.anchor;
        float yRot = anchorFacing(state);
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(ModelItems.of(TENTACLE_MODEL));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.5f);
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.setTransformation(transform(yRot, (float) ANCHOR_SCALE * 0.05f));
        });
    }

    private void updateAnchor(TetherState state) {
        ItemDisplay anchor = state.anchorTentacle;
        if (anchor == null || !anchor.isValid()) {
            state.anchorTentacle = spawnAnchor(state);
            return;
        }
        if (state.ticks == 1 || state.ticks % 3 == 0) {
            anchor.setInterpolationDelay(0);
            anchor.setInterpolationDuration(state.ticks <= 3 ? GROW_TICKS : 3);
            anchor.setTransformation(transform(anchorFacing(state), (float) ANCHOR_SCALE));
        }
    }

    /** The anchor tentacle leans towards the target it is holding. */
    private float anchorFacing(TetherState state) {
        double dx = state.target.getLocation().getX() - state.anchor.getX();
        double dz = state.target.getLocation().getZ() - state.anchor.getZ();
        if (dx * dx + dz * dz < 0.04) return state.lastAnchorYaw;
        state.lastAnchorYaw = (float) Math.atan2(-dz, dx);
        return state.lastAnchorYaw;
    }

    /** The curled tip of the anchor tentacle - where the rope starts. */
    private Location anchorTip(TetherState state) {
        Vector toward = state.target.getLocation().toVector().subtract(state.anchor.toVector()).setY(0);
        if (toward.lengthSquared() > 0.0001) toward.normalize(); else toward.zero();
        return state.anchor.clone()
                .add(toward.multiply(MODEL_CURL * ANCHOR_SCALE))
                .add(0, MODEL_HEIGHT * ANCHOR_SCALE * 0.95, 0);
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
        if (state.wrap != null) state.wrap.remove();
        if (state.anchorTentacle != null) state.anchorTentacle.remove();
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
            // tentacle down instead of just dropping the flag.
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
        return ChatColor.AQUA + "Tidal Tether";
    }

    @Override
    public String getDescription() {
        return "Tentacles anchor to the ground and wrap your target. Stray too far from the anchor and they get yanked back and slowed.";
    }

    private static class TetherState {
        final Player caster;
        final LivingEntity target;
        final Location anchor;      // ground point the target is leashed to
        ItemDisplay anchorTentacle; // the model standing on it
        ItemDisplay wrap;           // the single tentacle coiled around the target
        float lastAnchorYaw = 0f;
        org.bukkit.scheduler.BukkitTask task;
        int ticks = 0;
        int yankCooldown = 0;

        TetherState(Player caster, LivingEntity target, Location anchor) {
            this.caster = caster;
            this.target = target;
            this.anchor = anchor;
        }
    }
}