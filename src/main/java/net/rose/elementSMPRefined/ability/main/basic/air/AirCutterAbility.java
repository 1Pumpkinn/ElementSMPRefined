package net.rose.elementSMPRefined.ability.main.basic.air;

import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.visual.model.AirCutterVisual;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Air Cutter - fires a fan of three fast-moving blades of compressed air in front of
 * the player. Each blade cuts through anything in a narrow line, dealing damage and
 * knocking targets away.
 * <p>
 * The blades are drawn with the {@code elementsmp:air_cutter} resource-pack model via
 * {@link AirCutterVisual} (an ItemDisplay teleported each tick), the same way Hell's Chain
 * draws its chain with {@code ChainVisual}.
 */
public class AirCutterAbility extends BaseAbility {

    /** Number of blades per cast. They fan out symmetrically around the player's aim. */
    private static final int CUTTER_COUNT = 3;
    /** Horizontal angle, in degrees, between neighbouring blades. */
    private static final double SPREAD_DEGREES = 15.0;
    /** Horizontal knockback applied once per cast (was 1.4 and stacked once per blade). */
    private static final double KNOCKBACK_HORIZONTAL = 0.6;
    /** Small lift so targets slide instead of scraping along the ground. */
    private static final double KNOCKBACK_VERTICAL = 0.2;

    private final ElementSMPRefined plugin;

    public AirCutterAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("air_cutter", ElementType.AIR, 2, 60, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        TrustManager trust = context.getTrustManager();

        World w = player.getWorld();
        Vector aim = player.getLocation().getDirection().normalize();
        Location origin = player.getEyeLocation();

        w.playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 1.4f);

        // Shared across all blades of this cast so a target is only hit (and knocked back) once,
        // instead of once per blade with the knockback stacking up to 3x.
        final java.util.Set<java.util.UUID> hitEntities = new java.util.HashSet<>();

        for (int i = 0; i < CUTTER_COUNT; i++) {
            // Offsets are centred on the aim: for 3 blades that's -15deg, 0deg, +15deg.
            double offset = (i - (CUTTER_COUNT - 1) / 2.0) * SPREAD_DEGREES;
            Vector direction = aim.clone().rotateAroundY(Math.toRadians(offset)).normalize();
            launchCutter(player, trust, origin, direction, hitEntities);
        }

        return true;
    }

    /** Launches a single blade along {@code direction}; hits are tracked per cast via {@code hitEntities}. */
    private void launchCutter(Player player, TrustManager trust, Location origin, Vector direction,
                              java.util.Set<java.util.UUID> hitEntities) {
        World w = origin.getWorld();

        double range = 20.0;
        double hitboxWidth = 1.1;
        double damage = 6.0;

        new BukkitRunnable() {
            double travelled = 0;
            /** Where the blade was last tick, so each step can be ray-traced for blocks. */
            Location previous = origin.clone();
            final AirCutterVisual blade =
                    new AirCutterVisual(origin.clone().add(direction.clone().multiply(1.0)), direction, player);

            /** Every exit path calls cancel(), so the blade entity can never outlive the ability. */
            @Override
            public synchronized void cancel() throws IllegalStateException {
                blade.remove();
                super.cancel();
            }

            @Override
            public void run() {
                if (!player.isOnline() || travelled >= range) {
                    cancel();
                    return;
                }

                travelled += 1.5;
                Location slice = origin.clone().add(direction.clone().multiply(travelled));

                // The blade moves 1.5 blocks a tick, so check the whole step for a solid block rather
                // than just the end point (otherwise it skips straight through thin floors and walls).
                Vector step = slice.toVector().subtract(previous.toVector());
                double stepLength = step.length();
                boolean blocked = false;
                if (stepLength > 1.0E-4) {
                    RayTraceResult wall = w.rayTraceBlocks(previous, step.clone().normalize(), stepLength,
                            FluidCollisionMode.NEVER, true);
                    if (wall != null) {
                        blocked = true;
                        // Stop just short of the surface so the blade doesn't visually sink into it.
                        Vector stop = wall.getHitPosition().clone().subtract(step.clone().normalize().multiply(0.15));
                        slice = stop.toLocation(w);
                        w.spawnParticle(Particle.CLOUD, wall.getHitPosition().toLocation(w), 6, 0.2, 0.2, 0.2, 0.02, null, true);
                    }
                }
                previous = slice.clone();

                blade.moveTo(slice);
                // Faint wind trail behind the blade
                w.spawnParticle(Particle.CLOUD, slice, 2, 0.5, 0.05, 0.5, 0.0, null, true);

                for (LivingEntity e : slice.getNearbyLivingEntities(hitboxWidth)) {
                    if (e.equals(player)) continue;
                    if (e instanceof Player other && trust.isTrusted(player.getUniqueId(), other.getUniqueId())) continue;
                    if (hitEntities.contains(e.getUniqueId())) continue;

                    hitEntities.add(e.getUniqueId());
                    TrueDamage.of(damage).attacker(player).ability(getName()).ignoreIFrames(false).apply(e);

                    // Horizontal only, and SET rather than added onto existing velocity.
                    Vector knockback = new Vector(direction.getX(), 0, direction.getZ());
                    if (knockback.lengthSquared() > 1.0E-4) {
                        knockback.normalize().multiply(KNOCKBACK_HORIZONTAL);
                    }
                    knockback.setY(KNOCKBACK_VERTICAL);
                    e.setVelocity(knockback);
                    e.getWorld().spawnParticle(Particle.SWEEP_ATTACK, e.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0, null, true);
                }

                if (blocked) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public String getName() {
        return ChatColor.WHITE + "Air Cutter";
    }

    @Override
    public String getDescription() {
        return "Fires three blades of air that damage and knock back enemies.";
    }
}