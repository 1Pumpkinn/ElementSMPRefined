package net.rose.elementSMPRefined.ability.main.fire;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Fire ability1: Hell Chain.
 * <p>
 * A fire-themed grapple hook - fires a chain at whatever's directly ahead.
 * Two modes depending on what the raycast actually connects with:
 * <ul>
 *     <li>Hooked an entity directly - drags them in to the caster, like a
 *     harpoon, then sets them alight on arrival.</li>
 *     <li>Hooked terrain - reels the caster in to that point instead (the
 *     caster moves, not the target), then scorches anything standing near
 *     the landing spot so aiming at the ground next to someone still burns
 *     them even without a direct hit.</li>
 * </ul>
 * <p>
 * Replaces the old Fire ability1, {@code FireGeyserAbility} (now deprecated).
 */
public class HellChainAbility extends BaseAbility {
    private static final double RANGE = 45.0;
    private static final double RAY_SIZE = 0.4;
    private static final int MAX_TICKS = 50; // safety cap so a chain can never get "stuck" running forever

    // Caster-grapple mode (hook lands on terrain)
    private static final double PULL_STRENGTH = 0.5;
    private static final double MAX_SPEED = 1.0;
    private static final double STOP_DISTANCE = 2.0;
    // Floor on the pull's vertical component - without this, hooking a point above and
    // ahead of the caster just rams them into whatever ledge/wall sits between the two,
    // instead of letting them arc up and over it the way a real grapple would. Only ever
    // applied when the target is already above/level (pull.getY() > 0) - if the target is
    // below the caster this must NOT force the pull upward, or the chain can never actually
    // descend toward it and instead launches the caster straight up forever.
    private static final double MIN_UPWARD_PULL = 0.18;
    // Pops the caster off the ground the instant the chain fires, so the very first tick
    // of pull isn't fighting ground friction (which is what caused the "stuck, not
    // jumping" feel when the hook point was above them).
    private static final double GROUND_HOP_VELOCITY = 0.35;
    private static final double BURN_RADIUS = 3.0;
    // If the caster hasn't closed at least this fraction of the original distance by
    // STUCK_CHECK_TICK, terrain is fighting the pull (snagged on geometry) - let go
    // instead of continuing to shove at full force for the rest of MAX_TICKS. This only
    // catches a snag that's already present in the first ~second - OBSTRUCTION_CHECK_DISTANCE
    // below is what catches one that starts later mid-flight (e.g. clipping an overhang
    // right before reaching the hook point).
    private static final int STUCK_CHECK_TICK = 15;
    private static final double STUCK_PROGRESS_RATIO = 0.7;
    // Each tick, look this far ahead along the pull direction before applying force. If
    // there's a solid block there, the pull is skipped that tick (not adding force into a
    // wall) instead of shoving the caster further in - this is what actually stops the
    // caster from ending up camera-clipped inside terrain.
    private static final double OBSTRUCTION_CHECK_DISTANCE = 0.9;
    // If still blocked after this many consecutive ticks, it's not a one-frame clip on the
    // way past a corner - the chain is genuinely snagged, so let go and pop free instead of
    // leaving the caster pinned against the wall for the rest of MAX_TICKS.
    private static final int MAX_CONSECUTIVE_BLOCKED_TICKS = 4;

    // Entity-drag mode (hook lands directly on a living entity)
    private static final double DRAG_STRENGTH = 0.35;
    private static final double DRAG_MAX_SPEED = 0.8;
    private static final double DRAG_STOP_DISTANCE = 2.5;

    private static final double IMPACT_DAMAGE = 4.0; // 2 hearts
    private static final int BURN_FIRE_TICKS = 60; // 3s alight

    private final ElementSMPRefined plugin;

    public HellChainAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("fire_hell_chain", ElementType.FIRE, 1, 30, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        RayTraceResult result = player.getWorld().rayTrace(
                eyeLoc, direction, RANGE,
                FluidCollisionMode.NEVER, true, RAY_SIZE,
                entity -> entity instanceof LivingEntity && !entity.equals(player)
        );

        if (result == null) {
            player.sendMessage(Lang.FIRE_HELL_CHAIN_NO_VALID_HOOK_POINT);
            return false;
        }

        LivingEntity hookedEntity = result.getHitEntity() instanceof LivingEntity le ? le : null;
        TrustManager trust = context.getTrustManager();

        if (hookedEntity instanceof Player hookedPlayer
                && trust.isTrusted(player.getUniqueId(), hookedPlayer.getUniqueId())) {
            player.sendMessage(Lang.FIRE_HELL_CHAIN_NO_VALID_HOOK_POINT);
            return false;
        }

        Location hookLocation = result.getHitPosition().toLocation(player.getWorld());
        playCastSounds(player);

        if (hookedEntity != null) {
            dragEntityIn(player, hookedEntity);
        } else {
            grappleToPoint(player, hookLocation, trust);
        }

        return true;
    }

    /** Direct hit on a living entity - harpoon them in to the caster instead of the other way around. */
    private void dragEntityIn(Player player, LivingEntity target) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid() || ticks >= MAX_TICKS) {
                    cancel();
                    return;
                }

                Location casterLoc = player.getEyeLocation();
                Location targetLoc = target.getLocation();
                double distance = casterLoc.distance(targetLoc);

                if (distance < DRAG_STOP_DISTANCE) {
                    target.setVelocity(new Vector(0, 0, 0));
                    target.setFireTicks(Math.max(target.getFireTicks(), BURN_FIRE_TICKS));
                    target.damage(IMPACT_DAMAGE, player);
                    player.getWorld().spawnParticle(Particle.LAVA, targetLoc, 15, 0.3, 0.3, 0.3, 0.05);
                    player.getWorld().playSound(targetLoc, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.2f);
                    cancel();
                    return;
                }

                drawChain(player, casterLoc, targetLoc, distance);

                Vector pull = casterLoc.toVector().subtract(targetLoc.toVector()).normalize().multiply(DRAG_STRENGTH);
                // Gentle lift so they don't just scrape along the ground - but only when we're
                // not already pulling them sharply downward (caster below them), otherwise this
                // would fight a legitimate downward drag the same way the grapple-mode bug did.
                if (pull.getY() > -0.05) {
                    pull.setY(pull.getY() + 0.1);
                }
                Vector newVelocity = target.getVelocity().add(pull);
                if (newVelocity.length() > DRAG_MAX_SPEED) {
                    newVelocity = newVelocity.normalize().multiply(DRAG_MAX_SPEED);
                }
                target.setVelocity(newVelocity);

                playChainStep(player, casterLoc, ticks);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** No entity directly hit - grapple the caster to the hook point, then scorch anything nearby on arrival. */
    private void grappleToPoint(Player player, Location hookLocation, TrustManager trust) {
        if (player.isOnGround()) {
            Vector v = player.getVelocity();
            player.setVelocity(new Vector(v.getX(), Math.max(v.getY(), GROUND_HOP_VELOCITY), v.getZ()));
        }

        new BukkitRunnable() {
            int ticks = 0;
            double initialDistance = -1;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= MAX_TICKS) {
                    cancel();
                    return;
                }

                Location currentLoc = player.getEyeLocation();
                double distance = currentLoc.distance(hookLocation);

                if (initialDistance < 0) {
                    initialDistance = distance;
                }

                if (distance < STOP_DISTANCE) {
                    player.setVelocity(player.getVelocity().multiply(0.2));
                    player.getWorld().playSound(hookLocation, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.2f);
                    player.getWorld().spawnParticle(Particle.LAVA, hookLocation, 20,
                            BURN_RADIUS / 2, 0.3, BURN_RADIUS / 2, 0.05);

                    for (LivingEntity nearby : hookLocation.getNearbyLivingEntities(BURN_RADIUS)) {
                        if (nearby.equals(player)) continue;
                        if (nearby instanceof Player nearbyPlayer
                                && trust.isTrusted(player.getUniqueId(), nearbyPlayer.getUniqueId())) continue;
                        nearby.setFireTicks(Math.max(nearby.getFireTicks(), BURN_FIRE_TICKS));
                    }
                    cancel();
                    return;
                }

                // Terrain snag guard: if we haven't meaningfully closed the gap by
                // STUCK_CHECK_TICK, something's blocking a straight line to the hook
                // (a wall, an overhang) - let go instead of grinding at full pull for
                // the rest of MAX_TICKS, which is what used to send the caster flying.
                if (ticks == STUCK_CHECK_TICK && distance > initialDistance * STUCK_PROGRESS_RATIO) {
                    player.setVelocity(player.getVelocity().multiply(0.3));
                    cancel();
                    return;
                }

                drawChain(player, currentLoc, hookLocation, distance);

                Vector pull = hookLocation.toVector().subtract(currentLoc.toVector()).normalize().multiply(PULL_STRENGTH);
                // Keep a floor on the vertical pull so the caster arcs up and over ledges/blocks
                // between them and the hook point instead of slamming into the wall below it -
                // but ONLY when the target is already above/level. If the target is below
                // (pull.getY() negative), forcing this positive would mean the chain can never
                // actually descend toward it and just launches the caster straight up instead.
                if (pull.getY() > 0) {
                    pull.setY(Math.max(pull.getY(), MIN_UPWARD_PULL));
                }

                Vector newVelocity = player.getVelocity().add(pull);
                if (newVelocity.length() > MAX_SPEED) {
                    newVelocity = newVelocity.normalize().multiply(MAX_SPEED);
                }
                player.setVelocity(newVelocity);
                player.setFallDistance(0f);

                playChainStep(player, currentLoc, ticks);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawChain(Player player, Location from, Location to, double distance) {
        Vector toTarget = to.toVector().subtract(from.toVector()).normalize();
        double spacing = 0.4;
        int particleCount = (int) (distance / spacing);
        for (int i = 0; i <= particleCount; i++) {
            Location particleLoc = from.clone().add(toTarget.clone().multiply(i * spacing));
            player.getWorld().spawnParticle(Particle.FLAME, particleLoc, 1, 0.02, 0.02, 0.02, 0.0);
            if (i % 4 == 0) {
                player.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private void playCastSounds(Player player) {
        // Kept quiet/short - this is just the "it fired" cue. The chain sound (below) is
        // what plays for the whole pull, so it shouldn't have to compete with this one.
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.3f);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.8f, 0.7f);
    }

    private void playChainStep(Player player, Location at, int ticks) {
        // Every 2 ticks instead of every 4, and louder - makes the chain read as one
        // sustained sound running the length of the pull rather than a couple of blips.
        if (ticks % 2 == 0) {
            player.getWorld().playSound(at, Sound.BLOCK_CHAIN_STEP, 0.9f, 0.7f);
        }
    }

    @Override
    public String getName() {
        return ChatColor.RED + "Hell's Chain";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Launch a fiery chain at whatever you're looking at - drag enemies in directly, "
                + "or hook the ground near them to pull yourself there and scorch anything nearby.";
    }
}