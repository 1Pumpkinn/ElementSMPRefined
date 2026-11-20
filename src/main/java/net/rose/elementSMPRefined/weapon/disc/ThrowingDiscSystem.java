package net.rose.elementSMPRefined.weapon.disc;

import net.rose.elementSMPRefined.ElementSMPRefined;
import net.rose.elementevents.mythic.MythicBookProtection;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The throwing disc weapon: one diamond disc that can be upgraded to netherite, and only one of
 * it exists on the whole server. Ported from the Fabric ThrowingDiscs mod.
 * <ul>
 *   <li>{@link DiscRegistry} remembers that the disc was issued (craft / {@code /throwingdisc
 *       give}) so a second one can never be made.</li>
 *   <li>{@link DiscListener} throws it, enforces the one-copy rule on crafting and does the
 *       netherite smithing upgrade.</li>
 *   <li>{@link DiscProtection} keeps the item from being destroyed, so the only copy is never
 *       lost.</li>
 *   <li>{@link ThrownDisc} is a single flight.</li>
 * </ul>
 */
public final class ThrowingDiscSystem {
    private final ElementSMPRefined plugin;
    private final List<ThrownDisc> flights = new ArrayList<>();
    private final NamespacedKey recipeKey;

    private DiscRegistry registry;
    private DiscProtection protection;

    public ThrowingDiscSystem(ElementSMPRefined plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "diamond_throwing_disc");
    }

    public void start() {
        registry = new DiscRegistry(plugin);
        registry.load();
        registry.recoverInFlight();

        registerRecipe();

        protection = new DiscProtection(plugin);
        protection.start();
        plugin.getServer().getPluginManager().registerEvents(protection, plugin);
        plugin.getServer().getPluginManager().registerEvents(new DiscListener(this), plugin);

        PluginCommand command = plugin.getCommand("throwingdisc");
        if (command != null) {
            ThrowingDiscCommand executor = new ThrowingDiscCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            plugin.getLogger().warning("Command 'throwingdisc' is missing from plugin.yml!");
        }

        // Anyone online during a /reload still gets a disc that was waiting for them.
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            migrateInventory(player);
            deliverPending(player);
        }
    }

    /** Ends every flight (the disc goes back to its thrower) and removes the recipe. */
    public void stop() {
        for (ThrownDisc flight : new ArrayList<>(flights)) {
            flight.end();
        }
        if (protection != null) protection.stop();
        plugin.getServer().removeRecipe(recipeKey);
    }

    private void registerRecipe() {
        try {
            ShapedRecipe recipe =
                    new ShapedRecipe(recipeKey, ThrowingDisc.make(plugin, DiscTier.DIAMOND));
            recipe.shape(" D ", "D D", " D ");
            recipe.setIngredient('D', Material.DIAMOND);
            recipe.setCategory(CraftingBookCategory.EQUIPMENT);
            plugin.getServer().removeRecipe(recipeKey);
            plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error registering the throwing disc recipe", e);
        }
    }

    ElementSMPRefined plugin() {
        return plugin;
    }

    DiscRegistry registry() {
        return registry;
    }

    NamespacedKey recipeKey() {
        return recipeKey;
    }

    boolean isFlying(UUID owner) {
        return flights.stream().anyMatch(f -> f.ownerId().equals(owner));
    }

    /** The UUID of the player whose disc is in the air right now, or null. */
    UUID flyingOwner() {
        return flights.isEmpty() ? null : flights.getFirst().ownerId();
    }

    void flightEnded(ThrownDisc flight) {
        flights.remove(flight);
    }

    /** Takes the disc out of the player's hand and throws it. */
    void throwDisc(Player player, EquipmentSlot hand) {
        PlayerInventory inventory = player.getInventory();
        int slot = hand == EquipmentSlot.OFF_HAND ? 40 : inventory.getHeldItemSlot();
        ItemStack held = inventory.getItem(slot);
        DiscTier tier = ThrowingDisc.tierOf(plugin, held);
        if (tier == null || isFlying(player.getUniqueId())) return;

        ItemStack thrown = ThrowingDisc.migrate(plugin, held).clone();
        thrown.setAmount(1);
        ThrownDisc.wear(thrown);

        inventory.setItem(slot, null);
        registry.setInFlight(player.getUniqueId(), thrown);
        try {
            flights.add(ThrownDisc.launch(this, player, tier, thrown, slot));
        } catch (Throwable t) {
            // The throw failed after the disc left the inventory: put it straight back.
            plugin.getLogger().log(Level.SEVERE, "Could not throw the disc", t);
            inventory.setItem(slot, thrown);
            registry.clearInFlight();
        }
    }

    /** Hands a disc to a player, dropping it at their feet if their inventory is full. */
    void give(Player player, ItemStack disc) {
        for (ItemStack leftover :
                player.getInventory().addItem(ThrowingDisc.migrate(plugin, disc)).values()) {
            player.getWorld()
                    .dropItem(
                            player.getLocation(),
                            leftover,
                            item -> {
                                item.setVelocity(new Vector());
                                MythicBookProtection.protect(item);
                            });
        }
    }

    /** Turns a disc from before the hoe -> sword change into the current item, wherever the player carries it. */
    void migrateInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            ItemStack migrated = ThrowingDisc.migrate(plugin, item);
            if (migrated != item) inventory.setItem(slot, migrated);
        }
    }

    void deliverPending(Player player) {
        for (ItemStack disc : registry.takePending(player.getUniqueId())) {
            give(player, disc);
        }
    }

    /** Ends the flight of a player who is leaving, so the disc is saved into their inventory. */
    void recall(UUID owner) {
        for (ThrownDisc flight : new ArrayList<>(flights)) {
            if (flight.ownerId().equals(owner)) flight.end();
        }
    }
}
