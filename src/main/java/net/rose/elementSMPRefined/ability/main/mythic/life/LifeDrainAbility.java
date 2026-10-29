package net.rose.elementSMPRefined.ability.main.mythic.life;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.particles.element.LifeParticles;
import net.rose.elementSMPRefined.util.damage.TrueDamage;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Life ability 2: point at a player to lock onto them, then siphon their health for 5 seconds.
 * Once a second the target loses health and the caster gains exactly what was taken.
 * The lock-on is fixed at cast time - the beam is drawn caster -> target, so looking away
 * does not move it. It ends if the target dies, leaves, or gets out of range.
 * Self and trusted players can't be locked onto.
 */
public class LifeDrainAbility extends BaseAbility {

    private static final int DURATION_TICKS = 100;
    private static final int PULSE_INTERVAL_TICKS = 20;
    private static final double LOCK_RANGE = 20.0;
    private static final double BREAK_RANGE = 25.0; // a bit more than lock range so edge cases don't flicker
    private static final double BEAM_STEP = 1.0;       // blocks between particles
    private static final int BEAM_INTERVAL_TICKS = 3;    // redraw ~7x/sec instead of every tick
    private static final double DRAIN_PER_PULSE = 2.0; // 1 heart

    private final ElementSMPRefined plugin;

    public LifeDrainAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("life_drain", ElementType.LIFE, 2, Constants.Cooldown.ABILITY_2_SECONDS, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        Player target = findTarget(context, player);
        if (target == null) {
            player.sendMessage(Lang.DEATH_BACKSTAB_NO_TARGET_IN_RANGE);
            return false;
        }

        setActive(player, true);

        // One quiet sound on cast only - no per-pulse sounds while draining.
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.3f, 1.4f);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || !target.isOnline() || target.isDead()
                        || !target.isValid() || !target.getWorld().equals(player.getWorld())
                        || target.getLocation().distanceSquared(player.getLocation()) > BREAK_RANGE * BREAK_RANGE
                        || ticks >= DURATION_TICKS) {
                    setActive(player, false);
                    cancel();
                    return;
                }

                if (ticks % BEAM_INTERVAL_TICKS == 0) {
                    drawBeam(player, target);
                }

                if (ticks % PULSE_INTERVAL_TICKS == 0) {
                    drain(player, target);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /** Beam goes chest -> chest, so it stays between the two players whatever way the caster faces. */
    private void drawBeam(Player caster, Player target) {
        Location from = caster.getLocation().add(0, 1.2, 0);
        Location to = target.getLocation().add(0, 1.2, 0);
        Vector line = to.toVector().subtract(from.toVector());
        double length = line.length();
        if (length < 0.1) return;
        Vector step = line.clone().normalize().multiply(BEAM_STEP);

        // Start one step out so nothing spawns on top of the caster's screen.
        Location pt = from.clone().add(step);
        for (double d = BEAM_STEP; d < length; d += BEAM_STEP) {
            LifeParticles.drainBeamPoint(caster.getWorld(), pt);
            pt.add(step);
        }
    }

    /** Aim check used only at cast time to pick who to lock onto. */
    private Player findTarget(ElementContext context, Player caster) {
        RayTraceResult rt = caster.rayTraceEntities((int) LOCK_RANGE);
        if (rt == null || !(rt.getHitEntity() instanceof Player target)) return null;
        if (target.equals(caster) || target.isDead() || !target.isValid()) return null;
        if (context.getTrustManager().isTrusted(caster.getUniqueId(), target.getUniqueId())) return null;
        return target;
    }

    private void drain(Player caster, Player target) {
        double before = target.getHealth();
        boolean landed = TrueDamage.of(DRAIN_PER_PULSE).attacker(caster).ability(getName())
                .ignoreIFrames(true).apply(target);
        if (!landed) return;

        // Only heal for what was really taken (target may have had less than a full pulse left).
        double drained = Math.max(0.0, before - Math.max(0.0, target.getHealth()));
        if (drained <= 0.0) return;

        var maxAttr = caster.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr != null ? maxAttr.getValue() : 20.0;
        caster.setHealth(Math.min(max, caster.getHealth() + drained));

        LifeParticles.drainHit(target.getWorld(), target.getBoundingBox().getCenter().toLocation(target.getWorld()));
        LifeParticles.drainGain(caster.getWorld(), caster.getLocation().add(0, 1.0, 0));
    }

    @Override
    public String getName() {
        return ChatColor.DARK_RED + "Life Drain";
    }

    @Override
    public String getDescription() {
        return "Lock onto a player and siphon their health into yours. The beam stays locked on, even if you look away.";
    }
}
