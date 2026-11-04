package net.rose.elementSMPRefined.util.server.potplugin.enchantlimits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import net.rose.elementSMPRefined.core.AbstractElementPlugin;
import net.rose.elementSMPRefined.items.EnchantableMace;

/**
 * Per-enchantment level caps, stored in config.yml as "name=maxLevel"
 * (e.g. {@code sharpness=3}). A cap of 0 means the enchantment isn't allowed
 * at all. Also holds the strip / check logic everything else uses.
 */
public final class EnchantLimits {

    private static final String CONFIG_PATH = "enchant-limits";

    private final AbstractElementPlugin plugin;
    private final Map<String, Integer> limits = new TreeMap<>();

    public EnchantLimits(AbstractElementPlugin plugin) {
        this.plugin = plugin;
    }

    /** Re-reads config.yml from disk (through ConfigManager so it stays in sync), then loads. */
    public void reloadFromDisk() {
        plugin.getConfigManager().reload();
        load();
    }

    public void load() {
        limits.clear();
        for (String raw : plugin.getConfig().getStringList(CONFIG_PATH)) {
            String[] parts = raw.split("=", 2);
            String name = parts.length == 2 ? normalize(parts[0]) : null;
            Integer level = parts.length == 2 ? parseLevel(parts[1]) : null;
            if (name != null && level != null && lookup(name) != null) {
                limits.put(name, level);
            } else {
                plugin.getLogger().warning("Ignoring invalid enchant-limits entry in config.yml: " + raw);
            }
        }
    }

    public void save() {
        List<String> entries = new ArrayList<>();
        limits.forEach((name, level) -> entries.add(name + "=" + level));
        plugin.getConfig().set(CONFIG_PATH, entries);
        plugin.saveConfig();
    }

    public void set(String name, int maxLevel) {
        limits.put(name, maxLevel);
    }

    /** @return true if a limit existed */
    public boolean remove(String name) {
        return limits.remove(name) != null;
    }

    public Map<String, Integer> all() {
        return Map.copyOf(limits);
    }

    public boolean isEmpty() {
        return limits.isEmpty();
    }

    /** The key to store/type for an enchantment, or null if it doesn't exist. "sharpness", or "ns:name" for non-vanilla. */
    public String resolve(String input) {
        String name = normalize(input);
        return lookup(name) == null ? null : name;
    }

    public Enchantment enchantment(String name) {
        return lookup(name);
    }

    public List<String> allEnchantNames() {
        List<String> names = new ArrayList<>();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            names.add(nameOf(enchantment));
        }
        return names;
    }

    /** The cap for this enchantment (0 = not allowed), or null if it has no limit. */
    public Integer maxFor(Enchantment enchantment) {
        return limits.get(nameOf(enchantment));
    }

    /** True if this level is above the cap (or the enchantment isn't allowed). */
    public boolean exceeds(Enchantment enchantment, int level) {
        Integer max = maxFor(enchantment);
        return max != null && level > max;
    }

    /** The one tagged mace given by {@code /enchantablemace} is exempt from every limit. */
    public boolean isExempt(ItemStack item) {
        return EnchantableMace.isTagged(plugin, item);
    }

    /** A mace without the enchantable tag: it can't be enchanted at the table or anvil. */
    public boolean isLockedMace(ItemStack item) {
        return item != null && item.getType() == Material.MACE && !isExempt(item);
    }

    /**
     * True if the item carries an applied enchantment over its cap. Enchanted books are NOT
     * policed: a book holding a banned enchant is harmless on its own, and the anvil check
     * already blocks it from landing on anything except the exempt mace.
     */
    public boolean violates(ItemStack item) {
        if (limits.isEmpty() || item == null || item.getType().isAir() || isExempt(item)) {
            return false;
        }
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            if (exceeds(entry.getKey(), entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Brings the item within the limits: over-cap enchantments are lowered to
     * the cap, disallowed ones are removed.
     * Edits the given stack in place.
     *
     * @return true if anything changed
     */
    public boolean sanitize(ItemStack item) {
        if (!violates(item)) {
            return false;
        }

        for (Map.Entry<Enchantment, Integer> entry : new ArrayList<>(item.getEnchantments().entrySet())) {
            Enchantment enchantment = entry.getKey();
            if (exceeds(enchantment, entry.getValue())) {
                item.removeEnchantment(enchantment);
                int max = maxFor(enchantment);
                if (max > 0) {
                    item.addUnsafeEnchantment(enchantment, max);
                }
            }
        }

        return true;
    }

    /**
     * Same as {@link #sanitize(ItemStack)} but for a plain enchantment map
     * (the enchantment table's pending result).
     *
     * @return true if anything changed
     */
    public boolean sanitize(Map<Enchantment, Integer> enchantments) {
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : new ArrayList<>(enchantments.entrySet())) {
            Enchantment enchantment = entry.getKey();
            if (exceeds(enchantment, entry.getValue())) {
                int max = maxFor(enchantment);
                if (max > 0) {
                    enchantments.put(enchantment, max);
                } else {
                    enchantments.remove(enchantment);
                }
                changed = true;
            }
        }
        return changed;
    }

    private static String nameOf(Enchantment enchantment) {
        NamespacedKey key = enchantment.getKey();
        return key.getNamespace().equals(NamespacedKey.MINECRAFT) ? key.getKey() : key.toString();
    }

    private static Enchantment lookup(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.contains(":") ? name : "minecraft:" + name);
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    private static String normalize(String raw) {
        String name = raw.trim().toLowerCase(Locale.ROOT);
        return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
    }

    private static Integer parseLevel(String raw) {
        try {
            int level = Integer.parseInt(raw.trim());
            return level >= 0 ? level : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
