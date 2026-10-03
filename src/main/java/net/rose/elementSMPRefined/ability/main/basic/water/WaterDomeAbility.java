package net.rose.elementSMPRefined.ability.main.basic.water;

import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import net.rose.elementSMPRefined.lang.Lang;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Water element's defensive ability: raises a dome of real-looking water blocks
 * around the caster that follows them as they move. The shell is sent to nearby clients as fake block changes (the world
 * itself is never modified, so nothing flows, floods or needs cleaning up - it's just put
 * back to the real blocks when the dome ends). For its 10-second lifetime the dome acts as a barrier against every entity that
 * isn't the caster, a trusted player, or one of the caster's tamed pets:
 * <ul>
 *   <li>Living entities (players and mobs) inside the dome are pushed back out, so nothing
 *       hostile can walk in or stand in it.</li>
 *   <li>Projectiles from non-allies are destroyed the moment they enter the dome.</li>
 *   <li>Any entity damage (melee, explosions, projectiles) dealt to the caster or a trusted
 *       player while they're inside the dome is cancelled.</li>
 * </ul>
 * Non-entity damage (fall, fire, drowning, void, ...) is deliberately NOT blocked
 * <p>
 */
public class WaterDomeAbility extends BaseAbility implements Listener {

    private static final int MAX_DURATION_TICKS = 200; // 10 seconds
    private static final double DOME_RADIUS = 7.0;
    private static final double SHELL_THICKNESS = 1.2;  // blocks, measured radially
    private static final double PUSH_STRENGTH = 0.7;    // blocks/tick shoved at intruders
    private static final int REFRESH_INTERVAL_TICKS = 20; // re-send the shell so late joiners / reverted blocks see it
    private static final double VIEW_DISTANCE = DOME_RADIUS + 80.0;
    private static final BlockData WATER_DATA = Bukkit.createBlockData(Material.WATER);

    /** Block offsets (relative to the dome centre block) that make up the upper-hemisphere shell. */
    private static final List<int[]> SHELL_OFFSETS = buildShellOffsets();

    private static List<int[]> buildShellOffsets() {
        List<int[]> offsets = new ArrayList<>();
        int r = (int) Math.ceil(DOME_RADIUS);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = 0; dy <= r; dy++) { // dy >= 0: dome only, nothing under the floor
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d <= DOME_RADIUS && d >= DOME_RADIUS - SHELL_THICKNESS) {
                        offsets.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        return offsets;
    }

    private final ElementSMPRefined plugin;
    private final Set<UUID> activeUsers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, DomeState> domes = new ConcurrentHashMap<>();

    public WaterDomeAbility(JavaPlugin plugin, ConfigManager configManager) {
        super("water_dome", ElementType.WATER, 2, 30, 2, configManager);
        this.plugin = (ElementSMPRefined) plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        if (isActiveFor(player)) {
            player.sendMessage(Lang.WATER_BUBBLE_YOUR_WATER_BUBBLE_IS_ALREADY);
            return false;
        }

        Location center = player.getLocation().clone();
        World world = center.getWorld();

        SoundUtils.playTo(player, SoundUtils.Element.WATER);
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);
        world.playSound(center, Sound.ITEM_BUCKET_EMPTY, 1.2f, 0.8f);
        world.spawnParticle(Particle.SPLASH, center.clone().add(0, 0.5, 0), 60,
                DOME_RADIUS / 2, 0.3, DOME_RADIUS / 2, 0.1, null, true);

        setActive(player, true);

        DomeState state = new DomeState(player.getUniqueId(), center, context.getTrustManager());
        updateShell(state);
        domes.put(player.getUniqueId(), state);

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !isActiveFor(player)) {
                    cancel();
                    return;
                }

                if (ticks >= MAX_DURATION_TICKS) {
                    popDome(player.getUniqueId(), true);
                    return;
                }

                if (!player.getWorld().equals(state.center.getWorld())) {
                    popDome(player.getUniqueId(), true); // changed dimension - the dome can't come along
                    return;
                }

                followOwner(state, player);
                enforceBarrier(state);

                if (ticks % REFRESH_INTERVAL_TICKS == 0) {
                    sendShell(state);
                }
                ticks++;
            }
        };
        state.task = task.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    /** Shoves non-allied living entities out of the dome and destroys non-allied projectiles inside it. */
    private void enforceBarrier(DomeState state) {
        Location center = state.center;
        World world = center.getWorld();
        double r2 = DOME_RADIUS * DOME_RADIUS;
        double search = DOME_RADIUS + 2.0;

        for (Entity e : world.getNearbyEntities(center, search, search, search)) {
            if (isAllowed(state, e)) continue;

            if (e instanceof Projectile projectile) {
                if (projectile.getLocation().distanceSquared(center) <= r2) {
                    world.spawnParticle(Particle.SPLASH, projectile.getLocation(), 10, 0.2, 0.2, 0.2, 0.05, null, true);
                    world.playSound(projectile.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1.5f);
                    projectile.remove();
                }
                continue;
            }

            if (!(e instanceof LivingEntity living) || e instanceof ArmorStand) continue;

            Location body = living.getLocation().add(0, living.getHeight() / 2.0, 0);
            Vector away = body.toVector().subtract(center.toVector());
            double distSq = away.lengthSquared();
            if (distSq >= r2) continue; // outside the dome - nothing to do

            if (distSq < 0.0001) {
                away = new Vector(1, 0, 0); // dead centre: any direction out will do
            }
            away.setY(0); // push out sideways; the dome is a wall, not a launcher
            if (away.lengthSquared() < 0.0001) {
                away = new Vector(1, 0, 0);
            }
            Vector push = away.normalize().multiply(PUSH_STRENGTH);
            push.setY(living.isOnGround() ? 0.15 : living.getVelocity().getY());
            living.setVelocity(push);

            world.spawnParticle(Particle.BUBBLE_POP, body, 4, 0.3, 0.4, 0.3, 0.05, null, true);
        }
    }

    /**
     * Keeps the dome centred on the caster. Horizontal movement is followed every tick; the
     * dome's height only follows while the caster is standing on something, so jumping or
     * falling doesn't make the whole shell bob up and down (or chase a fall off a cliff).
     * The water blocks are only redone when the centre actually crosses into a new block.
     */
    private void followOwner(DomeState state, Player owner) {
        Location pos = owner.getLocation();
        double y = (owner.isOnGround() || owner.isInWater()) ? pos.getY() : state.center.getY();
        state.center = new Location(pos.getWorld(), pos.getX(), y, pos.getZ());

        if (state.center.getBlockX() != state.shellX
                || state.center.getBlockY() != state.shellY
                || state.center.getBlockZ() != state.shellZ) {
            updateShell(state);
        }
    }

    /**
     * Works out which blocks of the shell can actually hold water (air / replaceable only -
     * terrain is left alone) for the dome's current centre block.
     */
    private Set<Location> computeShell(DomeState state) {
        World world = state.center.getWorld();
        int cx = state.center.getBlockX();
        int cy = state.center.getBlockY();
        int cz = state.center.getBlockZ();

        Set<Location> shell = new HashSet<>();
        for (int[] o : SHELL_OFFSETS) {
            int x = cx + o[0];
            int y = cy + o[1];
            int z = cz + o[2];
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue; // never force a chunk load for a particle-grade effect

            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.LAVA) continue;
            if (block.isEmpty() || block.isReplaceable()) {
                shell.add(block.getLocation());
            }
        }
        return shell;
    }

    /**
     * Moves the shell to the dome's current centre block: new water goes up where the dome
     * now is, and blocks it just left are put back to their real state - only the difference
     * is sent, not the whole shell.
     */
    private void updateShell(DomeState state) {
        Set<Location> next = computeShell(state);
        Set<Location> old = state.shellBlocks;

        World world = state.center.getWorld();
        double maxSq = VIEW_DISTANCE * VIEW_DISTANCE;

        for (Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(state.center) > maxSq) continue;

            for (Location loc : next) {
                if (!old.contains(loc)) {
                    viewer.sendBlockChange(loc, WATER_DATA);
                }
            }
            for (Location loc : old) {
                if (!next.contains(loc)) {
                    viewer.sendBlockChange(loc, loc.getBlock().getBlockData());
                }
            }
        }

        state.shellBlocks = next;
        state.shellX = state.center.getBlockX();
        state.shellY = state.center.getBlockY();
        state.shellZ = state.center.getBlockZ();
    }

    /** Shows the water shell to every nearby player as client-side block changes. */
    private void sendShell(DomeState state) {
        World world = state.center.getWorld();
        double maxSq = VIEW_DISTANCE * VIEW_DISTANCE;

        for (Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(state.center) > maxSq) continue;
            for (Location loc : state.shellBlocks) {
                viewer.sendBlockChange(loc, WATER_DATA);
            }
        }
    }

    /** Puts the real blocks back on every client that might have been shown the shell. */
    private void restoreShell(DomeState state) {
        World world = state.center.getWorld();
        for (Player viewer : world.getPlayers()) {
            for (Location loc : state.shellBlocks) {
                viewer.sendBlockChange(loc, loc.getBlock().getBlockData());
            }
        }
        state.shellBlocks.clear();
    }

    /** Entities the dome lets through (and protects): the caster, their trusted players, and their tamed pets. */
    private boolean isAllowed(DomeState state, Entity e) {
        if (e.getUniqueId().equals(state.owner)) return true;
        if (e instanceof Player other && state.trust.isTrusted(state.owner, other.getUniqueId())) return true;
        if (e instanceof Tameable pet && state.owner.equals(pet.getOwnerUniqueId())) return true;
        if (e instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return isAllowed(state, shooter);
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (domes.isEmpty()) return;

        for (DomeState state : domes.values()) {
            if (!state.center.getWorld().equals(victim.getWorld())) continue;
            if (victim.getLocation().distanceSquared(state.center) > DOME_RADIUS * DOME_RADIUS) continue;
            if (!isAllowed(state, victim)) continue; // only the caster / trusted players are protected

            // A hit from the caster, a trusted player, or a tamed pet isn't what the dome is for.
            if (isAllowed(state, event.getDamager())) return;

            event.setCancelled(true);
            victim.getWorld().spawnParticle(Particle.BUBBLE_POP, victim.getLocation().add(0, 1, 0), 10,
                    0.4, 0.5, 0.4, 0.05, null, true);
            victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.6f);
            return;
        }
    }

    private void popDome(UUID ownerId, boolean notify) {
        activeUsers.remove(ownerId);
        DomeState state = domes.remove(ownerId);
        if (state == null) return;

        if (state.task != null) {
            state.task.cancel();
        }
        restoreShell(state);

        World world = state.center.getWorld();
        Location mid = state.center.clone().add(0, DOME_RADIUS / 2, 0);
        world.spawnParticle(Particle.BUBBLE_POP, mid, 60, DOME_RADIUS / 2, DOME_RADIUS / 3, DOME_RADIUS / 2, 0.15, null, true);
        world.playSound(state.center, Sound.ENTITY_PLAYER_HURT_DROWN, 1.0f, 0.8f);

        if (notify) {
            Player owner = plugin.getServer().getPlayer(ownerId);
            if (owner != null) {
                owner.sendMessage(Lang.WATER_BUBBLE_FADED);
            }
        }
    }

    @Override
    public boolean isActiveFor(Player player) {
        return activeUsers.contains(player.getUniqueId());
    }

    @Override
    public void setActive(Player player, boolean active) {
        if (active) {
            activeUsers.add(player.getUniqueId());
        } else {
            activeUsers.remove(player.getUniqueId());
        }
    }

    public void clearEffects(Player player) {
        if (isActiveFor(player)) {
            popDome(player.getUniqueId(), false);
        }
    }

    public void onPlayerQuit(UUID playerUuid) {
        popDome(playerUuid, false);
    }

    /**
     * Without this, a player who disconnects while their dome is up never gets cleared from
     * {@link #domes}/{@link #activeUsers} (the tick task's offline check only cancels itself,
     * it doesn't touch the maps) - so {@link #isActiveFor(Player)} stays permanently true for
     * them and Water Dome becomes unusable for the rest of that server's uptime, even after
     * they rejoin.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onPlayerQuit(event.getPlayer().getUniqueId());
    }

    @Override
    public String getName() {
        return ChatColor.AQUA + "Water Dome";
    }

    @Override
    public String getDescription() {
        return ChatColor.GRAY + "Raises a large dome of water that follows you for 10 seconds and blocks players, mobs and projectiles";
    }

    private static class DomeState {
        final UUID owner;
        Location center; // follows the caster
        final TrustManager trust;
        Set<Location> shellBlocks = new HashSet<>();
        int shellX = Integer.MIN_VALUE, shellY = Integer.MIN_VALUE, shellZ = Integer.MIN_VALUE; // centre block the shell was last built for
        BukkitTask task;

        DomeState(UUID owner, Location center, TrustManager trust) {
            this.owner = owner;
            this.center = center;
            this.trust = trust;
        }
    }
}