package hs.elementSMPRefined.ability.main.fire;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.API.ability.BaseAbility;
import hs.elementSMPRefined.managers.ManaManager;
import hs.elementSMPRefined.managers.TrustManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Meteor Crash - The player is launched high into the air, then steers
 * themselves toward wherever they're looking horizontally, so they can drift
 * over to what they're aiming at while following a normal launch and fall.
 */
public class MeteorCrashAbility extends BaseAbility {
    private final ElementSMPRefined plugin;

    private final Map<UUID, BukkitRunnable> meteorTasks = new HashMap<>();
    private final Map<UUID, TrustManager> activeTrust = new HashMap<>();

    // Launch tuning - 2.3 puts the apex at roughly 25 blocks under vanilla
    // gravity (~-0.08/tick with 0.98 drag), with the launch velocity itself
    // and not the look-steering doing the heavy lifting.
    private static final double LAUNCH_UP_VELOCITY = 2.3;
    private static final double LAUNCH_FORWARD_VELOCITY = 0.7; // horizontal, in the direction the player is facing

    // Steering tuning. Vertical motion is intentionally left to the launch
    // impulse and vanilla gravity so pitch changes cannot cause oscillation.
    private static final double STEER_ACCEL_HORIZONTAL = 0.1; // pull toward look direction's horizontal component each tick
    private static final double MAX_HORIZONTAL_SPEED = 1.6; // horizontal-only cap, applied after steering each tick

    // Ground-impact AOE tuning
    private static final int GRACE_TICKS = 6; // brief liftoff window before ground-impact checks start
    private static final int SAFETY_TIMEOUT_TICKS = 160; // 8s - force a crash at current location if never lands (water/void)
    private static final double IMPACT_RADIUS = 4.5;
    private static final double IMPACT_DAMAGE = 14.0;
    private static final double IMPACT_KNOCKBACK = 2.2;
    private static final double IMPACT_KNOCKBACK_UP = 1.6; // vertical launch applied to entities on ground-impact

    public MeteorCrashAbility(JavaPlugin plugin) {
        super("fire_meteor_crash", 75, 15, 2);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        UUID playerId = player.getUniqueId();

        if (meteorTasks.containsKey(playerId)) {
            // Already mid-crash - ignore, let it play out (cooldown normally prevents this anyway).
            return false;
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

        // --- Launch: an impulse, not a flight toggle. Steering + gravity
        // take over from here every tick in the task below. ---
        Vector launch = startLoc.getDirection().setY(0).normalize().multiply(LAUNCH_FORWARD_VELOCITY);
        launch.setY(LAUNCH_UP_VELOCITY);
        player.setVelocity(launch);
        player.setFallDistance(0f);

        activeTrust.put(playerId, trust);

        world.playSound(startLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        world.playSound(startLoc, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.8f);
        player.sendMessage(ChatColor.GOLD + "You launch into a meteor crash!");

        BukkitRunnable task = new BukkitRunnable() {
            private int ticksAlive = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    endMeteorCrash(player, false);
                    cancel();
                    return;
                }

                ticksAlive++;

                // Give the player a moment to lift off before we start
                // checking for a ground hit, so casting while standing on
                // the ground doesn't detonate instantly.
                if (ticksAlive > GRACE_TICKS && player.isOnGround()) {
                    endMeteorCrash(player, true);
                    cancel();
                    return;
                }

                // Safety valve: if they never touch ground (water, void,
                // some other effect), force the crash where they currently
                // are instead of leaving the task running forever.
                if (ticksAlive > SAFETY_TIMEOUT_TICKS) {
                    endMeteorCrash(player, true);
                    cancel();
                    return;
                }

                // Never a real fall - the crash's own impact damage covers
                // the landing, so don't let vanilla fall damage stack on top.
                player.setFallDistance(0f);

                // Steer horizontally toward the current heading. Leave the
                // vertical component alone so looking up or level cannot
                // fight the launch and gravity simulation.
                Vector look = player.getLocation().getDirection().normalize();
                Vector velocity = player.getVelocity();

                Vector horizLook = new Vector(look.getX(), 0, look.getZ());
                if (horizLook.lengthSquared() > 1.0E-4) {
                    velocity.add(horizLook.normalize().multiply(STEER_ACCEL_HORIZONTAL));
                }

                // Only cap horizontal speed. Capping the full 3D vector here
                // would also crush the vertical launch velocity back down to
                // MAX_HORIZONTAL_SPEED on literally the first tick, which is
                // what was killing the launch height - vertical motion is
                // left to gravity, the dive assist, and the compensation
                // above only.
                Vector horizontal = new Vector(velocity.getX(), 0, velocity.getZ());
                if (horizontal.length() > MAX_HORIZONTAL_SPEED) {
                    horizontal.normalize().multiply(MAX_HORIZONTAL_SPEED);
                    velocity.setX(horizontal.getX());
                    velocity.setZ(horizontal.getZ());
                }
                player.setVelocity(velocity);

                Location playerLoc = player.getLocation();

                // Fire trail
                world.spawnParticle(Particle.FLAME, playerLoc, 10, 0.5, 0.5, 0.5, 0.1, null, true);
                world.spawnParticle(Particle.LAVA, playerLoc, 3, 0.3, 0.3, 0.3, 0.05, null, true);

                world.playSound(playerLoc, Sound.ENTITY_BLAZE_BURN, 0.5f, 1.0f);
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        meteorTasks.put(playerId, task);

        return true;
    }

    private void endMeteorCrash(Player player, boolean groundImpact) {
        UUID playerId = player.getUniqueId();
        BukkitRunnable task = meteorTasks.remove(playerId);
        TrustManager trust = activeTrust.remove(playerId);

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

        // Landing is intentional (the ability's own damage covers it) -
        // clear fall distance one last time so the imminent ground contact
        // doesn't also deal vanilla fall damage to the caster.
        player.setFallDistance(0f);
    }

    /**
     * One-time AOE damage burst applied when the meteor actually crashes
     * into the ground - knocks nearby entities into the air and damages
     * them.
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
            knockback.setY(IMPACT_KNOCKBACK_UP);
            entity.setVelocity(entity.getVelocity().add(knockback));
        }
    }

    @Override
    public String getName() {
        return ChatColor.RED + "Meteor Crash";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Launch high into the air and steer wherever you look, then slam down as a blazing meteor, knocking nearby enemies airborne and dealing massive damage on impact.";
    }
}