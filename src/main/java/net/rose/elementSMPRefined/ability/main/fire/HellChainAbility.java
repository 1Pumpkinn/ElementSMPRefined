package net.rose.elementSMPRefined.ability.main.fire;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.visual.ChainVisual;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
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
 *     caster moves, not the target). If the hook landed on a flat/top
 *     surface, this ends with a scorch at the landing spot so aiming at the
 *     ground next to someone still burns them without a direct hit. If it
 *     landed on a vertical wall face instead, the caster scales the wall
 *     once they reach it, climbing until they clear the top, then mantles
 *     onto the ledge with the same scorch effect.</li>
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
    // If the caster's actual progress toward the hook point stalls for this many consecutive
    // ticks, terrain is fighting the pull - snagged on a corner, an overhang, a step that
    // wasn't there when the chain fired - so let go instead of continuing to shove at full
    // force for the rest of MAX_TICKS. This is checked every tick for the whole flight (not
    // just once early on), so a snag that develops mid-flight - e.g. clipping an overhang
    // partway through, well after an initial burst of good progress - gets caught too.
    private static final double PROGRESS_EPSILON = 0.03; // per-tick movement below this counts as "not really moving"
    private static final int MAX_CONSECUTIVE_BLOCKED_TICKS = 4;
    // Continuous step-assist: MIN_UPWARD_PULL only floors the pull's vertical component when
    // the hook point is already above the caster's eye line (pull.getY() > 0). Aiming level
    // at a wall/ledge dead ahead - the normal way to hit "elevated terrain" - puts the hit
    // point roughly at eye height, so pull.getY() ends up ~0 and that floor never applies;
    // the caster just gets shoved horizontally into the wall and stalls. Velocity-based
    // movement also doesn't get vanilla's walking auto-step, so nothing else pops them over a
    // 1-block ledge either. This checks, every tick, for a solid block at foot level in the
    // pull's horizontal direction with clear space above it, and if so applies a hop-height
    // boost - independent of the raw pull vector's vertical component - so the caster steps
    // up over the ledge instead of stopping at its base.
    private static final double STEP_CHECK_DISTANCE = 0.5;
    private static final double STEP_UP_VELOCITY = 0.42; // roughly vanilla jump height, enough to clear 1 block

    // Wall-scaling mode (hook lands on a vertical block face rather than a flat/top surface -
    // e.g. a cliff face directly ahead, as opposed to the ground or the top of a ledge). Instead
    // of the caster stopping dead at the wall like a flat-terrain hit, they climb it.
    private static final double WALL_STOP_DISTANCE = 1.0; // get this close to the wall before switching from approach to climbing
    private static final double WALL_CHECK_DISTANCE = 0.35; // how far ahead (into the wall) to check whether there's still wall left to climb
    private static final double CLIMB_SPEED = 0.28; // vertical speed while scaling the wall
    private static final double CLIMB_STICK_STRENGTH = 0.12; // small push into the wall each tick so the caster stays pressed against it instead of drifting off mid-climb
    private static final double MANTLE_FORWARD_VELOCITY = 0.45; // boost onto the ledge once the top of the wall is cleared
    private static final double MANTLE_UPWARD_VELOCITY = 0.25;

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
        BlockFace hitFace = result.getHitBlockFace();
        playCastSounds(player);

        if (hookedEntity != null) {
            dragEntityIn(player, hookedEntity);
        } else {
            grappleToPoint(player, hookLocation, hitFace, trust);
        }

        return true;
    }

    /** Direct hit on a living entity - harpoon them in to the caster instead of the other way around. */
    private void dragEntityIn(Player player, LivingEntity target) {
        new BukkitRunnable() {
            final ChainVisual chain = new ChainVisual(ChainVisual.HELL_CHAIN);
            int ticks = 0;

            /** Every exit path calls cancel(), so the chain entities can never outlive the ability. */
            @Override
            public synchronized void cancel() throws IllegalStateException {
                chain.remove();
                super.cancel();
            }

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

                chain.update(chainStart(player), target.getBoundingBox().getCenter().toLocation(target.getWorld()));

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

    /**
     * No entity directly hit - grapple the caster to the hook point. What happens on arrival
     * depends on what kind of surface was hit:
     * <ul>
     *     <li>Flat/top surface (e.g. the ground, the top of a ledge) - stop and scorch
     *     anything standing nearby, same as before.</li>
     *     <li>Vertical wall face (e.g. a cliff directly ahead) - rather than stopping dead at
     *     the base, the caster switches into climbing the wall once they're close enough,
     *     scaling it until they clear the top, then mantles onto the ledge with the same
     *     scorch effect.</li>
     * </ul>
     */
    private void grappleToPoint(Player player, Location hookLocation, BlockFace hitFace, TrustManager trust) {
        boolean wallHit = isWallFace(hitFace);
        // Direction from the caster INTO the wall - the opposite of the hit face's own outward
        // normal, since the face that was hit points back out toward whoever hit it.
        Vector intoWall = wallHit ? hitFace.getOppositeFace().getDirection() : null;

        if (player.isOnGround()) {
            Vector v = player.getVelocity();
            player.setVelocity(new Vector(v.getX(), Math.max(v.getY(), GROUND_HOP_VELOCITY), v.getZ()));
        }

        new BukkitRunnable() {
            final ChainVisual chain = new ChainVisual(ChainVisual.HELL_CHAIN);
            int ticks = 0;
            boolean climbing = false;
            double lastDistance = Double.MAX_VALUE; // approach-phase progress tracking
            int approachStuckTicks = 0;
            double lastClimbY = Double.NaN; // climb-phase progress tracking
            int climbStuckTicks = 0;

            /** Every exit path calls cancel(), so the chain entities can never outlive the ability. */
            @Override
            public synchronized void cancel() throws IllegalStateException {
                chain.remove();
                super.cancel();
            }

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= MAX_TICKS) {
                    cancel();
                    return;
                }

                Location currentLoc = player.getEyeLocation();

                if (climbing) {
                    Location aheadHead = currentLoc.clone().add(intoWall.clone().multiply(WALL_CHECK_DISTANCE));
                    if (!aheadHead.getBlock().getType().isSolid()) {
                        // Cleared the top of the wall - vault onto the ledge instead of just
                        // stopping mid-climb.
                        mantleOntoLedge(player, currentLoc, intoWall, trust);
                        cancel();
                        return;
                    }

                    // Climb-phase snag guard: if height gained per tick stalls (e.g. a ceiling
                    // or overhang above blocking further ascent), the wall ahead never actually
                    // clears, so the check above alone would pin the caster there for the rest
                    // of MAX_TICKS. Let go once that stall persists for a few ticks running.
                    double currentY = currentLoc.getY();
                    if (!Double.isNaN(lastClimbY) && currentY <= lastClimbY + PROGRESS_EPSILON) {
                        climbStuckTicks++;
                        if (climbStuckTicks >= MAX_CONSECUTIVE_BLOCKED_TICKS) {
                            player.setVelocity(player.getVelocity().multiply(0.2));
                            cancel();
                            return;
                        }
                    } else {
                        climbStuckTicks = 0;
                    }
                    lastClimbY = currentY;

                    // Keep rising, with a small push into the wall each tick so the caster
                    // stays pressed against it rather than drifting off and falling away
                    // mid-climb.
                    player.setVelocity(new Vector(
                            intoWall.getX() * CLIMB_STICK_STRENGTH,
                            CLIMB_SPEED,
                            intoWall.getZ() * CLIMB_STICK_STRENGTH));
                    player.setFallDistance(0f);

                    // Keep the chain anchored where it hit the wall while that anchor is still
                    // above the caster; once they've climbed past it there's nothing left to
                    // hang the chain from, so it drops away for the final vault.
                    if (hookLocation.getY() > currentLoc.getY() + 0.5) {
                        chain.update(chainStart(player), hookLocation);
                    } else {
                        chain.hide();
                    }
                    playChainStep(player, currentLoc, ticks);
                    ticks++;
                    return;
                }

                double distance = currentLoc.distance(hookLocation);

                double stopDistance = wallHit ? WALL_STOP_DISTANCE : STOP_DISTANCE;
                if (distance < stopDistance) {
                    if (wallHit) {
                        // Close enough to start scaling the wall instead of just stopping at
                        // its base - the next tick takes over with climb movement.
                        climbing = true;
                        return;
                    }

                    player.setVelocity(player.getVelocity().multiply(0.2));
                    player.getWorld().playSound(hookLocation, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.2f);
                    player.getWorld().spawnParticle(Particle.LAVA, hookLocation, 20,
                            BURN_RADIUS / 2, 0.3, BURN_RADIUS / 2, 0.05);
                    burnNearby(player, hookLocation, trust);
                    cancel();
                    return;
                }

                // Approach-phase snag guard: if actual progress toward the hook point stalls
                // for several ticks running - snagged on a corner, an overhang, geometry that
                // wasn't obvious from the initial cast - let go instead of grinding at full
                // pull for the rest of MAX_TICKS. Checked every tick for the whole flight, so
                // a snag that only develops mid-flight (not present from the very first tick)
                // still gets caught, not just one that's there from the start.
                if (distance >= lastDistance - PROGRESS_EPSILON) {
                    approachStuckTicks++;
                    if (approachStuckTicks >= MAX_CONSECUTIVE_BLOCKED_TICKS) {
                        player.setVelocity(player.getVelocity().multiply(0.3));
                        cancel();
                        return;
                    }
                } else {
                    approachStuckTicks = 0;
                }
                lastDistance = distance;

                chain.update(chainStart(player), hookLocation);

                Vector pull = hookLocation.toVector().subtract(currentLoc.toVector()).normalize().multiply(PULL_STRENGTH);
                // Keep a floor on the vertical pull so the caster arcs up and over ledges/blocks
                // between them and the hook point instead of slamming into the wall below it -
                // but ONLY when the target is already above/level. If the target is below
                // (pull.getY() negative), forcing this positive would mean the chain can never
                // actually descend toward it and just launches the caster straight up instead.
                if (pull.getY() > 0) {
                    pull.setY(Math.max(pull.getY(), MIN_UPWARD_PULL));
                }

                // Catches the case the floor above doesn't: hook point roughly level with the
                // caster (aiming dead-ahead at a ledge rather than up at its top), where
                // pull.getY() is ~0 and the caster would otherwise just stall at the wall.
                applyStepAssist(player, pull);

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

    private boolean isWallFace(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH
                || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    /** Boosts the caster forward and up onto the ledge once a wall-climb clears the top, then scorches anything nearby - the same arrival effect a flat-terrain hit gets. */
    private void mantleOntoLedge(Player player, Location at, Vector intoWall, TrustManager trust) {
        Vector boost = intoWall.clone().multiply(MANTLE_FORWARD_VELOCITY);
        boost.setY(MANTLE_UPWARD_VELOCITY);
        player.setVelocity(boost);
        player.setFallDistance(0f);
        player.getWorld().playSound(at, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.LAVA, at, 15, 0.4, 0.2, 0.4, 0.05);
        burnNearby(player, at, trust);
    }

    private void burnNearby(Player player, Location at, TrustManager trust) {
        for (LivingEntity nearby : at.getNearbyLivingEntities(BURN_RADIUS)) {
            if (nearby.equals(player)) continue;
            if (nearby instanceof Player nearbyPlayer
                    && trust.isTrusted(player.getUniqueId(), nearbyPlayer.getUniqueId())) continue;
            nearby.setFireTicks(Math.max(nearby.getFireTicks(), BURN_FIRE_TICKS));
        }
    }

    /**
     * If a solid block sits at foot level in the pull's horizontal direction and the block
     * above it is clear, gives the caster a hop-height upward boost so they step up over the
     * ledge instead of stalling against it. No-ops for a near-zero horizontal pull (e.g. the
     * hook point is directly overhead) since there's no "ahead" to check.
     */
    private void applyStepAssist(Player player, Vector pull) {
        Vector horizontal = new Vector(pull.getX(), 0, pull.getZ());
        if (horizontal.lengthSquared() < 1.0E-4) return;
        horizontal.normalize();

        Location feet = player.getLocation();
        Location aheadFeet = feet.clone().add(horizontal.clone().multiply(STEP_CHECK_DISTANCE));
        Location aheadHead = aheadFeet.clone().add(0, 1, 0);

        boolean blockedAtFeet = aheadFeet.getBlock().getType().isSolid();
        boolean clearAtHead = !aheadHead.getBlock().getType().isSolid();

        if (blockedAtFeet && clearAtHead) {
            Vector v = player.getVelocity();
            if (v.getY() < STEP_UP_VELOCITY) {
                player.setVelocity(new Vector(v.getX(), STEP_UP_VELOCITY, v.getZ()));
            }
        }
    }

    /**
     * Where the chain visually leaves the caster: down and to the right of the eyes, a little
     * forward - roughly a hand - so in first person it doesn't run straight through the middle
     * of the screen. Only affects the visual; all the movement math still uses the eye location.
     */
    private Location chainStart(Player player) {
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0); // looking straight up/down - any sideways direction will do
        }
        right.normalize();

        return eye.clone()
                .add(right.multiply(0.35))
                .add(forward.multiply(0.3))
                .subtract(0, 0.35, 0);
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
                + "hook the ground near them to pull yourself there, or scale a wall face outright, "
                + "scorching anything nearby on arrival.";
    }
}