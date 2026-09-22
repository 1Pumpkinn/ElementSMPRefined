package net.rose.elementSMPRefined.managers;

import net.rose.elementSMPRefined.core.API.element.Element;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.element.ElementId;
import net.rose.elementSMPRefined.core.API.element.ListenerProvider;
import net.rose.elementSMPRefined.core.API.event.AbilityActivateEvent;
import net.rose.elementSMPRefined.core.API.event.ElementAssignEvent;
import net.rose.elementSMPRefined.core.API.event.ElementSetEvent;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.data.DataStore;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.ability.passive.air.AirElement;
import net.rose.elementSMPRefined.ability.passive.death.DeathElement;
import net.rose.elementSMPRefined.ability.passive.earth.EarthElement;
import net.rose.elementSMPRefined.ability.passive.fire.FireElement;
import net.rose.elementSMPRefined.ability.passive.frost.FrostElement;
import net.rose.elementSMPRefined.ability.passive.life.LifeElement;
import net.rose.elementSMPRefined.ability.passive.metal.MetalElement;
import net.rose.elementSMPRefined.ability.passive.water.WaterElement;
import net.rose.elementSMPRefined.items.builder.ElementCoreItem;
import net.rose.elementSMPRefined.core.registry.ElementRegistry;
import net.rose.elementSMPRefined.services.EffectService;
import net.rose.elementSMPRefined.util.visual.ElementColours;
import net.rose.elementSMPRefined.util.visual.SoundUtils;
import net.rose.elementSMPRefined.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ElementManager {
    // Default classification, used only where config.yml doesn't override a
    // type's isBasic() flag. Kept as a single set (rather than separate
    // basic/advanced arrays) so every ElementType is classified exactly once -
    // "advanced" is always just "everything not basic", never a second,
    // independently-maintained list that could drift out of sync with it.
    private static final EnumSet<ElementType> DEFAULT_BASIC_ELEMENTS =
            EnumSet.of(ElementType.AIR, ElementType.WATER, ElementType.FIRE, ElementType.EARTH);

    private final ElementSMPRefined plugin;
    private final DataStore store;
    private final ManaManager manaManager;
    private final TrustManager trustManager;
    private final ConfigManager configManager;
    private final EffectService effectService;
    private final ElementRegistry elementRegistry;
    private final Set<UUID> currentlyRolling = new HashSet<>();

    public ElementManager(JavaPlugin plugin, DataStore store, ManaManager manaManager,
                          TrustManager trustManager, ConfigManager configManager) {
        this.plugin = (ElementSMPRefined) plugin;
        this.store = store;
        this.manaManager = manaManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.elementRegistry = new ElementRegistry(plugin);
        this.effectService = new EffectService(plugin, this);
        registerAllElements();
    }

    public ElementSMPRefined getPlugin() { return plugin; }
    public EffectService getEffectService() { return effectService; }
    public ElementRegistry getElementRegistry() { return elementRegistry; }

    /**
     * All basic elements that can be rolled initially (starter rolls, basic
     * reroller), per {@link ElementType} default plus any config.yml override.
     */
    public ElementType[] getBasicElements() {
        return classifyBasicElements().toArray(new ElementType[0]);
    }

    /**
     * All advanced (non-basic) elements that can be rolled with the advanced
     * reroller. Always the exact complement of {@link #getBasicElements()} -
     * every {@link ElementType} lands in exactly one of the two lists.
     */
    public ElementType[] getAdvancedElements() {
        EnumSet<ElementType> advanced = EnumSet.allOf(ElementType.class);
        advanced.removeAll(classifyBasicElements());
        return advanced.toArray(new ElementType[0]);
    }

    /**
     * Applies config.yml's per-element {@code isBasic} override (if any) on
     * top of {@link #DEFAULT_BASIC_ELEMENTS}. Single source of truth for the
     * basic/advanced split, queried fresh each call so a {@code /elementconfig
     * reload} takes effect immediately.
     */
    private EnumSet<ElementType> classifyBasicElements() {
        EnumSet<ElementType> basic = EnumSet.copyOf(DEFAULT_BASIC_ELEMENTS);
        var elementConfig = configManager.getElementConfiguration();

        for (ElementType type : ElementType.values()) {
            var config = elementConfig.getConfig(type);
            if (config == null) continue;

            Boolean override = config.isBasic();
            if (override == null) continue;

            if (override) {
                basic.add(type);
            } else {
                basic.remove(type);
            }
        }

        return basic;
    }

    /**
     * Get all registered elements
     */
    public Collection<Element> getAllElements() {
        return elementRegistry.getAllElements();
    }

    /**
     * Every element's own class already knows its display name, description, and
     * abilities (see {@link net.rose.elementSMPRefined.core.API.element.BaseElement}) - so
     * registering a new element is just adding one line here. Whether it's a
     * "basic" starter element is decided separately, by {@link #getBasicElements()}.
     */
    private void registerAllElements() {
        elementRegistry.register(new AirElement(plugin, configManager));
        elementRegistry.register(new WaterElement(plugin, configManager));
        elementRegistry.register(new FireElement(plugin, configManager));
        elementRegistry.register(new EarthElement(plugin, configManager));
        elementRegistry.register(new LifeElement(plugin, configManager));
        elementRegistry.register(new DeathElement(plugin, configManager));
        elementRegistry.register(new MetalElement(plugin, configManager));
        elementRegistry.register(new FrostElement(plugin, configManager));

        // Freeze the registry to prevent further modifications
        elementRegistry.freeze();
    }

    public PlayerData data(UUID uuid) {
        return store.getPlayerData(uuid);
    }

    public Element get(ElementType type) {
        return elementRegistry.get(type);
    }

    public Element get(ElementId id) {
        return elementRegistry.get(id);
    }

    public ElementType getPlayerElement(Player player) {
        return data(player.getUniqueId()).getCurrentElement();
    }

    public ElementId getPlayerElementId(Player player) {
        return data(player.getUniqueId()).getCurrentElementId();
    }

    public boolean isCurrentlyRolling(Player player) {
        return currentlyRolling.contains(player.getUniqueId());
    }

    public void cancelRolling(Player player) {
        currentlyRolling.remove(player.getUniqueId());
    }

    /**
     * Public entry point for handlers that run their own animation loop
     * (e.g. {@code AdvancedRerollerHandler}) but still need to share the
     * same "is this player mid-reroll" lock as the basic reroller and the
     * element-selection GUI, so the three can't overlap on one player.
     */
    public boolean beginRolling(Player player) {
        return beginRoll(player);
    }

    public void endRolling(Player player) {
        endRoll(player);
    }

    /**
     * Queues a basic reroller item to be handed back next time this player
     * joins. Called by {@code RerollerHandler} when a roll aborts (player
     * logged off mid-animation) after the item was already consumed.
     * Persisted immediately (synchronous save is fine here: this only runs
     * once, right as the player disconnects, not on any hot path) so the
     * refund survives even if the server restarts before they come back.
     */
    public void queueRerollerRefund(Player player) {
        PlayerData pd = data(player.getUniqueId());
        pd.addPendingRerollerRefund();
        store.save(pd);
    }

    /**
     * Same as {@link #queueRerollerRefund(Player)} but for the advanced
     * reroller. Public because {@code AdvancedRerollerHandler} runs its own
     * animation loop outside this class and needs to queue the refund
     * itself when a player logs off mid-roll.
     */
    public void queueAdvancedRerollerRefund(Player player) {
        PlayerData pd = data(player.getUniqueId());
        pd.addPendingAdvancedRerollerRefund();
        store.save(pd);
    }

    /**
     * Assigns a specific basic element, chosen by {@code RerollerHandler}'s
     * own {@code determineNewElement}. This stays in ElementManager (rather
     * than moving fully into the handler like the advanced reroller does)
     * because it goes through {@code assignElementInternal} - the same
     * shared plumbing used by element-selection and altar-granted elements,
     * which fires {@code ElementAssignEvent} and handles the old-element
     * switch. That's genuinely shared machinery, not reroller-specific code.
     */
    public void assignBasicElement(Player player, ElementType type) {
        assignElementInternal(player, ElementId.builtin(type), "Element Assigned!", false);
    }

    public void assignElement(Player player, ElementType type) {
        assignElement(player, ElementId.builtin(type));
    }

    /**
     * Generic version of {@link #assignElement(Player, ElementType)} that also
     * accepts addon element IDs. Used e.g. by an altar/event granting a specific
     * collected element outright (no rolling).
     */
    public void assignElement(Player player, ElementId id) {
        assignElementInternal(player, id, "Element Chosen!", true);
    }

    public void setElement(Player player, ElementType type) {
        setElement(player, ElementId.builtin(type));
    }

    /**
     * Generic version of {@link #setElement(Player, ElementType)} that also
     * accepts addon element IDs.
     */
    public void setElement(Player player, ElementId id) {
        PlayerData pd = data(player.getUniqueId());
        ElementId old = pd.getCurrentElementId();

        if (old != null && !old.equals(id)) {
            handleElementSwitch(player, old);
        }

        pd.setCurrentElement(id);
        store.save(pd);

        player.sendMessage(Lang.elementManagerYourElementIsNow(displayNameOf(id)));
        applyUpsides(player);

        plugin.getServer().getPluginManager().callEvent(new ElementSetEvent(player, id, old));
    }

    private void assignElementInternal(Player player, ElementId id, String titleText, boolean resetLevel) {
        PlayerData pd = data(player.getUniqueId());
        ElementId old = pd.getCurrentElementId();

        if (old != null && !old.equals(id)) {
            handleElementSwitch(player, old);
        }

        if (resetLevel) {
            pd.setCurrentElement(id);
        } else {
            int currentUpgrade = pd.getCurrentElementUpgradeLevel();
            pd.setCurrentElementWithoutReset(id);
            pd.setCurrentElementUpgradeLevel(currentUpgrade);
        }

        // saveAsync() updates the in-memory cache immediately (so the player's
        // effects/data are correct right away) and defers the actual YAML
        // read-modify-write to a background thread. store.save() reloads and
        // rewrites the ENTIRE players.yml synchronously on the main thread -
        // that cost scales with total player count/history, not with this one
        // reroll, and was the cause of the reroll lag spikes.
        store.saveAsync(pd);
        showElementTitle(player, id, titleText);
        applyUpsides(player);
        SoundUtils.playTo(player, SoundUtils.UI.SUCCESS);

        plugin.getServer().getPluginManager().callEvent(new ElementAssignEvent(player, id, old));
    }

    private String displayNameOf(ElementId id) {
        Element element = elementRegistry.get(id);
        return element != null ? element.getDisplayName() : id.key();
    }

    private void handleElementSwitch(Player player, ElementId oldId) {
        ElementType oldType = oldId.toBuiltinType();
        if (oldType != null) {
            returnElementCore(player, oldType);
        }
        // Only clear the element actually being left - see EffectService.clearElementEffects
        // for why this replaced the old full-registry clearAllElementEffects() call here.
        // Pass the full ElementId (not oldType) so this still works for addon elements.
        effectService.clearElementEffects(player, oldId);
    }

    /**
     * Gives the player back a core for the element they're switching away from,
     * if that element type has a core item. Drops it on the ground instead if
     * their inventory is full.
     */
    public void returnElementCore(Player player, ElementType oldElement) {
        if (oldElement == null) return;

        var core = ElementCoreItem.createCore(plugin, oldElement);
        if (core == null) return; // this element type has no physical core

        var leftover = player.getInventory().addItem(core);
        if (leftover.isEmpty()) {
            player.sendMessage(Lang.elementManagerYour(oldElement.name()));
        } else {
            for (var drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            player.sendMessage(Lang.elementManagerYourInventoryWasFullSo(oldElement.name()));
        }
    }

    public void applyUpsides(Player player) {
        effectService.applyPassiveEffects(player);
    }

    public boolean useAbility1(Player player) {
        return useAbility(player, 1);
    }

    public boolean useAbility2(Player player) {
        return useAbility(player, 2);
    }

    private boolean useAbility(Player player, int number) {
        PlayerData pd = data(player.getUniqueId());
        ElementId id = pd.getCurrentElementId();
        if (id == null) return false;

        Element element = elementRegistry.get(id);
        if (element == null) return false;

        ElementContext ctx = buildContext(player, pd, id);

        boolean success = number == 1 ? element.ability1(ctx) : element.ability2(ctx);
        if (success) {
            String abilityName = number == 1 ? element.getAbility1Name() : element.getAbility2Name();
            plugin.getServer().getPluginManager()
                    .callEvent(new AbilityActivateEvent(player, id, number, abilityName));
        }
        return success;
    }

    /** Shared builder for the {@link ElementContext} every ability call needs. */
    private ElementContext buildContext(Player player, PlayerData pd, ElementId id) {
        return ElementContext.builder()
                .player(player)
                .upgradeLevel(pd.getUpgradeLevel(id))
                .elementType(id.toBuiltinType())
                .elementId(id)
                .manaManager(manaManager)
                .trustManager(trustManager)
                .configManager(configManager)
                .plugin(plugin)
                .build();
    }

    public void giveElementItem(Player player, ElementType type) {
        var item = ElementCoreItem.createCore(plugin, type);
        if (item != null) {
            player.getInventory().addItem(item);
            // Track that the player now owns this element item
            var pd = data(player.getUniqueId());
            pd.addElementItem(type);
            store.save(pd);
        }
    }

    public DataStore getStore() {
        return store;
    }

    private void showElementTitle(Player player, ElementId id, String title) {
        // getDisplayName() carries legacy '&'/ChatColor codes for chat-message use;
        // Adventure's Component.text() doesn't parse those, so strip them for the
        // displayed text but pull the actual color out first via ElementColours so
        // the title shows this element's real color instead of one fixed color.
        String rawName = displayNameOf(id);
        String plainName = ChatColor.stripColor(rawName);
        NamedTextColor nameColor = ElementColours.fromLegacy(rawName);

        var titleObj = Title.title(
                Component.text(title).color(NamedTextColor.GOLD),
                Component.text(plainName).color(nameColor),
                Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofMillis(2000),
                        Duration.ofMillis(500)
                )
        );
        player.showTitle(titleObj);
    }

    private boolean beginRoll(Player player) {
        if (isCurrentlyRolling(player)) {
            player.sendMessage(Lang.ELEMENT_MANAGER_YOU_ARE_ALREADY_REROLLING);
            return false;
        }
        currentlyRolling.add(player.getUniqueId());
        return true;
    }

    private void endRoll(Player player) {
        currentlyRolling.remove(player.getUniqueId());
    }
}