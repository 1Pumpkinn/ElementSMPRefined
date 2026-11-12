package net.rose.elementevents.mythic;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.altar.ForgeRecipe;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.event.ElementChangeEvent;
import net.rose.elementSMPRefined.items.recipes.ForgedUpgraderItem;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementevents.mythic.MythicBook.Kind;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Right-clicking a {@link MythicBook} claims its element: the book is used up and the player's
 * element becomes the mythic one. Only one player can hold each mythic element
 * ({@link MythicElements}); if someone already does, the book is kept and nothing happens.
 * <ul>
 *   <li>If the player's old element was forged (Lava/Metal/Frost/Storm) they get the Forged
 *       Upgrader back, like a reroll does.</li>
 *   <li>If it was a different mythic element, that element is freed and its book is given back, so
 *       a mythic element is never lost by swapping.</li>
 * </ul>
 * This handler also keeps {@link MythicElements} in sync when a mythic element leaves or arrives by
 * some other route (a reroll, {@code /element set}, or an existing holder logging in).
 */
public class MythicBookHandler implements Listener {

    private final ElementSMPRefined plugin;
    private final ElementManager elements;
    private final MythicElements registry;

    public MythicBookHandler(
            ElementSMPRefined plugin, ElementManager elements, MythicElements registry) {
        this.plugin = plugin;
        this.elements = elements;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        Kind kind = MythicBook.kindOf(plugin, item);
        if (kind == null || event.getHand() == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        ElementType type = kind.element();

        if (elements.isCurrentlyRolling(player)) {
            player.sendMessage(
                    Component.text("Wait for your reroll to finish first.", NamedTextColor.RED));
            return;
        }
        ElementType before = elements.getPlayerElement(player);
        if (before == type) {
            player.sendMessage(
                    Component.text("You already hold the " + kind.elementName() + ".", NamedTextColor.RED));
            return;
        }
        if (!registry.claim(type, player.getUniqueId())) {
            player.sendMessage(
                    Component.text(
                            "The " + kind.elementName() + " has already been claimed by someone else.",
                            NamedTextColor.RED));
            return;
        }

        // Use up the book, then swap the element (ElementManager clears the old element's effects).
        player.getInventory().setItem(event.getHand(), null);

        boolean wasForged = before != null && ForgeRecipe.forTarget(before).isPresent();
        Kind previousMythic = MythicBook.kindFor(before);
        if (previousMythic != null) registry.release(before, player.getUniqueId());

        elements.assignElement(player, type, ElementChangeEvent.Cause.MYTHIC_CLAIM);

        if (wasForged) {
            dropOrGive(player, ForgedUpgraderItem.make(plugin));
        }
        if (previousMythic != null) {
            MythicBook.giveOrDrop(plugin, player, previousMythic);
            player.sendMessage(
                    Component.text(
                            "Your " + previousMythic.elementName() + " was returned as a book.",
                            NamedTextColor.YELLOW));
        }

        Bukkit.broadcast(
                Component.text(
                        player.getName() + " has claimed the " + kind.elementName() + "!",
                        NamedTextColor.GOLD));
    }

    private void dropOrGive(Player player, ItemStack stack) {
        for (ItemStack rest : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    // ---- keep the registry in sync with every other way an element can change ---------------

    /** Covers every route: rerolls, forging, withdrawing, mythic books and {@code /element set}. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onElementChange(ElementChangeEvent event) {
        sync(event.getPlayer(), event.getPreviousElement(), event.getNewElement());
    }

    private void sync(Player player, ElementType previous, ElementType current) {
        if (previous == current) return;
        if (previous != null && previous.isMythic()) {
            registry.release(previous, player.getUniqueId());
        }
        if (current != null && current.isMythic() && !registry.claim(current, player.getUniqueId())) {
            plugin.getLogger()
                    .warning(
                            player.getName()
                                    + " was given the "
                                    + current
                                    + " element but someone else already holds it.");
        }
    }

    /** A player who already holds a mythic element (given before this system existed) is recorded as its owner. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ElementType current = elements.getPlayerElement(player);
        if (current != null && current.isMythic() && registry.ownerOf(current) == null) {
            registry.claim(current, player.getUniqueId());
        }
    }
}
