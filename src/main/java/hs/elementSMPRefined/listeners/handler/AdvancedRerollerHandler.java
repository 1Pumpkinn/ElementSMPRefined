package hs.elementSMPRefined.listeners.handler;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.items.ItemKeys;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.util.bukkit.ItemUtil;
import hs.elementSMPRefined.util.visual.ElementColours;
import hs.elementSMPRefined.util.visual.SoundUtils;
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
import java.util.Random;

/**
 * Handles advanced element reroller item usage for non-basic elements
 */
public class AdvancedRerollerHandler implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;
    private final Random random = new Random();

    public AdvancedRerollerHandler(ElementSMPRefined plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
    }

    @EventHandler
    public void onAdvancedRerollerUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !isAdvancedReroller(item)) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        // Claim the shared rolling lock so a basic reroller, the element
        // selection GUI, or a second advanced reroller can't be used
        // concurrently on the same player. beginRolling() already messages
        // the player if it returns false, so there's nothing else to do here.
        if (!elementManager.beginRolling(player)) return;

        ElementType newElement = determineNewElement();

        consumeItem(player, item);
        performAdvancedRoll(player, newElement);
    }

    private boolean isAdvancedReroller(ItemStack item) {
        return ItemUtil.hasTag(item, ItemKeys.advancedReroller(plugin), PersistentDataType.BYTE);
    }

    /**
     * Picks a random advanced element with no exclusion of the player's
     * current element - unlike the basic reroller, the advanced reroller is
     * allowed to reroll back into the same element you already have.
     */
    private ElementType determineNewElement() {
        ElementType[] advancedElements = elementManager.getAdvancedElements();

        if (advancedElements.length == 0) {
            // Fallback to default behavior
            return random.nextBoolean() ? ElementType.METAL : ElementType.FROST;
        }

        return advancedElements[random.nextInt(advancedElements.length)];
    }

    private void consumeItem(Player player, ItemStack item) {
        item.setAmount(item.getAmount() - 1);
        if (item.getAmount() <= 0) {
            player.getInventory().removeItem(item);
        }
    }

    private void performAdvancedRoll(Player player, ElementType targetElement) {
        elementManager.data(player.getUniqueId());
        SoundUtils.playTo(player, SoundUtils.UI.ROLL);

        ElementType[] advancedElements = elementManager.getAdvancedElements();
        final ElementType[] rollPool = advancedElements.length == 0
                ? new ElementType[]{ElementType.METAL, ElementType.FROST}
                : advancedElements;

        final String[] names = java.util.Arrays.stream(rollPool)
                .map(Enum::name)
                .toArray(String[]::new);
        // Parallel array, same order as `names` - each element's own real color
        // (pulled from that element's getDisplayName()) instead of one fixed color
        // for every name shown during the animation.
        final NamedTextColor[] colors = java.util.Arrays.stream(rollPool)
                .map(type -> {
                    var element = elementManager.get(type);
                    return element != null ? ElementColours.fromLegacy(element.getDisplayName()) : NamedTextColor.AQUA;
                })
                .toArray(NamedTextColor[]::new);

        final int steps = 20;
        final long interval = 3L;

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                // Mirror RerollerHandler/ElementSelectionGUI: if the player
                // logs off (or the roll gets externally cancelled) partway
                // through the animation, stop here and never assign the new
                // element. Cancelling the task BEFORE touching the player
                // also means a stray exception on an offline player can't
                // leave this timer running forever.
                if (!player.isOnline()) {
                    // Player disconnected mid-roll - the item was already
                    // consumed when they used it, so queue it to be handed
                    // back next time they join.
                    elementManager.queueAdvancedRerollerRefund(player);
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
                        assignAdvancedElement(player, targetElement);
                    } finally {
                        elementManager.endRolling(player);
                    }
                    return;
                }

                String name = names[tick % names.length];
                NamedTextColor color = colors[tick % colors.length];
                player.showTitle(Title.title(
                        Component.text("Rolling...").color(NamedTextColor.GOLD),
                        Component.text(name).color(color),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO)
                ));
                tick++;
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    private void assignAdvancedElement(Player player, ElementType element) {
        PlayerData playerData = elementManager.data(player.getUniqueId());

        clearOldElementEffects(player, playerData);

        int currentUpgradeLevel = playerData.getCurrentElementUpgradeLevel();
        playerData.setCurrentElementWithoutReset(element);
        playerData.setCurrentElementUpgradeLevel(currentUpgradeLevel);
        plugin.getDataStore().save(playerData);

        var chosenElement = elementManager.get(element);
        NamedTextColor titleColor = chosenElement != null
                ? ElementColours.fromLegacy(chosenElement.getDisplayName())
                : NamedTextColor.AQUA;

        Title title = Title.title(
                Component.text("Element Chosen!").color(NamedTextColor.GOLD),
                Component.text(element.name()).color(titleColor),
                Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(500)
                )
        );

        player.showTitle(title);
        elementManager.applyUpsides(player);
        SoundUtils.playTo(player, SoundUtils.UI.SUCCESS);

        player.sendMessage(Component.text("Your element has been rerolled").color(NamedTextColor.GREEN));
    }

    private void clearOldElementEffects(Player player, PlayerData playerData) {
        ElementType oldElement = playerData.getCurrentElement();

        if (oldElement == null) return;

        var element = elementManager.get(oldElement);
        if (element != null) {
            element.clearEffects(player);
        }

        elementManager.returnElementCore(player, oldElement);

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
}