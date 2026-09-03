package hs.elementSMPRefined.API;

import hs.elementSMPRefined.API.ability.Ability;
import hs.elementSMPRefined.API.addon.ElementAddon;
import hs.elementSMPRefined.API.element.Element;
import hs.elementSMPRefined.API.element.ElementId;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.API.event.AbilityActivateEvent;
import hs.elementSMPRefined.API.event.ElementAssignEvent;
import hs.elementSMPRefined.API.event.ManaSpendEvent;
import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.items.api.ElementItem;
import hs.elementSMPRefined.managers.ElementManager;
import hs.elementSMPRefined.registry.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

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

    /**
     * Get the ElementSMPRefined API.
     *
     * @return The registered instance of the API.
     * @throws IllegalStateException if the plugin is not loaded.
     */
    public static @NotNull ElementApi get() {
        var instance = Bukkit.getServicesManager().load(ElementApi.class);
        if (instance == null) {
            throw new IllegalStateException("ElementApi is not loaded!");
        }
        return instance;
    }

    public ElementSMPRefined getPlugin() {
        return plugin;
    }

    public void registerAddon(ElementAddon addon) {
        plugin.getAddonManager().register(addon);
    }

    public boolean isAddonRegistered(String name) {
        return plugin.getAddonManager().isRegistered(name);
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

    public void registerDimension(NamespacedKey id, World world) {
        plugin.getAddonManager().registerDimension(id, world);
    }

    public void registerBiome(NamespacedKey id) {
        plugin.getAddonManager().registerBiome(id);
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

    public PlayerElementState getPlayerState(Player player) {
        var data = elements.data(player.getUniqueId());
        ElementId id = data.getCurrentElementId();
        return new PlayerElementState(player.getUniqueId(), id, data.getUpgradeLevel(id), data.getMana());
    }

    public ElementId getPlayerElement(Player player) {
        return elements.getPlayerElementId(player);
    }

    public int getUpgradeLevel(Player player) {
        var data = elements.data(player.getUniqueId());
        return data.getUpgradeLevel(data.getCurrentElementId());
    }

    /**
     * Assigns an element to a player. Fires a cancellable {@link ElementAssignEvent}
     * first - if cancelled, the player's element is left unchanged.
     */
    public void assignElement(Player player, ElementId id) {
        ElementId previous = elements.getPlayerElementId(player);
        ElementAssignEvent event = new ElementAssignEvent(player, previous, id);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        elements.assignElement(player, id);
    }

    /**
     * Sets a player's element outright. Fires a cancellable {@link ElementAssignEvent}
     * first - if cancelled, the player's element is left unchanged.
     */
    public void setElement(Player player, ElementId id) {
        ElementId previous = elements.getPlayerElementId(player);
        ElementAssignEvent event = new ElementAssignEvent(player, previous, id);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        elements.setElement(player, id);
    }

    /**
     * Activates an ability by ID, subject to the normal upgrade-level and mana
     * checks. Fires a cancellable {@link AbilityActivateEvent} after those
     * checks pass but before mana is spent or the ability executes, then a
     * {@link ManaSpendEvent} once mana is actually deducted.
     */
    public boolean activateAbility(Player player, String id) {
        Ability ability = getAbility(id);
        if (ability == null) return false;

        var data = elements.data(player.getUniqueId());
        ElementId elementId = data.getCurrentElementId();
        if (elementId == null || data.getUpgradeLevel(elementId) < ability.getRequiredUpgradeLevel()) return false;
        if (!plugin.getManaManager().hasMana(player, ability.getManaCost())) return false;

        AbilityActivateEvent activateEvent = new AbilityActivateEvent(player, ability, elementId);
        Bukkit.getPluginManager().callEvent(activateEvent);
        if (activateEvent.isCancelled()) return false;

        ElementContext context = ElementContext.builder()
                .player(player)
                .upgradeLevel(data.getUpgradeLevel(elementId))
                .elementType(elementId.toBuiltinType())
                .elementId(elementId)
                .manaManager(plugin.getManaManager())
                .trustManager(plugin.getTrustManager())
                .configManager(plugin.getConfigManager())
                .plugin(plugin)
                .build();

        if (!ability.execute(context)) return false;

        int cost = ability.getManaCost();
        plugin.getManaManager().spend(player, cost);
        int remaining = plugin.getManaManager().get(player.getUniqueId()).getMana();
        Bukkit.getPluginManager().callEvent(new ManaSpendEvent(player, cost, remaining));

        return true;
    }

    /**
     * Activates the ability bound to slot 1 or 2. NOTE: this delegates straight
     * to {@link ElementManager#useAbility1}/{@code useAbility2} and does not
     * currently route through the event-firing {@link #activateAbility(Player, String)}
     * path above - {@link AbilityActivateEvent} and {@link ManaSpendEvent} are
     * not fired for slot-based casts until ElementManager's own ability
     * handling is updated to fire them too.
     */
    public boolean activateAbility(Player player, int slot) {
        return switch (slot) {
            case 1 -> elements.useAbility1(player);
            case 2 -> elements.useAbility2(player);
            default -> throw new IllegalArgumentException("Ability slot must be 1 or 2");
        };
    }
}