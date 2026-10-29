package net.rose.elementSMPRefined.ability.main.mythic.death;

import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.server.MaceCooldown;
import net.rose.elementSMPRefined.lang.Lang;
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

public class DeathBackstabAbility extends BaseAbility {
    private static final double RANGE = 8.0;
    private static final double LOOK_DOT_THRESHOLD = 0.8;
    private static final double BEHIND_DISTANCE = 1.2;
    private static final double MIN_BEHIND_DISTANCE = 0.4;
    private static final double SWEEP_STEP = 0.2;
    private static final double TRUE_DAMAGE = 8.0; // 4 hearts
    private static final int MANA_STEAL_AMOUNT = 30;
    private static final boolean MANA_STEAL_GIVES_CASTER = true;
    private static final Particle.DustOptions BLACK_DUST =
            new Particle.DustOptions(Color.fromRGB(5, 5, 5), 1.4F);

    private final ElementSMPRefined plugin;

    public DeathBackstabAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("death_backstab", ElementType.DEATH, 2, Constants.Cooldown.ABILITY_2_SECONDS, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        LivingEntity target = findLookedAtTarget(context);
        if (target == null) {
            player.sendMessage(Lang.DEATH_BACKSTAB_NO_TARGET_IN_RANGE);
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

        return true;
    }

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

    private boolean isBlocked(Location loc) {
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        return feet.getType().isSolid() || head.getType().isSolid();
    }

    private void dealTrueDamage(LivingEntity target, Player attacker) {
        double newHealth = target.getHealth() - TRUE_DAMAGE;
        if (newHealth <= 0) {
            MaceCooldown.ignoreHits(() -> target.damage(TRUE_DAMAGE, attacker));
        } else {
            // triggers hurt overlay/animation/sound only
            MaceCooldown.ignoreHits(() -> target.damage(0.001, attacker));
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
        return "Teleport behind the enemy you're looking at.";
    }
}