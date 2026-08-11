package hs.elementSMPRefined.listeners.item;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.elements.ElementType;
import hs.elementSMPRefined.items.ItemKeys;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.util.visual.SoundUtils;
import net.kyori.adventure.title.Title;
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

        if (elementManager.isCurrentlyRolling(player)) {
            player.sendMessage(ChatColor.RED + "You are already rerolling your element!");
            return;
        }

        PlayerData playerData = elementManager.data(player.getUniqueId());
        ElementType currentElement = playerData.getCurrentElement();
        ElementType newElement = determineNewElement(currentElement);

        consumeItem(player, item);
        performAdvancedRoll(player, newElement);
    }

    private boolean isAdvancedReroller(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(ItemKeys.advancedReroller(plugin), PersistentDataType.BYTE);
    }

    private ElementType determineNewElement(ElementType current) {
        ElementType[] advancedElements = elementManager.getAdvancedElements();
        
        if (advancedElements.length == 0) {
            // Fallback to default behavior
            return switch (current) {
                case METAL -> ElementType.FROST;
                case FROST -> ElementType.METAL;
                default -> random.nextBoolean() ? ElementType.METAL : ElementType.FROST;
            };
        }
        
        // Filter out current element and choose from remaining
        if (current != null) {
            ElementType[] available = java.util.Arrays.stream(advancedElements)
                    .filter(type -> type != current)
                    .toArray(ElementType[]::new);
            
            if (available.length > 0) {
                return available[random.nextInt(available.length)];
            }
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
        final String[] names;
        
        if (advancedElements.length == 0) {
            names = new String[]{"METAL", "FROST"};
        } else {
            names = java.util.Arrays.stream(advancedElements)
                    .map(Enum::name)
                    .toArray(String[]::new);
        }
        
        final int steps = 20;
        final long interval = 3L;
        
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (tick >= steps) {
                    assignAdvancedElement(player, targetElement);
                    cancel();
                    return;
                }

                String name = names[tick % names.length];
                player.sendTitle(
                        ChatColor.GOLD + "Rolling...",
                        ChatColor.AQUA + name,
                        0, 10, 0
                );
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

        Title title = Title.title(
                net.kyori.adventure.text.Component.text("Element Chosen!")
                        .color(net.kyori.adventure.text.format.NamedTextColor.GOLD),
                net.kyori.adventure.text.Component.text(element.name())
                        .color(net.kyori.adventure.text.format.NamedTextColor.AQUA),
                Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(500)
                )
        );

        player.showTitle(title);
        elementManager.applyUpsides(player);
        SoundUtils.playTo(player, SoundUtils.UI.SUCCESS);

        player.sendMessage(ChatColor.GREEN + "Your element has been rerolled");
    }

    private void clearOldElementEffects(Player player, PlayerData playerData) {
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
}
