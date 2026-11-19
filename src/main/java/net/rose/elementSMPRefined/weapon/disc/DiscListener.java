package net.rose.elementSMPRefined.weapon.disc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;

import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmithingInventory;

/** Throwing, crafting (one copy only) and the netherite upgrade of the disc. */
final class DiscListener implements Listener {
    private final ThrowingDiscSystem system;
    private final ElementSMPRefined plugin;

    DiscListener(ThrowingDiscSystem system) {
        this.system = system;
        this.plugin = system.plugin();
    }

    // ---- throwing ---------------------------------------------------------------------------

    // Not ignoreCancelled: a click on air has no block to use, so Bukkit reports it as cancelled
    // from the start. What matters is whether another plugin denied using the item.
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.useItemInHand() == Event.Result.DENY) return;
        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) return;
        if (ThrowingDisc.tierOf(plugin, event.getItem()) == null) return;

        Player player = event.getPlayer();
        // Chests, doors, buttons... still work like normal, unless the player is sneaking.
        if (action == Action.RIGHT_CLICK_BLOCK
                && !player.isSneaking()
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType().isInteractable()) {
            return;
        }
        // A hoe would till the ground on right-click, so the click is always consumed.
        event.setCancelled(true);
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        system.throwDisc(player, hand);
    }

    // ---- keeping the disc safe while it is away from the player -----------------------------

    /** A disc in the air would be lost if the player left, so it comes home first. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        system.recall(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getTaskScheduler().runLater(() -> system.deliverPending(player), 1L);
    }

    // ---- crafting: only one disc ever -------------------------------------------------------

    private boolean isDiscRecipe(Recipe recipe) {
        return recipe instanceof Keyed keyed && keyed.getKey().equals(system.recipeKey());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (isDiscRecipe(event.getRecipe()) && system.registry().isIssued()) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!isDiscRecipe(event.getRecipe())) return;
        if (system.registry().isIssued()) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick()) {
            // Shift-click can craft in a loop; the disc has to be crafted one click at a time.
            event.setCancelled(true);
            event.getWhoClicked()
                    .sendMessage(
                            Component.text(
                                    "Click the result normally to craft the disc.",
                                    NamedTextColor.YELLOW));
        }
    }

    /** The craft went through: from now on no other disc can be made. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftDone(CraftItemEvent event) {
        if (isDiscRecipe(event.getRecipe())) {
            system.registry().markIssued();
        }
    }

    /** Autocrafters could produce it without a player, so the disc can't be crafted in one. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getRecipe().getKey().equals(system.recipeKey())) {
            event.setCancelled(true);
        }
    }

    // ---- netherite upgrade ------------------------------------------------------------------

    /**
     * Diamond disc + netherite upgrade template + netherite ingot gives the netherite disc. The
     * vanilla recipe would hand back a plain netherite hoe that still wears the diamond model, so
     * the result is replaced with the proper upgraded disc (same durability, same enchantments).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        SmithingInventory inventory = event.getInventory();
        ItemStack base = inventory.getInputEquipment();
        if (ThrowingDisc.tierOf(plugin, base) != DiscTier.DIAMOND) return;

        ItemStack template = inventory.getInputTemplate();
        ItemStack addition = inventory.getInputMineral();
        boolean valid =
                template != null
                        && template.getType() == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                        && addition != null
                        && addition.getType() == Material.NETHERITE_INGOT;
        event.setResult(valid ? ThrowingDisc.upgrade(plugin, base) : null);
    }
}
