package hs.elementSMPRefined.util.example;

import hs.elementSMPRefined.ElementSMPRefined;
import hs.elementSMPRefined.elements.ElementContext;
import hs.elementSMPRefined.elements.abilities.Ability;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Example ability implementation to demonstrate the ability system.
 * This is a template for creating new abilities.
 */
public class ExampleAbility implements Ability {
    private final ElementSMPRefined plugin;
    private final Set<UUID> activePlayers = new HashSet<>();

    public ExampleAbility(ElementSMPRefined plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean execute(ElementContext context) {
        Player player = context.getPlayer();

        // Check if ability is already active (toggle behavior)
        if (isActiveFor(player)) {
            setActive(player, false);
            player.sendMessage("§cExample ability deactivated");
            return true;
        }

        // Activate the ability
        setActive(player, true);
        player.sendMessage("§aExample ability activated");

        // Add your ability logic here
        // For example: deal damage, apply effects, spawn particles, etc.

        return true;
    }

    @Override
    public int getManaCost() {
        return 50;
    }

    @Override
    public int getCooldownSeconds() {
        return 0; // No cooldown for toggle abilities
    }

    @Override
    public int getRequiredUpgradeLevel() {
        return 0;
    }

    @Override
    public String getAbilityId() {
        return "example_ability";
    }

    @Override
    public boolean isActiveFor(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    @Override
    public void setActive(Player player, boolean active) {
        if (active) {
            activePlayers.add(player.getUniqueId());
        } else {
            activePlayers.remove(player.getUniqueId());
        }
    }

    @Override
    public String getName() {
        return "Example Ability";
    }

    @Override
    public String getDescription() {
        return "An example ability template for creating new abilities";
    }
}