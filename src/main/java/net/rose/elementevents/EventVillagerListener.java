package net.rose.elementevents;

import net.rose.elementSMPRefined.ElementSMPRefined;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

/** Keeps event villagers unkillable, frozen in place, and serving fresh trades from the config. */
public class EventVillagerListener implements Listener {

    private final CustomEventVilager villagers;

    public EventVillagerListener(ElementSMPRefined plugin, CustomEventVilager villagers) {
        this.villagers = villagers;
    }

    /**
     * Cancels every kind of damage - players (including creative), mobs, explosions, fire,
     * lightning, void. Subclasses such as EntityDamageByEntityEvent are covered too.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (villagers.isEventVillager(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** No lightning -> witch, no zombie conversion. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTransform(EntityTransformEvent event) {
        if (villagers.isEventVillager(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCareerChange(VillagerCareerChangeEvent event) {
        if (villagers.isEventVillager(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (villagers.isEventVillager(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!villagers.isEventVillager(event.getRightClicked())) return;
        Villager villager = (Villager) event.getRightClicked();

        // No renaming or leashing them.
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        if (held.getType() == Material.NAME_TAG || held.getType() == Material.LEAD) {
            event.setCancelled(true);
            return;
        }

        // Pull the latest trades for this villager's type from event_villagers.yml right before the trade window opens.
        // Skipped while someone else is mid-trade so their open window isn't swapped out.
        if (!villager.isTrading()) {
            villager.setRecipes(villagers.buildRecipes(villagers.getType(villager)));
        }
    }
}
