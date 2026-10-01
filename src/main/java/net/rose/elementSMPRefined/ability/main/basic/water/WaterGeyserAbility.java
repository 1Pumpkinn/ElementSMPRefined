package net.rose.elementSMPRefined.ability.main.basic.water;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Water element's movement ability. Erupts two geysers in sequence under the caster:
 * the first is a pure vertical launch, and the second fires once the player's ascent
 * stalls near the apex, kicking them horizontally in whatever direction they're
 * looking at that instant. Splitting the launch this way means the horizontal boost
 * doesn't fight the vertical one on the way up - it lands right as the player crests,
 * carrying them into a proper arc instead of a straight hop.
 * <p>
 * Implements {@link Listener} and self-registers (see {@code WaterBubbleAbility} for the
 * same pattern) so the fall damage from the geyser's own launch can be cancelled.
 */
public class WaterGeyserAbility extends BaseAbility implements Listener {

    private static final double VERTICAL_POWER = 1.80;        // first geyser's pure upward burst
    private static final double HORIZONTAL_BOOST_POWER = 1.75; // second geyser's forward kick at the apex
    private static final double APEX_VELOCITY_THRESHOLD = 0.05; // vertical speed under which we consider the ascent "stalled"
    private static final int APEX_CHECK_MAX_TICKS = 20;         // safety cap if the apex never registers (e.g. water/lily pads messing with velocity)
    private static final int TRAIL_TICKS = 14;
    private static final int GEYSER_WATER_BLOCKS = 2;    // feeds the vanilla geyser particle's plume height/impulse

    private final ElementSMPRefined plugin;

    public WaterGeyserAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("water_geyser", ElementType.WATER, 1, 20, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();
        Location origin = player.getLocation();

        // First geyser: a clean, pure vertical launch. No horizontal component here -
        // that's the second geyser's job once this launch's rise stalls out near the apex.
        player.setVelocity(new Vector(0, VERTICAL_POWER, 0));

        // Reset fall distance so the eventual landing doesn't chunk them for the height
        // the geysers themselves just gave them.
        player.setFallDistance(0f);

        // Grant immunity to the fall damage the launch itself is about to cause;
        // onFallDamage() below cancels the next FALL hit and clears this.
        setActive(player, true);
        grantFallImmunity(player);

        eruptGeyser(origin);
        trailPlayer(player);
        scheduleSecondGeyser(player);

        SoundUtils.playTo(player, SoundUtils.Element.WATER);
        player.getWorld().playSound(origin, Sound.ENTITY_DOLPHIN_JUMP, 1.2f, 0.9f);
        player.getWorld().playSound(origin, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.5f, 0.7f);

        return true;
    }

    /**
     * Watches the first geyser's launch tick by tick and waits for vertical velocity
     * to stall out near the apex - checking actual velocity instead of a flat delay
     * means the second geyser fires at the right moment regardless of how gravity,
     * water, or anything else affects the arc of any given launch. {@code
     * APEX_CHECK_MAX_TICKS} is just a safety net in case the apex is never cleanly
     * detected (e.g. the player re-enters a water/bubble column mid-flight).
     */
    private void scheduleSecondGeyser(Player player) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !isActiveFor(player)) {
                    cancel();
                    return;
                }

                boolean atApex = player.getVelocity().getY() <= APEX_VELOCITY_THRESHOLD;

                if (atApex || ticks >= APEX_CHECK_MAX_TICKS) {
                    fireSecondGeyser(player);
                    cancel();
                    return;
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L); // starts a tick late so the initial upward velocity has a chance to register
    }

    /**
     * Fires the second geyser's kick right as the player's ascent stalls, pushing them
     * horizontally in whatever direction they're looking at that instant - this is the
     * "boost" half of the launch, so it only touches horizontal velocity and leaves
     * whatever vertical motion the player already has untouched.
     */
    private void fireSecondGeyser(Player player) {
        Location location = player.getLocation();

        Vector horizontal = location.getDirection().setY(0);
        if (horizontal.lengthSquared() > 0.0001) {
            horizontal.normalize();
        } else {
            horizontal = new Vector(0, 0, 0);
        }

        Vector boost = horizontal.multiply(HORIZONTAL_BOOST_POWER);
        boost.setY(player.getVelocity().getY());
        player.setVelocity(boost);

        burstForward(location, horizontal);

        player.getWorld().playSound(location, Sound.ENTITY_DOLPHIN_JUMP, 1.0f, 1.3f);
        player.getWorld().playSound(location, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.2f, 1.1f);
    }

    /**
     * Visual for the second geyser's kick - a directional spray thrown along the boost
     * vector instead of the vanilla {@code GEYSER} emitter the first one uses. Reusing
     * the vertical plume here would read as another upward eruption, which is exactly
     * backwards for what's supposed to read as a horizontal push; this offsets a burst
     * of splash/bubble particles slightly ahead of the player along {@code direction}
     * with a matching directional velocity so it visibly sprays outward instead.
     */
    private void burstForward(Location location, Vector direction) {
        Location sprayOrigin = direction.lengthSquared() > 0.0001
                ? location.clone().add(direction.clone().multiply(0.6)).add(0, 0.9, 0)
                : location.clone().add(0, 0.9, 0);

        sprayOrigin.getWorld().spawnParticle(
                Particle.SPLASH, sprayOrigin, 25,
                0.3, 0.3, 0.3, 0.15, null, true
        );
        sprayOrigin.getWorld().spawnParticle(
                Particle.BUBBLE_POP, sprayOrigin, 15,
                0.3, 0.2, 0.3, 0.1, null, true
        );
        sprayOrigin.getWorld().spawnParticle(
                Particle.BUBBLE, sprayOrigin, 20,
                0.4, 0.2, 0.4, 0.02, null, true
        );
    }

    /**
     * Keeps fall immunity alive until the player is actually back on the ground, rather
     * than expiring on a fixed clock - a flat timeout either has to be long enough to
     * cover the tallest possible geyser launch (in which case it's pointless dead weight
     * most of the time) or short enough to be safe most of the time (in which case a
     * strong enough launch outlives it and the player takes full fall damage anyway,
     * which is exactly backwards for an ability whose entire point is a safe launch).
     * {@code onFallDamage()} still does the actual damage cancellation the instant they
     * land; this only decides how long that protection is allowed to stay armed. No tick
     * cap - {@code onQuit}, {@code PlayerLifecycle}'s death handler, and element-switch
     * (via {@code BaseElement.clearEffects}) all clear this flag independently, so there's
     * no path left where it could stay set forever even without one.
     */
    private void grantFallImmunity(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isActiveFor(player)) {
                    cancel();
                    return;
                }

                if (player.isOnGround()) {
                    setActive(player, false);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 2L, 1L); // 2-tick grace so we don't see "grounded" from the takeoff tick itself
    }

    /**
     * Erupts the eruption point using 26.2's native geyser particle. {@code GEYSER} is an
     * emitter that spawns the {@code GEYSER_BASE}, {@code GEYSER_POOF}, and {@code GEYSER_PLUME}
     * particles itself (base/poof burst impulses of 1.5/2.0 baked into the emitter), so this is
     * the same visual vanilla uses for a real potent-sulfur geyser eruption - no manual tick loop
     * needed. {@code waterBlocks} drives the plume height ({@code 5 * waterBlocks} blocks tall).
     */
    private void eruptGeyser(Location origin) {
        origin.getWorld().spawnParticle(Particle.GEYSER, origin, 0, 0, 0, 0, new Particle.Geyser(GEYSER_WATER_BLOCKS));
    }

    /**
     * Trailing particles behind the player while they ride the boost through the air,
     * following {@code AirDashAbility}'s tick-loop pattern.
     */
    private void trailPlayer(Player player) {
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= TRAIL_TICKS || !player.isOnline()) {
                    cancel();
                    return;
                }

                player.getWorld().spawnParticle(Particle.SPLASH, player.getLocation(), 6, 0.25, 0.25, 0.25, 0.02, null, true);
                player.getWorld().spawnParticle(Particle.BUBBLE, player.getLocation(), 4, 0.2, 0.2, 0.2, 0.01, null, true);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!isActiveFor(player)) return;

        event.setCancelled(true);
        setActive(player, false);
    }

    /**
     * Without this, a player who disconnects mid-flight never gets cleared from the
     * active set (see {@code WaterBubbleAbility}'s identical note), leaving them
     * permanently immune to fall damage for the rest of that server's uptime.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        setActive(event.getPlayer(), false);
    }

    @Override
    public String getName() {
        return ChatColor.AQUA + "Geyser";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Erupt a geyser beneath you launching you up, then a second geyser at the apex boosts you forward in the direction you're facing.";
    }
}