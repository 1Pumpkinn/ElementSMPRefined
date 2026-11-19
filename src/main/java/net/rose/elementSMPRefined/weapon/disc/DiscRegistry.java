package net.rose.elementSMPRefined.weapon.disc;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Server-wide state of the one throwing disc, stored in {@code throwing_disc.yml}.
 * <ul>
 *   <li><b>issued</b> - the disc has been crafted or given. Once true nobody can craft another
 *       one, so exactly one exists. Only {@code /throwingdisc reset} clears it.</li>
 *   <li><b>inflight</b> - while the disc is flying it is in nobody's inventory, so the item is
 *       also written here. If the server dies mid-throw the disc is recovered on the next start
 *       instead of vanishing.</li>
 *   <li><b>pending</b> - discs waiting to be handed back to a player the next time they join.</li>
 * </ul>
 */
public final class DiscRegistry {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public DiscRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "throwing_disc.yml");
    }

    public void load() {
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isIssued() {
        return yaml.getBoolean("issued", false);
    }

    public void markIssued() {
        yaml.set("issued", true);
        save();
    }

    /** Forgets that the disc was ever issued, so it can be crafted or given again. */
    public void reset() {
        yaml.set("issued", false);
        yaml.set("inflight", null);
        yaml.set("pending", null);
        save();
    }

    public void setInFlight(UUID owner, ItemStack disc) {
        yaml.set("inflight.owner", owner.toString());
        yaml.set("inflight.item", encode(disc));
        save();
    }

    public void clearInFlight() {
        if (!yaml.contains("inflight")) return;
        yaml.set("inflight", null);
        save();
    }

    /** After a crash: turns a disc that was mid-flight into a pending return for its owner. */
    public void recoverInFlight() {
        String owner = yaml.getString("inflight.owner");
        String item = yaml.getString("inflight.item");
        if (owner == null || item == null) return;
        try {
            queuePending(UUID.fromString(owner), decode(item));
            plugin.getLogger().warning("Recovered a throwing disc that was in flight at shutdown.");
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not recover the in-flight throwing disc", e);
        }
        clearInFlight();
    }

    public void queuePending(UUID owner, ItemStack disc) {
        List<String> list = new ArrayList<>(yaml.getStringList("pending." + owner));
        list.add(encode(disc));
        yaml.set("pending." + owner, list);
        yaml.set("issued", true);
        save();
    }

    public List<ItemStack> takePending(UUID owner) {
        List<String> list = yaml.getStringList("pending." + owner);
        if (list.isEmpty()) return List.of();
        yaml.set("pending." + owner, null);
        save();
        List<ItemStack> out = new ArrayList<>();
        for (String encoded : list) {
            try {
                out.add(decode(encoded));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not read a pending throwing disc", e);
            }
        }
        return out;
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save throwing_disc.yml", e);
        }
    }

    private static String encode(ItemStack stack) {
        return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    private static ItemStack decode(String encoded) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
    }
}
