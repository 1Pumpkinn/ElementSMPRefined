package rose.elementSMPRefined.ability.passive.death.listeners;

import rose.elementSMPRefined.API.element.ElementType;
import rose.elementSMPRefined.managers.ElementManager;
import rose.elementSMPRefined.managers.TrustManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Death passive: hitting an entity has a chance to apply Wither for 5 seconds.
 * Does not apply to trusted/allied players.
 */
public class DeathWitherOnHitListener implements Listener {

    private static final double WITHER_CHANCE = 0.35; // 35%
    private static final int WITHER_TICKS = 100;       // 5 seconds

    private final ElementManager elementManager;
    private final TrustManager trustManager;

    public DeathWitherOnHitListener(ElementManager elementManager, TrustManager trustManager) {
        this.elementManager = elementManager;
        this.trustManager = trustManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        var playerData = elementManager.data(attacker.getUniqueId());
        if (playerData == null || playerData.getCurrentElement() != ElementType.DEATH) return;

        if (victim instanceof Player targetPlayer &&
                trustManager.isTrusted(attacker.getUniqueId(), targetPlayer.getUniqueId())) {
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() > WITHER_CHANCE) return;

        victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, WITHER_TICKS, 0, true, true, true));
    }
}