package net.rose.elementSMPRefined.core.API;

import net.rose.elementSMPRefined.core.API.ability.Ability;
import net.rose.elementSMPRefined.core.API.addon.ElementAddon;
import net.rose.elementSMPRefined.core.API.element.Element;
import net.rose.elementSMPRefined.core.API.element.ElementId;
import net.rose.elementSMPRefined.core.API.event.AbilityActivateEvent;
import net.rose.elementSMPRefined.core.API.event.ElementAssignEvent;
import net.rose.elementSMPRefined.core.API.event.ElementSetEvent;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.items.api.ElementItem;
import net.rose.elementSMPRefined.managers.ElementManager;
import net.rose.elementSMPRefined.core.registry.ItemRegistry;
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

    /** Passives use the same Bukkit listener lifecycle, but have a semantic API name. */
    public void registerPassive(String id, Listener listener) {
        plugin.getAddonManager().registerPassive(id, listener);
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
     * Assigns an element to a player (initial roll, admin grant, altar reward,
     * etc.) - resets their upgrade level for the new element. Delegates to
     * {@link ElementManager#assignElement(Player, ElementId)}, which is the
     * single source of truth for this action and fires {@link ElementAssignEvent}
     * once the assignment is saved. That event is informational only and is
     * not cancellable - to block an assignment, intervene before calling this.
     */
    public void assignElement(Player player, ElementId id) {
        elements.assignElement(player, id);
    }

    /**
     * Sets a player's element outright (e.g. a GUI reroll) - preserves their
     * existing upgrade level. Delegates to {@link ElementManager#setElement(Player, ElementId)},
     * which fires {@link ElementSetEvent} once the change is saved.
     */
    public void setElement(Player player, ElementId id) {
        elements.setElement(player, id);
    }

    /**
     * Activates an addon-registered ability by ID, subject to the normal
     * upgrade-level and mana checks. Delegates to
     * {@link ElementManager#activateAbility(Player, String)}, which spends
     * mana and fires {@link AbilityActivateEvent} only once the ability has
     * actually succeeded - so listeners never see an event for a failed
     * activation.
     */
    public boolean activateAbility(Player player, String id) {
        return elements.activateAbility(player, id);
    }

    /**
     * Activates the ability bound to slot 1 or 2. Delegates to
     * {@link ElementManager#useAbility1} / {@code useAbility2}, which fire
     * {@link AbilityActivateEvent} on success - the same path a real player's
     * slot-1/slot-2 cast takes, so addon-triggered and player-triggered casts
     * behave identically.
     */
    public boolean activateAbility(Player player, int slot) {
        return switch (slot) {
            case 1 -> elements.useAbility1(player);
            case 2 -> elements.useAbility2(player);
            default -> throw new IllegalArgumentException("Ability slot must be 1 or 2");
        };
    }
}