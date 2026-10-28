package net.rose.elementSMPRefined.ability.main.basic.fire;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.AbilityDamage;
import net.rose.elementSMPRefined.util.visual.model.ChainVisual;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
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
 *     <li>Hooked a projectile in flight (arrow, snowball, wind charge, fireball, trident...) -
 *     latches onto it and the projectile pulls the caster along behind it, lifting them as it goes.
 *     The projectile itself is untouched. Projectiles stuck in a block and projectiles fired by a player the caster trusts are ignored,
 *     so they never block a terrain hook.</li>
 *     <li>Hooked terrain - reels the caster in to that point instead (the
 *     caster moves, not the target). If the hook landed on a flat/top
 *     surface, this ends with a scorch at the landing spot so aiming at the
 *     ground next to someone still burns them without a direct hit. If it
 *     landed on a vertical wall face instead, the caster scales the wall
 *     once they reach it, climbing until they clear the top, then mantles
 *     onto the ledge with the same scorch effect. The climb only goes as high as the
 *     hooked point (plus a ledge's worth), and ends if the caster comes off the wall.</li>
 * </ul>
 * <p>
 * Replaces the old Fire ability1, {@code FireGeyserAbility} (now deprecated).
 */
public class HellChainAbility extends BaseAbility {
    private static final double RANGE = 35.0;
    private static final double RAY_SIZE = 0.4;
    private static final int MAX_TICKS = 30; // safety cap so a chain can never get "stuck" running forever
    // The flat 50-tick cap is too short for a long hook: the grapple tops out at 1 block/tick, so a 45 block
    // hook needs 46+ ticks even with perfect progress, and any lag/ramp-up pushes it past the cap and the pull
    // just stops partway. The cap now grows with the hook distance (still bounded, so nothing runs forever).
    private static final int MAX_TICKS_CAP = 100;
    private static final double SLOWEST_EXPECTED_SPEED = 0.6; // blocks/tick the cap assumes the pull manages on average
    private static final int EXTRA_TICKS = 0;                 // slack (was 20; every chain now lasts 20 ticks less) for ramp-up, lag and arcing over obstacles

    /** Flip to true to log why every chain ended (arrived / timed out / snagged / cancelled) in the console. */
    private static final boolean DEBUG = false;

    private static final Component NOTHING_IN_RANGE = Component.text(
            "Hell's Chain: nothing to hook within " + (int) RANGE + " blocks.", NamedTextColor.RED);

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
    // Wall-climb limits. The climb used to run until the TOP of the wall (or the tick cap), no matter
    // where the hook landed, so hooking the base of any tall wall (e.g. aiming steeply down at it) and
    // then pressing/strafing against it let the caster crawl all the way up. It now only goes as far as
    // the point that was hooked, and it lets go if the caster stops touching the wall.
    /** How far (blocks) the caster's eyes may rise above the top of the hooked block - enough to vault that ledge, no more. */
    private static final double CLIMB_LEDGE_ALLOWANCE = 1.5;
    /** How far (blocks) into the wall, at lower-chest height, still counts as "touching" it. */
    private static final double WALL_CONTACT_DISTANCE = 0.8;
    /** Climb ticks given to settle against the wall (the stick-to-wall push needs a moment) before contact is enforced. */
    private static final int CLIMB_CONTACT_GRACE_TICKS = 6;

    // Entity-drag mode (hook lands directly on a living entity)
    private static final double DRAG_MAX_SPEED = 0.8;
    private static final double DRAG_MIN_SPEED = 0.25;
    private static final double DRAG_SPEED_PER_BLOCK = 0.25; // slows down as the target nears the caster so it can't overshoot
    private static final double DRAG_STOP_DISTANCE = 2.5; // horizontal distance
    private static final double DRAG_GROUND_LIFT = 0.2;   // just enough to break ground friction
    private static final double DRAG_MAX_UPWARD = 0.25;   // hard cap so targets can never be launched over the caster's head
    private static final double DRAG_MAX_DOWNWARD = -0.5;
    private static final double DRAG_VERTICAL_FACTOR = 0.15;

    // Projectile-latch mode (hook lands on a projectile in flight): the caster is hauled along behind it
    private static final double PROJECTILE_MAX_PULL_SPEED = 1.3; // a bit quicker than a terrain hook, projectiles move
    private static final double PROJECTILE_RIDE_DISTANCE = 2.0;  // closer than this, match the projectile's velocity

    // Ping compensation: a player's server-side position lags behind what they actually see, so the
    // very first ticks of the pull look like "no movement" to the server. Snag detection gets a
    // grace period + extra tolerance equal to the caster's ping (in ticks) so it doesn't bail early.
    private static final double MS_PER_TICK = 50.0;
    private static final int MAX_PING_TICKS = 10;

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

        // Pressed again while the chain is out: let go. (FireElement lets this through without a
        // cooldown check, and the cooldown from the original cast keeps running.) The running task
        // sees the ability go inactive on its next tick and cleans up.
        if (isActiveFor(player)) {
            setActive(player, false);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.8f, 1.0f);
            return true;
        }

        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        RayTraceResult result = player.getWorld().rayTrace(
                eyeLoc, direction, RANGE,
                FluidCollisionMode.NEVER, true, RAY_SIZE,
                entity -> isHookable(entity, player, context.getTrustManager())
        );

        if (result == null) {
            // Silent failures made "the ray missed" look the same as "the pull stopped early".
            //player.sendActionBar(NOTHING_IN_RANGE);
            return false;
        }

        LivingEntity hookedEntity = result.getHitEntity() instanceof LivingEntity le ? le : null;
        Projectile hookedProjectile = result.getHitEntity() instanceof Projectile pr ? pr : null;
        TrustManager trust = context.getTrustManager();

        if (hookedEntity instanceof Player hookedPlayer
                && trust.isTrusted(player.getUniqueId(), hookedPlayer.getUniqueId())) {
            return false;
        }

        Location hookLocation = result.getHitPosition().toLocation(player.getWorld());
        BlockFace hitFace = result.getHitBlockFace();
        playCastSounds(player);

        setActive(player, true);
        if (hookedEntity != null) {
            dragEntityIn(player, hookedEntity);
        } else if (hookedProjectile != null) {
            latchOntoProjectile(player, hookedProjectile);
        } else {
            grappleToPoint(player, hookLocation, hitFace, trust);
        }

        return true;
    }

    /** Direct hit on a living entity - harpoon them in to the caster instead of the other way around. */
    private void dragEntityIn(Player player, LivingEntity target) {
        // Horizontal vector caster -> target at cast time; used to detect the target passing the caster.
        final Vector startOffset = target.getLocation().toVector()
                .subtract(player.getLocation().toVector()).setY(0);
        final int maxTicks = maxTicksFor(player.getLocation().distance(target.getLocation()));

        new BukkitRunnable() {
            final ChainVisual chain = new ChainVisual(ChainVisual.HELL_CHAIN, player);
            int ticks = 0;

            /** Every exit path calls cancel(), so the chain entities can never outlive the ability. */
            @Override
            public synchronized void cancel() throws IllegalStateException {
                chain.remove();
                setActive(player, false);
                super.cancel();
            }

            @Override
            public void run() {
                if (!player.isOnline() || !target.isValid()) {
                    cancel();
                    return;
                }
                if (!isActiveFor(player)) {
                    // Cancelled mid-pull (pressed again, or element changed) - let the target go.
                    target.setVelocity(new Vector(0, 0, 0));
                    cancel();
                    return;
                }
                if (ticks >= maxTicks) {
                    // Timed out - kill leftover momentum so the target doesn't keep flying.
                    target.setVelocity(new Vector(0, 0, 0));
                    cancel();
                    return;
                }

                Location casterEye = player.getEyeLocation();
                Location magnetar = player.getLocation();
                Location targetLoc = target.getLocation();

                Vector offset = magnetar.toVector().subtract(targetLoc.toVector()); // target -> caster
                double horizontal = Math.hypot(offset.getX(), offset.getZ());

                // "Passed" = the target is now on the opposite side of the caster from where it started
                // (laggy position updates can skip right over the stop radius).
                Vector fromCaster = targetLoc.toVector().subtract(magnetar.toVector()).setY(0);
                boolean passedCaster = startOffset.lengthSquared() > 1.0E-4 && fromCaster.dot(startOffset) <= 0;

                if (horizontal < DRAG_STOP_DISTANCE || passedCaster) {
                    target.setVelocity(new Vector(0, 0, 0));
                    target.setFireTicks(Math.max(target.getFireTicks(), BURN_FIRE_TICKS));
                    AbilityDamage.deal(target, getName(), () -> target.damage(IMPACT_DAMAGE, player));
                    player.getWorld().spawnParticle(Particle.LAVA, targetLoc, 15, 0.3, 0.3, 0.3, 0.05);
                    player.getWorld().playSound(targetLoc, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.2f);
                    cancel();
                    return;
                }

                chain.update(chainStart(player), target.getBoundingBox().getCenter().toLocation(target.getWorld()));

                // Velocity is SET each tick (not added onto the target's old velocity) so momentum can
                // never build up and carry them past/over the caster.
                double speed = Math.max(DRAG_MIN_SPEED, Math.min(DRAG_MAX_SPEED, horizontal * DRAG_SPEED_PER_BLOCK));
                double vx = offset.getX() / horizontal * speed;
                double vz = offset.getZ() / horizontal * speed;

                // Aim at the caster's feet (not eye) and clamp the vertical component hard.
                double vy = Math.max(DRAG_MAX_DOWNWARD, Math.min(DRAG_MAX_UPWARD, offset.getY() * DRAG_VERTICAL_FACTOR));
                if (target.isOnGround()) {
                    vy = Math.max(vy, DRAG_GROUND_LIFT);
                }

                target.setVelocity(new Vector(vx, vy, vz));

                playChainStep(player, casterEye, ticks);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * What the chain's ray may latch onto: living entities (other than the caster) and projectiles
     * that are actually flying. Arrows/tridents stuck in a block and projectiles shot by a player the
     * caster trusts are skipped, so a stray arrow in a wall never hijacks a terrain hook.
     */
    private static boolean isHookable(Entity entity, Player caster, TrustManager trust) {
        if (entity.equals(caster)) return false;
        if (entity instanceof LivingEntity) return true;
        if (!(entity instanceof Projectile projectile)) return false;
        if (!projectile.isValid() || projectile.isDead()) return false;
        if (projectile instanceof AbstractArrow arrow && (arrow.isInBlock() || arrow.isOnGround())) return false;
        return !(projectile.getShooter() instanceof Player shooter
                && trust.isTrusted(caster.getUniqueId(), shooter.getUniqueId()));
    }

    /**
     * Direct hit on a projectile (wind charge, arrow, snowball...) - the chain latches onto it and the
     * projectile hauls the caster along behind it. The projectile is left completely alone (it flies
     * on with its own velocity and gravity); each tick the caster is pulled toward wherever it is
     * now, the same pull a terrain hook uses. Once the caster catches up they ride along with it.
     * The chain lets go when the projectile lands, hits something or despawns, when the ability is
     * pressed again, or on timeout, keeping a little momentum.
     */
    private void latchOntoProjectile(Player player, Projectile projectile) {
        final boolean startedOnGround = player.isOnGround();
        final int maxTicks = maxTicksFor(player.getEyeLocation().distance(projectile.getLocation()));

        new BukkitRunnable() {
            final ChainVisual chain = new ChainVisual(ChainVisual.HELL_CHAIN, player);
            int ticks = 0;

            /** Every exit path calls cancel(), so the chain entities can never outlive the ability. */
            @Override
            public synchronized void cancel() throws IllegalStateException {
                chain.remove();
                setActive(player, false);
                super.cancel();
            }

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }
                // Projectile landed / hit something / despawned, pressed again, or timed out: let go.
                if (!projectile.isValid() || projectile.isDead() || !isActiveFor(player) || ticks >= maxTicks) {
                    player.setVelocity(player.getVelocity().multiply(0.5));
                    player.setFallDistance(0f);
                    cancel();
                    return;
                }

                Location eye = player.getEyeLocation();
                Location anchor = projectile.getLocation();
                Vector toProjectile = anchor.toVector().subtract(eye.toVector());
                double distance = toProjectile.length();

                chain.update(chainStart(player), anchor);

                Vector velocity;
                if (distance < PROJECTILE_RIDE_DISTANCE) {
                    // Caught up - travel with the projectile instead of stopping or overshooting it.
                    velocity = projectile.getVelocity().clone();
                    if (velocity.length() > PROJECTILE_MAX_PULL_SPEED) {
                        velocity.normalize().multiply(PROJECTILE_MAX_PULL_SPEED);
                    }
                } else {
                    // Velocity is SET each tick, ramping up to the max speed, aimed at the projectile's current spot.
                    double speed = Math.min(PROJECTILE_MAX_PULL_SPEED, PULL_STRENGTH * (ticks + 1));
                    velocity = toProjectile.normalize().multiply(speed);
                }

                // The projectile hauls you up: never let the pull point downward-only while it's above you.
                if (anchor.getY() >= eye.getY() - 1.0) {
                    velocity.setY(Math.max(velocity.getY(), MIN_UPWARD_PULL));
                }
                if (ticks == 0 && startedOnGround) {
                    velocity.setY(Math.max(velocity.getY(), GROUND_HOP_VELOCITY));
                }
                applyStepAssist(player, velocity);

                player.setVelocity(velocity);
                player.setFallDistance(0f);

                playChainStep(player, eye, ticks);
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

        final boolean startedOnGround = player.isOnGround();
        final int latencyTicks = pingTicks(player);
        final int maxTicks = maxTicksFor(player.getEyeLocation().distance(hookLocation));
        // Highest the caster's eyes may get while climbing: the top of the hooked block plus a ledge's worth.
        final double climbCeilingY = Math.floor(hookLocation.getY()) + 1.0 + CLIMB_LEDGE_ALLOWANCE;

        new BukkitRunnable() {
            final ChainVisual chain = new ChainVisual(ChainVisual.HELL_CHAIN, player);
            int ticks = 0;
            boolean climbing = false;
            double bestDistance = Double.MAX_VALUE; // approach-phase progress tracking (closest point reached so far)
            int approachStuckTicks = 0;
            double bestClimbY = Double.NaN; // climb-phase progress tracking (highest point reached so far)
            int climbStuckTicks = 0;
            int climbTicks = 0;

            /** Every exit path calls cancel(), so the chain entities can never outlive the ability. */
            @Override
            public synchronized void cancel() throws IllegalStateException {
                chain.remove();
                setActive(player, false);
                super.cancel();
            }

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= maxTicks) {
                    if (player.isOnline()) {
                        debug(player, "timed out", ticks, player.getEyeLocation().distance(hookLocation));
                    }
                    cancel();
                    return;
                }
                if (!isActiveFor(player)) {
                    // Cancelled mid-grapple (pressed again, or element changed) - drop the chain,
                    // keeping a little momentum so it doesn't feel like hitting a wall.
                    debug(player, "cancelled (pressed again / element changed)", ticks, player.getEyeLocation().distance(hookLocation));
                    player.setVelocity(player.getVelocity().multiply(0.3));
                    cancel();
                    return;
                }

                Location currentLoc = player.getEyeLocation();

                if (climbing) {
                    // Came off the wall (strafing / backing away, knocked off...): let go instead of
                    // rising through open air. Skipped for the first few ticks while the stick-to-wall
                    // push settles them against it (longer on high ping).
                    if (climbTicks >= CLIMB_CONTACT_GRACE_TICKS + latencyTicks && !touchingWall(currentLoc, intoWall)) {
                        debug(player, "came off the wall while climbing", ticks, currentLoc.distance(hookLocation));
                        player.setVelocity(player.getVelocity().multiply(0.3));
                        cancel();
                        return;
                    }

                    Location aheadHead = currentLoc.clone().add(intoWall.clone().multiply(WALL_CHECK_DISTANCE));
                    if (!aheadHead.getBlock().getType().isSolid()) {
                        // Cleared the top of the wall - vault onto the ledge instead of just
                        // stopping mid-climb.
                        mantleOntoLedge(player, currentLoc, intoWall, trust);
                        cancel();
                        return;
                    }

                    // Reached the hooked point and the wall keeps going: stop here. Without this cap
                    // the caster kept climbing to the top of ANY wall, whatever they aimed at.
                    if (currentLoc.getY() >= climbCeilingY) {
                        debug(player, "reached hooked height", ticks, currentLoc.distance(hookLocation));
                        player.setVelocity(player.getVelocity().multiply(0.2));
                        player.setFallDistance(0f);
                        player.getWorld().playSound(hookLocation, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.2f);
                        burnNearby(player, currentLoc, trust);
                        cancel();
                        return;
                    }

                    // Climb-phase snag guard: if height gained per tick stalls (e.g. a ceiling
                    // or overhang above blocking further ascent), the wall ahead never actually
                    // clears, so the check above alone would pin the caster there for the rest
                    // of MAX_TICKS. Let go once that stall persists for a few ticks running.
                    // Compared against the best height reached (not just last tick) and given a
                    // ping-based grace period, so delayed position updates on high ping don't read
                    // as "stuck".
                    double currentY = currentLoc.getY();
                    if (Double.isNaN(bestClimbY) || currentY > bestClimbY + PROGRESS_EPSILON) {
                        bestClimbY = currentY;
                        climbStuckTicks = 0;
                    } else if (climbTicks >= latencyTicks) {
                        climbStuckTicks++;
                        if (climbStuckTicks >= MAX_CONSECUTIVE_BLOCKED_TICKS + latencyTicks) {
                            debug(player, "snagged while climbing", ticks, currentLoc.distance(hookLocation));
                            player.setVelocity(player.getVelocity().multiply(0.2));
                            cancel();
                            return;
                        }
                    }
                    climbTicks++;

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

                    debug(player, "arrived", ticks, distance);
                    player.setVelocity(player.getVelocity().multiply(0.2));
                    player.getWorld().playSound(hookLocation, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.2f);
                    player.getWorld().spawnParticle(Particle.LAVA, hookLocation, 20,
                            BURN_RADIUS / 2, 0.3, BURN_RADIUS / 2, 0.05);
                    burnNearby(player, hookLocation, trust);
                    cancel();
                    return;
                }

                // Approach-phase snag guard: let go if the caster stops getting closer to the hook point
                // (snagged on a corner/overhang). Progress is measured against the CLOSEST point reached
                // so far, and the check only starts after a ping-based grace period with extra tolerance -
                // on high ping the server doesn't see the caster move for the first few ticks, which
                // used to cancel the chain after ~0.5s.
                if (distance < bestDistance - PROGRESS_EPSILON) {
                    bestDistance = distance;
                    approachStuckTicks = 0;
                } else if (ticks >= latencyTicks) {
                    approachStuckTicks++;
                    if (approachStuckTicks >= MAX_CONSECUTIVE_BLOCKED_TICKS + latencyTicks) {
                        debug(player, "snagged on terrain (no progress toward hook)", ticks, distance);
                        player.setVelocity(player.getVelocity().multiply(0.3));
                        cancel();
                        return;
                    }
                }

                chain.update(chainStart(player), hookLocation);

                // Velocity is SET each tick from the pull direction (ramping up to MAX_SPEED) instead of
                // added onto player.getVelocity(), which is stale/unreliable for players and made the
                // pull behave differently depending on latency.
                Vector pull = hookLocation.toVector().subtract(currentLoc.toVector()).normalize();
                double speed = Math.min(MAX_SPEED, PULL_STRENGTH * (ticks + 1));
                Vector newVelocity = pull.multiply(speed);

                // Floor on the vertical pull so the caster arcs up and over ledges - ONLY when the
                // target is above/level; forcing it when the target is below would launch the caster up.
                if (newVelocity.getY() > 0) {
                    newVelocity.setY(Math.max(newVelocity.getY(), MIN_UPWARD_PULL));
                }
                if (ticks == 0 && startedOnGround) {
                    newVelocity.setY(Math.max(newVelocity.getY(), GROUND_HOP_VELOCITY));
                }

                // Hook roughly level with the caster (aiming dead-ahead at a ledge): hop over it.
                applyStepAssist(player, newVelocity);

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

    /** How long a chain may run for a hook this far away: at least MAX_TICKS, growing with distance, never past MAX_TICKS_CAP. */
    private static int maxTicksFor(double distance) {
        int needed = (int) Math.ceil(distance / SLOWEST_EXPECTED_SPEED) + EXTRA_TICKS;
        return Math.min(MAX_TICKS_CAP, Math.max(MAX_TICKS, needed));
    }

    private void debug(Player player, String reason, int ticks, double distance) {
        if (!DEBUG) return;
        plugin.getLogger().info("[HellChain] " + player.getName() + ": " + reason
                + " (tick " + ticks + ", " + String.format("%.1f", distance) + " blocks from hook)");
    }

    /** Caster's ping converted to server ticks, capped so a huge spike can't disable snag detection. */
    private int pingTicks(Player player) {
        int ping = Math.max(0, player.getPing());
        return Math.min(MAX_PING_TICKS, (int) Math.ceil(ping / MS_PER_TICK));
    }

    /**
     * Whether the caster is still against the wall they hooked: a solid block {@link #WALL_CONTACT_DISTANCE}
     * into the wall at lower-chest height. Deliberately NOT checked at eye height - that block going
     * clear is how the top of the wall is detected, and the lower body is still against the ledge then.
     */
    private boolean touchingWall(Location eye, Vector intoWall) {
        Location lowerChest = eye.clone().subtract(0, 0.9, 0).add(intoWall.clone().multiply(WALL_CONTACT_DISTANCE));
        return lowerChest.getBlock().getType().isSolid();
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
     * above it is clear, raises {@code velocity}'s Y to hop height (mutating it in place) so the caster
     * steps up over the ledge instead of stalling against it. No-ops for a near-zero horizontal pull (e.g. the
     * hook point is directly overhead) since there's no "ahead" to check.
     */
    private void applyStepAssist(Player player, Vector velocity) {
        Vector horizontal = new Vector(velocity.getX(), 0, velocity.getZ());
        if (horizontal.lengthSquared() < 1.0E-4) return;
        horizontal.normalize();

        Location feet = player.getLocation();
        Location aheadFeet = feet.clone().add(horizontal.clone().multiply(STEP_CHECK_DISTANCE));
        Location aheadHead = aheadFeet.clone().add(0, 1, 0);

        boolean blockedAtFeet = aheadFeet.getBlock().getType().isSolid();
        boolean clearAtHead = !aheadHead.getBlock().getType().isSolid();

        if (blockedAtFeet && clearAtHead && velocity.getY() < STEP_UP_VELOCITY) {
            velocity.setY(STEP_UP_VELOCITY);
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
        return "Shoots a chain at an entity you are looking at. It pulls the entity to you, latches onto a projectile (arrow, snowball, wind charge...) so it drags you along, or pulls you to a block, and sets nearby enemies on fire. Use again to stop mid-chain.";
    }
}