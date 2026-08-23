package hs.elementSMPRefined.ability.main.death;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.API.ability.BaseAbility;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.managers.ConfigManager;
import hs.elementSMPRefined.status.StatusEffectType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Death ability2: Ability Disarm.
 * <p>
 * Applies {@link StatusEffectType#ABILITY_DISARM} - via
 * {@link hs.elementSMPRefined.status.DisarmManager#applyAbilityDisarm} since
 * disarm types are tracked there, not by {@code StatusEffectManager} - to the
 * nearest valid enemy in range, disabling their element abilities for 10 seconds.
 */
public class DeathAbilityDisarmAbility extends BaseAbility {
    private static final double RANGE = 6.0;
    private static final int DISARM_DURATION_TICKS = 200; // 10 seconds

    private final ElementSMPRefined plugin;

    public DeathAbilityDisarmAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("death_ability_disarm", ElementType.DEATH, 2, 20, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        Player target = findNearestEnemy(context);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "No target in range.");
            return false;
        }

        plugin.getDisarmManager().applyAbilityDisarm(target, DISARM_DURATION_TICKS);

        Location loc = target.getLocation().add(0, 1, 0);
        target.getWorld().spawnParticle(Particle.SQUID_INK, loc, 20, 0.3, 0.5, 0.3, 0.05, null, true);
        target.getWorld().playSound(loc, Sound.ENTITY_WITHER_HURT, 0.7f, 1.4f);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.6f, 1.6f);

        return true;
    }

    private Player findNearestEnemy(ElementContext context) {
        Player player = context.getPlayer();
        Player nearest = null;
        double bestDistSq = RANGE * RANGE;

        for (LivingEntity entity : player.getLocation().getNearbyLivingEntities(RANGE)) {
            if (!(entity instanceof Player targetPlayer)) continue; // only players carry element abilities
            if (targetPlayer.equals(player)) continue;
            if (context.getTrustManager().isTrusted(player.getUniqueId(), targetPlayer.getUniqueId())) continue;

            double distSq = targetPlayer.getLocation().distanceSquared(player.getLocation());
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                nearest = targetPlayer;
            }
        }
        return nearest;
    }

    @Override
    public String getName() {
        return ChatColor.DARK_PURPLE + "Ability Disarm";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Disable the nearest enemy's ability use for 10 seconds. (45 mana)";
    }
}