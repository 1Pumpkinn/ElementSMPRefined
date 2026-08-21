package hs.elementSMPRefined.API;

import hs.elementSMPRefined.API.ability.Ability;
import hs.elementSMPRefined.API.element.Element;
import hs.elementSMPRefined.API.element.ElementId;
import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.items.api.ElementItem;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.registry.ItemRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.Collection;

/**
 * Supported integration facade for plugins extending ElementSMPRefined.
 * Addons should prefer this facade over accessing core managers directly.
 */
public final class ElementApi {
    private final ElementSMPRefined plugin;
    private final ElementManager elements;

    public ElementApi(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.elements = plugin.getElementManager();
    }

    public ElementSMPRefined getPlugin() {
        return plugin;
    }

    public void registerElement(Element element) {
        plugin.getAddonManager().registerElement(element);
    }

    public void registerAbility(String id, Ability ability) {
        plugin.getAddonManager().registerAbility(id, ability);
    }

    public void registerItem(String id, ElementItem item, ItemRegistry.ItemData data) {
        plugin.getAddonManager().registerItem(id, item, data);
    }

    public void registerListener(String id, Listener listener) {
        plugin.getAddonManager().registerListener(id, listener);
    }

    public Element getElement(ElementId id) {
        return elements.get(id);
    }

    public Collection<Element> getElements() {
        return elements.getAllElements();
    }

    public Ability getAbility(String id) {
        return plugin.getAddonManager().abilities().get(id);
    }

    public ElementItem getItem(String id) {
        return plugin.getItemManager().getItem(id);
    }

    public ElementId getPlayerElement(Player player) {
        return elements.getPlayerElementId(player);
    }

    public int getUpgradeLevel(Player player) {
        var data = elements.data(player.getUniqueId());
        return data.getUpgradeLevel(data.getCurrentElementId());
    }

    public void assignElement(Player player, ElementId id) {
        elements.assignElement(player, id);
    }

    public void setElement(Player player, ElementId id) {
        elements.setElement(player, id);
    }

    public boolean activateAbility(Player player, int slot) {
        return switch (slot) {
            case 1 -> elements.useAbility1(player);
            case 2 -> elements.useAbility2(player);
            default -> throw new IllegalArgumentException("Ability slot must be 1 or 2");
        };
    }
}
