package net.rose.elementevents;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementSMPRefined.items.recipes.ForgedUpgraderItem;
import net.rose.elementSMPRefined.items.recipes.RerollerItem;
import net.rose.elementSMPRefined.items.recipes.UpgraderItem;
import net.rose.elementevents.api.config.EventVillagerConfig;
import net.rose.elementevents.command.EventVillagerCommand;
import net.rose.elementevents.listener.EventVillagerListener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Event villagers: frozen, unkillable trader NPCs that only server operators can spawn
 * (see {@link EventVillagerCommand}). Each one has a <i>type</i> defined in
 * {@code event_villagers.yml} ({@link EventVillagerConfig}) giving it its own name, outfit and
 * trades, so many different villagers can exist at once.
 * <p>
 * A villager is an "event villager" if it carries the {@link #KEY} tag in its persistent data,
 * and its type id is stored next to it, so both survive restarts and chunk unloads with no
 * extra save file. Invulnerability is
 * enforced twice: {@code setInvulnerable(true)} on the entity, plus {@link EventVillagerListener}
 * cancelling every damage event (which also covers creative-mode players, who ignore the flag).
 * <p>
 * Trades are rebuilt from the type's config every time a player opens the trade window, so
 * editing event_villagers.yml and reloading takes effect on villagers that are already placed.
 */
public class CustomEventVillager {

    public static final String KEY = "event_villager";
    public static final String TYPE_KEY = "event_villager_type";
    private static final String DEFAULT_NAME = "&6&lEvent Trader";

    private final ElementSMPRefined plugin;
    private final NamespacedKey key;
    private final NamespacedKey typeKey;
    private final EventVillagerConfig config;

    public CustomEventVillager(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, KEY);
        this.typeKey = new NamespacedKey(plugin, TYPE_KEY);
        this.config = new EventVillagerConfig(plugin);
    }

    public EventVillagerConfig getConfig() {
        return config;
    }

    /** Re-reads event_villagers.yml. @return false if the file was broken (old types kept). */
    public boolean reload() {
        return config.reload();
    }

    /** The type id stored on an event villager, or null if it has none. */
    public @Nullable String getType(Entity entity) {
        return entity.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    /** Points an existing event villager at a different type and applies its trades/outfit/name. */
    public boolean setType(Villager villager, String typeId, @Nullable String nameOverride) {
        ConfigurationSection section = config.getType(typeId);
        if (section == null) return false;
        villager.getPersistentDataContainer()
                .set(typeKey, PersistentDataType.STRING, typeId.toLowerCase(Locale.ROOT));
        villager.customName(
                parseName(
                        nameOverride == null || nameOverride.isBlank()
                                ? section.getString("name", DEFAULT_NAME)
                                : nameOverride));
        villager.setProfession(parseProfession(section.getString("profession", "librarian")));
        villager.setRecipes(buildRecipes(typeId));
        return true;
    }

    public boolean isEventVillager(Entity entity) {
        return entity instanceof Villager
                && entity.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /**
     * Spawns an event villager at the block centre of {@code at}, facing the same way as {@code at}.
     *
     * @param typeId a type id from event_villagers.yml (must exist - check {@link EventVillagerConfig#getType})
     * @param name display name (supports {@code &} colour codes), or null for the type's own name
     */
    public Villager spawn(Location at, String typeId, @Nullable String name) {
        World world = at.getWorld();
        Location loc =
                new Location(
                        world,
                        at.getBlockX() + 0.5,
                        at.getY(),
                        at.getBlockZ() + 0.5,
                        at.getYaw(),
                        0f);

        ConfigurationSection section = config.getType(typeId);
        String displayName =
                (name == null || name.isBlank())
                        ? (section == null ? DEFAULT_NAME : section.getString("name", DEFAULT_NAME))
                        : name;
        Villager.Profession profession =
                parseProfession(
                        section == null
                                ? "librarian"
                                : section.getString("profession", "librarian"));
        List<MerchantRecipe> recipes = buildRecipes(typeId);

        // The consumer runs before the entity is added to the world, so it never ticks
        // un-configured.
        return world.spawn(
                loc,
                Villager.class,
                villager -> {
                    villager.getPersistentDataContainer()
                            .set(key, PersistentDataType.BYTE, (byte) 1);
                    villager.getPersistentDataContainer()
                            .set(
                                    typeKey,
                                    PersistentDataType.STRING,
                                    typeId.toLowerCase(Locale.ROOT));
                    villager.customName(parseName(displayName));
                    villager.setCustomNameVisible(true);
                    villager.setProfession(profession);
                    villager.setVillagerLevel(5);
                    villager.setRecipes(recipes);

                    villager.setInvulnerable(true);
                    villager.setAware(
                            false); // never wanders, panics, sleeps or looks for a workstation
                    villager.setCollidable(false);
                    villager.setSilent(true);
                    villager.setCanPickupItems(false);
                    villager.setPersistent(true);
                    villager.setRemoveWhenFarAway(false);
                });
    }

    /** Every loaded event villager, across all worlds. */
    public List<Villager> getLoaded() {
        List<Villager> found = new ArrayList<>();
        for (World world : plugin.getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (isEventVillager(villager)) found.add(villager);
            }
        }
        return found;
    }

    /**
     * Builds the trade list for a type from its {@code trades} list in event_villagers.yml.
     * Each entry looks like
     * <pre>
     * - cost: "DIAMOND:16"       # required, "ITEM" or "ITEM:amount"
     *   cost2: "GOLD_INGOT:4"    # optional second cost
     *   result: "reroller"       # required
     * </pre>
     * where ITEM is a Material name or one of {@code upgrader_1, upgrader_2, reroller, forged_upgrader}.
     * Bad entries are skipped with a console warning. Every trade is unlimited and ignores
     * hero-of-the-village / reputation discounts.
     */
    public List<MerchantRecipe> buildRecipes(@Nullable String typeId) {
        List<MerchantRecipe> recipes = new ArrayList<>();
        ConfigurationSection section = config.getType(typeId);
        if (section == null) {
            plugin.getLogger()
                    .warning(
                            "Event villager type '"
                                    + typeId
                                    + "' is not in event_villagers.yml - it has no trades.");
            return recipes;
        }
        List<Map<?, ?>> entries = section.getMapList("trades");

        for (int i = 0; i < entries.size(); i++) {
            Map<?, ?> entry = entries.get(i);
            String label = "event_villagers.yml villagers." + typeId + ".trades[" + i + "]";
            try {
                ItemStack cost = parseStack(entry.get("cost"));
                ItemStack cost2 =
                        entry.get("cost2") == null ? null : parseStack(entry.get("cost2"));
                ItemStack result = parseStack(entry.get("result"));
                if (cost == null
                        || result == null
                        || (entry.get("cost2") != null && cost2 == null)) {
                    plugin.getLogger()
                            .warning("Skipping " + label + ": unknown item in cost/cost2/result.");
                    continue;
                }

                MerchantRecipe recipe = new MerchantRecipe(result, 0, Integer.MAX_VALUE, false);
                recipe.addIngredient(cost);
                if (cost2 != null) recipe.addIngredient(cost2);
                recipe.setIgnoreDiscounts(true);
                recipes.add(recipe);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Skipping " + label + ": " + e.getMessage());
            }
        }
        return recipes;
    }

    /** Parses {@code "ITEM"} or {@code "ITEM:amount"} into a stack, or null if the item is unknown. */
    private @Nullable ItemStack parseStack(@Nullable Object raw) {
        if (raw == null) return null;
        String[] parts = raw.toString().trim().split(":");
        String id = parts[0].trim().toLowerCase(Locale.ROOT);
        int amount = 1;
        if (parts.length > 1) {
            amount = Integer.parseInt(parts[1].trim());
        }

        ItemStack stack =
                switch (id) {
                    case "upgrader_1" -> UpgraderItem.make(plugin, 1);
                    case "upgrader_2" -> UpgraderItem.make(plugin, 2);
                    case "reroller" -> RerollerItem.make(plugin);
                    case "forged_upgrader" -> ForgedUpgraderItem.make(plugin);
                    default -> {
                        Material material = Material.matchMaterial(id);
                        yield (material == null || material.isAir() || !material.isItem())
                                ? null
                                : new ItemStack(material);
                    }
                };
        if (stack == null) return null;

        stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
        return stack;
    }

    private static Villager.Profession parseProfession(String name) {
        Villager.Profession profession =
                Registry.VILLAGER_PROFESSION.get(
                        NamespacedKey.minecraft(name.trim().toLowerCase(Locale.ROOT)));
        return profession != null ? profession : Villager.Profession.LIBRARIAN;
    }

    private static Component parseName(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
