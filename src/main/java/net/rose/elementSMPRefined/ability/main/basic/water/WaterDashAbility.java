package net.rose.elementSMPRefined.ability.main.basic.water;

import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.particles.element.WaterParticles;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WaterDashAbility extends BaseAbility implements Listener {

    private static final int SURGE_TICKS = 14;
    private static final double SURGE_SPEED = 1.15;          // blocks/tick held for the whole surge
    private static final double IN_WATER_SPEED_MULT = 1.3;   // the current runs faster in real water
    private static final double STEERING = 0.35;             // 0 = locked to launch direction, 1 = instantly follows look
    private static final double MAX_RISE = 0.10;             // keeps the surge a low glide
    private static final double MIN_FALL = -0.25;            // gentle sink so you don't float forever
    private static final int WALL_CHECK_AFTER_TICKS = 3;     // grace so launch velocity can register
    private static final double WALL_STOP_SPEED = 0.12;      // horizontal speed under which we call it a wall hit
    private static final double CANCEL_DECAY = 0.88;         // horizontal speed kept each tick after a cancel (higher = slower slow-down)
    private static final double CANCEL_FLOOR_SPEED = 0.30;   // slow-down stops here, never reaches 0; normal movement takes over
    private static final int CANCEL_MAX_TICKS = 30;          // hard cap on the slow-down
    private static final double GRAVITY = 0.08;              // per-tick gravity used while we drive the velocity ourselves

    private final ElementSMPRefined plugin;

    /** Players currently mid-surge. Removing a player from here is what stops their dash. */
    private final Set<UUID> surging = new HashSet<>();

    /** Horizontal velocity the surge last applied to each player - what a cancel slows down from. */
    private final Map<UUID, Vector> lastSurgeVelocity = new HashMap<>();

    public WaterDashAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("water_dash", ElementType.WATER, 1, Constants.Cooldown.ABILITY_1_SECONDS, 1, configManager);
        this.plugin = (ElementSMPRefined) plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * True only while the surge itself is running - NOT during the landing-immunity window after it,
     * which isActiveFor() also covers. WaterElement uses this for canCancelAbility1.
     */
    public boolean isSurging(Player player) {
        return surging.contains(player.getUniqueId());
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        // Pressed again mid-surge: stop the dash. (WaterElement lets this through without a cooldown
        // check, and the cooldown from the original cast keeps running.)
        if (isSurging(player)) {
            cancelSurge(player);
            return true;
        }

        Location start = player.getLocation();

        Vector launchLook = horizontalLook(player);
        final Vector heading = launchLook.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : launchLook;

        // Fall immunity for the duration of the surge and the landing right after it.
        setActive(player, true);
        surging.add(player.getUniqueId());
        player.setFallDistance(0f);

        WaterParticles.surgeBurst(start);
        start.getWorld().playSound(start, Sound.ENTITY_DOLPHIN_JUMP, 1.2f, 0.8f);
        start.getWorld().playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.2f);

        new BukkitRunnable() {
            int ticks = 0;
            Vector current = heading.clone().normalize();

            @Override
            public void run() {
                // Cancelled mid-dash (pressed again): cancelSurge() already wound everything down.
                if (!surging.contains(player.getUniqueId())) {
                    cancel();
                    return;
                }

                if (!player.isOnline() || player.isDead() || !isActiveFor(player)) {
                    surging.remove(player.getUniqueId());
                    finish(player);
                    cancel();
                    return;
                }

                if (ticks >= SURGE_TICKS) {
                    surging.remove(player.getUniqueId());
                    finish(player);
                    cancel();
                    return;
                }

                Vector velocity = player.getVelocity();

                // Wall hit: after the grace window, if we're barely moving sideways, stop.
                if (ticks >= WALL_CHECK_AFTER_TICKS) {
                    double horizontalSpeed = Math.hypot(velocity.getX(), velocity.getZ());
                    if (horizontalSpeed < WALL_STOP_SPEED) {
                        WaterParticles.surgeImpact(player.getLocation().add(0, 0.9, 0));
                        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1.0f, 1.0f);
                        surging.remove(player.getUniqueId());
                        finish(player);
                        cancel();
                        return;
                    }
                }

                // Steer: ease the current heading toward wherever the player is looking.
                Vector look = horizontalLook(player);
                if (look.lengthSquared() > 1.0E-4) {
                    current = current.clone().multiply(1.0 - STEERING).add(look.multiply(STEERING));
                    if (current.lengthSquared() < 1.0E-4) {
                        current = look.clone();
                    }
                    current.normalize();
                }

                boolean inWater = player.getLocation().getBlock().getType() == Material.WATER;
                double speed = inWater ? SURGE_SPEED * IN_WATER_SPEED_MULT : SURGE_SPEED;

                // Low glide: keep gravity, but clamp rise so it never turns into a jump.
                double y = Math.max(MIN_FALL, Math.min(velocity.getY(), MAX_RISE));
                if (player.isOnGround()) {
                    y = 0.0;
                }

                Vector applied = new Vector(current.getX() * speed, y, current.getZ() * speed);
                player.setVelocity(applied);
                lastSurgeVelocity.put(player.getUniqueId(), new Vector(applied.getX(), 0, applied.getZ()));
                player.setFallDistance(0f);

                WaterParticles.surgeWake(player.getLocation(), current);

                if (ticks % 4 == 0) {
                    player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 0.6f, 1.4f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /**
     * Stops a surge in progress: the horizontal speed bleeds off gradually (CANCEL_DECAY per tick)
     * down to CANCEL_FLOOR_SPEED, then control is handed back to normal movement. The velocity is
     * never forced to zero, so the player eases out instead of freezing.
     */
    private void cancelSurge(Player player) {
        UUID id = player.getUniqueId();
        if (!surging.remove(id)) return;

        Vector start = lastSurgeVelocity.get(id);
        if (start == null) {
            Vector v = player.getVelocity();
            start = new Vector(v.getX(), 0, v.getZ());
        }

        final double startSpeed = Math.hypot(start.getX(), start.getZ());
        final double dirX = startSpeed > 1.0E-4 ? start.getX() / startSpeed : 0.0;
        final double dirZ = startSpeed > 1.0E-4 ? start.getZ() / startSpeed : 0.0;
        final double[] speed = {startSpeed};
        final double[] vy = {Math.min(player.getVelocity().getY(), 0.0)};

        player.setFallDistance(0f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1.2f);
        finish(player);

        // Already at or below the floor (or no direction): nothing to slow down, let normal movement take over.
        if (startSpeed <= CANCEL_FLOOR_SPEED) {
            return;
        }

        // We drive the velocity ourselves while it slows down, so gravity is applied by hand
        // (otherwise a stale vertical value would make an airborne player hover).
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                // Gone, dead, or started a fresh dash: leave them alone.
                if (!player.isOnline() || player.isDead() || surging.contains(id) || ticks >= CANCEL_MAX_TICKS) {
                    cancel();
                    return;
                }

                // Decay gradually, but never below the floor.
                speed[0] = Math.max(speed[0] * CANCEL_DECAY, CANCEL_FLOOR_SPEED);
                boolean reachedFloor = speed[0] <= CANCEL_FLOOR_SPEED;

                double y;
                if (player.isOnGround()) {
                    vy[0] = 0.0;
                    y = 0.0;
                } else {
                    vy[0] = (vy[0] - GRAVITY) * 0.98;
                    y = vy[0];
                }

                player.setVelocity(new Vector(dirX * speed[0], y, dirZ * speed[0]));
                player.setFallDistance(0f);

                // Floor reached: stop driving the velocity. No zeroing, normal friction takes it from here.
                if (reachedFloor) {
                    cancel();
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finish(Player player) {
        lastSurgeVelocity.remove(player.getUniqueId());

        if (player.isOnline()) {
            WaterParticles.surgeEnd(player.getLocation().add(0, 0.5, 0));
        }

        // Keep immunity armed until they touch down, then clear it (onFallDamage clears it too).
        new BukkitRunnable() {
            int waited = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !isActiveFor(player) || player.isOnGround() || waited >= 100) {
                    setActive(player, false);
                    cancel();
                    return;
                }
                waited++;
            }
        }.runTaskTimer(plugin, 2L, 1L);
    }

    private Vector horizontalLook(Player player) {
        Vector dir = player.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() > 1.0E-4) {
            dir.normalize();
        }
        return dir;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!isActiveFor(player)) return;

        event.setCancelled(true);
        setActive(player, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        surging.remove(event.getPlayer().getUniqueId());
        lastSurgeVelocity.remove(event.getPlayer().getUniqueId());
        setActive(event.getPlayer(), false);
    }

    @Override
    public String getName() {
        return ChatColor.AQUA + "Tidal Surge";
    }

    @Override
    public String getDescription() {
        return "Cast a surge curving wherever you look. Press again to stop mid-dash.";
    }
}