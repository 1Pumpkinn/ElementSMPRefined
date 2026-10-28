package net.rose.elementSMPRefined.ability.passive.upgraded.frost.listeners;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.element.BaseElement;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.status.StatusEffectType;
import net.rose.elementSMPRefined.util.visual.model.IceBlockVisual;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Frost Element Passive: Flash Freeze (unlocks at Upgrade II)
 *
 * Every melee hit a Frost player lands on another <b>player</b> has a {@value #CHANCE_PERCENT}% chance
 * to encase them in a block of ice for {@value #FREEZE_TICKS} ticks (2 seconds), during which they
 * cannot move. Mobs are never frozen.
 * <ul>
 *     <li>The victim gets the {@link StatusEffectType#STUN} status (can't move, can still look and
 *     interact), so the existing stun handling covers movement, knockback and the HUD message.</li>
 *     <li>A frozen player is always on the ground: if the victim is airborne when the freeze lands
 *     they are set down on the ground below them first, so nobody hangs frozen in mid-air. If there
 *     is no ground beneath them (the void) the freeze simply doesn't happen.</li>
 * </ul>
 * The ice is an {@link IceBlockVisual}. Trusted players, creative/spectator players are never
 * frozen, and a victim that is already frozen can't be re-frozen until it thaws.
 */
public class FrostFreezeListener implements Listener {

    private static final double CHANCE = 0.05; // 5% (keep FrostElement's info text in sync)
    private static final int CHANCE_PERCENT = 5;
    private static final int FREEZE_TICKS = 40; // 2 seconds

    /** How far below the victim to look for ground before giving up (blocks). */
    private static final double MAX_GROUND_SEARCH = 64.0;
    /** Anything further than this above the ground counts as airborne and gets set down (blocks). */
    private static final double AIRBORNE_THRESHOLD = 0.15;

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
        // Players only - mobs are never frozen.
        if (!(event.getEntity() instanceof Player victim) || victim.equals(attacker)) return;

        // Roll first - it fails 95% of the time, so this keeps every other hit on the server cheap.
        if (ThreadLocalRandom.current().nextDouble() >= CHANCE) return;

        var data = elementManager.data(attacker.getUniqueId());
        if (data == null || data.getCurrentElement() != ElementType.FROST) return;
        if (data.getUpgradeLevel(ElementType.FROST) < BaseElement.PASSIVE_2_UPGRADE_LEVEL) return;

        GameMode mode = victim.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return;
        if (trustManager.isTrusted(attacker.getUniqueId(), victim.getUniqueId())) return;

        freeze(attacker, victim);
    }

    private void freeze(Player attacker, Player victim) {
        UUID id = victim.getUniqueId();
        if (!frozen.add(id)) return; // already encased

        // Wait one tick so the knockback from the hit that triggered this has been applied - the
        // victim is then set down from wherever that knockback left them.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!victim.isOnline() || victim.isDead() || !victim.isValid()) {
                frozen.remove(id);
                return;
            }

            if (!settleOnGround(victim)) {
                frozen.remove(id); // nothing to stand on (void) - no freeze
                return;
            }

            plugin.getStatusEffectManager().applyEffect(attacker, victim, StatusEffectType.STUN, FREEZE_TICKS, 1);
            IceBlockVisual.encase(plugin, victim, FREEZE_TICKS);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> frozen.remove(id), FREEZE_TICKS);
        });
    }

    /**
     * Makes sure the victim is standing on the ground before they're frozen: if they're in the air,
     * they are put down on the first solid surface beneath them, with their momentum and fall
     * distance cleared and gliding stopped.
     *
     * @return false if there is no ground below the victim, in which case nothing was changed
     */
    private boolean settleOnGround(Player victim) {
        Location at = victim.getLocation();
        // Start a touch above the feet so a victim standing inside a carpet/slab edge still finds their floor.
        Location from = at.clone().add(0, 0.1, 0);
        RayTraceResult ground = victim.getWorld().rayTraceBlocks(
                from, new Vector(0, -1, 0), MAX_GROUND_SEARCH, FluidCollisionMode.NEVER, true);
        if (ground == null) return false;

        double groundY = ground.getHitPosition().getY();
        boolean airborne = at.getY() - groundY > AIRBORNE_THRESHOLD;

        if (airborne) {
            if (victim.isGliding()) victim.setGliding(false);
            Location landing = new Location(at.getWorld(), at.getX(), groundY, at.getZ(), at.getYaw(), at.getPitch());
            victim.teleport(landing);
        }
        victim.setVelocity(new Vector(0, 0, 0));
        victim.setFallDistance(0f);
        return true;
    }
}
