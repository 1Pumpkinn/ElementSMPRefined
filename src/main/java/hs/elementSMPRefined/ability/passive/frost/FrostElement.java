package hs.elementSMPRefined.ability.passive.frost;

import hs.elementSMPRefined.API.element.BaseElement;
import hs.elementSMPRefined.API.element.ElementType;
import hs.elementSMPRefined.ability.main.frost.FrostCircleAbility;
import hs.elementSMPRefined.ability.main.frost.FrostPunchAbility;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class FrostElement extends BaseElement {
    public static final String META_FROZEN_PUNCH_READY = "frost_frozen_punch_ready";

    public FrostElement(JavaPlugin plugin) {
        super(plugin, new FrostCircleAbility(plugin), new FrostPunchAbility(plugin));
    }

    @Override
    public ElementType getType() {
        return ElementType.FROST;
    }

    @Override
    public void applyUpsides(Player player, int upgradeLevel) {
        // Upsides are handled by FrostPassiveListener
        // Upside 1: Speed 2 when wearing leather boots (always active)
        // Upside 2: Speed 3 on ice (requires upgrade level 2)
    }

    @Override
    public void clearEffects(Player player) {
        super.clearEffects(player);
        player.removeMetadata(META_FROZEN_PUNCH_READY, plugin);
    }

    @Override
    public String getDisplayName() {
        return ChatColor.AQUA + "Frost";
    }

    @Override
    public String getDescription() {
        return "Masters of ice and cold. Frost users can slow enemies and freeze them in their tracks.";
    }

    @Override
    public List<String> getPassiveBenefits() {
        return List.of(
                "Speed II on snow",
                "Speed III on ice (Upgrade II)"
        );
    }
}
