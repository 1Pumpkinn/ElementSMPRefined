package net.rose.elementSMPRefined.ability.main.upgraded.metal;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.damage.AbilityDamage;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import net.rose.elementSMPRefined.util.visual.model.MetalShieldVisual;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Metal element ability 1: surrounds the caster with {@value #SHIELD_COUNT} orbiting Metal
 * Shields ({@link MetalShieldVisual}):
 * <ul>
 *   <li>each melee or projectile hit is absorbed by one shield, popping the shield closest to
 *       the attacker, and</li>
 *   <li>a damaging ability (anything dealt through
 *       {@link net.rose.elementSMPRefined.util.damage.TrueDamage} or flagged with
 *       {@link AbilityDamage}) is absorbed too, but shatters <b>all</b> remaining shields at
 *       once. Non-damage abilities (pulls, dashes...) never touch a shield.</li>
 * </ul>
 * An absorbed hit is cancelled outright - no damage, no knockback. The effect ends when the last
 * shield pops, or fades on its own after {@value #DURATION_TICKS} ticks. Environmental damage
 * (fall, fire, drowning, explosions...) is deliberately not blocked.
 * <p>
 * Hits that would be cancelled anyway are ignored so they don't waste a charge: self-damage
 * and trusted players.
 * <p>
 * The listener runs at LOW priority so it cancels the hit before on-hit passives (e.g. Death's
 * wither) get to apply their effects to a shielded player.
 */
public class MetalShieldAbility extends BaseAbility implements Listener {

    private static final int SHIELD_COUNT = 3;
    private static final int DURATION_TICKS = 15 * 20; // 15 seconds if it's never broken
    private static final int COOLDOWN_SECONDS = 45;

    private enum EndReason { BROKEN, EXPIRED, REMOVED }

    private static final class Shield {
        final MetalShieldVisual visual;
        BukkitRunnable task;

        Shield(MetalShieldVisual visual) {
            this.visual = visual;
        }
    }

    private final ElementSMPRefined plugin;
    private final Map<UUID, Shield> shields = new HashMap<>();

    public MetalShieldAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("metal_shield", ElementType.METAL, 1, COOLDOWN_SECONDS, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        if (player == null) return false;

        if (shields.containsKey(player.getUniqueId())) {
            return false;
        }

        Shield shield = new Shield(new MetalShieldVisual(player, SHIELD_COUNT));
        shields.put(player.getUniqueId(), shield);
        setActive(player, true);

        SoundUtils.playTo(player, SoundUtils.Element.METAL);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 0.9f);

        shield.task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    end(player, EndReason.REMOVED);
                    return;
                }
                if (ticks >= DURATION_TICKS) {
                    end(player, EndReason.EXPIRED);
                    return;
                }
                shield.visual.update(ticks);
                ticks++;
            }
        };
        shield.task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Shield shield = shields.get(victim.getUniqueId());
        if (shield == null || event.getDamage() <= 0) return; // 0-damage hits (snowballs...) cost nothing

        // Self-damage and trusted allies never spend a charge (trust would cancel the hit anyway).
        DamageSource damageSource = event.getDamageSource();
        Entity source = damageSource.getCausingEntity();
        if (source instanceof Player attacker
                && (attacker.equals(victim)
                || plugin.getTrustManager().isTrusted(victim.getUniqueId(), attacker.getUniqueId()))) {
            return;
        }

        // Ability damage is checked first - a TrueDamage hit can show up as an entity attack too.
        if (AbilityDamage.isAbilityDamage(victim)) {
            AbilityDamage.markBlocked(victim);
            absorb(victim, shield, event, source, true);
            return;
        }

        if (!isMeleeOrProjectile(event.getCause())) return;

        absorb(victim, shield, event, source, false);
    }

    private boolean isMeleeOrProjectile(DamageCause cause) {
        return cause == DamageCause.ENTITY_ATTACK
                || cause == DamageCause.ENTITY_SWEEP_ATTACK
                || cause == DamageCause.PROJECTILE;
    }

    /**
     * Cancels the hit and pops the shield nearest the attacker. An ability hit shatters every
     * remaining shield instead of just one.
     */
    private void absorb(Player victim, Shield shield, EntityDamageEvent event, Entity source, boolean abilityHit) {
        event.setCancelled(true);

        Location from = source != null ? source.getLocation() : null;
        int toPop = abilityHit ? shield.visual.count() : 1;

        Location at = null;
        for (int i = 0; i < toPop && shield.visual.count() > 0; i++) {
            Location popped = shield.visual.popNearest(from);
            if (popped == null) popped = victim.getLocation().add(0, 1.0, 0);
            at = popped;
            popEffects(popped, abilityHit);
        }
        if (at == null) at = victim.getLocation().add(0, 1.0, 0);

        if (abilityHit) {
            at.getWorld().playSound(at, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.7f);
        }
        at.getWorld().playSound(at, Sound.ITEM_SHIELD_BLOCK, 1.0f, abilityHit ? 0.8f : 1.1f);

        if (shield.visual.count() <= 0) {
            end(victim, EndReason.BROKEN);
        }
    }

    private void popEffects(Location at, boolean abilityHit) {
        at.getWorld().spawnParticle(Particle.BLOCK, at, 20, 0.25, 0.35, 0.25, 0.1,
                Material.IRON_BLOCK.createBlockData(), true);
        at.getWorld().spawnParticle(Particle.CRIT, at, abilityHit ? 24 : 12, 0.3, 0.4, 0.3, 0.2, null, true);
    }


    private void end(Player player, EndReason reason) {
        Shield shield = shields.remove(player.getUniqueId());
        if (shield == null) return;

        if (shield.task != null) shield.task.cancel();
        shield.visual.remove();
        Location at = player.getLocation().add(0, 1.0, 0);
        super.setActive(player, false);

        switch (reason) {
            case BROKEN -> {
                at.getWorld().playSound(at, Sound.ITEM_SHIELD_BREAK, 1.0f, 0.9f);
            }
            case EXPIRED -> {
                at.getWorld().playSound(at, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.8f, 0.8f);
                at.getWorld().spawnParticle(Particle.BLOCK, at, 20, 0.6, 0.5, 0.6, 0.05,
                        Material.IRON_BLOCK.createBlockData(), true);
            }
            case REMOVED -> { /* quit / death / element change - nothing to show */ }
        }
    }

    /** Called by {@code BaseElement.clearEffects} (element change etc.) - drops the shield with the flag. */
    @Override
    public void setActive(Player player, boolean active) {
        super.setActive(player, active);
        if (!active) {
            end(player, EndReason.REMOVED);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer(), EndReason.REMOVED);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        end(event.getEntity(), EndReason.REMOVED);
    }


    @Override
    public String getName() {
        return ChatColor.GRAY + "Metal Shield";
    }

    @Override
    public String getDescription() {
        return "Orbiting shields each block one hit. A damaging ability breaks all of them.";
    }
}
