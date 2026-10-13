package net.rose.elementSMPRefined.listeners.handler;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.core.Constants;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.bukkit.ItemUtil;
    import net.rose.elementSMPRefined.util.visual.ElementColours;
import net.rose.elementSMPRefined.util.visual.model.ElementGemOrbitVisual;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.List;
import java.util.Random;

/**
 * Handles basic element reroller item usage. Owns its own "Rolling..."
 * title animation and its own element-picking logic the same way
 * {@code AdvancedRerollerHandler} does, rather than delegating that to
 * ElementManager, so both rerollers are structured the same way and
 * ElementManager only deals with the shared element-assignment plumbing.
 */
public class RerollerHandler implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final Random random = new Random();

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

        ElementType newElement = determineNewElement(player);

        consumeItem(player, item);
        clearOldElementEffects(player);
        performBasicRoll(player, newElement);
    }

    private boolean isReroller(ItemStack item) {
        return ItemUtil.hasTag(item, ItemKeys.reroller(plugin), PersistentDataType.BYTE);
    }

    /**
     * Picks a random basic element, excluding the player's current element
     * so a basic reroll can never leave you right back where you started -
     * unlike the advanced reroller, which allows repeats.
     */
    private ElementType determineNewElement(Player player) {
        ElementType[] basicElements = elementManager.getBasicElements();
        ElementType current = elementManager.getPlayerElement(player);

        List<ElementType> available = java.util.Arrays.stream(basicElements)
                .filter(type -> type != current)
                .toList();

        return available.isEmpty() ?
                basicElements[random.nextInt(basicElements.length)] :
                available.get(random.nextInt(available.size()));
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
     * Runs the reroll animation: a ring of element gems orbits the player, gets knocked out one
     * by one, and the survivor - the element already chosen by {@code determineNewElement} -
     * glides to a stop in front of them. The element is assigned the moment that gem lands, and
     * the gem hovers for a moment before it bursts. The "Rolling..." title keeps cycling through
     * the basic elements (round-robin, so it never repeats a name back to back) while the ring
     * is still spinning.
     */
    private void performBasicRoll(Player player, ElementType targetElement) {
        SoundUtils.playTo(player, SoundUtils.UI.ROLL);

        ElementType[] basicElements = elementManager.getBasicElements();
        String[] names = java.util.Arrays.stream(basicElements)
                .map(Enum::name)
                .toArray(String[]::new);
        // Parallel array, same order as `names` - each element's own real color
        // (pulled from that element's getDisplayName()) instead of one fixed color
        // for every name shown during the animation.
        NamedTextColor[] colors = java.util.Arrays.stream(basicElements)
                .map(type -> {
                    var element = elementManager.get(type);
                    return element != null ? ElementColours.fromLegacy(element.getDisplayName()) : NamedTextColor.AQUA;
                })
                .toArray(NamedTextColor[]::new);

        final long titleEveryTicks = Constants.Animation.ROLL_DELAY_TICKS;
        final ElementGemOrbitVisual visual = new ElementGemOrbitVisual(player, basicElements, targetElement);

        new BukkitRunnable() {
            int tick = 0;
            int titleStep = 0;
            boolean assigned = false;

            @Override
            public void run() {
                // If the player logs off partway through, stop here. Cancelling the task BEFORE
                // touching the player means a stray exception on an offline player can't leave
                // this timer running forever. The item was already consumed, so if the element
                // hasn't been handed out yet, queue the item to be given back on next join.
                if (!player.isOnline()) {
                    visual.remove();
                    if (!assigned) elementManager.queueRerollerRefund(player);
                    cancel();
                    elementManager.endRolling(player);
                    return;
                }

                // Externally cancelled roll (or the player died mid-animation): clean up the
                // gems and release the lock. Nothing is assigned.
                if (!elementManager.isCurrentlyRolling(player) || player.isDead()) {
                    visual.remove();
                    // The item was already consumed - queue it back (delivered on respawn) instead of eating it.
                    if (!assigned) elementManager.queueRerollerRefund(player);
                    cancel();
                    elementManager.endRolling(player);
                    return;
                }

                visual.tick();

                // The survivor has landed in front of the player: hand out the element now, so
                // the "Element Assigned!" title lines up with the gem appearing.
                if (!assigned && visual.hasRevealed()) {
                    assigned = true;
                    elementManager.assignBasicElement(player, targetElement);
                    player.sendMessage(Component.text("Your element has been rerolled!").color(NamedTextColor.GREEN));
                }

                // Whole animation finished (survivor has burst) - release the lock.
                if (visual.isDone()) {
                    cancel();
                    elementManager.endRolling(player);
                    return;
                }

                if (!assigned && tick % titleEveryTicks == 0) {
                    int i = titleStep++;
                    player.showTitle(Title.title(
                            Component.text("Rolling...").color(NamedTextColor.GOLD),
                            Component.text(names[i % names.length]).color(colors[i % colors.length]),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO)
                    ));
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
