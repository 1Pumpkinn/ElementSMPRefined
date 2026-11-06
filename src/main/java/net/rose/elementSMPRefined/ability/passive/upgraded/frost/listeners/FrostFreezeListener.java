package net.rose.elementSMPRefined.ability.passive.upgraded.frost.listeners;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.managers.TeamManager;
import net.rose.elementSMPRefined.status.StatusEffectType;
import net.rose.elementSMPRefined.util.visual.model.FreezeEffectVisual;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FrostFreezeListener implements Listener {

    private static final double CHANCE = 0.03; // 3% (keep FrostElement's info text in sync)
    private static final int CHANCE_PERCENT = 3;
    private static final int FREEZE_TICKS = 30; // 1.5 seconds

    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final TeamManager teamManager;
    private final Set<UUID> frozen = new HashSet<>();

    public FrostFreezeListener(
            ElementSMPRefined plugin, ElementManager elementManager, TeamManager teamManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        this.teamManager = teamManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        // Direct melee only - projectiles and abilities don't count.
        if (event.getCause() != DamageCause.ENTITY_ATTACK
                && event.getCause() != DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        // Players only - mobs are never frozen.
        if (!(event.getEntity() instanceof Player victim) || victim.equals(attacker)) return;

        // Roll first - it fails 95% of the time, so this keeps every other hit on the server cheap.
        if (ThreadLocalRandom.current().nextDouble() >= CHANCE) return;

        var data = elementManager.data(attacker.getUniqueId());
        if (data == null || data.getCurrentElement() != ElementType.FROST) return;
        if (data.getUpgradeLevel(ElementType.FROST) < BaseElement.PASSIVE_2_UPGRADE_LEVEL) return;

        // A raised shield stops the freeze. Checked both ways: isBlocking() covers the common case,
        // and the
        // BLOCKING damage modifier still catches a hit that was blocked even if it disabled the
        // shield (axes).
        if (victim.isBlocking()) return;
        if (event.isApplicable(DamageModifier.BLOCKING)
                && event.getDamage(DamageModifier.BLOCKING) < 0) return;

        GameMode mode = victim.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return;
        if (teamManager.isFriendly(attacker.getUniqueId(), victim.getUniqueId())) return;

        freeze(attacker, victim);
    }

    private void freeze(Player attacker, Player victim) {
        UUID id = victim.getUniqueId();
        if (!frozen.add(id)) return; // already encased

        // Wait one tick so the knockback from the hit that triggered this has been applied. The
        // victim is
        // frozen where they are - no teleporting. Horizontal movement is locked by the stun, but
        // they still
        // fall (see StatusEffectListener), so a victim in the air drops to the ground under normal
        // gravity.
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (!victim.isOnline() || victim.isDead() || !victim.isValid()) {
                                frozen.remove(id);
                                return;
                            }

                            try {
                                // Gliding would hold them up (and carry them), so end it and let
                                // gravity take over.
                                if (victim.isGliding()) victim.setGliding(false);

                                plugin.getStatusEffectManager()
                                        .applyEffect(
                                                attacker,
                                                victim,
                                                StatusEffectType.STUN,
                                                FREEZE_TICKS,
                                                1);
                                FreezeEffectVisual.encase(plugin, victim, FREEZE_TICKS);

                                plugin.getServer()
                                        .getScheduler()
                                        .runTaskLater(plugin, () -> frozen.remove(id), FREEZE_TICKS);
                            } catch (RuntimeException e) {
                                frozen.remove(id); // never leave the victim un-freezable
                                throw e;
                            }
                        });
    }
}
