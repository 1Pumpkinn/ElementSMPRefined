package net.rose.elementSMPRefined.ability.main.upgraded.storm;

import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.particles.element.StormParticles;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.status.StatusEffectType;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import net.rose.elementSMPRefined.util.visual.model.LightningSpearVisual;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Lightning Spear - hurls a spear of crackling lightning in a straight line at very high speed.
 * It stops on the first thing it touches: a block, or an enemy. On an enemy it deals damage,
 * shocks them (a short stun on players, heavy slow on mobs) and a real lightning strike
 * goes off at the point of impact.
 * <p>
 * The spear itself is the {@code elementsmp:lightning_spear} resource-pack model (see
 * {@link LightningSpearVisual}); the crackle around it is particles, but the impact is a real
 * visual-only lightning strike (no vanilla damage, fire or mob conversion - all damage is ours).
 * Every tick it travels {@link #SPEED} blocks, and the whole segment is ray-traced in one go, so
 * even at that speed it can't skip over a thin wall or a player.
 */
public class LightningSpearAbility extends BaseAbility {

    /** Blocks travelled per tick. */
    private static final double SPEED = 2.2 ;
    /** Max distance before the spear fizzles out. */
    private static final double RANGE = 48.0;
    /** How wide the spear is for hit detection (radius, in blocks). */
    private static final double HIT_RADIUS = 0.6;
    /** Visible length of the spear shaft, tip to tail. */
    private static final double SPEAR_LENGTH = 3.0;

    /** True damage dealt to the entity the spear hits directly. */
    private static final double DIRECT_DAMAGE = 5.0; // 2.5 hearts
    /** Radius of the shock burst at the impact point. Set SHOCK_DAMAGE to 0 to disable the burst damage. */
    private static final double SHOCK_RADIUS = 3.0;
    /** True damage dealt to everything else caught in the shock burst. */
    private static final double SHOCK_DAMAGE = 3.0;

    /** How long a hit player is stunned (ticks). */
    private static final int PLAYER_STUN_TICKS = 15;
    /** How long a hit mob is heavily slowed (ticks). */
    private static final int MOB_SLOW_TICKS = 30;

    // Sound volumes (1.0 = vanilla "normal"; lower = quieter and shorter range)
    // The impact thunder/zap are played by the lightning entity itself on the client, so their
    // volume can't be set here (override entity.lightning_bolt.* in the resource pack if needed).
    /** Launch whoosh. */
    private static final float THROW_VOLUME = 0.45f;
    /** Launch crackle. */
    private static final float CRACKLE_VOLUME = 0.25f;
    /** Faint rumble under the launch. */
    private static final float RUMBLE_VOLUME = 0.12f;
    /** Spear fizzling out at max range. */
    private static final float FIZZLE_VOLUME = 0.35f;

    /**
     * Inspection helper: while the server tick is frozen ({@code /tick freeze}) the spear stops
     * advancing and stays exactly where it is, so you can walk up and look at the model. It
     * carries on when you {@code /tick unfreeze}, and moves one step per {@code /tick step}.
     */
    private static final boolean HOLD_WHEN_TICK_FROZEN = true;


    private final ElementSMPRefined plugin;

    public LightningSpearAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("storm_lightning_spear", ElementType.STORM, 1, Constants.Cooldown.ABILITY_1_SECONDS, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        TrustManager trust = context.getTrustManager();
        World world = player.getWorld();

        Vector direction = player.getEyeLocation().getDirection().normalize();
        // Starts just in front of and slightly below the eyes so it doesn't blot out your view.
        Location start = player.getEyeLocation().add(0, -0.25, 0).add(direction.clone().multiply(0.6));

        world.playSound(start, Sound.ITEM_TRIDENT_THROW, THROW_VOLUME, 1.7f);
        world.playSound(start, Sound.ITEM_TRIDENT_THUNDER, CRACKLE_VOLUME, 1.6f);
        world.playSound(start, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, RUMBLE_VOLUME, 2.0f);

        new BukkitRunnable() {
            final Location position = start.clone();
            final LightningSpearVisual spear = new LightningSpearVisual(start, direction, player);
            double travelled = 0;

            /** Every exit path calls cancel(), so the spear entity can never outlive the ability. */
            @Override
            public synchronized void cancel() throws IllegalStateException {
                spear.remove();
                super.cancel();
            }

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                // Frozen: hang in the air (model stays visible) instead of flying on and vanishing.
                if (HOLD_WHEN_TICK_FROZEN && isTickFrozen()) return;

                // One ray per tick covers the whole step: first block or first valid entity along it.
                RayTraceResult hit = world.rayTrace(
                        position, direction, SPEED, FluidCollisionMode.NEVER, true, HIT_RADIUS,
                        entity -> isTargetable(context, entity));

                if (hit != null) {
                    Location impact = hit.getHitPosition().toLocation(world);
                    spear.moveTo(impact);
                    drawSpear(world, impact, direction);
                    Entity hitEntity = hit.getHitEntity();
                    explode(context, impact, hitEntity instanceof LivingEntity living ? living : null, direction);
                    cancel();
                    return;
                }

                position.add(direction.clone().multiply(SPEED));
                travelled += SPEED;
                spear.moveTo(position);
                drawSpear(world, position, direction);

                if (travelled >= RANGE) {
                    // Ran out of range - fizzle out instead of vanishing silently.
                    StormParticles.spearFizzle(world, position);
                    world.playSound(position, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, FIZZLE_VOLUME, 1.8f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /** True while {@code /tick freeze} is active and no {@code /tick step} is being run. */
    private static boolean isTickFrozen() {
        var ticks = Bukkit.getServerTickManager();
        return ticks.isFrozen() && !ticks.isStepping();
    }

    /** Whether the spear should collide with this entity. */
    private boolean isTargetable(ElementContext context, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (entity instanceof ArmorStand) return false;
        if (!living.isValid() || living.isDead()) return false;
        if (living instanceof Player other) {
            GameMode mode = other.getGameMode();
            if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) return false;
        }
        return isValidTarget(context, living);
    }

    /**
     * Particle accents around the spear model (tip at {@code head}): a glowing tip, a thin
     * energy trail and loose arcs of electricity. The shaft itself is the model.
     */
    private void drawSpear(World world, Location head, Vector direction) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Glowing tip.
        StormParticles.spearTip(world, head);

        // Thin energy trail behind the tail so the fast spear still leaves a streak.
        Location tail = head.clone().subtract(direction.clone().multiply(SPEAR_LENGTH));
        for (double d = 0; d <= SPEAR_LENGTH; d += 0.75) {
            Location p = tail.clone().subtract(direction.clone().multiply(d));
            StormParticles.spearTrail(world, p);
        }

        // Loose arcs of electricity crackling around the shaft.
        for (int i = 0; i < 3; i++) {
            double along = random.nextDouble() * SPEAR_LENGTH;
            Location p = head.clone().subtract(direction.clone().multiply(along));
            StormParticles.spearArc(world, p);
        }
    }

    /** Impact: real lightning strike, then the direct hit's damage + shock and the shock burst. */
    private void explode(ElementContext context, Location impact, LivingEntity directHit, Vector direction) {
        Player caster = context.getPlayer();
        World world = impact.getWorld();

        // Visual-only lightning: no vanilla damage, no fire, no creeper charging, no mob
        // conversion. The damage below is ours, so it stays tunable via the constants above.
        // Its thunder/impact sounds are played by the client when the entity appears.
        world.strikeLightningEffect(impact);

        if (directHit != null) {
            // A raised shield stops the spear itself: no damage and no shock for the one holding it up.
            // (The burst below is an area effect, so it isn't blockable.)
            boolean landed = TrueDamage.of(DIRECT_DAMAGE).attacker(caster).ability(getName()).type(DamageType.LIGHTNING_BOLT)
                    .ignoreIFrames(false).blockableByShield(direction).apply(directHit);
            if (landed) {
                shock(caster, directHit);
            }
        }

        for (LivingEntity nearby : impact.getNearbyLivingEntities(SHOCK_RADIUS)) {
            if (nearby.equals(directHit)) continue;
            if (!isTargetable(context, nearby)) continue;

            TrueDamage.of(SHOCK_DAMAGE).attacker(caster).ability(getName()).type(DamageType.LIGHTNING_BOLT)
                    .ignoreIFrames(false).apply(nearby);
            shock(caster, nearby);
            StormParticles.spearAreaShock(nearby.getLocation());
        }
    }

    /** The "shock": a very short stun for players, a heavy slow for everything else. */
    private void shock(Player caster, LivingEntity target) {
        if (target instanceof Player player) {
            plugin.getStatusEffectManager().applyEffect(caster, player, StatusEffectType.STUN, PLAYER_STUN_TICKS, 1);
        } else {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, MOB_SLOW_TICKS, 4, false, false));
        }
        StormParticles.spearShock(target.getLocation());
    }

    @Override
    public String getName() {
        return ChatColor.YELLOW + "Lightning Spear";
    }

    @Override
    public String getDescription() {
        return "Throws a lightning spear that damages and shocks what it hits and anything near it.";
    }
}