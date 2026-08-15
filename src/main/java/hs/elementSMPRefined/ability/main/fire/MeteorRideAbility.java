package hs.elementSMPRefined.ability.main.fire;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.API.ElementContext;
import hs.elementSMPRefined.API.BaseAbility;
import hs.elementSMPRefined.managers.ManaManager;
import hs.elementSMPRefined.managers.TrustManager;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Meteor Ride - The player is granted direct flight control (so all normal
 * WASD/space/shift movement input applies to them) while a magma-block
 * Block Display is glued to their position every tick, making it look like
 * they're riding/embedded in a molten meteor.
 *
 * IMPORTANT ROTATION NOTE: Display transformations apply translation AFTER
 * rotation (world = translation + rotation * scale * vertex). A constant
 * translation therefore only centers the block at one specific angle - as
 * the angle changes the pivot silently shifts to the block's corner and it
 * sweeps an arc instead of spinning in place. buildTransform() below
 * recomputes the translation every frame from the current angle so the
 * pivot always stays fixed at the display's own location.
 */
public class MeteorRideAbility extends BaseAbility {
    private final ElementSMPRefined plugin;

    private final Map<UUID, BlockDisplay> activeDisplays = new HashMap<>();
    private final Map<UUID, BukkitRunnable> meteorTasks = new HashMap<>();
    private final Map<UUID, Float> spinAngles = new HashMap<>();

    // Saved flight state to restore when the ride ends
    private final Map<UUID, Boolean> prevAllowFlight = new HashMap<>();
    private final Map<UUID, Boolean> prevFlying = new HashMap<>();
    private final Map<UUID, Float> prevFlySpeed = new HashMap<>();
    private final Map<UUID, TrustManager> activeTrust = new HashMap<>();

    private static final float METEOR_SCALE = 1.35f;
    private static final float SPIN_SPEED = 0.12f; // radians per tick
    private static final float RIDE_FLY_SPEED = 0.1f; // vanilla default flying speed - tune to taste
    private static final double VERTICAL_OFFSET = 1.1; // how far below the player the meteor sits

    // Ground-impact AOE tuning
    private static final int GRACE_TICKS = 10; // ~0.5s liftoff window before ground-impact checks start
    private static final double IMPACT_RADIUS = 4.5;
    private static final double IMPACT_DAMAGE = 14.0;
    private static final double IMPACT_KNOCKBACK = 2.2;

    public MeteorRideAbility(ElementSMPRefined plugin) {
        super("fire_meteor_ride", 60, 15, 2);
        this.plugin = plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        UUID playerId = player.getUniqueId();

        // Re-casting while already riding toggles it off early instead of
        // starting a new ride (sneak is left alone since it's needed to
        // descend while flying).
        if (activeDisplays.containsKey(playerId)) {
            endMeteorRide(player, false);
            return true;
        }

        ManaManager mana = context.getManaManager();
        TrustManager trust = context.getTrustManager();
        int cost = getManaCost();

        if (!mana.hasMana(player, cost)) {
            player.sendMessage(ChatColor.RED + "Not enough mana (" + cost + ")");
            return false;
        }

        World world = player.getWorld();
        Location startLoc = player.getLocation();

        // --- Save current flight state, then grant full flight control ---
        prevAllowFlight.put(playerId, player.getAllowFlight());
        prevFlying.put(playerId, player.isFlying());
        prevFlySpeed.put(playerId, player.getFlySpeed());

        player.setAllowFlight(true);
        player.setFlying(true);
        player.setFlySpeed(RIDE_FLY_SPEED);

        // --- Spawn the magma block display that acts as the meteor skin ---
        BlockDisplay display = world.spawn(startLoc, BlockDisplay.class, d -> {
            d.setBlock(Material.MAGMA_BLOCK.createBlockData());
            d.setBrightness(new Display.Brightness(15, 15));
            // Interpolation window matches our per-tick update rate (1 tick).
            // A longer window (e.g. 3) makes the display perpetually chase a
            // target that's already moved again before it catches up, which
            // reads as lag when the player moves continuously.
            d.setInterpolationDuration(1);
            d.setInterpolationDelay(0);
            d.setTeleportDuration(1);
            d.setTransformation(buildTransform(0f));
        });

        activeDisplays.put(playerId, display);
        spinAngles.put(playerId, 0f);
        activeTrust.put(playerId, trust);

        world.playSound(startLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        world.playSound(startLoc, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.8f);
        player.sendMessage(ChatColor.GOLD + "You are riding the meteor! Use the ability again to end early.");

        BukkitRunnable task = new BukkitRunnable() {
            private int ticksAlive = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !display.isValid()) {
                    endMeteorRide(player, false);
                    cancel();
                    return;
                }

                ticksAlive++;

                // Give the player a moment to lift off before we start
                // checking for a ground hit, so casting while standing on
                // the ground doesn't detonate instantly.
                if (ticksAlive > GRACE_TICKS && player.isOnGround()) {
                    endMeteorRide(player, true);
                    cancel();
                    return;
                }

                Location playerLoc = player.getLocation();
                Location meteorLoc = playerLoc.clone().subtract(0, VERTICAL_OFFSET, 0);

                // Keep the meteor skin glued beneath the player, spinning in place
                float angle = spinAngles.merge(playerId, SPIN_SPEED, Float::sum);
                display.teleport(meteorLoc);
                display.setTransformation(buildTransform(angle));

                // Fire trail
                world.spawnParticle(Particle.FLAME, playerLoc, 10, 0.5, 0.5, 0.5, 0.1, null, true);
                world.spawnParticle(Particle.LAVA, playerLoc, 3, 0.3, 0.3, 0.3, 0.05, null, true);

                // Damage entities on contact
                double damageRadius = 2.0;
                for (LivingEntity entity : playerLoc.getNearbyLivingEntities(damageRadius)) {
                    if (entity.equals(player)) continue;
                    if (entity instanceof Player other && trust.isTrusted(player.getUniqueId(), other.getUniqueId())) continue;

                    entity.damage(8.0, player);
                    entity.setFireTicks(80); // 4 seconds

                    Vector knockback = entity.getLocation().toVector()
                            .subtract(playerLoc.toVector())
                            .normalize()
                            .multiply(1.5);
                    knockback.setY(0.5);
                    entity.setVelocity(entity.getVelocity().add(knockback));
                }

                world.playSound(playerLoc, Sound.ENTITY_BLAZE_BURN, 0.5f, 1.0f);
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        meteorTasks.put(playerId, task);

        // Auto-end after 8 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activeDisplays.containsKey(playerId)) {
                    endMeteorRide(player, false);
                }
            }
        }.runTaskLater(plugin, 160L);

        return true;
    }

    /**
     * Builds a transformation that rotates the block around its own center
     * (a fixed point at the display entity's location) regardless of angle,
     * by recomputing the translation each frame to cancel out the pivot
     * shift that a constant translation would otherwise introduce.
     */
    private Transformation buildTransform(float angleRadians) {
        float half = 0.5f * METEOR_SCALE;

        // Rotate the half-extent vector around the Y axis by hand (simple
        // trig instead of relying on AxisAngle4f#transform, to keep this
        // dependency-free and easy to verify).
        float cos = (float) Math.cos(angleRadians);
        float sin = (float) Math.sin(angleRadians);
        float rotatedHalfX = half * cos + half * sin;
        float rotatedHalfZ = -half * sin + half * cos;

        Vector3f translation = new Vector3f(-rotatedHalfX, -half, -rotatedHalfZ);

        return new Transformation(
                translation,
                new AxisAngle4f(angleRadians, 0f, 1f, 0f),
                new Vector3f(METEOR_SCALE, METEOR_SCALE, METEOR_SCALE),
                new AxisAngle4f(0f, 0f, 0f, 1f)
        );
    }

    private void endMeteorRide(Player player, boolean groundImpact) {
        UUID playerId = player.getUniqueId();
        BlockDisplay display = activeDisplays.remove(playerId);
        BukkitRunnable task = meteorTasks.remove(playerId);
        TrustManager trust = activeTrust.remove(playerId);
        spinAngles.remove(playerId);

        if (task != null) {
            task.cancel();
        }

        Location endLoc = player.getLocation();
        World world = endLoc.getWorld();

        // Cosmetic boom only (particles + sound) - deliberately NOT using
        // World#createExplosion here, since that deals its own vanilla
        // explosion damage/knockback on top of whatever we apply manually
        // below, which would make ground impacts hit twice as hard as
        // intended and be affected by explosion resistance/protection in
        // ways we don't control.
        if (groundImpact) {
            world.spawnParticle(Particle.EXPLOSION, endLoc, 3, 0.5, 0.3, 0.5, 0);
            world.playSound(endLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.8f);
            triggerGroundImpactDamage(player, trust, endLoc, world);
        } else {
            world.spawnParticle(Particle.EXPLOSION, endLoc, 1);
            world.playSound(endLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        }

        if (display != null && display.isValid()) {
            display.remove();
        }

        // Restore the player's original flight state
        Boolean wasFlying = prevFlying.remove(playerId);
        Boolean allowFlight = prevAllowFlight.remove(playerId);
        Float flySpeed = prevFlySpeed.remove(playerId);

        if (wasFlying != null) {
            player.setFlying(wasFlying);
        }
        if (allowFlight != null) {
            player.setAllowFlight(allowFlight);
        }
        if (flySpeed != null) {
            player.setFlySpeed(flySpeed);
        }

        // Small upward boost so the player doesn't immediately drop if flight was revoked mid-air
        if (allowFlight == null || !allowFlight) {
            player.setVelocity(new Vector(0, 0.3, 0));
        }
    }

    /**
     * One-time AOE damage burst applied when the meteor actually crashes
     * into the ground, separate from the smaller continuous contact damage
     * applied while still airborne.
     */
    private void triggerGroundImpactDamage(Player player, TrustManager trust, Location impactLoc, World world) {
        for (LivingEntity entity : impactLoc.getNearbyLivingEntities(IMPACT_RADIUS)) {
            if (entity.equals(player)) continue;
            if (trust != null && entity instanceof Player other && trust.isTrusted(player.getUniqueId(), other.getUniqueId())) continue;

            entity.damage(IMPACT_DAMAGE, player);
            entity.setFireTicks(100); // 5 seconds

            Vector knockback = entity.getLocation().toVector().subtract(impactLoc.toVector());
            if (knockback.lengthSquared() < 1.0E-4) {
                // Entity is essentially on top of the impact point - push it
                // in an arbitrary horizontal direction instead of failing
                // to normalize a zero-length vector.
                knockback = new Vector(1, 0, 0);
            }
            knockback = knockback.normalize().multiply(IMPACT_KNOCKBACK);
            knockback.setY(0.8);
            entity.setVelocity(entity.getVelocity().add(knockback));
        }
    }

    @Override
    public String getName() {
        return ChatColor.RED + "Meteor Ride";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Become a blazing meteor with full flight control and crash into enemies to deal massive damage. Recast to end early.";
    }
}