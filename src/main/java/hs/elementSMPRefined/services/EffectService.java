package hs.elementSMPRefined.services;

import hs.elementSMPRefined.config.Constants;
import hs.elementSMPRefined.data.PlayerData;
import hs.elementSMPRefined.API.element.Element;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.managers.ElementManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Centralized service for managing element passive effects.
 * Single source of truth for all effect-related operations.
 */
public class EffectService implements Listener {
    private final JavaPlugin plugin;
    private final ElementManager elementManager;

    // Cache of required effects per element
    private final Map<ElementType, EffectRequirement[]> requiredEffects = new EnumMap<>(ElementType.class);

    public EffectService(JavaPlugin plugin, ElementManager elementManager) {
        this.plugin = plugin;
        this.elementManager = elementManager;
        initializeRequirements();
        startMonitoring();
    }

    private void initializeRequirements() {
        // Water
        requiredEffects.put(ElementType.WATER, new EffectRequirement[] {
                new EffectRequirement(PotionEffectType.WATER_BREATHING, 0, false),
                new EffectRequirement(PotionEffectType.CONDUIT_POWER, 0, false)
        });

        // Fire
        requiredEffects.put(ElementType.FIRE, new EffectRequirement[] {
                new EffectRequirement(PotionEffectType.FIRE_RESISTANCE, 0, false)
        });

        // Earth
        requiredEffects.put(ElementType.EARTH, new EffectRequirement[] {
                new EffectRequirement(PotionEffectType.HERO_OF_THE_VILLAGE, 0, false)
        });

        // Life
        requiredEffects.put(ElementType.LIFE, new EffectRequirement[] {
                new EffectRequirement(PotionEffectType.REGENERATION, 0, false)
        });

        // Death
        requiredEffects.put(ElementType.DEATH, new EffectRequirement[] {
                new EffectRequirement(PotionEffectType.NIGHT_VISION, 0, false)
        });

        // Metal
        requiredEffects.put(ElementType.METAL, new EffectRequirement[] {
                new EffectRequirement(PotionEffectType.HASTE, 0, false)
        });
    }

    /**
     * Clear ALL element effects from a player.
     * Used when switching elements or logging out.
     */
    public void clearAllElementEffects(Player player) {
        PlayerData pd = elementManager.data(player.getUniqueId());
        ElementType currentElement = pd.getCurrentElement();

        // Clear effects from ALL elements (including current one when switching)
        for (ElementType type : ElementType.values()) {
            Element element = elementManager.get(type);
            if (element != null) {
                element.clearEffects(player);
            }
        }

        // Reset health if not Life element
        resetHealthIfNeeded(player, currentElement);
    }

    /**
     * Remove a potion effect only if it was applied by the element system.
     * Element effects have very long durations (Integer.MAX_VALUE), while player potions have shorter durations.
     * This preserves legitimate potion effects from drinking/splashing potions.
     */
    public static void removeElementPotionEffect(Player player, PotionEffectType type) {
        PotionEffect effect = player.getPotionEffect(type);
        if (effect != null && effect.getDuration() > 1000000) {
            // Only remove if it has an element-like duration (near Integer.MAX_VALUE)
            player.removePotionEffect(type);
        }
    }

    /**
     * Apply passive effects for player's current element.
     * Single source of truth for effect application.
     */
    public void applyPassiveEffects(Player player) {
        PlayerData pd = elementManager.data(player.getUniqueId());
        ElementType type = pd.getCurrentElement();

        if (type == null) return;

        Element element = elementManager.get(type);
        if (element != null) {
            element.applyUpsides(player, pd.getUpgradeLevel(type));
        }
    }

    /**
     * Validate and restore effects if needed.
     * Called periodically and after certain events.
     */
    public void validateEffects(Player player) {
        PlayerData pd = elementManager.data(player.getUniqueId());
        ElementType currentElement = pd.getCurrentElement();

        if (currentElement == null) return;

        int upgradeLevel = pd.getUpgradeLevel(currentElement);

        // Check required effects
        EffectRequirement[] requirements = requiredEffects.get(currentElement);
        if (requirements != null) {
            for (EffectRequirement req : requirements) {
                if (!req.upgradeRequired || upgradeLevel >= 2) {
                    if (!hasValidEffect(player, req.type)) {
                        player.addPotionEffect(new PotionEffect(
                                req.type, Integer.MAX_VALUE, req.level, true, false
                        ));
                    }
                }
            }
        }

        // Special handling for Water upgrade 2
        if (currentElement == ElementType.WATER && upgradeLevel >= 2) {
            if (!hasValidEffect(player, PotionEffectType.DOLPHINS_GRACE)) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.DOLPHINS_GRACE, Integer.MAX_VALUE, 4, true, false
                ));
            }
        }

        // Validate health
        resetHealthIfNeeded(player, currentElement);
    }

    private boolean hasValidEffect(Player player, PotionEffectType type) {
        PotionEffect effect = player.getPotionEffect(type);
        return effect != null && effect.getDuration() > 100;
    }

    private void resetHealthIfNeeded(Player player, ElementType currentElement) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        double targetHealth = currentElement == ElementType.LIFE ?
                Constants.Health.LIFE_MAX : Constants.Health.NORMAL_MAX;

        if (attr.getBaseValue() != targetHealth) {
            attr.setBaseValue(targetHealth);
            if (!player.isDead() && player.getHealth() > targetHealth) {
                player.setHealth(targetHealth);
            }
        }
    }

    /**
     * Start periodic monitoring of effects
     */
    private void startMonitoring() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    validateEffects(player);
                }
            }
        }.runTaskTimer(plugin, Constants.Timing.TWO_SECONDS, Constants.Timing.TWO_SECONDS);
    }

    // Event handlers
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMilkConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != org.bukkit.Material.MILK_BUCKET) return;

        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                applyPassiveEffects(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEffectRemove(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityPotionEffectEvent.Cause.COMMAND &&
                event.getCause() != EntityPotionEffectEvent.Cause.PLUGIN) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                validateEffects(player);
            }
        }, 2L);
    }

    /**
     * Helper class for effect requirements
     */
    private static class EffectRequirement {
        final PotionEffectType type;
        final int level;
        final boolean upgradeRequired;

        EffectRequirement(PotionEffectType type, int level, boolean upgradeRequired) {
            this.type = type;
            this.level = level;
            this.upgradeRequired = upgradeRequired;
        }
    }
}