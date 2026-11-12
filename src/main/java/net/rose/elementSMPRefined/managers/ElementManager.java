package net.rose.elementSMPRefined.managers;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.ability.main.mythic.death.DeathSoulSiphonAbility;
import net.rose.elementSMPRefined.ability.passive.basic.air.AirElement;
import net.rose.elementSMPRefined.ability.passive.basic.earth.EarthElement;
import net.rose.elementSMPRefined.ability.passive.basic.fire.FireElement;
import net.rose.elementSMPRefined.ability.passive.basic.water.WaterElement;
import net.rose.elementSMPRefined.ability.passive.mythic.death.DeathElement;
import net.rose.elementSMPRefined.ability.passive.mythic.life.LifeElement;
import net.rose.elementSMPRefined.ability.passive.upgraded.frost.FrostElement;
import net.rose.elementSMPRefined.ability.passive.upgraded.lava.LavaElement;
import net.rose.elementSMPRefined.ability.passive.upgraded.metal.MetalElement;
import net.rose.elementSMPRefined.ability.passive.upgraded.storm.StormElement;
import net.rose.elementSMPRefined.core.API.ability.Ability;
import net.rose.elementSMPRefined.core.API.element.Element;
import net.rose.elementSMPRefined.core.API.element.ElementContext;
import net.rose.elementSMPRefined.core.API.element.ElementType;
import net.rose.elementSMPRefined.core.API.event.AbilityActivateEvent;
import net.rose.elementSMPRefined.core.API.event.ElementAssignEvent;
import net.rose.elementSMPRefined.core.API.event.ElementChangeEvent;
import net.rose.elementSMPRefined.core.API.event.ElementSetEvent;
import net.rose.elementSMPRefined.core.registry.ElementRegistry;
import net.rose.elementSMPRefined.data.DataStore;
import net.rose.elementSMPRefined.data.PlayerData;
import net.rose.elementSMPRefined.lang.Lang;
import net.rose.elementSMPRefined.services.EffectService;
import net.rose.elementSMPRefined.util.sound.SoundUtils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ElementManager {
    private final ElementSMPRefined plugin;
    private final DataStore store;
    private final CooldownManager cooldownManager;
    private final TeamManager teamManager;
    private final ConfigManager configManager;
    private final ElementRegistry elementRegistry;
    private final Set<UUID> currentlyRolling = new HashSet<>();

    public ElementManager(
            JavaPlugin plugin,
            DataStore store,
            CooldownManager cooldownManager,
            TeamManager teamManager,
            ConfigManager configManager) {
        this.plugin = (ElementSMPRefined) plugin;
        this.store = store;
        this.cooldownManager = cooldownManager;
        this.teamManager = teamManager;
        this.configManager = configManager;
        this.elementRegistry = new ElementRegistry(plugin);
        registerAllElements();
    }

    public EffectService getEffectService() {
        return plugin.getEffectService();
    }

    public ElementRegistry getElementRegistry() {
        return elementRegistry;
    }

    /**
     * All basic elements that can be rolled initially (starter rolls, basic reroller): every
     * {@link ElementType.Tier#BASIC} type, in declaration order.
     */
    public ElementType[] getBasicElements() {
        return ElementType.ofTier(ElementType.Tier.BASIC).toArray(new ElementType[0]);
    }

    /**
     * All advanced elements that can be rolled with the advanced reroller: every
     * {@link ElementType.Tier#FORGED} type. Mythic elements are not part of this - they can only
     * be claimed through a mythic book.
     */
    public ElementType[] getAdvancedElements() {
        return ElementType.ofTier(ElementType.Tier.FORGED).toArray(new ElementType[0]);
    }

    public Collection<Element> getAllElements() {
        return elementRegistry.getAllElements();
    }

    private void registerAllElements() {
        elementRegistry.register(new AirElement(plugin, configManager));
        elementRegistry.register(new WaterElement(plugin, configManager));
        elementRegistry.register(new FireElement(plugin, configManager));
        elementRegistry.register(new EarthElement(plugin, configManager));
        elementRegistry.register(new LifeElement(plugin, configManager));
        elementRegistry.register(new DeathElement(plugin, configManager));
        elementRegistry.register(new MetalElement(plugin, configManager));
        elementRegistry.register(new FrostElement(plugin, configManager));
        elementRegistry.register(new LavaElement(plugin, configManager));
        elementRegistry.register(new StormElement(plugin, configManager));

        elementRegistry.freeze();
    }

    public PlayerData data(UUID uuid) {
        return store.getPlayerData(uuid);
    }

    public Element get(ElementType type) {
        return elementRegistry.get(type);
    }

    public ElementType getPlayerElement(Player player) {
        return data(player.getUniqueId()).getCurrentElement();
    }

    public boolean isCurrentlyRolling(Player player) {
        return currentlyRolling.contains(player.getUniqueId());
    }

    public void cancelRolling(Player player) {
        currentlyRolling.remove(player.getUniqueId());
    }

    /**
     * Public entry point for handlers that run their own animation loop
     * (e.g. {@code ForgedUpgraderHandler}) but still need to share the
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
     * reroller. Public because {@code ForgedUpgraderHandler} runs its own
     * animation loop outside this class and needs to queue the refund
     * itself when a player logs off mid-roll.
     */
    public void queueAdvancedRerollerRefund(Player player) {
        PlayerData pd = data(player.getUniqueId());
        pd.addPendingAdvancedRerollerRefund();
        store.save(pd);
    }

    /**
     * Gives the player {@code type} for {@code cause} - chosen by whatever handler is running
     * the roll, forge, withdraw or claim. This stays in ElementManager (rather than in each
     * handler) because it handles the old-element switch and fires {@link ElementAssignEvent} -
     * shared machinery, not handler-specific code. The upgrade level is kept.
     */
    public void assignElement(Player player, ElementType type, ElementChangeEvent.Cause cause) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(cause, "cause");
        PlayerData pd = data(player.getUniqueId());
        ElementType old = switchElement(player, pd, type);

        // saveAsync() updates the in-memory cache immediately (so the player's
        // effects/data are correct right away) and defers the actual YAML
        // read-modify-write to a background thread. store.save() reloads and
        // rewrites the ENTIRE players.yml synchronously on the main thread -
        // that cost scales with total player count/history, not with this one
        // reroll, and was the cause of the reroll lag spikes.
        store.saveAsync(pd);
        applyUpsides(player);
        SoundUtils.playTo(player, SoundUtils.UI.SUCCESS);

        plugin.getServer()
                .getPluginManager()
                .callEvent(new ElementAssignEvent(player, type, old, cause));
    }

    /** Admin set (/element set). The player's upgrade level is kept. */
    public void setElement(Player player, ElementType type) {
        Objects.requireNonNull(type, "type");
        PlayerData pd = data(player.getUniqueId());
        ElementType old = switchElement(player, pd, type);
        store.save(pd);

        player.sendMessage(Lang.elementManagerYourElementIsNow(displayNameOf(type)));
        applyUpsides(player);

        plugin.getServer().getPluginManager().callEvent(new ElementSetEvent(player, type, old));
    }

    /**
     * Shared by {@link #assignElement} and {@link #setElement}: clears the element being left and
     * records the new one with the upgrade level kept.
     *
     * @return the element the player had before, or null
     */
    private ElementType switchElement(Player player, PlayerData pd, ElementType type) {
        ElementType old = pd.getCurrentElement();

        if (old != null && old != type) {
            handleElementSwitch(player, old);
        }

        int currentUpgrade = pd.getCurrentElementUpgradeLevel();
        pd.setCurrentElementWithoutReset(type);
        pd.setCurrentElementUpgradeLevel(currentUpgrade);
        return old;
    }

    private String displayNameOf(ElementType type) {
        Element element = elementRegistry.get(type);
        return element != null ? element.getDisplayName() : type.name();
    }

    private void handleElementSwitch(Player player, ElementType oldType) {
        // Only clear the element actually being left - see EffectService.clearElementEffects
        // for why this replaced the old full-registry clearAllElementEffects() call here.
        plugin.getEffectService().clearElementEffects(player, oldType);
    }

    public void applyUpsides(Player player) {
        plugin.getEffectService().applyPassiveEffects(player);
    }

    public boolean useAbility1(Player player) {
        return useAbility(player, 1);
    }

    public boolean useAbility2(Player player) {
        return useAbility(player, 2);
    }

    private boolean useAbility(Player player, int number) {
        PlayerData pd = data(player.getUniqueId());
        ElementType ownType = pd.getCurrentElement();
        if (ownType == null) return false;

        // Soul Siphon: for its duration the caster casts the drained player's abilities instead.
        ElementType stolenType = plugin.getStolenAbilityManager().getStolenElement(player);

        // Sneaking while casting ability 2 cancels the siphon, so a plain ability 2 still casts
        // the stolen element's second ability.
        if (stolenType != null && number == 2 && player.isSneaking()) {
            cancelSoulSiphon(player);
            return false;
        }
        ElementType type = stolenType != null ? stolenType : ownType;

        Element element = elementRegistry.get(type);
        if (element == null) return false;

        boolean stolen = stolenType != null;
        ElementContext ctx =
                stolen
                        ? buildContext(player, type, pd.getUpgradeLevel(ownType), true)
                        : buildContext(player, type, pd.getUpgradeLevel(type), false);

        boolean success = number == 1 ? element.ability1(ctx) : element.ability2(ctx);
        if (success) {
            Ability ability = element.getAbility(number);
            plugin.getServer()
                    .getPluginManager()
                    .callEvent(
                            new AbilityActivateEvent(
                                    player,
                                    type,
                                    number,
                                    ability.getAbilityId(),
                                    ability.getName(),
                                    stolen));
        }
        return success;
    }

    private void cancelSoulSiphon(Player caster) {
        UUID victimId = plugin.getStolenAbilityManager().cancel(caster);
        if (victimId == null) return;

        plugin.getDisarmManager().clearAbilityDisarm(victimId);
        cooldownManager.startCooldown(
                caster,
                DeathSoulSiphonAbility.ID,
                DeathSoulSiphonAbility.COOLDOWN_AFTER_STEAL_SECONDS);
    }

    private ElementContext buildContext(
            Player player, ElementType type, int upgradeLevel, boolean stolen) {
        return ElementContext.builder()
                .player(player)
                .upgradeLevel(upgradeLevel)
                .elementType(type)
                .cooldownManager(cooldownManager)
                .teamManager(teamManager)
                .configManager(configManager)
                .plugin(plugin)
                .stolen(stolen)
                .build();
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
