package net.rose.elementSMPRefined.ability.main.upgraded.storm;

import net.rose.elementSMPRefined.util.damage.AbilityTargets;
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
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
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

public class LightningSpearAbility extends BaseAbility {

    private static final double SPEED = 2.2 ;
    private static final double RANGE = 48.0;
    private static final double HIT_RADIUS = 0.6;
    private static final double SPEAR_LENGTH = 3.0;

    private static final double DIRECT_DAMAGE = 5.0; // 2.5 hearts
    private static final double SHOCK_RADIUS = 3.0;
    private static final double SHOCK_DAMAGE = 3.0;

    private static final int PLAYER_STUN_TICKS = 15;
    private static final int MOB_SLOW_TICKS = 30;

    // Sound volumes (1.0 = vanilla "normal"; lower = quieter and shorter range)
    // The impact thunder/zap are played by the lightning entity itself on the client, so their
    // volume can't be set here (override entity.lightning_bolt.* in the resource pack if needed).
    private static final float THROW_VOLUME = 0.45f;
    private static final float CRACKLE_VOLUME = 0.25f;
    private static final float RUMBLE_VOLUME = 0.12f;
    private static final float FIZZLE_VOLUME = 0.35f;

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

    private static boolean isTickFrozen() {
        var ticks = Bukkit.getServerTickManager();
        return ticks.isFrozen() && !ticks.isStepping();
    }

    private boolean isTargetable(ElementContext context, Entity entity) {
        return entity instanceof LivingEntity living
                && AbilityTargets.isValid(context.getPlayer(), living, context.getTrustManager());
    }

    private void drawSpear(World world, Location head, Vector direction) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

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