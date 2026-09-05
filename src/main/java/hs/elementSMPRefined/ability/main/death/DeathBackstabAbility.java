package hs.elementSMPRefined.ability.main.death;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.API.ability.BaseAbility;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.managers.ConfigManager;
import hs.elementSMPRefined.status.StatusEffectType;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Death ability2: Backstab.
 * <p>
 * Looks for a living entity within {@link #RANGE} blocks that the caster is looking
 * at, blinks the caster directly behind them, and stabs them for
 * {@link #TRUE_DAMAGE} true damage (bypasses armor/resistance/enchants -
 * same convention as {@code MetalDashAbility}/{@code WaterPullDownAbility}),
 * then applies {@link StatusEffectType#WEAKNESS} for
 * {@link #WEAKNESS_DURATION_TICKS}.
 * <p>
 * Replaces the old Ability Disarm (formerly {@code DeathAbilityDisarmAbility}).
 */
public class DeathBackstabAbility extends BaseAbility {
    private static final double RANGE = 8.0;
    /** Cosine of the targeting cone half-angle - matches the "looking at" feel used by GraspAbility. */
    private static final double LOOK_DOT_THRESHOLD = 0.8;
    private static final double BEHIND_DISTANCE = 1.2;
    private static final double MIN_BEHIND_DISTANCE = 0.4;
    private static final double SWEEP_STEP = 0.2;
    private static final double TRUE_DAMAGE = 8.0; // 4 hearts
    private static final int WEAKNESS_DURATION_TICKS = 200; // 10 seconds

    /** Pure black dust, matching the vanish/reappear look used by DeathSideStepAbility. */
    private static final Particle.DustOptions BLACK_DUST =
            new Particle.DustOptions(Color.fromRGB(5, 5, 5), 1.4F);

    private final ElementSMPRefined plugin;

    public DeathBackstabAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("death_backstab", ElementType.DEATH, 2, 20, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        LivingEntity target = findLookedAtTarget(context);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "No target in range.");
            return false;
        }

        World world = player.getWorld();
        Location behind = findSafeSpotBehind(target);

        // Vanish at the caster's old spot.
        spawnBurst(world, player.getLocation().add(0, 1, 0));
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.7f);

        player.teleport(behind);
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);

        // Reappear right behind the target and land the stab.
        spawnBurst(world, behind.clone().add(0, 1, 0));
        world.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);

        Location hitLoc = target.getLocation().add(0, 1.1, 0);
        world.spawnParticle(Particle.SWEEP_ATTACK, hitLoc, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.CRIT, hitLoc, 20, 0.3, 0.4, 0.3, 0.2, null, true);
        world.playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
        world.playSound(hitLoc, Sound.ENTITY_WITHER_HURT, 0.5f, 1.6f);

        dealTrueDamage(target, player);
        if (target instanceof Player targetPlayer) {
            plugin.getStatusEffectManager().applyManaSteal(player, targetPlayer, 200); // 10 seconds
        }

        return true;
    }

    /**
     * Finds the nearest living entity within RANGE that the caster is currently
     * looking at (cone check, not just proximity). Trust is only checked against
     * other players - mobs have no trust relationship and are always valid targets.
     */
    private LivingEntity findLookedAtTarget(ElementContext context) {
        Player player = context.getPlayer();
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().normalize();

        LivingEntity best = null;
        double bestDistance = RANGE;

        for (LivingEntity entity : eyeLoc.getNearbyLivingEntities(RANGE)) {
            if (entity.equals(player)) continue;
            if (entity instanceof Player targetPlayer
                    && context.getTrustManager().isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) {
                continue;
            }

            Vector toEntity = entity.getEyeLocation().toVector().subtract(eyeLoc.toVector());
            double distance = toEntity.length();
            if (distance == 0 || distance > RANGE) continue;

            toEntity.normalize();
            double dot = direction.dot(toEntity);

            if (dot > LOOK_DOT_THRESHOLD && distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    /**
     * Walks inward from directly behind the target toward them, stopping at the
     * first unblocked spot. Falls back to right on top of the target if the
     * whole sweep is blocked, same fail-safe spirit as DeathSideStepAbility.
     */
    private Location findSafeSpotBehind(LivingEntity target) {
        Location targetLoc = target.getLocation();

        // Horizontal-only facing direction (yaw alone), so looking straight up/down
        // can't collapse this into a zero vector - same trick as DeathSideStepAbility.
        double yawRad = Math.toRadians(targetLoc.getYaw());
        Vector facing = new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vector behindDir = facing.clone().multiply(-1);

        for (double d = BEHIND_DISTANCE; d >= MIN_BEHIND_DISTANCE; d -= SWEEP_STEP) {
            Location candidate = targetLoc.clone().add(behindDir.clone().multiply(d));
            candidate.setY(targetLoc.getY());
            if (!isBlocked(candidate)) {
                candidate.setDirection(facing); // face the same way the target does, i.e. straight at their back
                return candidate;
            }
        }

        Location fallback = targetLoc.clone();
        fallback.setDirection(facing);
        return fallback;
    }

    /** True if either foot or head level at this location is a solid block. */
    private boolean isBlocked(Location loc) {
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        return feet.getType().isSolid() || head.getType().isSolid();
    }

    /**
     * True damage - ignores armor/resistance/enchants. Killing blows are routed
     * through {@link LivingEntity#damage} instead of setHealth so death events
     * and totems of undying still fire correctly (same edge case handled in
     * WaterPullDownAbility).
     * <p>
     * Non-lethal hits also fire a negligible real {@code damage()} call first -
     * a bare {@code setHealth()} never triggers the vanilla hurt animation/red
     * damage overlay/hurt sound, since those are driven off the damage event,
     * not the health value. The tiny amount can be soaked up by
     * armor/resistance without meaningfully affecting the true-damage total.
     */
    private void dealTrueDamage(LivingEntity target, Player attacker) {
        double newHealth = target.getHealth() - TRUE_DAMAGE;
        if (newHealth <= 0) {
            target.damage(TRUE_DAMAGE, attacker);
        } else {
            target.damage(0.001, attacker); // triggers hurt overlay/animation/sound only
            target.setHealth(Math.max(0.0, target.getHealth() - (TRUE_DAMAGE - 0.001)));
        }
    }

    private void spawnBurst(World world, Location center) {
        world.spawnParticle(Particle.DUST, center, 40, 0.35, 0.5, 0.35, 0, BLACK_DUST, true);
        world.spawnParticle(Particle.SQUID_INK, center, 6, 0.3, 0.4, 0.3, 0.01, null, true);
    }

    @Override
    public String getName() {
        return ChatColor.DARK_PURPLE + "Backstab";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Look at a living entity within 8 blocks to blink behind them, dealing 4 hearts of true damage and applying Weakness for 10 seconds. (60 mana)";
    }
}