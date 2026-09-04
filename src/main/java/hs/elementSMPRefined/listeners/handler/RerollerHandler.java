package hs.elementSMPRefined.listeners.handler;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.config.Constants;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.items.ItemKeys;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.util.visual.SoundUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Handles basic element reroller item usage. Owns its own "Rolling..."
 * title animation the same way {@code AdvancedRerollerHandler} does, rather
 * than delegating that to ElementManager, so both rerollers are structured
 * the same way and ElementManager only deals with element data/assignment.
 */
public class RerollerHandler implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;

    public RerollerHandler(ElementSMPRefined plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
    }

    @EventHandler
    public void onRerollerUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !isReroller(item)) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        // beginRolling() already messages the player if it returns false,
        // and claims the shared rolling lock so an advanced reroller or the
        // element selection GUI can't be used concurrently on this player.
        if (!elementManager.beginRolling(player)) return;

        consumeItem(player, item);
        clearOldElementEffects(player);
        performBasicRoll(player);
    }

    private boolean isReroller(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(ItemKeys.reroller(plugin), PersistentDataType.BYTE);
    }

    private void consumeItem(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().removeItem(item);
        }
    }

    private void clearOldElementEffects(Player player) {
        PlayerData playerData = elementManager.data(player.getUniqueId());
        ElementType oldElement = playerData.getCurrentElement();

        if (oldElement == null) return;

        var element = elementManager.get(oldElement);
        if (element != null) {
            element.clearEffects(player);
        }

        if (oldElement == ElementType.LIFE) {
            var attr = player.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) {
                attr.setBaseValue(20.0);
                if (!player.isDead() && player.getHealth() > 0 && player.getHealth() > 20.0) {
                    player.setHealth(20.0);
                }
            }
        }
    }

    /**
     * Runs the "Rolling..." title animation and, once it finishes, assigns
     * the actual random basic element. Cycles through the basic elements in
     * a fixed round-robin (rather than picking randomly each tick) so the
     * title never shows the same name twice in a row mid-animation.
     */
    private void performBasicRoll(Player player) {
        SoundUtils.playTo(player, SoundUtils.UI.ROLL);

        String[] names = java.util.Arrays.stream(elementManager.getBasicElements())
                .map(Enum::name)
                .toArray(String[]::new);

        final int steps = Constants.Animation.ROLL_STEPS;
        final long interval = Constants.Animation.ROLL_DELAY_TICKS;

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                // Mirror AdvancedRerollerHandler: if the player logs off (or
                // the roll gets externally cancelled) partway through the
                // animation, stop here and never assign the new element.
                // Cancelling the task BEFORE touching the player also means
                // a stray exception on an offline player can't leave this
                // timer running forever.
                if (!player.isOnline()) {
                    // Player disconnected mid-roll - the item was already
                    // consumed when they used it, so queue it to be handed
                    // back next time they join.
                    elementManager.queueRerollerRefund(player);
                    cancel();
                    elementManager.endRolling(player);
                    return;
                }

                if (!elementManager.isCurrentlyRolling(player)) {
                    cancel();
                    elementManager.endRolling(player);
                    return;
                }

                if (tick >= steps) {
                    cancel();
                    try {
                        elementManager.assignRandomBasicElement(player);
                        player.sendMessage(Component.text("Your element has been rerolled!").color(NamedTextColor.GREEN));
                    } finally {
                        elementManager.endRolling(player);
                    }
                    return;
                }

                String name = names[tick % names.length];
                player.sendTitle(ChatColor.GOLD + "Rolling...", ChatColor.AQUA + name, 0, 10, 0);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, interval);
    }
}