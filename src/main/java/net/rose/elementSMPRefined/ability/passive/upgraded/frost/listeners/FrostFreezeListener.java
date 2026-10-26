package net.rose.elementSMPRefined.ability.passive.upgraded.frost.listeners;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.status.StatusEffectType;
import net.rose.elementSMPRefined.util.visual.model.IceBlockVisual;
import org.bukkit.GameMode;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Frost Element Passive: Flash Freeze (unlocks at Upgrade II)
 *
 * Every melee hit a Frost player lands has a {@value #CHANCE_PERCENT}% chance to encase the target
 * in a block of ice for {@value #FREEZE_TICKS} ticks (2 seconds), during which it cannot move.
 * <ul>
 *     <li>Players get the {@link StatusEffectType#STUN} status (can't move, can still look and
 *     interact), so the existing stun handling covers movement, knockback and the HUD message.</li>
 *     <li>Mobs have their AI switched off for the duration and switched back on afterwards.</li>
 * </ul>
 * The ice is an {@link IceBlockVisual}. Trusted players, creative/spectator players and armor
 * stands are never frozen, and a target that is already frozen can't be re-frozen until it thaws.
 */
public class FrostFreezeListener implements Listener {

    private static final double CHANCE = 0.05; // 5% (keep FrostElement's info text in sync)
    private static final int CHANCE_PERCENT = 5;
    private static final int FREEZE_TICKS = 40; // 2 seconds

    /** Mobs whose AI this passive switched off, so plugin disable can switch it back on. */
    private static final Map<UUID, Mob> FROZEN_MOBS = new ConcurrentHashMap<>();

    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final TrustManager trustManager;
    private final Set<UUID> frozen = new HashSet<>();

    public FrostFreezeListener(ElementSMPRefined plugin, ElementManager elementManager, TrustManager trustManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        this.trustManager = trustManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        // Direct melee only - projectiles and abilities don't count.
        if (event.getCause() != DamageCause.ENTITY_ATTACK && event.getCause() != DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target) || target.equals(attacker)) return;
        if (target instanceof ArmorStand) return;

        // Roll first - it fails 95% of the time, so this keeps every other hit on the server cheap.
        if (ThreadLocalRandom.current().nextDouble() >= CHANCE) return;

        var data = elementManager.data(attacker.getUniqueId());
        if (data == null || data.getCurrentElement() != ElementType.FROST) return;
        if (data.getUpgradeLevel(ElementType.FROST) < BaseElement.PASSIVE_2_UPGRADE_LEVEL) return;

        if (target instanceof Player victim) {
            GameMode mode = victim.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return;
            if (trustManager.isTrusted(attacker.getUniqueId(), victim.getUniqueId())) return;
        }

        freeze(attacker, target);
    }

    private void freeze(Player attacker, LivingEntity target) {
        UUID id = target.getUniqueId();
        if (!frozen.add(id)) return; // already encased

        boolean aiSwitchedOff = false;
        if (target instanceof Player victim) {
            plugin.getStatusEffectManager().applyEffect(attacker, victim, StatusEffectType.STUN, FREEZE_TICKS, 1);
        } else if (target instanceof Mob mob && mob.hasAI()) {
            mob.setAI(false);
            FROZEN_MOBS.put(id, mob);
            aiSwitchedOff = true;
        }

        IceBlockVisual.encase(plugin, target, FREEZE_TICKS);

        final boolean restoreAi = aiSwitchedOff;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            frozen.remove(id);
            if (restoreAi) {
                Mob mob = FROZEN_MOBS.remove(id);
                if (mob != null && mob.isValid()) mob.setAI(true);
            }
        }, FREEZE_TICKS);
    }

    /** Switches AI back on for every mob still frozen. Call on plugin disable - AI-off would otherwise be saved with the mob. */
    public static void releaseAll() {
        for (Mob mob : FROZEN_MOBS.values()) {
            if (mob.isValid()) mob.setAI(true);
        }
        FROZEN_MOBS.clear();
    }
}