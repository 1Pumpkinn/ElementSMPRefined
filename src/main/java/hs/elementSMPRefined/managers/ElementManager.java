package hs.elementSMPRefined.managers;

import hs.elementSMPRefined.API.element.Element;
import hs.elementSMPRefined.API.element.ElementContext;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.config.Constants;
import hs.elementSMPRefined.data.DataStore;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.ability.passive.air.AirElement;
import hs.elementSMPRefined.ability.passive.death.DeathElement;
import hs.elementSMPRefined.ability.passive.earth.EarthElement;
import hs.elementSMPRefined.ability.passive.fire.FireElement;
import hs.elementSMPRefined.ability.passive.frost.FrostElement;
import hs.elementSMPRefined.ability.passive.life.LifeElement;
import hs.elementSMPRefined.ability.passive.metal.MetalElement;
import hs.elementSMPRefined.ability.passive.water.WaterElement;
import hs.elementSMPRefined.registry.ElementRegistry;
import hs.elementSMPRefined.services.EffectService;
import hs.elementSMPRefined.util.visual.SoundUtils;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ElementManager {
    private static final ElementType[] BASIC_ELEMENTS = {
            ElementType.AIR, ElementType.WATER, ElementType.FIRE, ElementType.EARTH
    };

    private final ElementSMPRefined plugin;
    private final DataStore store;
    private final ManaManager manaManager;
    private final TrustManager trustManager;
    private final ConfigManager configManager;
    private final EffectService effectService;
    private final ElementRegistry elementRegistry;
    private final Set<UUID> currentlyRolling = new HashSet<>();
    private final Random random = new Random();

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
     * Get all basic elements that can be rolled initially
     */
    public ElementType[] getBasicElements() {
        // Start with default basic elements
        List<ElementType> basicElements = new ArrayList<>(Arrays.asList(BASIC_ELEMENTS));

        // Add or remove elements based on configuration
        for (ElementType type : ElementType.values()) {
            var config = configManager.getElementConfiguration().getConfig(type);
            if (config != null) {
                // If configured as basic, add it (if not already there)
                if (config.isBasic() && !basicElements.contains(type)) {
                    basicElements.add(type);
                }
                // If configured as not basic and it's in default basics, remove it
                else if (!config.isBasic() && basicElements.contains(type)) {
                    basicElements.remove(type);
                }
            }
        }

        return basicElements.toArray(new ElementType[0]);
    }

    /**
     * Get all advanced (non-basic) elements that can be rolled with advanced reroller
     */
    public ElementType[] getAdvancedElements() {
        // Start with default advanced elements
        List<ElementType> advancedElements = new ArrayList<>(Arrays.asList(
                ElementType.LIFE, ElementType.DEATH, ElementType.METAL, ElementType.FROST
        ));

        // Add or remove elements based on configuration
        for (ElementType type : ElementType.values()) {
            var config = configManager.getElementConfiguration().getConfig(type);
            if (config != null) {
                // If configured as not basic, add it to advanced (if not already there)
                if (!config.isBasic() && !advancedElements.contains(type)) {
                    advancedElements.add(type);
                }
                // If configured as basic and it's in default advanced, remove it
                else if (config.isBasic() && advancedElements.contains(type)) {
                    advancedElements.remove(type);
                }
            }
        }

        return advancedElements.toArray(new ElementType[0]);
    }

    /**
     * Get all registered elements
     */
    public Collection<Element> getAllElements() {
        return elementRegistry.getAllElements();
    }

    /**
     * Every element's own class already knows its display name, description, and
     * abilities (see {@link hs.elementSMPRefined.API.element.BaseElement}) - so
     * registering a new element is just adding one line here. Whether it's a
     * "basic" starter element is decided separately, by {@link #getBasicElements()}.
     */
    private void registerAllElements() {
        elementRegistry.register(new AirElement(plugin));
        elementRegistry.register(new WaterElement(plugin));
        elementRegistry.register(new FireElement(plugin));
        elementRegistry.register(new EarthElement(plugin));
        elementRegistry.register(new LifeElement(plugin));
        elementRegistry.register(new DeathElement(plugin));
        elementRegistry.register(new MetalElement(plugin));
        elementRegistry.register(new FrostElement(plugin));

        // Freeze the registry to prevent further modifications
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

    public void rollAndAssign(Player player) {
        if (!beginRoll(player)) return;

        SoundUtils.playTo(player, SoundUtils.UI.ROLL);

        new RollingAnimation(player, BASIC_ELEMENTS)
                .start(() -> {
                    assignRandomElement(player);
                    endRoll(player);
                });
    }

    public void rollAndAssignBasic(Player player) {
        if (!beginRoll(player)) return;

        SoundUtils.playTo(player, SoundUtils.UI.ROLL);

        new RollingAnimation(player, getBasicElements())
                .start(() -> {
                    assignRandomBasicElement(player);
                    endRoll(player);
                });
    }

    private void assignRandomElement(Player player) {
        ElementType randomType = BASIC_ELEMENTS[random.nextInt(BASIC_ELEMENTS.length)];
        assignElementInternal(player, randomType, "Element Assigned!");
    }

    private void assignRandomBasicElement(Player player) {
        ElementType[] basicElements = getBasicElements();
        ElementType randomType = basicElements[random.nextInt(basicElements.length)];
        assignElementInternal(player, randomType, "Element Assigned!");
    }

    public void assignRandomDifferentElement(Player player) {
        ElementType current = getPlayerElement(player);
        List<ElementType> available = Arrays.stream(BASIC_ELEMENTS)
                .filter(type -> type != current)
                .toList();

        ElementType newType = available.isEmpty() ?
                BASIC_ELEMENTS[random.nextInt(BASIC_ELEMENTS.length)] :
                available.get(random.nextInt(available.size()));

        assignElementInternal(player, newType, "Element Rerolled!");
    }

    public void assignElement(Player player, ElementType type) {
        assignElementInternal(player, type, "Element Chosen!", true);
    }

    public void setElement(Player player, ElementType type) {
        PlayerData pd = data(player.getUniqueId());
        ElementType old = pd.getCurrentElement();

        if (old != null && old != type) {
            handleElementSwitch(player, old);
        }

        pd.setCurrentElement(type);
        store.save(pd);

        player.sendMessage(ChatColor.GOLD + "Your element is now " + ChatColor.AQUA + type.name());
        applyUpsides(player);
    }

    private void assignElementInternal(Player player, ElementType type, String titleText) {
        assignElementInternal(player, type, titleText, false);
    }

    private void assignElementInternal(Player player, ElementType type, String titleText, boolean resetLevel) {
        PlayerData pd = data(player.getUniqueId());
        ElementType old = pd.getCurrentElement();

        if (old != null && old != type) {
            handleElementSwitch(player, old);
        }

        if (resetLevel) {
            pd.setCurrentElement(type);
        } else {
            int currentUpgrade = pd.getCurrentElementUpgradeLevel();
            pd.setCurrentElementWithoutReset(type);
            pd.setCurrentElementUpgradeLevel(currentUpgrade);
        }

        store.save(pd);
        showElementTitle(player, type, titleText);
        applyUpsides(player);
        SoundUtils.playTo(player, SoundUtils.UI.SUCCESS);
    }

    private void handleElementSwitch(Player player, ElementType oldElement) {
        effectService.clearAllElementEffects(player);
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
        ElementType type = pd.getCurrentElement();
        Element element = elementRegistry.get(type);

        if (element == null) return false;

        ElementContext ctx = ElementContext.builder()
                .player(player)
                .upgradeLevel(pd.getUpgradeLevel(type))
                .elementType(type)
                .manaManager(manaManager)
                .trustManager(trustManager)
                .configManager(configManager)
                .plugin(plugin)
                .build();

        return number == 1 ? element.ability1(ctx) : element.ability2(ctx);
    }

    public void giveElementItem(Player player, ElementType type) {
        var item = hs.elementSMPRefined.items.ElementCoreItem.createCore(plugin, type);
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

    private void showElementTitle(Player player, ElementType type, String title) {
        var titleObj = net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.Component.text(title).color(net.kyori.adventure.text.format.NamedTextColor.GOLD),
                net.kyori.adventure.text.Component.text(type.name()).color(net.kyori.adventure.text.format.NamedTextColor.AQUA),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(500),
                        java.time.Duration.ofMillis(2000),
                        java.time.Duration.ofMillis(500)
                )
        );
        player.showTitle(titleObj);
    }

    private boolean beginRoll(Player player) {
        if (isCurrentlyRolling(player)) {
            player.sendMessage(ChatColor.RED + "You are already rerolling!");
            return false;
        }
        currentlyRolling.add(player.getUniqueId());
        return true;
    }

    private void endRoll(Player player) {
        currentlyRolling.remove(player.getUniqueId());
    }

    /**
     * Reusable rolling animation
     */
    private class RollingAnimation {
        private final Player player;
        private final ElementType[] elements;

        RollingAnimation(Player player, ElementType[] elements) {
            this.player = player;
            this.elements = elements;
        }

        void start(Runnable onComplete) {
            new BukkitRunnable() {
                int tick = 0;

                @Override
                public void run() {
                    if (!player.isOnline() || !isCurrentlyRolling(player)) {
                        endRoll(player);
                        cancel();
                        return;
                    }

                    if (tick >= Constants.Animation.ROLL_STEPS) {
                        if (onComplete != null) onComplete.run();
                        cancel();
                        return;
                    }

                    String name = elements[random.nextInt(elements.length)].name();
                    player.sendTitle(ChatColor.GOLD + "Rolling...", ChatColor.AQUA + name, 0, 10, 0);
                    tick++;
                }
            }.runTaskTimer(plugin, 0L, Constants.Animation.ROLL_DELAY_TICKS);
        }
    }
}