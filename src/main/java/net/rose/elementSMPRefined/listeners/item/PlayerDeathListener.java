package net.rose.elementSMPRefined.listeners.item;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.altar.ForgeRecipe;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.items.recipes.ForgedUpgraderItem;
import net.rose.elementSMPRefined.items.recipes.UpgraderItem;
import net.rose.elementSMPRefined.managers.ElementManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public class PlayerDeathListener implements Listener {

    private static final long REAPPLY_DELAY_TICKS = 1L;

    private final ElementSMPRefined plugin;
    private final ElementManager elements;

    public PlayerDeathListener(ElementSMPRefined plugin, ElementManager elements) {
        this.plugin = plugin;
        this.elements = elements;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerData data = elements.data(player.getUniqueId());
        if (data == null) {
            return;
        }

        ElementType element = data.getCurrentElement();
        if (element == null) {
            return;
        }

        // With keepInventory the player keeps their items, so they must keep their level and element too
        if (event.getKeepInventory()) {
            return;
        }

        // Read the level up front: it is looked up per element, so it must be read before any rollback
        int level = data.getUpgradeLevel(element);

        boolean changed = false;

        // 1) Advanced element -> drop a Forged Upgrader and fall back to the basic element
        changed |= dropForgedUpgraderAndRevert(event, player, element);

        // 2) Upgrade level -> drop the matching Upgrader and lose one level.
        // Read from the element the player holds now, which may be the basic one after step 1.
        changed |= dropUpgraderAndLowerLevel(event, player, data, level);

        if (changed) {
            plugin.getDataStore().save(data);
            scheduleUpsideReapply(player);
        }
    }

    /**
     * If {@code element} is an advanced element, adds a Forged Upgrader to the drops and moves the
     * player back to its basic element (the upgrade level is kept by {@code assignBasicElement}).
     *
     * @return true if the player was reverted
     */
    private boolean dropForgedUpgraderAndRevert(PlayerDeathEvent event, Player player, ElementType element) {
        Optional<ForgeRecipe> forge = ForgeRecipe.forTarget(element);
        if (forge.isEmpty()) {
            return false;
        }

        ItemStack forged = ForgedUpgraderItem.make(plugin);
        if (forged == null) {
            // Item couldn't be created, so don't strip the element for nothing
            plugin.getLogger().warning("Could not create Forged Upgrader (player " + player.getName() + ")");
            return false;
        }

        event.getDrops().add(forged);
        elements.assignBasicElement(player, forge.get().base());
        return true;
    }

    private boolean dropUpgraderAndLowerLevel(PlayerDeathEvent event, Player player, PlayerData data, int level) {
        if (level <= 0) {
            return false;
        }

        ItemStack drop = UpgraderItem.make(plugin, level);
        if (drop == null) {
            // Item couldn't be created, so don't strip the level for nothing
            plugin.getLogger().warning("Could not create upgrader for level " + level
                    + " (player " + player.getName() + ")");
            return false;
        }

        event.getDrops().add(drop);

        // The current element may have just been swapped to the basic one, so use whatever is active now
        ElementType current = data.getCurrentElement();
        data.setUpgradeLevel(current, level - 1);
        return true;
    }

    private void scheduleUpsideReapply(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                elements.applyUpsides(player);
            }
        }, REAPPLY_DELAY_TICKS);
    }
}
