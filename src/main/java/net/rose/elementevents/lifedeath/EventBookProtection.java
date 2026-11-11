package net.rose.elementevents.lifedeath;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementevents.lifedeath.EventBook.Kind;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps the Life/Death books from ever being destroyed once they are dropped in the world.
 * <ul>
 *   <li><b>Burning / lava / cactus / explosions / any damage:</b> the item entity is
 *       invulnerable and every damage and combust event on it is cancelled.</li>
 *   <li><b>Despawning:</b> it gets an unlimited lifetime ({@link Item#setUnlimitedLifetime}) and
 *       {@link ItemDespawnEvent} is cancelled.</li>
 *   <li><b>The void:</b> the void ignores invulnerability and silently discards entities far
 *       below the world, so a repeating task lifts any book that falls out of the world back onto
 *       solid ground.</li>
 *   <li><b>Anything else that still removes it</b> (death, despawn, explosion, out of world): the
 *       book is dropped again, protected, at a safe spot where it was.</li>
 *   <li>Mobs can't pick it up, and it can't be used as a crafting ingredient (the other way a
 *       book could be consumed).</li>
 * </ul>
 * Picking it up, hoppers, containers, portals and so on are untouched - it stays a normal item.
 */
public class EventBookProtection implements Listener {

    /** Removal causes that mean "destroyed", as names so a renamed enum constant can't break the build. */
    private static final Set<String> DESTROY_CAUSES =
            Set.of("DEATH", "DESPAWN", "EXPLODE", "OUT_OF_WORLD");

    private static final long CHECK_PERIOD_TICKS = 5L;
    /** Rescue this far below the world bottom - well before the game discards it (64 below). */
    private static final int VOID_RESCUE_DEPTH = 8;

    private final ElementSMPRefined plugin;
    private final Map<UUID, Item> tracked = new HashMap<>();
    private BukkitTask voidTask;

    public EventBookProtection(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    /** Starts the void-rescue task and protects any books already lying in loaded chunks. */
    public void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (isBook(item)) protectAndTrack(item);
            }
        }
        voidTask =
                plugin.getTaskScheduler()
                        .runTimer(this::checkTracked, CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS);
    }

    public void stop() {
        if (voidTask != null) {
            voidTask.cancel();
            voidTask = null;
        }
        tracked.clear();
    }

    /** Makes a dropped book indestructible. Safe to call more than once. */
    public static void protect(Item item) {
        item.setInvulnerable(true);
        item.setUnlimitedLifetime(true);
        item.setCanMobPickup(false);
        item.setFireTicks(0);
    }

    private void protectAndTrack(Item item) {
        protect(item);
        tracked.put(item.getUniqueId(), item);
    }

    private boolean isBook(Item item) {
        return EventBook.kindOf(plugin, item.getItemStack()) != null;
    }

    // ---- protection -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(ItemSpawnEvent e) {
        Item item = e.getEntity();
        if (isBook(item)) protectAndTrack(item);
    }

    /** Books that were lying in a chunk while it was unloaded (or across a restart). */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) {
            if (entity instanceof Item item && isBook(item)) protectAndTrack(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Item item && isBook(item)) {
            e.setCancelled(true);
            item.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent e) {
        if (e.getEntity() instanceof Item item && isBook(item)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDespawn(ItemDespawnEvent e) {
        if (isBook(e.getEntity())) e.setCancelled(true);
    }

    /** Last resort: if the book entity was removed for a destructive reason, drop it again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent e) {
        if (!(e.getEntity() instanceof Item item)) return;
        tracked.remove(item.getUniqueId());
        Kind kind = EventBook.kindOf(plugin, item.getItemStack());
        if (kind == null || !DESTROY_CAUSES.contains(e.getCause().name())) return;

        Location where = item.getLocation().clone();
        ItemStack copy = item.getItemStack().clone();
        plugin.getTaskScheduler()
                .runLater(() -> redrop(copy, safeSpot(where)), 1L);
    }

    /** A book can't be burned up as a crafting ingredient (bookshelves, etc). */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        for (ItemStack stack : e.getInventory().getMatrix()) {
            if (EventBook.kindOf(plugin, stack) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    // ---- void rescue ------------------------------------------------------------------------

    private void checkTracked() {
        tracked.values()
                .removeIf(item -> !item.isValid()); // picked up, unloaded, or removed
        for (Item item : tracked.values().toArray(new Item[0])) {
            World world = item.getWorld();
            if (item.getLocation().getY() < world.getMinHeight() - VOID_RESCUE_DEPTH) {
                Location spot = safeSpot(item.getLocation());
                item.setVelocity(new Vector(0, 0, 0));
                item.teleport(spot);
            }
        }
    }

    private void redrop(ItemStack stack, Location at) {
        at.getWorld()
                .dropItem(
                        at,
                        stack,
                        (Item item) -> {
                            item.setVelocity(new Vector(0, 0, 0));
                            protect(item);
                        });
    }

    /**
     * Solid ground in the same column as {@code from}: scanning down from the top of the world (or
     * below the roof in the Nether) for a floor with two free blocks above it. Falls back to the
     * world spawn when the column is empty - e.g. the void around the End islands.
     */
    private Location safeSpot(Location from) {
        World world = from.getWorld();
        int x = from.getBlockX();
        int z = from.getBlockZ();
        int top =
                world.getEnvironment() == World.Environment.NETHER
                        ? Math.min(world.getMaxHeight() - 1, 120)
                        : world.getMaxHeight() - 1;
        for (int y = top; y > world.getMinHeight(); y--) {
            Block floor = world.getBlockAt(x, y, z);
            if (floor.isPassable() || floor.isLiquid() || floor.getType() == Material.BEDROCK) {
                continue;
            }
            if (world.getBlockAt(x, y + 1, z).isPassable()
                    && world.getBlockAt(x, y + 2, z).isPassable()
                    && !world.getBlockAt(x, y + 1, z).isLiquid()) {
                return new Location(world, x + 0.5, y + 1.25, z + 0.5);
            }
        }
        Location spawn = world.getSpawnLocation();
        return new Location(world, spawn.getX() + 0.5, spawn.getY() + 1.25, spawn.getZ() + 0.5);
    }
}
