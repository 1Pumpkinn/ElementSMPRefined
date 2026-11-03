package net.rose.elementSMPRefined.listeners.handler;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.altar.ForgeRecipe;
import net.rose.elementSMPRefined.items.recipes.ForgedUpgraderItem;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.bukkit.ItemUtil;
import net.rose.elementSMPRefined.util.visual.model.ElementGemOrbitVisual;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

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
        // A forged element (Lava/Metal/Frost/Storm) being rerolled away gives the Forged Upgrader back.
        ElementType before = elementManager.getPlayerElement(player);
        boolean wasForged = before != null && ForgeRecipe.forTarget(before).isPresent();

        consumeItem(player, item);
        clearOldElementEffects(player);
        performBasicRoll(player, newElement, wasForged);
    }

    private boolean isReroller(ItemStack item) {
        return ItemUtil.hasTag(item, ItemKeys.reroller(plugin), PersistentDataType.BYTE);
    }

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

    private void giveForgedUpgraderBack(Player player) {
        for (ItemStack leftover : player.getInventory().addItem(ForgedUpgraderItem.make(plugin)).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void performBasicRoll(Player player, ElementType targetElement, boolean wasForged) {
        SoundUtils.playTo(player, SoundUtils.UI.ROLL);

        ElementType[] basicElements = elementManager.getBasicElements();
        final ElementGemOrbitVisual visual = new ElementGemOrbitVisual(player, basicElements, targetElement);

        new BukkitRunnable() {
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
                // the element lines up with the gem appearing.
                if (!assigned && visual.hasRevealed()) {
                    assigned = true;
                    elementManager.assignBasicElement(player, targetElement);
                    if (wasForged) giveForgedUpgraderBack(player);
                }

                // Whole animation finished (survivor has burst) - release the lock.
                if (visual.isDone()) {
                    cancel();
                    elementManager.endRolling(player);
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
