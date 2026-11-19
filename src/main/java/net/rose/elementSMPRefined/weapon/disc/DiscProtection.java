package net.rose.elementSMPRefined.weapon.disc;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementevents.mythic.MythicBookProtection;

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
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
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
 * Only one throwing disc exists, so it must never be destroyed. Same approach as
 * {@link MythicBookProtection} for the Life/Death books: a dropped disc is invulnerable, never
 * despawns, can't be picked up by mobs, is lifted out of the void, and is dropped again if it is
 * ever removed anyway. It also can't be used up as a crafting, anvil or grindstone ingredient,
 * and can't be cloned with a creative-mode middle click.
 */
final class DiscProtection implements Listener {
    private static final Set<String> DESTROY_CAUSES =
            Set.of("DEATH", "DESPAWN", "EXPLODE", "OUT_OF_WORLD");
    private static final long CHECK_PERIOD_TICKS = 5L;
    private static final int VOID_RESCUE_DEPTH = 8;

    private final ElementSMPRefined plugin;
    private final Map<UUID, Item> tracked = new HashMap<>();
    private BukkitTask voidTask;

    DiscProtection(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    void start() {
        for (World world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (isDisc(item)) protectAndTrack(item);
            }
        }
        voidTask =
                plugin.getTaskScheduler()
                        .runTimer(this::checkTracked, CHECK_PERIOD_TICKS, CHECK_PERIOD_TICKS);
    }

    void stop() {
        if (voidTask != null) {
            voidTask.cancel();
            voidTask = null;
        }
        tracked.clear();
    }

    private boolean isDisc(Item item) {
        return ThrowingDisc.isDisc(plugin, item.getItemStack());
    }

    private void protectAndTrack(Item item) {
        MythicBookProtection.protect(item);
        tracked.put(item.getUniqueId(), item);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(ItemSpawnEvent event) {
        if (isDisc(event.getEntity())) protectAndTrack(event.getEntity());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item item && isDisc(item)) protectAndTrack(item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && isDisc(item)) {
            event.setCancelled(true);
            item.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Item item && isDisc(item)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDespawn(ItemDespawnEvent event) {
        if (isDisc(event.getEntity())) event.setCancelled(true);
    }

    /** Last resort: the disc entity was removed for a destructive reason, so drop it again. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;
        tracked.remove(item.getUniqueId());
        if (!isDisc(item) || !DESTROY_CAUSES.contains(event.getCause().name())) return;

        Location where = item.getLocation().clone();
        ItemStack copy = item.getItemStack().clone();
        plugin.getTaskScheduler()
                .runLater(
                        () -> {
                            Location spot = safeSpot(where);
                            spot.getWorld()
                                    .dropItem(
                                            spot,
                                            copy,
                                            (Item dropped) -> {
                                                dropped.setVelocity(new Vector());
                                                MythicBookProtection.protect(dropped);
                                            });
                        },
                        1L);
    }

    private void checkTracked() {
        tracked.values().removeIf(item -> !item.isValid());
        for (Item item : tracked.values().toArray(new Item[0])) {
            World world = item.getWorld();
            if (item.getLocation().getY() < world.getMinHeight() - VOID_RESCUE_DEPTH) {
                item.setVelocity(new Vector());
                item.teleport(safeSpot(item.getLocation()));
            }
        }
    }

    /** Solid ground in the same column, or the world spawn if the column is empty (the void). */
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack stack : event.getInventory().getMatrix()) {
            if (ThrowingDisc.isDisc(plugin, stack)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Combining in an anvil would use up the disc when it is the sacrificed item. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (ThrowingDisc.isDisc(plugin, event.getInventory().getSecondItem())) {
            event.setResult(null);
        }
    }

    /** Same for the grindstone: a disc plus another item would merge the disc away. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        ItemStack upper = event.getInventory().getUpperItem();
        ItemStack lower = event.getInventory().getLowerItem();
        boolean both = upper != null && !upper.isEmpty() && lower != null && !lower.isEmpty();
        if (both && (ThrowingDisc.isDisc(plugin, upper) || ThrowingDisc.isDisc(plugin, lower))) {
            event.setResult(null);
        }
    }

    /** Middle-clicking the disc in creative would hand out a second copy. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreativeClone(InventoryCreativeEvent event) {
        if (event.getAction() == InventoryAction.CLONE_STACK
                && ThrowingDisc.isDisc(plugin, event.getCursor())) {
            event.setCancelled(true);
        }
    }
}
