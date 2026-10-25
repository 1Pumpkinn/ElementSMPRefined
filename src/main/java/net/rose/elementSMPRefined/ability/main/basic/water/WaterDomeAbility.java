package net.rose.elementSMPRefined.ability.main.basic.water;

import net.rose.elementSMPRefined.particles.element.WaterParticles;
import net.rose.elementSMPRefined.core.API.ability.BaseAbility;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.items.builder.ModelItems;
import net.rose.elementSMPRefined.managers.ConfigManager;
import net.rose.elementSMPRefined.managers.TrustManager;
import net.rose.elementSMPRefined.util.damage.AbilityDamage;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
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
import java.util.HashMap;
import java.util.Objects;
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
 *       player while they're inside the dome is cancelled - except damage from element
 *       abilities ({@link AbilityDamage}), which still goes through.</li>
 * </ul>
 * Non-entity damage (fall, fire, drowning, void, ...) is deliberately NOT blocked
 * <p>
 */
public class WaterDomeAbility extends BaseAbility implements Listener {

    private static final int MAX_DURATION_TICKS = 200; // 10 seconds
    private static final double DOME_RADIUS = 5.0;
    private static final double PUSH_STRENGTH = 0.4;    // blocks/tick shoved at intruders
    /**
     * The shell is built from ItemDisplays wearing this resource-pack model: a full 16x16x16 cube
     * with the vanilla animated water texture, tinted water-blue (see assets/elementsmp/items and
     * models/item/water_cube.json). Real or fake water blocks can't be drawn as full cubes - clients
     * shorten and slope them - but a model can.
     */
    private static final NamespacedKey CUBE_MODEL =
            Objects.requireNonNull(NamespacedKey.fromString("elementsmp:water_cube"));

    /** Block offsets (relative to the dome centre block) that make up the upper-hemisphere shell. */
    private static final List<int[]> SHELL_OFFSETS = buildShellOffsets();

    /**
     * The shell is exactly one block thick: a block belongs to it when it's inside the dome
     * radius and at least one of its six face-neighbours is outside. (A radial thickness
     * test used to make the shell two blocks thick along the axes, which left an extra layer
     * of water on top of the dome and on the sides at the middle.)
     */
    private static List<int[]> buildShellOffsets() {
        List<int[]> offsets = new ArrayList<>();
        int r = (int) Math.ceil(DOME_RADIUS);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = 0; dy <= r; dy++) { // dy >= 0: dome only, nothing under the floor
                for (int dz = -r; dz <= r; dz++) {
                    if (!insideDome(dx, dy, dz)) continue;
                    if (!insideDome(dx + 1, dy, dz) || !insideDome(dx - 1, dy, dz)
                            || !insideDome(dx, dy + 1, dz) || !insideDome(dx, dy - 1, dz)
                            || !insideDome(dx, dy, dz + 1) || !insideDome(dx, dy, dz - 1)) {
                        offsets.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        return offsets;
    }

    /**
     * Shell radius is half a block smaller than the barrier radius: with the full radius the
     * axis points (top, and the four sides at the middle) were lone single blocks poking out
     * of the curve. This way the top and sides are flat like the rest of the surface.
     */
    private static boolean insideDome(int dx, int dy, int dz) {
        double r = DOME_RADIUS - 0.5;
        return dx * dx + dy * dy + dz * dz <= r * r;
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
            return false;
        }

        Location center = player.getLocation().clone();
        World world = center.getWorld();

        SoundUtils.playTo(player, SoundUtils.Element.WATER);
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);
        world.playSound(center, Sound.ITEM_BUCKET_EMPTY, 1.2f, 0.8f);
        WaterParticles.domeCast(world, center, DOME_RADIUS);

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
            // Cheap type check first: the box is full of items, XP orbs and display entities
            // (including the Ice Spikes / Icicle displays) that the barrier never touches.
            if (!(e instanceof LivingEntity) && !(e instanceof Projectile)) continue;
            if (e instanceof ArmorStand) continue;
            if (isAllowed(state, e)) continue;

            if (e instanceof Projectile projectile) {
                if (projectile.getLocation().distanceSquared(center) <= r2) {
                    WaterParticles.domeProjectileBlocked(world, projectile.getLocation());
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

            WaterParticles.domePush(world, body);
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

    /** Packs a block position into one long (26 bits x, 26 bits z, 12 bits y), so the shell needs no Location objects. */
    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFFL);
    }

    private static int unpackX(long key) {
        return (int) (key >> 38);
    }

    private static int unpackZ(long key) {
        return (int) ((key << 26) >> 38);
    }

    private static int unpackY(long key) {
        return (int) (key & 0xFFFL) - 2048;
    }

    /**
     * Works out which blocks of the shell can actually hold water (air / replaceable only -
     * terrain is left alone) for the dome's current centre block.
     */
    private Set<Long> computeShell(DomeState state) {
        World world = state.center.getWorld();
        int cx = state.center.getBlockX();
        int cy = state.center.getBlockY();
        int cz = state.center.getBlockZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        Set<Long> shell = new HashSet<>(SHELL_OFFSETS.size() * 2);

        // The shell is ~400 blocks spread over a handful of chunks: remember the last chunk's
        // loaded state instead of asking the world for every block.
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;
        boolean lastLoaded = false;

        for (int[] o : SHELL_OFFSETS) {
            int x = cx + o[0];
            int y = cy + o[1];
            int z = cz + o[2];
            if (y < minY || y >= maxY) continue;

            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
                // never force a chunk load for a particle-grade effect
                lastLoaded = world.isChunkLoaded(chunkX, chunkZ);
            }
            if (!lastLoaded) continue;

            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.LAVA) continue;
            if (block.isEmpty() || block.isReplaceable()) {
                shell.add(pack(x, y, z));
            }
        }
        return shell;
    }

    /**
     * Moves the shell to the dome's current centre block: cubes appear where the dome now is and
     * the ones it just left are removed - only the difference is touched, not the whole shell.
     */
    private void updateShell(DomeState state) {
        Set<Long> next = computeShell(state);
        World world = state.center.getWorld();

        for (java.util.Iterator<Map.Entry<Long, ItemDisplay>> it = state.cubes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, ItemDisplay> entry = it.next();
            if (!next.contains(entry.getKey())) {
                entry.getValue().remove();
                it.remove();
            }
        }
        for (long key : next) {
            ItemDisplay existing = state.cubes.get(key);
            if (existing != null && existing.isValid()) continue;
            state.cubes.put(key, spawnCube(world, unpackX(key), unpackY(key), unpackZ(key)));
        }

        state.shellX = state.center.getBlockX();
        state.shellY = state.center.getBlockY();
        state.shellZ = state.center.getBlockZ();
    }

    /** One full water-textured cube, centred on the given block. */
    private static ItemDisplay spawnCube(World world, int x, int y, int z) {
        Location centre = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        // The base material is irrelevant - the item_model component replaces its model entirely.
        var modelItem = ModelItems.of(CUBE_MODEL);
        return world.spawn(centre, ItemDisplay.class, d -> {
            d.setItemStack(modelItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2.0f);
            d.setPersistent(false);
            d.setInvulnerable(true);
        });
    }

    /** Removes every cube of a dome. */
    private void removeCubes(DomeState state) {
        for (ItemDisplay cube : state.cubes.values()) {
            cube.remove();
        }
        state.cubes.clear();
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

        // Element abilities (TrueDamage / AbilityDamage) still hurt people inside the dome;
        // only plain player attacks, mobs and projectiles are blocked.
        if (AbilityDamage.isAbilityDamage(victim)) return;

        for (DomeState state : domes.values()) {
            if (!state.center.getWorld().equals(victim.getWorld())) continue;
            if (victim.getLocation().distanceSquared(state.center) > DOME_RADIUS * DOME_RADIUS) continue;
            if (!isAllowed(state, victim)) continue; // only the caster / trusted players are protected

            // A hit from the caster, a trusted player, or a tamed pet isn't what the dome is for.
            if (isAllowed(state, event.getDamager())) return;

            event.setCancelled(true);
            WaterParticles.domeHitBlocked(victim.getLocation());
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
        removeCubes(state);

        World world = state.center.getWorld();
        Location mid = state.center.clone().add(0, DOME_RADIUS / 2, 0);
        WaterParticles.domeBreak(world, mid, DOME_RADIUS);
        world.playSound(state.center, Sound.ENTITY_PLAYER_HURT_DROWN, 1.0f, 0.8f);

        if (notify) {
            Player owner = plugin.getServer().getPlayer(ownerId);
            if (owner != null) {
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
            // BaseElement.clearEffects (element change, death) deactivates through here. Just dropping
            // the flag made the tick task bail out without restoring the fake water and left the
            // dome registered, so onDamage kept protecting the owner/trusted players there forever.
            popDome(player.getUniqueId(), false);
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
        return "Raises a dome of water around you that follows you and blocks players, mobs and projectiles.";
    }

    private static class DomeState {
        final UUID owner;
        Location center; // follows the caster
        final TrustManager trust;
        final Map<Long, ItemDisplay> cubes = new HashMap<>(); // packed block position (see pack()) -> its cube
        int shellX = Integer.MIN_VALUE, shellY = Integer.MIN_VALUE, shellZ = Integer.MIN_VALUE; // centre block the shell was last built for
        BukkitTask task;

        DomeState(UUID owner, Location center, TrustManager trust) {
            this.owner = owner;
            this.center = center;
            this.trust = trust;
        }
    }
}