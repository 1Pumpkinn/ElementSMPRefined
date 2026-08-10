package hs.elementSMPRefined.managers;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.config.Constants;
import hs.elementSMPRefined.data.DataStore;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.elements.*;
import hs.elementSMPRefined.elements.impl.air.AirElement;
import hs.elementSMPRefined.elements.impl.death.DeathElement;
import hs.elementSMPRefined.elements.impl.earth.EarthElement;
import hs.elementSMPRefined.elements.impl.fire.FireElement;
import hs.elementSMPRefined.elements.impl.frost.FrostElement;
import hs.elementSMPRefined.elements.impl.life.LifeElement;
import hs.elementSMPRefined.elements.impl.metal.MetalElement;
import hs.elementSMPRefined.elements.impl.water.WaterElement;
import hs.elementSMPRefined.services.EffectService;
import hs.elementSMPRefined.util.scheduling.TaskScheduler;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
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
    private final TaskScheduler scheduler;
    private final Map<ElementType, Element> registry = new EnumMap<>(ElementType.class);
    private final Set<UUID> currentlyRolling = new HashSet<>();
    private final Random random = new Random();

    public ElementManager(ElementSMPRefined plugin, DataStore store, ManaManager manaManager,
                          TrustManager trustManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.store = store;
        this.manaManager = manaManager;
        this.trustManager = trustManager;
        this.configManager = configManager;
        this.effectService = new EffectService(plugin, this);
        this.scheduler = new TaskScheduler(plugin);
        registerAllElements();
    }

    public ElementSMPRefined getPlugin() { return plugin; }
    public EffectService getEffectService() { return effectService; }

    private void registerAllElements() {
        registry.put(ElementType.AIR, new AirElement(plugin));
        registry.put(ElementType.WATER, new WaterElement(plugin));
        registry.put(ElementType.FIRE, new FireElement(plugin));
        registry.put(ElementType.EARTH, new EarthElement(plugin));
        registry.put(ElementType.LIFE, new LifeElement(plugin));
        registry.put(ElementType.DEATH, new DeathElement(plugin));
        registry.put(ElementType.METAL, new MetalElement(plugin));
        registry.put(ElementType.FROST, new FrostElement(plugin));
    }

    public PlayerData data(UUID uuid) {
        return store.getPlayerData(uuid);
    }

    public Element get(ElementType type) {
        return registry.get(type);
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

        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1f, 1.2f);

        new RollingAnimation(player, BASIC_ELEMENTS)
                .start(() -> {
                    assignRandomElement(player);
                    endRoll(player);
                });
    }

    private void assignRandomElement(Player player) {
        ElementType randomType = BASIC_ELEMENTS[random.nextInt(BASIC_ELEMENTS.length)];
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
        player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    private void handleElementSwitch(Player player, ElementType oldElement) {
        returnLifeOrDeathCore(player, oldElement);
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
        Element element = registry.get(type);

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
        }
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

    private void returnLifeOrDeathCore(Player player, ElementType oldElement) {
        if (oldElement != ElementType.LIFE && oldElement != ElementType.DEATH) return;
        if (!data(player.getUniqueId()).hasElementItem(oldElement)) return;

        var core = hs.elementSMPRefined.items.ElementCoreItem.createCore(plugin, oldElement);
        if (core != null) {
            player.getInventory().addItem(core);
            player.sendMessage(ChatColor.YELLOW + "Your core has been returned!");
        }
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