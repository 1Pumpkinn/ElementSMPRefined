package net.rose.elementSMPRefined.listeners.handler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.altar.ForgeRecipe;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.util.bukkit.ItemUtil;
import net.rose.elementSMPRefined.util.sound.SoundUtils;
import net.rose.elementSMPRefined.util.visual.ElementColours;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.Optional;

public class ForgedUpgraderHandler implements Listener {
    private final ElementSMPRefined plugin;
    private final ElementManager elementManager;

    public ForgedUpgraderHandler(ElementSMPRefined plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
    }

    @EventHandler
    public void onForgedUpgraderUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || !ItemUtil.hasTag(item, ItemKeys.forgedUpgrader(plugin), PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        EquipmentSlot hand = event.getHand();
        if (hand == null) return;
        Player player = event.getPlayer();

        // Shares the reroll lock so this can't overlap a basic reroll / element GUI mid-animation.
        if (elementManager.isCurrentlyRolling(player)) {
            player.sendMessage(Lang.ELEMENT_MANAGER_YOU_ARE_ALREADY_REROLLING);
            return;
        }

        ElementType current = elementManager.getPlayerElement(player);
        if (current == null) {
            player.sendMessage(Lang.UPGRADER_NO_ELEMENT_YET);
            return;
        }

        Optional<ForgeRecipe> forge = ForgeRecipe.forBase(current);
        if (forge.isEmpty()) {
            player.sendMessage(Component.text(
                    "Your element can't be reforged - the Forged Upgrader only works on Fire, Earth, Water and Air.",
                    NamedTextColor.RED));
            return;
        }

        ElementType target = forge.get().target();
        elementManager.assignBasicElement(player, target);
        consume(player, hand);
        showForgedTitle(player, current, target);
    }

    private void consume(Player player, EquipmentSlot hand) {
        ItemStack inHand = player.getInventory().getItem(hand);
        if (inHand.getAmount() <= 1) {
            player.getInventory().setItem(hand, null);
        } else {
            inHand.setAmount(inHand.getAmount() - 1);
        }
    }

    private void showForgedTitle(Player player, ElementType from, ElementType to) {
        var element = elementManager.get(to);
        NamedTextColor color = element != null
                ? ElementColours.fromLegacy(element.getDisplayName())
                : NamedTextColor.AQUA;

        // Replaces the generic "Element Assigned!" title assignBasicElement() just showed.
        player.showTitle(Title.title(
                Component.text("Element Forged!").color(NamedTextColor.GOLD),
                Component.text(from.name() + " -> " + to.name()).color(color),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500))
        ));
        SoundUtils.playTo(player, SoundUtils.UI.SUCCESS);
    }
}
