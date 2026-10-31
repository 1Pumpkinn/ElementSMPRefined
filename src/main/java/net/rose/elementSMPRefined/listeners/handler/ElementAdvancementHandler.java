package net.rose.elementSMPRefined.listeners.handler;

import net.kyori.adventure.key.Key;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.items.ItemKeys;
import net.rose.elementSMPRefined.util.bukkit.ItemUtil;
import net.rose.elementSMPRefined.util.damage.AbilityDamage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Registers the "Element SMP" advancement tab (a silent root advancement, whose title
 * is the tab's name) and the advancements inside it:
 * <ul>
 *   <li>A Upgrade Of Many - craft an Upgrader I</li>
 *   <li>Fatal Element - kill something with an element ability</li>
 *   <li>Forged In Element - reforge your element into its upgraded form (Forged Upgrader)</li>
 * </ul>
 */
public class ElementAdvancementHandler implements Listener {
    private static final String CRITERION = "crafted";

    /** Root advancement: its title is the tab title. Awarded silently on join so the tab shows up. */
    private static final String ROOT_JSON = """
            {
              "display": {
                "icon": { "id": "minecraft:wind_charge" },
                "title": { "text": "Element SMP" },
                "description": { "text": "Maybe the WE are Element's were the friends we made along the way..." },
                "background": "minecraft:gui/advancements/backgrounds/stone",
                "frame": "task",
                "show_toast": false,
                "announce_to_chat": false,
                "hidden": false
              },
              "criteria": {
                "crafted": { "trigger": "minecraft:impossible" }
              }
            }
            """;

    private static final String UPGRADE_JSON = """
            {
              "parent": "%s",
              "display": {
                "icon": {
                  "id": "minecraft:amethyst_shard",
                  "components": { "minecraft:item_model": "elementsmp:upgrader_1" }
                },
                "title": { "text": "A Upgrade Of Many" },
                "description": { "text": "Craft an Upgrader I" },
                "frame": "task",
                "show_toast": true,
                "announce_to_chat": true,
                "hidden": false
              },
              "criteria": {
                "crafted": { "trigger": "minecraft:impossible" }
              }
            }
            """;

    private static final String ABILITY_KILL_JSON = """
            {
              "parent": "%s",
              "display": {
                "icon": { "id": "minecraft:fire_charge" },
                "title": { "text": "Fatal Fall" },
                "description": { "text": "Get a kill with a ability" },
                "frame": "task",
                "show_toast": true,
                "announce_to_chat": true,
                "hidden": false
              },
              "criteria": {
                "crafted": { "trigger": "minecraft:impossible" }
              }
            }
            """;

    private static final String REFORGE_JSON = """
            {
              "parent": "%s",
              "display": {
                "icon": { "id": "minecraft:ender_eye" },
                "title": { "text": "Forge Your Element" },
                "description": { "text": "Reforge your element into its upgraded stage" },
                "frame": "goal",
                "show_toast": true,
                "announce_to_chat": true,
                "hidden": false
              },
              "criteria": {
                "crafted": { "trigger": "minecraft:impossible" }
              }
            }
            """;

    private final ElementSMPRefined plugin;
    private final NamespacedKey rootKey;
    private final NamespacedKey upgradeKey;
    private final NamespacedKey abilityKillKey;
    private final NamespacedKey reforgeKey;
    private final List<NamespacedKey> allKeys;

    public ElementAdvancementHandler(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.rootKey = new NamespacedKey(plugin, "element_smp_root");
        this.upgradeKey = new NamespacedKey(plugin, "a_upgrade_of_many");
        this.abilityKillKey = new NamespacedKey(plugin, "fatal_element");
        this.reforgeKey = new NamespacedKey(plugin, "forged_in_element");
        this.allKeys = List.of(rootKey, upgradeKey, abilityKillKey, reforgeKey);
        registerOnStartup();
    }

    /**
     * Paper persists advancements loaded through loadAdvancement(s)() into the world's
     * "bukkit" datapack by default, and that stored copy is what gets loaded on the
     * next start - so an older version of the JSON would otherwise stick forever.
     * We delete any stored copy and register fresh, non-persisted ones every start.
     */
    @SuppressWarnings("deprecation")
    private void registerOnStartup() {
        boolean staleLoaded = false;
        for (NamespacedKey key : allKeys) {
            Bukkit.getUnsafe().removeAdvancement(key);
            if (Bukkit.getAdvancement(key) != null) staleLoaded = true;
        }

        if (!staleLoaded) {
            load();
            return;
        }

        // A stale persisted copy is still loaded this run (only happens once, right after the
        // stored file is deleted above). Reload server data once the server is up, then load ours.
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.reloadData();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Could not reload data to drop the old advancement copies", e);
            }
            load();
        });
    }

    @SuppressWarnings("deprecation")
    private void load() {
        if (allKeys.stream().allMatch(k -> Bukkit.getAdvancement(k) != null)) return;
        try {
            String parent = rootKey.asString();
            // Loaded together so the children can find their parent.
            Map<Key, String> advancements = new LinkedHashMap<>();
            advancements.put(rootKey, ROOT_JSON);
            advancements.put(upgradeKey, UPGRADE_JSON.formatted(parent));
            advancements.put(abilityKillKey, ABILITY_KILL_JSON.formatted(parent));
            advancements.put(reforgeKey, REFORGE_JSON.formatted(parent));
            Bukkit.getUnsafe().loadAdvancements(advancements, false);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not register the Element SMP advancements", e);
        }
    }

    private void award(Player player, NamespacedKey key) {
        Advancement advancement = Bukkit.getAdvancement(key);
        if (advancement == null) return;
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (!progress.isDone()) {
            progress.awardCriteria(CRITERION);
        }
    }

    /** Called by the Forged Upgrader when it reforges a player's element. */
    public void awardReforged(Player player) {
        load();
        award(player, rootKey);
        award(player, reforgeKey);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // /reload wipes non-persisted advancements, so re-register on demand.
        load();
        award(event.getPlayer(), rootKey);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getCurrentItem();
        if (result == null) return;

        int level = ItemUtil.getTag(result, ItemKeys.upgraderLevel(plugin), PersistentDataType.INTEGER).orElse(0);
        if (level != 1) return;

        load();
        award(player, rootKey);
        award(player, upgradeKey);
    }

    /**
     * Ability damage (TrueDamage / AbilityDamage.deal) is flagged on the victim for the
     * duration of the damage call, which is exactly when the death event fires.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!AbilityDamage.isAbilityDamage(victim)) return;

        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        load();
        award(killer, rootKey);
        award(killer, abilityKillKey);
    }
}